/*
 * This is a private project. All rights reserved.
 */

package com.storyteller_f.a.dev.appium;

import com.sun.jna.Pointer;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Function;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.ptr.IntByReference;
import io.appium.java_client.AppiumDriver;
import java.util.ArrayList;
import java.util.List;

/** Switch only between the launched JVM's native frame and its dialogs. */
public final class DesktopWindowsWindowTracker {
    private final AppiumDriver driver;
    private final int processId;
    private final HWND mainWindow;
    private final int guiThreadId;
    private String currentHandle;

    public DesktopWindowsWindowTracker(AppiumDriver driver, long processId) {
        this.driver = driver;
        this.processId = Math.toIntExact(processId);
        currentHandle = driver.getWindowHandle();
        mainWindow = new HWND(Pointer.createConstant(Long.parseUnsignedLong(currentHandle.substring(2), 16)));
        IntByReference owner = new IntByReference();
        guiThreadId = User32.INSTANCE.GetWindowThreadProcessId(mainWindow, owner);
        if (owner.getValue() != this.processId) {
            throw new IllegalStateException("DesktopDriver window does not belong to the launched test JVM");
        }
    }

    public void synchronizeWindow() {
        HWND foreground = User32.INSTANCE.GetForegroundWindow();
        HWND target = belongsToApp(foreground) ? foreground : findDialogOrMainWindow();
        String handle = "0x" + Long.toHexString(Pointer.nativeValue(target.getPointer()));
        if (!handle.equals(currentHandle) || !belongsToApp(foreground)) {
            driver.switchTo().window(handle);
            currentHandle = handle;
        }
    }

    /** Java-agent roots cannot use DesktopDriver's UIA resize path. */
    public void resizeWindow(int width, int height) {
        int dpi = NativeLibrary.getInstance("user32").getFunction("GetDpiForWindow", Function.ALT_CONVENTION)
                .invokeInt(new Object[] {mainWindow});
        if (dpi <= 0) throw new IllegalStateException("The owned test window has no DPI");
        int physicalWidth = Math.round(width * dpi / 96.0f);
        int physicalHeight = Math.round(height * dpi / 96.0f);
        User32.INSTANCE.ShowWindow(mainWindow, 9);
        if (!User32.INSTANCE.SetWindowPos(mainWindow, null, 0, 0, physicalWidth, physicalHeight, 0x0002 | 0x0004)) {
            throw new IllegalStateException("Failed to resize the owned test window");
        }
    }

    private HWND findDialogOrMainWindow() {
        List<HWND> dialogs = new ArrayList<>();
        User32.INSTANCE.EnumThreadWindows(guiThreadId, (window, ignored) -> {
            if (belongsToApp(window) && User32.INSTANCE.IsWindowVisible(window)) {
                char[] className = new char[256];
                int length = User32.INSTANCE.GetClassName(window, className, className.length);
                if (new String(className, 0, length).equals("SunAwtDialog")) dialogs.add(window);
            }
            return true;
        }, null);
        return dialogs.isEmpty() ? mainWindow : dialogs.getFirst();
    }

    private boolean belongsToApp(HWND window) {
        if (window == null) return false;
        IntByReference owner = new IntByReference();
        User32.INSTANCE.GetWindowThreadProcessId(window, owner);
        return owner.getValue() == processId;
    }
}
