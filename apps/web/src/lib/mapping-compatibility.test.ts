import { describe, expect, it } from "vitest"

import { findPublishedMapping, unavailableExchangeMessage } from "./mapping-compatibility"

describe("partner exchange mapping compatibility", () => {
  it("selects by exact coordinates rather than words in the display name", () => {
    const selected = findPublishedMapping([
      {
        id: "correct",
        name: "Analyst-defined title",
        status: "PUBLISHED",
        compatibility: { direction: "OUTBOUND", documentType: "INVOICE", standard: "TRADACOMS", release: "9", messageType: "INVOIC" },
      },
      {
        id: "misleading",
        name: "Outbound TRADACOMS invoice",
        status: "PUBLISHED",
        compatibility: { direction: "INBOUND", documentType: "INVOICE", standard: "TRADACOMS", release: "9", messageType: "INVOIC" },
      },
    ], "OUTBOUND", "INVOICE", "TRADACOMS")

    expect(selected?.id).toBe("correct")
  })

  it("tells an analyst exactly what must be configured", () => {
    expect(unavailableExchangeMessage("OUTBOUND", "ORDER_RESPONSE", "EDIFACT"))
      .toBe("EdiSpark cannot send order response as EDIFACT D96A yet. Publish a compatible outbound mapping, then add this document exchange again.")
  })
})
