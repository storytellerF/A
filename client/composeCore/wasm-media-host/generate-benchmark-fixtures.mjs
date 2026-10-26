/* This is a private project. All rights reserved. */
import { execFileSync } from "node:child_process";
import { resolve } from "node:path";
import { work } from "./verification-site.mjs";
const ffmpeg = process.env.MEDIA_FFMPEG ?? "ffmpeg";
for (const [w, h] of [
  [1280, 720],
  [1920, 1080],
])
  for (const fps of [30, 60]) {
    const filters = [
      `drawbox=x=32:y=32:w=128:h=16:color=black:t=fill`,
      ...Array.from(
        { length: 16 },
        (_, bit) =>
          `drawbox=x=${32 + bit * 8}:y=32:w=8:h=16:color=white:t=fill:enable='mod(trunc(n/${2 ** bit}),2)'`,
      ),
      "drawbox=x=0:y=0:w=4:h=ih:color=red:t=fill",
      "drawbox=x=iw-4:y=0:w=4:h=ih:color=lime:t=fill",
      "drawbox=x=0:y=0:w=iw:h=4:color=yellow:t=fill",
      "drawbox=x=0:y=ih-4:w=iw:h=4:color=blue:t=fill",
    ];
    execFileSync(
      ffmpeg,
      [
        "-hide_banner",
        "-loglevel",
        "error",
        "-f",
        "lavfi",
        "-i",
        `testsrc2=size=${w}x${h}:rate=${fps}`,
        "-f",
        "lavfi",
        "-i",
        "sine=frequency=440:sample_rate=48000",
        "-t",
        "14",
        "-vf",
        filters.join(","),
        "-c:v",
        "libx264",
        "-preset",
        "ultrafast",
        "-crf",
        "18",
        "-pix_fmt",
        "yuv420p",
        "-c:a",
        "aac",
        "-movflags",
        "+faststart",
        "-y",
        resolve(work, `counter-${h}-${fps}.mp4`),
      ],
      { stdio: "inherit", windowsHide: true },
    );
    console.info(`Generated ${w}x${h} ${fps}fps counter fixture`);
  }
