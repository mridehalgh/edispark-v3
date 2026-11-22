import { describe, expect, it } from "vitest"
import { parseReferenceCsv } from "./reference-csv"

const header = "code,description"

describe("reference CSV", () => {
  it("preserves identifiers and decodes quoted cells with commas", () => {
    expect(parseReferenceCsv(`\uFEFF${header}\r\n0001,"[address], ""unit"""\r\n`, "code"))
      .toEqual([{ code: "0001", description: '[address], "unit"' }])
  })

  it("flags duplicate keys and malformed rows before publishing", () => {
    const row = "0001,[address]"
    expect(() => parseReferenceCsv(`${header}\n${row}\n${row}`, "code")).toThrow("unique lookup key")
    expect(() => parseReferenceCsv(`${header}\n0001,"unterminated`, "code")).toThrow("quoting")
  })
})
