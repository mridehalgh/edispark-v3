import { localEnvironment, workspaceDirectory } from './local-environment.mjs'
import { releasePorts } from './ports.mjs'
import { mirrorExit, start, waitForExit } from './processes.mjs'

const cwd = workspaceDirectory()
const env = await localEnvironment()
const managedByRootRunner = env.EDISPARK_PORTS_PREPARED === 'true'
if (!managedByRootRunner) {
  await releasePorts([{ name: 'integration API', port: new URL(env.INTEGRATION_API_URL).port || 8083 }])
}

const build = start('./mvnw', ['-B', '-pl', 'apps/integration-api', '-am', '-DskipTests', 'install'], {
  cwd, detached: !managedByRootRunner, env,
})
const buildExitCode = await waitForExit(build)
if (buildExitCode !== 0) {
  process.exitCode = buildExitCode
} else {
  const child = start('./mvnw', ['-B', '-f', 'apps/integration-api/pom.xml', '-DskipTests', 'spring-boot:run'], {
    cwd, detached: !managedByRootRunner, env,
  })
  mirrorExit(child)
}
