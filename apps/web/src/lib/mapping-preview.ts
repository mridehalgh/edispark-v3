import type { MappingNode } from '@edispark/integration-client'

export function previewMapping(input: unknown, nodes: MappingNode[]): unknown {
  const output: Record<string, unknown> = {}
  for (const node of nodes) {
    if (!node.targetPointer) continue
    const sources = (node.sourcePointers ?? []).map((pointer) => readPointer(input, pointer))
    const value = evaluate(node, sources)
    if (value !== undefined) writePointer(output, node.targetPointer, value)
  }
  return output
}

export function readPointer(value: unknown, pointer: string): unknown {
  if (pointer === '' || pointer === '/') return value
  return pointer.split('/').slice(1).reduce<unknown>((current, segment) => {
    if (current === null || typeof current !== 'object') return undefined
    const key = segment.replaceAll('~1', '/').replaceAll('~0', '~')
    return (current as Record<string, unknown>)[key]
  }, value)
}

function evaluate(node: MappingNode, sources: unknown[]): unknown {
  switch (node.operation) {
    case 'CONSTANT': return node.constant
    case 'CONCAT': return sources.filter((item) => item !== undefined).join(node.separator ?? '')
    case 'COALESCE': return sources.find((item) => item !== undefined && item !== null && item !== '')
    case 'UPPERCASE': return sources[0] === undefined ? undefined : String(sources[0]).toUpperCase()
    case 'LOWERCASE': return sources[0] === undefined ? undefined : String(sources[0]).toLowerCase()
    default: return sources[0]
  }
}

function writePointer(target: Record<string, unknown>, pointer: string, value: unknown) {
  const parts = pointer.split('/').slice(1).map((segment) => segment.replaceAll('~1', '/').replaceAll('~0', '~'))
  if (parts.length === 0) return
  let current: Record<string, unknown> | unknown[] = target
  parts.forEach((part, index) => {
    const key = Array.isArray(current) && /^\d+$/.test(part) ? Number(part) : part
    if (index === parts.length - 1) {
      ;(current as Record<string | number, unknown>)[key] = value
      return
    }
    const next = (current as Record<string | number, unknown>)[key]
    if (next === null || typeof next !== 'object') {
      ;(current as Record<string | number, unknown>)[key] = /^\d+$/.test(parts[index + 1]) ? [] : {}
    }
    current = (current as Record<string | number, unknown>)[key] as Record<string, unknown> | unknown[]
  })
}
