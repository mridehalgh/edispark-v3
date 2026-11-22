type DiffLine = { kind: 'same' | 'missing' | 'added'; text: string }

const maximumDiffCells = 1_000_000

function splitUnescaped(value: string, separator: string, escape: string) {
  const parts: string[] = []
  let start = 0
  for (let index = 0; index < value.length; index++) {
    if (value[index] === escape) index++
    else if (value[index] === separator) {
      parts.push(value.slice(start, index))
      start = index + 1
    }
  }
  parts.push(value.slice(start))
  return parts
}

export function ediSegments(value: string, ignoreGeneratedControls = false) {
  const edi = value.trim()
  const hasServiceAdvice = edi.startsWith('UNA') && edi.length >= 9
  const terminator = hasServiceAdvice ? edi[8] : "'"
  const separator = hasServiceAdvice ? edi[4] : '+'
  const escape = hasServiceAdvice ? edi[6] : '?'
  const segments = splitUnescaped(edi, terminator, escape).map((segment) => segment.trim()).filter(Boolean)
  if (!ignoreGeneratedControls) return segments
  return segments.map((segment) => {
    const elements = splitUnescaped(segment, separator, escape)
    const positions = elements[0] === 'UNB' ? [4, 5] : elements[0] === 'UNZ' ? [2]
      : elements[0].startsWith('STX=') ? [3, 4] : []
    for (const position of positions) {
      if (elements[position]) elements[position] = '[generated]'
    }
    return elements.join(separator)
  })
}

export function mappingFileDiff(expected: string[], actual: string[]) {
  if (expected.length === actual.length && expected.every((line, index) => line === actual[index])) {
    return { matches: true, lines: [] as DiffLine[] }
  }
  const columns = actual.length + 1
  const size = (expected.length + 1) * columns
  if (size > maximumDiffCells) return { matches: false, lines: undefined }
  const lengths = new Uint16Array(size)
  for (let i = expected.length - 1; i >= 0; i--) {
    for (let j = actual.length - 1; j >= 0; j--) {
      const cell = i * columns + j
      lengths[cell] = expected[i] === actual[j] ? 1 + lengths[cell + columns + 1]
        : Math.max(lengths[cell + columns], lengths[cell + 1])
    }
  }
  const lines: DiffLine[] = []
  let i = 0
  let j = 0
  while (i < expected.length || j < actual.length) {
    if (i < expected.length && j < actual.length && expected[i] === actual[j]) {
      lines.push({ kind: 'same', text: actual[j++] })
      i++
    } else if (i < expected.length && (j === actual.length || lengths[(i + 1) * columns + j] >= lengths[i * columns + j + 1])) {
      lines.push({ kind: 'missing', text: expected[i++] })
    } else {
      lines.push({ kind: 'added', text: actual[j++] })
    }
  }
  return { matches: false, lines }
}
