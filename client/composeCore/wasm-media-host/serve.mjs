/* This is a private project. All rights reserved. */
import { startSite } from "./verification-site.mjs";
const site = await startSite();
console.info(`Compose media experiment: ${site.origin}/compose/`);
process.on("SIGINT", () => site.close());
process.on("SIGTERM", () => site.close());
