import { describe, expect, it } from 'vitest'

import { ediSegments, mappingFileDiff } from './mapping-file-diff'

describe('mapping file comparison', () => {
  it('keeps escaped delimiters in the data and supports UNA separators', () => {
    expect(ediSegments("UNA:+.? ~UNH+1~FTX+Left?~right~UNT+3+1~"))
      .toEqual(['UNA:+.?', 'UNH+1', 'FTX+Left?~right', 'UNT+3+1'])
  })

  it('ignores generated envelope dates and references but compares business fields', () => {
    const expected = "UNB+UNOC:3+SENDER+PARTNER+261030:1200+SAMPLE'BGM+380+INV-1'UNZ+1+SAMPLE'"
    const actual = "UNB+UNOC:3+SENDER+PARTNER+261122:1800+PREVIEW'\nBGM+380+INV-2'\nUNZ+1+PREVIEW'"
    const comparison = mappingFileDiff(ediSegments(expected, true), ediSegments(actual, true))
    expect(comparison.matches).toBe(false)
    expect(comparison.lines?.filter((line) => line.kind !== 'same')).toEqual([
      { kind: 'missing', text: 'BGM+380+INV-1' }, { kind: 'added', text: 'BGM+380+INV-2' },
    ])
  })

  it('aligns segments after an insertion', () => {
    const comparison = mappingFileDiff(['UNH+1', 'UNT+2+1'], ['UNH+1', 'BGM+380', 'UNT+2+1'])
    expect(comparison.lines?.map((line) => line.kind)).toEqual(['same', 'added', 'same'])
  })
})
