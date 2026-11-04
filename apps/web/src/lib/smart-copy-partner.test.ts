import { describe, expect, it } from "vitest"

import { resolveSmartCopyTradingPartner } from "./smart-copy-partner"

const tradingPartnerId = "d9c01ea5-f555-4c6d-9c87-1465ea78ac05"
const connectionId = "0f269680-d95b-4a9f-9a90-b970e2c45682"

describe("smart-copy trading partner", () => {
  it("copies the retained trading partner directly", () => {
    expect(resolveSmartCopyTradingPartner({ partnerId: tradingPartnerId })).toBe(tradingPartnerId)
  })

  it("resolves legacy inbound connection metadata through the active agreement", () => {
    expect(resolveSmartCopyTradingPartner({ mftPartnerId: connectionId }, [{
      partnerId: tradingPartnerId,
      status: "ACTIVE",
      capabilities: [{ as2ConnectionId: connectionId }],
    }])).toBe(tradingPartnerId)
  })

  it("never treats an unresolved MFT connection as a trading partner", () => {
    expect(resolveSmartCopyTradingPartner({ mftPartnerId: connectionId })).toBeUndefined()
  })
})
