import { copyFileSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const outDir = resolve(root, "public/dist-widget");

mkdirSync(outDir, { recursive: true });

for (const file of ["chatbot-widget.iife.js", "widget.css"]) {
  copyFileSync(resolve(root, "dist-widget", file), resolve(outDir, file));
}

console.log("[sync-widget] copied dist-widget → public/dist-widget");
