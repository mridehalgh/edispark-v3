import { readFile, writeFile } from 'node:fs/promises'

const target = new URL('../src/generated/sdk.gen.ts', import.meta.url)
const source = await readFile(target, 'utf8')
const normalized = source.replace(/[ \t]+$/gm, '')

if (source !== normalized) await writeFile(target, normalized)
