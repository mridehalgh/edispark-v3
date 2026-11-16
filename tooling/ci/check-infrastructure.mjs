import { readdir, readFile } from 'node:fs/promises'
async function check(directory) {
  for (const file of await readdir(directory, { withFileTypes: true })) {
    if (file.name.startsWith('.')) continue
    const path = `${directory}/${file.name}`
    if (file.isDirectory()) await check(path)
    else if (file.name.endsWith('.tf')) {
      const source = await readFile(path, 'utf8')
      if (/resource\s+"aws_(?:cloudformation_[^"]+|nat_gateway)"/.test(source)) throw new Error(`Banned CloudFormation/NAT resource in ${path}`)
    }
  }
}
await check('infrastructure/terraform')
