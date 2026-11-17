import { test } from 'node:test'
import assert from 'node:assert/strict'
import { mkdtempSync, writeFileSync, existsSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { isGeneratedFile } from '../../.agents/skills/impeccable/scripts/lib/is-generated.mjs'

test('generated-file detection treats shell syntax in filenames as literal text', () => {
  const cwd = mkdtempSync(join(tmpdir(), 'generated-file-security-'))
  try {
    execFileSync('git', ['init', '--quiet', cwd])
    writeFileSync(join(cwd, '.gitignore'), 'ignored-*\n')
    const filename = 'ignored-$(touch injected).txt'
    writeFileSync(join(cwd, filename), 'example')
    assert.equal(isGeneratedFile(filename, { cwd }), true)
    assert.equal(existsSync(join(cwd, 'injected')), false)
    writeFileSync(join(cwd, 'source.txt'), 'ordinary source')
    assert.equal(isGeneratedFile('source.txt', { cwd }), false)
    writeFileSync(join(cwd, 'output.txt'), '// @generated\n')
    assert.equal(isGeneratedFile('output.txt', { cwd }), true)
  } finally {
    rmSync(cwd, { recursive: true, force: true })
  }
})
