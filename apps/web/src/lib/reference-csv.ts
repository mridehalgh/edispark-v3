export function parseReferenceCsv(value: string, keyColumn: string): Record<string, string>[] {
  const text = value.replace(/^\uFEFF/, "")
  const field = /("(?:[^"]|"")*"|[^",\r\n]*)(,|\r\n|\n|\r|$)/gy
  const records: string[][] = []
  let row: string[] = []
  let offset = 0

  while (offset < text.length) {
    const match = field.exec(text)
    if (!match || field.lastIndex === offset) throw new Error("Check the CSV quoting and separators.")
    offset = field.lastIndex
    const [, cell, separator] = match
    row.push(cell.startsWith('"') ? cell.slice(1, -1).replace(/""/g, '"') : cell)
    if (separator === ",") {
      if (offset === text.length) row.push("")
      else continue
    }
    if (row.length > 1 || row[0] !== "") records.push(row)
    row = []
  }

  const [header = [], ...values] = records
  if (header.length > 20 || new Set(header).size !== header.length
    || header.some((column) => !/^[a-zA-Z][a-zA-Z0-9_]{0,63}$/.test(column))
    || !header.includes(keyColumn)) {
    throw new Error("Use unique column names and include the selected key column.")
  }
  if (values.length === 0 || values.length > 1_000) throw new Error("Provide between 1 and 1,000 reference rows.")
  const keys = new Set<string>()
  return values.map((cells, index) => {
    const number = index + 2
    if (cells.length !== header.length || cells.some((cell) => cell.length > 256)) {
      throw new Error(`Check the column count and cell lengths in row ${number}.`)
    }
    const result = Object.fromEntries(header.map((column, i) => [column, cells[i]]))
    const key = result[keyColumn]
    if (!key.trim()) throw new Error(`Provide a lookup key in row ${number}.`)
    if (keys.has(key)) throw new Error(`Use a unique lookup key in row ${number}.`)
    keys.add(key)
    return result
  })
}
