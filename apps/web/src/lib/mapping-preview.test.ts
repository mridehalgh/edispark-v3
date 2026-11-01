import { describe, expect, it } from 'vitest'

import { previewMapping, readPointer } from './mapping-preview'

describe('mapping preview', () => {
  it('builds nested output from copy and transformation operations', () => {
    const input = { order: { id: 'po-42', buyer: { name: 'Example retailer' } } }

    expect(previewMapping(input, [
      { id: '1', operation: 'UPPERCASE', sourcePointers: ['/order/id'], targetPointer: '/document/id' },
      { id: '2', operation: 'COPY', sourcePointers: ['/order/buyer/name'], targetPointer: '/parties/buyer' },
      { id: '3', operation: 'CONSTANT', sourcePointers: [], targetPointer: '/document/type', constant: 'ORDER' },
    ])).toEqual({
      document: { id: 'PO-42', type: 'ORDER' },
      parties: { buyer: 'Example retailer' },
    })
  })

  it('reads array positions from JSON pointers', () => {
    expect(readPointer({ lines: [{ sku: 'SKU-1' }] }, '/lines/0/sku')).toBe('SKU-1')
  })

  it('creates arrays when target pointers contain numeric positions', () => {
    expect(previewMapping({ id: 'PO-42' }, [
      { id: '1', operation: 'COPY', sourcePointers: ['/id'], targetPointer: '/Order/0/ID/0/_' },
    ])).toEqual({ Order: [{ ID: [{ _: 'PO-42' }] }] })
  })
})
