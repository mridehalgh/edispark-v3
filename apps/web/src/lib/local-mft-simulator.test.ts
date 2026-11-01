import { describe, expect, it } from "vitest"

import { isLocalDevelopmentEnabled, isLocalSimulationDirection, localMftSample } from "./local-mft-simulator"

describe("local MFT simulator contract", () => {
  it("is available only in a development runtime", () => {
    expect(isLocalDevelopmentEnabled("development")).toBe(true)
    expect(isLocalDevelopmentEnabled("production")).toBe(false)
    expect(isLocalDevelopmentEnabled(undefined)).toBe(false)
  })

  it("accepts only supported simulation directions", () => {
    expect(isLocalSimulationDirection("INBOUND")).toBe(true)
    expect(isLocalSimulationDirection("OUTBOUND")).toBe(true)
    expect(isLocalSimulationDirection("DELETE")).toBe(false)
  })

  it("provides deterministic, non-PII EDI samples", () => {
    expect(localMftSample("INBOUND")).toMatchObject({ filename: "local-inbound-order.edi" })
    expect(localMftSample("OUTBOUND")).toMatchObject({ filename: "local-outbound-invoice.edi" })
    expect(localMftSample("INBOUND").content.split("\n")).toHaveLength(33)
    expect(localMftSample("OUTBOUND").content.split("\n")).toHaveLength(36)
    expect(localMftSample("INBOUND").content).toContain("UNH+1+ORDERS:D:96A:UN'")
    expect(localMftSample("OUTBOUND").content).toContain("UNH+1+INVOIC:D:96A:UN'")
    expect(localMftSample("INBOUND").content).toContain("[name]")
  })
})
