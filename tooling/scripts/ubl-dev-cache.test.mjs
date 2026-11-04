import assert from "node:assert/strict"
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, resolve } from "node:path"
import test from "node:test"
import {
  canReuseGeneratedUblSources,
  recordGeneratedUblSources,
  ublCachePaths,
} from "./ubl-dev-cache.mjs"

test("reuses generated UBL sources only while inputs and outputs are complete", () => {
  const workspace = createWorkspace()
  try {
    recordGeneratedUblSources(workspace)
    assert.equal(canReuseGeneratedUblSources(workspace), true)

    writeFileSync(resolve(workspace, "vendor/ubl/2.1/json-schema/order.json"), "changed schema")
    assert.equal(canReuseGeneratedUblSources(workspace), false)
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})

test("invalidates the cache when generator configuration changes", () => {
  const workspace = createWorkspace()
  try {
    recordGeneratedUblSources(workspace)
    writeFileSync(resolve(workspace, "standards/ubl/pom.xml"), "changed generator configuration")
    assert.equal(canReuseGeneratedUblSources(workspace), false)
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})

test("invalidates the cache when generated output changes or disappears", () => {
  const workspace = createWorkspace()
  try {
    const generatedFile = resolve(workspace, "standards/ubl/target/generated-sources/java/Order.java")
    recordGeneratedUblSources(workspace)
    writeFileSync(generatedFile, "corrupt output")
    assert.equal(canReuseGeneratedUblSources(workspace), false)

    recordGeneratedUblSources(workspace)
    rmSync(generatedFile)
    assert.equal(canReuseGeneratedUblSources(workspace), false)
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})

test("treats malformed cache metadata as a cache miss", () => {
  const workspace = createWorkspace()
  try {
    const { cacheFile } = ublCachePaths(workspace)
    mkdirSync(dirname(cacheFile), { recursive: true })
    writeFileSync(cacheFile, "not json")
    assert.equal(canReuseGeneratedUblSources(workspace), false)
  } finally {
    rmSync(workspace, { recursive: true, force: true })
  }
})

function createWorkspace() {
  const workspace = mkdtempSync(resolve(tmpdir(), "edispark-ubl-cache-"))
  const paths = [
    ["standards/ubl/pom.xml", "generator configuration"],
    ["vendor/ubl/2.1/json-schema/order.json", "order schema"],
    ["standards/ubl/target/generated-sources/java/Order.java", "generated order"],
    ["standards/ubl/target/generated-sources/java/common/Identifier.java", "generated identifier"],
  ]
  for (const [path, contents] of paths) {
    const file = resolve(workspace, path)
    mkdirSync(dirname(file), { recursive: true })
    writeFileSync(file, contents)
  }
  return workspace
}
