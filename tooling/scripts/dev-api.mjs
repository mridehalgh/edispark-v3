import { localEnvironment, workspaceDirectory } from "./local-environment.mjs"
import { releasePorts } from "./ports.mjs"
import { mirrorExit, start, waitForExit } from "./processes.mjs"

const cwd = workspaceDirectory()
const env = await localEnvironment()
const managedByRootRunner = env.EDISPARK_PORTS_PREPARED === "true"
if (!managedByRootRunner) {
  await releasePorts([
    { name: "embedded DynamoDB", port: new URL(env.DYNAMODB_LOCAL_ENDPOINT ?? "http://localhost:8000").port || 8000 },
    { name: "documents API", port: new URL(env.DOCUMENTS_API_URL).port || 8080 },
  ])
}
const build = start("./mvnw", [
  "-B",
  "-pl", "apps/documents-api",
  "-am",
  "-Plocal-dev",
  "-DskipTests",
  "install",
], { cwd, detached: !managedByRootRunner, env })

const buildExitCode = await waitForExit(build)
if (buildExitCode !== 0) {
  process.exitCode = buildExitCode
} else {
  const child = start("./mvnw", [
    "-B",
    "-f", "apps/documents-api/pom.xml",
    "-Plocal-dev",
    "-DskipTests",
    "spring-boot:run",
  ], { cwd, detached: !managedByRootRunner, env })
  mirrorExit(child)
}
