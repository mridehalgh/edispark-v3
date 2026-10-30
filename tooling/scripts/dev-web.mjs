import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { releasePorts } from "./ports.mjs"
import { mirrorExit, start } from "./processes.mjs"

const env = await localEnvironment()
const managedByRootRunner = env.EDISPARK_PORTS_PREPARED === "true"
if (!managedByRootRunner) {
  await releasePorts([{ name: "web application", port: new URL(env.BETTER_AUTH_URL).port || 3000 }])
}
const child = start("pnpm", ["--filter", "@edispark/web", "dev"], {
  cwd: workspaceDirectory(),
  detached: !managedByRootRunner,
  env,
})
mirrorExit(child)
