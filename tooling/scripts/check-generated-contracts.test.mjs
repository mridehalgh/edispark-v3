import { execFileSync, spawnSync } from "node:child_process"
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from "node:fs"
import { tmpdir } from "node:os"
import { dirname, join } from "node:path"
import { fileURLToPath } from "node:url"
import { test } from "node:test"
import assert from "node:assert/strict"

const script = fileURLToPath(new URL("./check-generated-contracts.mjs", import.meta.url))

function repository(t) {
  const cwd = mkdtempSync(join(tmpdir(), "edispark-contract-drift-"))
  t.after(() => rmSync(cwd, { recursive: true, force: true }))
  const git = (...args) => execFileSync("git", args, { cwd, stdio: "pipe" })
  const write = (path, value) => {
    mkdirSync(dirname(join(cwd, path)), { recursive: true })
    writeFileSync(join(cwd, path), value)
  }
  git("init", "--quiet")
  write("contracts/documents/openapi.json", "{}\n")
  write("packages/mft-client/src/generated/index.ts", "export {}\n")
  git("add", ".")
  git("-c", "commit.gpgsign=false", "-c", "user.name=Local test", "-c", "user.email=test@example.invalid", "commit", "--quiet", "-m", "fixture")
  return { git, write, check: () => spawnSync(process.execPath, [script], { cwd, encoding: "utf8" }) }
}

test("unchanged contracts pass and unrelated work does not fail the SDK gate", (t) => {
  const fixture = repository(t)
  fixture.write("README.md", "unrelated")
  assert.equal(fixture.check().status, 0)
})

test("changed exported contracts fail before they are committed", (t) => {
  const fixture = repository(t)
  fixture.write("contracts/documents/openapi.json", '{"changed":true}\n')
  assert.equal(fixture.check().status, 1)
})

test("new generated SDK files fail even though git diff would omit them", (t) => {
  const fixture = repository(t)
  fixture.write("packages/integration-client/src/generated/new.ts", "export {}\n")
  const result = fixture.check()
  assert.equal(result.status, 1)
  assert.match(result.stderr, /new\.ts/)
})

test("staged generated changes also fail the committed-file comparison", (t) => {
  const fixture = repository(t)
  fixture.write("packages/mft-client/src/generated/index.ts", "export const changed = true\n")
  fixture.git("add", ".")
  assert.equal(fixture.check().status, 1)
})
