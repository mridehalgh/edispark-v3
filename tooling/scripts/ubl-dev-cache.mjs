import { createHash } from "node:crypto"
import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  statSync,
  writeFileSync,
} from "node:fs"
import { relative, resolve } from "node:path"

const CACHE_VERSION = 1
const defaultWorkspace = resolve(import.meta.dirname, "../..")

export function canReuseGeneratedUblSources(workspace = defaultWorkspace) {
  const paths = ublCachePaths(workspace)
  if (!existsSync(paths.cacheFile)) return false

  try {
    const cache = JSON.parse(readFileSync(paths.cacheFile, "utf8"))
    if (cache.version !== CACHE_VERSION) return false
    if (cache.inputFingerprint !== inputFingerprint(paths)) return false
    if (!Array.isArray(cache.outputs) || cache.outputs.length === 0) return false

    const currentOutputs = outputManifest(paths)
    return JSON.stringify(cache.outputs) === JSON.stringify(currentOutputs)
  } catch {
    return false
  }
}

export function recordGeneratedUblSources(workspace = defaultWorkspace) {
  const paths = ublCachePaths(workspace)
  const outputs = outputManifest(paths)
  if (outputs.length === 0) {
    throw new Error(`UBL generation produced no Java sources in ${paths.generatedSources}`)
  }

  const cache = {
    version: CACHE_VERSION,
    inputFingerprint: inputFingerprint(paths),
    outputs,
  }
  mkdirSync(paths.targetDirectory, { recursive: true })
  const temporaryFile = `${paths.cacheFile}.tmp-${process.pid}`
  writeFileSync(temporaryFile, `${JSON.stringify(cache, null, 2)}\n`)
  renameSync(temporaryFile, paths.cacheFile)
}

export function ublCachePaths(workspace = defaultWorkspace) {
  const moduleDirectory = resolve(workspace, "standards/ubl")
  const targetDirectory = resolve(moduleDirectory, "target")
  return {
    cacheFile: resolve(targetDirectory, "edispark-dev-cache.json"),
    generatedSources: resolve(targetDirectory, "generated-sources/java"),
    modulePom: resolve(moduleDirectory, "pom.xml"),
    schemaDirectory: resolve(workspace, "vendor/ubl/2.1/json-schema"),
    targetDirectory,
    workspace,
  }
}

function inputFingerprint(paths) {
  return hashFiles([
    paths.modulePom,
    ...listFiles(paths.schemaDirectory),
  ], paths.workspace)
}

function outputManifest(paths) {
  return listFiles(paths.generatedSources)
    .filter((file) => file.endsWith(".java"))
    .map((file) => ({
      path: relative(paths.generatedSources, file),
      size: statSync(file).size,
      sha256: hashFile(file),
    }))
}

function hashFiles(files, root) {
  const hash = createHash("sha256")
  for (const file of files) {
    hash.update(relative(root, file))
    hash.update("\0")
    hash.update(readFileSync(file))
    hash.update("\0")
  }
  return hash.digest("hex")
}

function hashFile(file) {
  return createHash("sha256").update(readFileSync(file)).digest("hex")
}

function listFiles(directory) {
  if (!existsSync(directory)) return []
  return readdirSync(directory, { withFileTypes: true })
    .flatMap((entry) => {
      const path = resolve(directory, entry.name)
      return entry.isDirectory() ? listFiles(path) : [path]
    })
    .sort()
}
