/* This is a private project. All rights reserved. */
import { createServer } from "node:http";
import { readFile, mkdir } from "node:fs/promises";
import { resolve, extname, relative, isAbsolute } from "node:path";
import { pathToFileURL } from "node:url";
export const root = resolve(import.meta.dirname, "../../..");
export const work = resolve(root, "build/wasm-media-research");
export const composeBuild = process.env.MEDIA_COMPOSE_BUILD ?? "development";
if (!["development", "production"].includes(composeBuild))
  throw new Error("Unknown Compose build");
export const chromiumBinaries = Object.freeze({
  chrome:
    process.env.MEDIA_CHROME_BINARY ??
    "C:/Program Files/Google/Chrome/Application/chrome.exe",
  edge:
    process.env.MEDIA_EDGE_BINARY ??
    "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe",
});
export async function startSite() {
  const temporary = resolve(work, "browser-temp");
  await mkdir(temporary, { recursive: true });
  process.env.TMP = temporary;
  process.env.TEMP = temporary;
  process.env.TMPDIR = temporary;

  const { build } = await import(
    pathToFileURL(resolve(work, "browser/node_modules/esbuild/lib/main.js"))
  );
  const source = resolve(root, "client/composeCore/wasm-media-host");
  await mkdir(resolve(work, "site"), { recursive: true });
  for (const name of ["host", "decode-worker", "counter-observer"])
    await build({
      entryPoints: [resolve(source, `${name}.mjs`)],
      bundle: true,
      format: "esm",
      platform: "browser",
      outfile: resolve(work, `site/${name}.mjs`),
    });
  const html = `<!doctype html><button id="play">Play</button><script type="module">
import { createHost } from '/host.mjs';
window.states=[]; window.hashes=[];
window.host=createHost(new URL(location.href).searchParams.get('src')??'/fixture.webm', s=>states.push(s), f=>{
 let hash=0; const bytes=new Uint8Array(f.pixels); for(let i=0;i<bytes.length;i+=97)hash=(hash*31+bytes[i])|0;
 hashes.push(hash);
});
document.querySelector('#play').onclick=()=>host.play();
</script>`;
  const server = createServer(async (req, res) => {
    try {
      const name = new URL(req.url, "http://localhost").pathname;
      if (name === "/compose-bootstrap.html") {
        res.setHeader("Content-Type", "text/html");
        res.end(
          '<!doctype html><html><head><base href="/compose/"><style>html,body{width:100%;height:100%;margin:0;overflow:hidden}</style></head><body></body></html>',
        );
        return;
      }
      if (name === "/favicon.ico") {
        res.writeHead(204).end();
        return;
      }
      if (name === "/") {
        res.setHeader("Content-Type", "text/html");
        res.end(html);
        return;
      }
      if (name.includes("..")) {
        res.writeHead(400).end();
        return;
      }
      const directory = name.startsWith("/compose/")
        ? resolve(
            root,
            `dev/wasmMediaExperiment/build/dist/wasmJs/${composeBuild}Executable`,
          )
        : work;
      const child = name.startsWith("/compose/")
        ? name.slice("/compose/".length) || "index.html"
        : name.endsWith(".mjs")
          ? `site/${name.slice(1)}`
          : name.slice(1);
      const file = resolve(directory, child);
      const within = relative(directory, file);
      if (within.startsWith("..") || isAbsolute(within)) {
        res.writeHead(400).end();
        return;
      }
      const bytes = await readFile(file);
      res.setHeader(
        "Content-Type",
        {
          ".mjs": "text/javascript",
          ".js": "text/javascript",
          ".wasm": "application/wasm",
          ".html": "text/html",
          ".css": "text/css",
          ".mp4": "video/mp4",
          ".webm": "video/webm",
          ".wav": "audio/wav",
        }[extname(file)] ?? "application/octet-stream",
      );
      res.setHeader("Accept-Ranges", "bytes");
      if (req.headers.range) {
        const [, startValue, endValue] = /bytes=(\d+)-(\d*)/.exec(
          req.headers.range,
        );
        const start = Number(startValue),
          end = endValue
            ? Math.min(Number(endValue), bytes.length - 1)
            : bytes.length - 1;
        res.writeHead(206, {
          "Content-Range": `bytes ${start}-${end}/${bytes.length}`,
          "Content-Length": end - start + 1,
        });
        res.end(bytes.subarray(start, end + 1));
      } else {
        res.setHeader("Content-Length", bytes.length);
        res.end(bytes);
      }
    } catch {
      res.writeHead(404).end();
    }
  });
  await new Promise((done) => server.listen(0, "127.0.0.1", done));
  const origin = `http://127.0.0.1:${server.address().port}`;
  return {
    origin,
    close: () => {
      server.closeAllConnections();
      server.close();
    },
  };
}
