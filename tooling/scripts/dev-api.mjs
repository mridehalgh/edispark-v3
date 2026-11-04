import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { ensureAs2Lab } from "./as2-lab.mjs"
import { releasePorts } from "./ports.mjs"
import { mirrorExit, start, waitForExit } from "./processes.mjs"
import { canReuseGeneratedUblSources, recordGeneratedUblSources } from "./ubl-dev-cache.mjs"
import { resolve } from "node:path"

const cwd = workspaceDirectory()
const env = await localEnvironment()
ensureAs2Lab()
const managedByRootRunner = env.EDISPARK_PORTS_PREPARED === "true"
if (!managedByRootRunner) {
  await releasePorts([
    { name: "embedded DynamoDB", port: new URL(env.DYNAMODB_LOCAL_ENDPOINT ?? "http://localhost:8000").port || 8000 },
    { name: "EdiSpark API", port: new URL(env.EDISPARK_API_URL).port || 8080 },
  ])
}
const reuseGeneratedUblSources = canReuseGeneratedUblSources(cwd)
console.log(reuseGeneratedUblSources
  ? "Reusing verified generated UBL model sources."
  : "Generating and verifying UBL model sources.")
const buildArguments = [
  "-B",
  "-T", "1C",
  "-pl", "apps/edispark-api",
  "-am",
  "-Plocal-dev",
  "-Dmaven.test.skip=true",
]
if (reuseGeneratedUblSources) buildArguments.push("-Djsonschema2pojo.skip=true")
buildArguments.push("package")
const build = start("./mvnw", buildArguments, { cwd, detached: !managedByRootRunner, env })

const buildExitCode = await waitForExit(build)
if (buildExitCode !== 0) {
  process.exitCode = buildExitCode
} else {
  if (!reuseGeneratedUblSources) recordGeneratedUblSources(cwd)
  const executableJar = resolve(cwd, "apps/edispark-api/target/edispark-api-dev.jar")
  const child = start("java", ["-XX:TieredStopAtLevel=1", "-jar", executableJar], {
    cwd: resolve(cwd, "apps/edispark-api"),
    detached: !managedByRootRunner,
    env,
  })
  mirrorExit(child)
}
