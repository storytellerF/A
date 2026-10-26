/* This is a private project. All rights reserved. */
using System;
using System.Collections.Generic;
using System.Diagnostics;
using System.Runtime.InteropServices;
using System.Threading;

// Diagnostic-only independent observation of the Windows desktop compositor.
// This is not a photodiode or a guarantee about panel scanout.
public static class DesktopFrameProbe {
    [DllImport("user32.dll")] static extern IntPtr GetDC(IntPtr window);
    [DllImport("user32.dll")] static extern int ReleaseDC(IntPtr window, IntPtr dc);
    [DllImport("user32.dll")] static extern int GetSystemMetrics(int index);
    [DllImport("user32.dll")] static extern IntPtr SetThreadDpiAwarenessContext(IntPtr context);
    [DllImport("user32.dll")] static extern IntPtr GetForegroundWindow();
    [DllImport("user32.dll")] static extern uint GetWindowThreadProcessId(IntPtr window, out uint process);
    [DllImport("gdi32.dll")] static extern IntPtr CreateCompatibleDC(IntPtr dc);
    [DllImport("gdi32.dll")] static extern IntPtr SelectObject(IntPtr dc, IntPtr value);
    [DllImport("gdi32.dll")] static extern bool DeleteObject(IntPtr value);
    [DllImport("gdi32.dll")] static extern bool DeleteDC(IntPtr dc);
    [DllImport("gdi32.dll")] static extern bool BitBlt(IntPtr target, int x, int y, int w, int h, IntPtr source, int sx, int sy, uint flags);
    [DllImport("gdi32.dll")] static extern IntPtr CreateDIBSection(IntPtr dc, ref BitmapInfo info, uint usage, out IntPtr bits, IntPtr section, uint offset);
    [StructLayout(LayoutKind.Sequential)] struct BitmapInfo {
        public uint Size; public int Width, Height; public ushort Planes, BitCount;
        public uint Compression, ImageSize; public int XPels, YPels; public uint Colors, Important;
    }
    public sealed class Bounds {
        public int X {get; internal set;} public int Y {get; internal set;}
        public int Width {get; internal set;} public int Height {get; internal set;}
        public int ScreenWidth {get; internal set;} public int ScreenHeight {get; internal set;}
    }
    public sealed class Frame {
        public long Epoch {get; internal set;} public int Id {get; internal set;}
        public double ReadMs {get; internal set;}
    }
    public sealed class Result {
        public string Error {get; internal set;}
        public IReadOnlyList<Frame> Frames {get; internal set;}
    }
    sealed class Bitmap : IDisposable {
        readonly IntPtr screen, memory, bitmap, previous, bits;
        readonly int width, height; readonly byte[] pixels;
        public Bitmap(int w, int h) {
            width=w; height=h; pixels=new byte[w*h*4];
            screen=GetDC(IntPtr.Zero); memory=CreateCompatibleDC(screen);
            var info=new BitmapInfo {Size=(uint)Marshal.SizeOf<BitmapInfo>(),Width=w,Height=-h,Planes=1,BitCount=32};
            bitmap=CreateDIBSection(screen, ref info, 0, out bits, IntPtr.Zero, 0);
            if(screen==IntPtr.Zero || memory==IntPtr.Zero || bitmap==IntPtr.Zero) {Dispose(); throw new InvalidOperationException("Desktop capture allocation failed");}
            previous=SelectObject(memory,bitmap);
        }
        public byte[] Read(int x,int y) {
            if(!BitBlt(memory,0,0,width,height,screen,x,y,0x40CC0020)) throw new InvalidOperationException("Desktop capture failed");
            Marshal.Copy(bits,pixels,0,pixels.Length); return pixels;
        }
        public void Dispose() {
            if(previous!=IntPtr.Zero) SelectObject(memory,previous);
            if(bitmap!=IntPtr.Zero) DeleteObject(bitmap);
            if(memory!=IntPtr.Zero) DeleteDC(memory);
            if(screen!=IntPtr.Zero) ReleaseDC(IntPtr.Zero,screen);
        }
    }
    static void RequireForeground(int process) {
        uint actual; GetWindowThreadProcessId(GetForegroundWindow(),out actual);
        if(actual!=(uint)process) throw new InvalidOperationException($"Owned browser is not foreground (expected {process}, actual {actual})");
    }
    public static void WaitForForeground(int process,int seconds) {
        var watch=Stopwatch.StartNew();
        while(watch.Elapsed.TotalSeconds<seconds) {
            uint actual;GetWindowThreadProcessId(GetForegroundWindow(),out actual);
            if(actual==(uint)process) return;
            Thread.Sleep(100);
        }
        RequireForeground(process);
    }
    static bool Color(byte[] data,int width,int x,int y,int color) {
        int i=(y*width+x)*4; int b=data[i],g=data[i+1],r=data[i+2];
        if(color==0) return r>170 && g<100 && b<100;
        if(color==1) return g>170 && r<100 && b<100;
        if(color==2) return r>170 && g>170 && b<100;
        return b>170 && r<100 && g<100;
    }
    public static Bounds Calibrate(int process) {
        var previous=SetThreadDpiAwarenessContext(new IntPtr(-4));
        try {
            RequireForeground(process);
            int w=GetSystemMetrics(0),h=GetSystemMetrics(1);
            using(var bitmap=new Bitmap(w,h)) {
                var data=bitmap.Read(0,0);
                for(int x=0;x<=w-1920;x++) {
                    int run=0;
                    for(int y=0;y<h;y++) {
                        run=Color(data,w,x,y,0)?run+1:0;
                        if(run<1000) continue;
                        for(int top=Math.Max(0,y-1080);top<=Math.Min(y-999,h-1080);top++) {
                            if(Color(data,w,x+960,top+2,2) && Color(data,w,x+960,top+1077,3)
                                && Color(data,w,x+1917,top+540,1) && Color(data,w,x+2,top+540,0)) {
                                int edgeTop=top+2;while(edgeTop>0 && Color(data,w,x+960,edgeTop-1,2)) edgeTop--;
                                return new Bounds {X=x,Y=edgeTop,Width=1920,Height=1080,ScreenWidth=w,ScreenHeight=h};
                            }
                        }
                        break;
                    }
                }
            }
            throw new InvalidOperationException("A fully visible physical 1920x1080 calibrated video was not found");
        } finally {SetThreadDpiAwarenessContext(previous);}
    }
    static Thread thread; static volatile bool stopping; static string error;
    static readonly List<Frame> frames=new List<Frame>();
    public static void Start(Bounds bounds,int process,int sourceWidth,bool samplePixels) {
        if(thread!=null) throw new InvalidOperationException("Probe already started");
        stopping=false; error=""; frames.Clear();
        thread=new Thread(()=> {
            var previous=SetThreadDpiAwarenessContext(new IntPtr(-4));
            try {
                double scale=1920.0/sourceWidth; int width=(int)Math.Round(128*scale);
                using(var bitmap=new Bitmap(width,2)) {
                    var watch=Stopwatch.StartNew(); double next=0;
                    while(!stopping) {
                        RequireForeground(process);
                        double begin=watch.Elapsed.TotalMilliseconds;
                        if(!samplePixels){Thread.Sleep(8);continue;}
                        var data=bitmap.Read(bounds.X+(int)Math.Round(32*scale),bounds.Y+(int)Math.Round(40*scale));
                        int id=0;
                        for(int bit=0;bit<16;bit++) {int i=(int)Math.Floor((bit*8+4)*scale)*4; if(data[i]+data[i+1]+data[i+2]>384) id|=1<<bit;}
                        frames.Add(new Frame {Epoch=DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),Id=id,ReadMs=watch.Elapsed.TotalMilliseconds-begin});
                        next+=1000.0/120;
                        int delay=(int)Math.Floor(next-watch.Elapsed.TotalMilliseconds);
                        if(delay>0) Thread.Sleep(delay); else next=watch.Elapsed.TotalMilliseconds;
                    }
                }
            } catch(Exception failure) {error=failure.Message;}
            finally {SetThreadDpiAwarenessContext(previous);}
        });
        thread.IsBackground=true; thread.Start();
    }
    public static Result Stop() {
        stopping=true; if(thread!=null) thread.Join(); thread=null;
        return new Result {Error=error,Frames=frames.AsReadOnly()};
    }
}
