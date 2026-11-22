import type { MappingNode, MappingRevision } from '@edispark/integration-client'

import { loopDepth, readPointer } from './mapping-preview'

const lineCollection = /^(?:OrderLine|InvoiceLine|DespatchLine|ReceiptLine|RemittanceAdviceLine|lines)$/
const nestedCollection = /^(?:items|packages?|allocations?|taxes|Package|TransportHandlingUnit|GoodsItem|TaxSubtotal)$/i

export function repeatLine(path: string) {
  return path.split('/').map((part, index, parts) => /^\d+$/.test(part) && lineCollection.test(parts[index - 1]) ? '*' : part).join('/')
}

export function fieldLabel(pointer: string) {
  const parts = pointer.split('/').filter((part) => part && !/^(?:\d+|\*|_)$/.test(part))
  const leaf = parts.at(-1) ?? pointer
  const words = leaf.toLowerCase() === 'id' ? parts.slice(-2).join(' ') : leaf
  const spaced = words.replace(/([a-z0-9])([A-Z])/g, '$1 $2').replace(/([A-Z])([A-Z][a-z])/g, '$1 $2')
  return spaced.charAt(0).toUpperCase() + spaced.slice(1)
}

type Field = { path: string; label: string; required?: boolean }

const ediFields: Array<[string, string, string, string, string?]> = [
  ['/document/id', 'Document number', 'BGM', 'DOC / IRF'],
  ['/document/issueDate', 'Issue date', 'DTM+137', 'DOC / IRF'],
  ['/parties/sender', 'Sender identifier', 'UNB / NAD+SU', 'STX'],
  ['/parties/receiver', 'Receiver identifier', 'UNB / NAD+BY', 'STX'],
  ['/document/currency', 'Currency', 'CUX', 'ORD', 'ORDER'],
  ['/document/orderReference', 'Order reference', 'RFF+ON', 'ODD', 'INVOICE DESPATCH_ADVICE RECEIPT_ADVICE REMITTANCE_ADVICE'],
  ['/document/dueDate', 'Payment due date', 'DTM+13', '', 'INVOICE'],
  ['/delivery/requestedDate', 'Requested delivery date', 'DTM+2', '', 'ORDER'],
  ['/delivery/locationId', 'Delivery location identifier', 'NAD+DP', 'CLO', 'ORDER'],
  ['/delivery/terms', 'Delivery terms', 'TOD', '', 'ORDER'],
  ['/document/totalPaymentAmount', 'Total payment amount', 'MOA+9', 'OTR', 'REMITTANCE_ADVICE'],
  ['/lines/*/id', 'Line number', 'LIN', 'OLD / ILD'],
  ['/lines/*/gtin', 'Item GTIN', 'LIN', 'OLD / ILD'],
  ['/lines/*/buyerItemId', 'Buyer item code', 'PIA (BP)', 'OLD / ILD'],
  ['/lines/*/sellerItemId', 'Supplier item code', 'PIA (SA)', 'OLD / ILD'],
  ['/lines/*/name', 'Item name', 'IMD', 'OLD / ILD'],
  ['/lines/*/description', 'Item description', 'IMD', 'OLD / ILD'],
  ['/lines/*/quantity', 'Line quantity', 'QTY', 'OLD / ILD'],
  ['/lines/*/unitCode', 'Unit of measure', 'QTY', 'OLD / ILD'],
  ['/lines/*/price', 'Unit price', 'PRI', 'OLD / ILD'],
  ['/lines/*/lineExtension', 'Line amount', 'MOA+203', 'ILD', 'INVOICE'],
  ['/lines/*/taxCategory', 'Line tax category', 'TAX', '', 'INVOICE'],
  ['/lines/*/taxPercent', 'Line tax rate', 'TAX', '', 'INVOICE'],
  ['/totals/lineExtension', 'Total line amount', 'MOA+79', 'STL / OTR'],
  ['/totals/taxExclusive', 'Total before tax', 'MOA+125', ''],
  ['/tax/amount', 'Total tax amount', 'MOA+176', 'STL / TLR', 'INVOICE'],
  ['/totals/payable', 'Total payable', 'MOA+86', 'STL / OTR'],
]

const requiredHeader = new Set(['/document/id', '/document/issueDate', '/parties/sender', '/parties/receiver', '/lines/*/id'])

export function mappingDestinationFields(mapping: MappingRevision, template?: MappingRevision): Field[] {
  const { direction, standard, documentType } = mapping.compatibility ?? {}
  if (direction !== 'OUTBOUND' || documentType === 'ORDER_RESPONSE') {
    const paths = (template?.nodes ?? mapping.nodes ?? []).filter((node) => node.operation !== 'OMIT')
      .flatMap((node) => node.targetPointer ? [repeatLine(node.targetPointer)] : [])
    return [...new Set(paths)].map((path) => ({ path, label: fieldLabel(path) }))
  }
  const edifact = standard === 'EDIFACT'
  return ediFields.flatMap(([path, name, ediSegment, tradacomsSegment, types]) => {
    const segment = edifact ? ediSegment : tradacomsSegment
    const restrictType = !edifact || !['/document/currency', '/lines/*/lineExtension', '/tax/amount', '/document/orderReference'].includes(path)
    if (!segment || (restrictType && types && !types.split(' ').includes(documentType ?? ''))) return []
    if (edifact && path === '/document/orderReference' && documentType === 'ORDER') return []
    const required = requiredHeader.has(path) || (documentType !== 'REMITTANCE_ADVICE' && ['/lines/*/quantity', '/lines/*/unitCode'].includes(path))
    return [{ path, label: `${name} · ${segment}`, required }]
  })
}

export function isPlaceholderRule(node: MappingNode) {
  return node.operation === 'COPY' && node.targetPointer === '/document/id'
    && node.sourcePointers?.length === 1 && node.sourcePointers[0] === '/document/id'
}

export function templateRule(path: string, template?: MappingRevision) {
  const rule = template?.nodes?.find((node) => node.targetPointer && repeatLine(node.targetPointer) === path && node.operation !== 'OMIT')
  return rule && { ...rule, targetPointer: path, sourcePointers: rule.sourcePointers?.map((source) => loopDepth(path) ? repeatLine(source) : source) }
}

export function sourceScopes(path: string, input: unknown) {
  const parts = path.split('/')
  const concrete = parts.map((part) => part === '*' ? '0' : part)
  const candidates = parts.flatMap((part, index) => {
    if (!/^\d+$|^\*$/.test(part)) return []
    const parent = readPointer(input, concrete.slice(0, index).join('/'))
    const name = parts[index - 1]
    return part === '*' || lineCollection.test(name) || nestedCollection.test(name) || (Array.isArray(parent) && parent.length > 1) ? [{ index, name }] : []
  })
  return [
    { path: concrete.join('/'), label: 'Single value' },
    ...candidates.map(({ name }, index) => {
      const indices = new Set(candidates.slice(0, index + 1).map((candidate) => candidate.index))
      return { path: concrete.map((part, position) => indices.has(position) ? '*' : part).join('/'),
        label: index ? `Each ${fieldLabel(name).toLowerCase()} within each parent` : `Each ${fieldLabel(name).toLowerCase()}` }
    }),
  ]
}
