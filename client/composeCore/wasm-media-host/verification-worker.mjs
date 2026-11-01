/* This is a private project. All rights reserved. */
import "node:test";
import { pathToFileURL } from "node:url";
import { writeFile, rename } from "node:fs/promises";

try {
  await import(pathToFileURL(process.argv[2]));
} catch (error) {
  console.error(error);
  process.exitCode = 1;
} finally {
  // A completed test flow is distinct from root exit and container release.
  const file = process.env.MEDIA_WORKER_COMPLETION_FILE;
  if (file) {
    const temporary = `${file}.tmp`;
    await writeFile(
      temporary,
      JSON.stringify({ token: process.env.MEDIA_WORKER_TOKEN }),
    );
    await rename(temporary, file);
  }
}
