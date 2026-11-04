import { describe, expect, it } from "vitest"

import { attentionReason, latestDocumentAttentionReason } from "./attention-reason"

describe("attention reason", () => {
  it("uses a transfer failure reason when one is available", () => {
    expect(attentionReason({
      status: "NEEDS_REVIEW",
      failureReason: "Partner certificate rollover requires confirmation",
    })).toBe("Partner certificate rollover requires confirmation")
  })

  it("uses the latest exchange stage for a failed exchange", () => {
    expect(attentionReason({
      status: "NEEDS_REVIEW",
      stages: [
        { detail: "Exchange accepted" },
        { detail: "Required invoice identifier is missing" },
      ],
    })).toBe("Required invoice identifier is missing")
  })

  it("explains why a transfer is waiting for an automatic retry", () => {
    expect(attentionReason({
      status: "RETRY_SCHEDULED",
      failureReason: "Partner endpoint returned a transient response",
    })).toBe("Partner endpoint returned a transient response")
  })

  it("does not present routine stage detail as an attention reason", () => {
    expect(attentionReason({
      status: "DELIVERED",
      stages: [{ detail: "Delivery completed" }],
    })).toBeUndefined()
  })

  it("selects the newest attention reason for one document", () => {
    const exchanges = [
      exchange("set-1", "document-1", "2026-11-04T12:00:00Z", "Older failure"),
      exchange("set-2", "document-2", "2026-11-04T13:00:00Z", "Another document"),
      exchange("set-1", "document-1", "2026-11-04T14:00:00Z", "Format must be EDI"),
    ]

    expect(latestDocumentAttentionReason(exchanges, "set-1", "document-1"))
      .toBe("Format must be EDI")
  })

  it("clears an older reason after a newer exchange succeeds", () => {
    const failed = exchange("set-1", "document-1", "2026-11-04T12:00:00Z", "Older failure")
    const delivered = {
      ...exchange("set-1", "document-1", "2026-11-04T14:00:00Z", "Delivered"),
      status: "DELIVERED",
    }

    expect(latestDocumentAttentionReason([failed, delivered], "set-1", "document-1"))
      .toBeUndefined()
  })
})

function exchange(documentSetId: string, documentId: string, updatedAt: string, detail: string) {
  return {
    status: "NEEDS_REVIEW",
    sourceDocument: { documentSetId, documentId },
    updatedAt,
    stages: [{ detail }],
  }
}
