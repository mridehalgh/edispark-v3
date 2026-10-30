import { rm } from "node:fs/promises"
import { resolve } from "node:path"

const workspace = resolve(import.meta.dirname, "../..")
const localDirectory = resolve(workspace, ".local")
if (localDirectory !== resolve(workspace, ".local")) throw new Error("Refusing to remove an unexpected path")
await rm(localDirectory, { recursive: true, force: true })
console.info("Removed local EdiSpark state from .local/")
