import type { MappingNode } from '@edispark/integration-client'

export function loopDepth(pointer?: string) {
  return pointer?.split('/').filter((part) => part === '*').length ?? 0
}

export function previewMapping(input: unknown, nodes: MappingNode[]): unknown {
  const output: Record<string, unknown> = {}
  function apply(node: MappingNode, remaining: { items: number }) {
    if (!node.targetPointer || node.operation === 'OMIT') return
    const repeated = node.sourcePointers?.reduce<string | undefined>((deepest, pointer) =>
      loopDepth(pointer) > loopDepth(deepest) ? pointer : deepest, undefined)
    if (loopDepth(repeated) !== loopDepth(node.targetPointer)) return
    if (repeated) {
      const collection = readPointer(input, repeated.slice(0, repeated.search(/\/\*(?=\/|$)/)))
      if (!Array.isArray(collection)) return
      remaining.items -= collection.length
      if (remaining.items < 0) throw new Error('Repeated mapping exceeds 10000 items')
      collection.forEach((_, index) => {
        const expand = (pointer: string) => pointer.replace(/\/\*(?=\/|$)/, `/${index}`)
        apply({ ...node, sourcePointers: node.sourcePointers?.map(expand), targetPointer: expand(node.targetPointer!) }, remaining)
      })
      return
    }
    const sources = (node.sourcePointers ?? []).map((pointer) => readPointer(input, pointer))
    const value = evaluate(node, sources)
    if (value !== undefined) writePointer(output, node.targetPointer, value)
  }
  nodes.forEach((node) => apply(node, { items: 10_000 }))
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
    case 'CODE_LOOKUP':
    case 'OPTIONAL_CODE_LOOKUP': return sources[0] === undefined ? undefined : node.codeMap?.[String(sources[0])]
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
