import { readFile, writeFile } from 'node:fs/promises'
const file = process.argv[2]
const config = JSON.parse(await readFile(file, 'utf8'))
const commit = process.env.GITHUB_SHA
const weight = Number(process.argv[3])
if (!config.revisions[commit] || ![10, 50, 100].includes(weight)) throw new Error('Invalid traffic promotion')
const current = Object.entries(config.traffic).find(([id, value]) => id !== commit && value > 0)?.[0]
config.traffic = current && weight !== 100 ? { [current]: 100 - weight, [commit]: weight } : { [commit]: 100 }
await writeFile(file, JSON.stringify(config))
