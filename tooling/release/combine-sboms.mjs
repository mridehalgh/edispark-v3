import { readFile, writeFile } from 'node:fs/promises'
import { randomUUID } from 'node:crypto'
const components = []
const dependencies = []
for (const [index, path] of process.argv.slice(3).entries()) {
  const bom = JSON.parse(await readFile(path, 'utf8'))
  if (bom.bomFormat !== 'CycloneDX' || !bom.components?.length) throw new Error('A release component inventory is missing')
  const ref = value => `${index}:${value}`
  for (const component of [...bom.components, ...(bom.metadata?.component ? [bom.metadata.component] : [])]) {
    components.push({ ...component, 'bom-ref': ref(component['bom-ref'] ?? component.purl ?? component.name) })
  }
  for (const dependency of bom.dependencies ?? []) dependencies.push({ ref: ref(dependency.ref), dependsOn: (dependency.dependsOn ?? []).map(ref) })
}
await writeFile(process.argv[2], JSON.stringify({ bomFormat: 'CycloneDX', specVersion: '1.6', serialNumber: `urn:uuid:${randomUUID()}`, version: 1, components, dependencies }))
