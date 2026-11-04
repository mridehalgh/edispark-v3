import assert from "node:assert/strict"
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from "node:fs"
import { tmpdir } from "node:os"
import { resolve } from "node:path"
import test from "node:test"
import { as2LabIsComplete, as2LabPaths, ensureAs2Lab } from "./as2-lab.mjs"

test("repairs an incomplete AS2 lab before API startup", () => {
  const directory = mkdtempSync(resolve(tmpdir(), "edispark-as2-lab-"))
  try {
    writeFileSync(resolve(directory, "edispark.p12"), "partial")
    let generated = false

    const paths = ensureAs2Lab({
      directory,
      generate: ({ force }) => {
        assert.equal(force, true)
        generated = true
        writeManifest(directory)
      },
    })

    assert.equal(generated, true)
    assert.equal(paths.passwordFile, resolve(directory, "password.txt"))
    assert.equal(as2LabIsComplete(directory), true)
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
})

test("keeps a complete AS2 lab unchanged on restart", () => {
  const directory = mkdtempSync(resolve(tmpdir(), "edispark-as2-lab-"))
  try {
    writeManifest(directory)
    const paths = ensureAs2Lab({
      directory,
      generate: () => assert.fail("complete lab must not be regenerated"),
    })

    assert.deepEqual(paths, as2LabPaths(directory))
  } finally {
    rmSync(directory, { recursive: true, force: true })
  }
})

function writeManifest(directory) {
  mkdirSync(directory, { recursive: true })
  for (const filename of ["edispark.cer", "edispark.p12", "simulator.cer", "simulator.p12"]) {
    writeFileSync(resolve(directory, filename), "fixture")
  }
  writeFileSync(resolve(directory, "password.txt"), "local-password\n")
}
