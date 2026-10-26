/* This is a private project. All rights reserved. */
import { mkdir } from "node:fs/promises";
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { work } from "./verification-site.mjs";
await mkdir(work, { recursive: true });
const ffmpeg = process.env.MEDIA_FFMPEG ?? "ffmpeg";
function generate(name, args) {
  execFileSync(
    ffmpeg,
    ["-hide_banner", "-loglevel", "error", ...args, "-y", resolve(work, name)],
    { stdio: "inherit", windowsHide: true },
  );
}
generate("fixture.webm", [
  "-f",
  "lavfi",
  "-i",
  "testsrc2=size=320x180:rate=30",
  "-f",
  "lavfi",
  "-i",
  "sine=frequency=440:sample_rate=48000",
  "-t",
  "6",
  "-c:v",
  "libvpx-vp9",
  "-b:v",
  "350k",
  "-c:a",
  "libopus",
]);
for (const [name, frequency] of [
  ["fixture.wav", 660],
  ["fixture-alt.wav", 880],
]) {
  generate(name, [
    "-f",
    "lavfi",
    "-i",
    `sine=frequency=${frequency}:sample_rate=48000`,
    "-t",
    "6",
  ]);
}
generate("silent.webm", [
  "-i",
  resolve(work, "fixture.webm"),
  "-an",
  "-c:v",
  "copy",
]);
generate("fixture.mp4", [
  "-i",
  resolve(work, "fixture.webm"),
  "-c:v",
  "libx264",
  "-pix_fmt",
  "yuv420p",
  "-c:a",
  "aac",
  "-movflags",
  "+faststart",
]);
for (const [name, size] of [
  ["fixture-hd.mp4", "1280x720"],
  ["fixture-fhd.mp4", "1920x1080"],
]) {
  generate(name, [
    "-f",
    "lavfi",
    "-i",
    `testsrc2=size=${size}:rate=30`,
    "-f",
    "lavfi",
    "-i",
    "sine=frequency=440:sample_rate=48000",
    "-t",
    "4",
    "-c:v",
    "libx264",
    "-preset",
    "ultrafast",
    "-pix_fmt",
    "yuv420p",
    "-c:a",
    "aac",
    "-movflags",
    "+faststart",
  ]);
}

generate("unsupported.mov", [
  "-f",
  "lavfi",
  "-i",
  "testsrc2=size=320x180:rate=30",
  "-t",
  "1",
  "-c:v",
  "prores_ks",
  "-profile:v",
  "0",
  "-pix_fmt",
  "yuv422p10le",
]);

generate("sync.mp4", [
  "-f",
  "lavfi",
  "-i",
  "color=c=black:size=320x180:rate=30:duration=8",
  "-f",
  "lavfi",
  "-i",
  "aevalsrc=exprs='0.2*sin(2*PI*1000*t)*lt(mod(t+0.5,1),0.1)':s=48000:d=8",
  "-vf",
  "drawbox=color=white:t=fill:enable='lt(mod(t+0.5,1),0.1)'",
  "-c:v",
  "libx264",
  "-preset",
  "ultrafast",
  "-pix_fmt",
  "yuv420p",
  "-c:a",
  "aac",
  "-movflags",
  "+faststart",
]);

generate("fixture.mp3", [
  "-i",
  resolve(work, "fixture.wav"),
  "-c:a",
  "libmp3lame",
]);

generate("rotated.mp4", [
  "-i",
  resolve(work, "fixture.mp4"),
  "-c",
  "copy",
  "-metadata:s:v:0",
  "rotate=90",
]);
