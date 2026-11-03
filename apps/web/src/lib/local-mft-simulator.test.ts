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
    expect(localMftSample("INBOUND")).toMatchObject({ filename: "local-inbound-orders-batch.edi" })
    expect(localMftSample("OUTBOUND")).toMatchObject({ filename: "local-outbound-invoice.edi" })
    expect(localMftSample("INBOUND").content.match(/^LIN\+/gm)).toHaveLength(16)
    expect(localMftSample("OUTBOUND").content.match(/^LIN\+/gm)).toHaveLength(8)
    expect(localMftSample("INBOUND").content).toContain("UNH+1+ORDERS:D:96A:UN'")
    expect(localMftSample("INBOUND").content).toContain("UNH+2+ORDERS:D:96A:UN'")
    expect(localMftSample("INBOUND").content).toContain("UNZ+2+LOCALINBOUND1'")
    expect(localMftSample("OUTBOUND").content).toContain("UNH+1+INVOIC:D:96A:UN'")
    expect(localMftSample("INBOUND").content).toContain("[name]")
    expect(localMftSample("INBOUND").content).toContain("CNT+2:8'")
    expect(localMftSample("OUTBOUND").content).toContain("MOA+86:2575.58'")
  })
})
