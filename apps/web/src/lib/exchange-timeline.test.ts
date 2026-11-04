import { describe, expect, it } from "vitest"
import {
  documentExchanges,
  duration,
  exchangeTimeline,
} from "./exchange-timeline"

describe("Document exchange timeline", () => {
  it("numbers events from earliest to latest and preserves recorded order when timestamps tie", () => {
    const events = exchangeTimeline([
      {
        stages: [
          { status: "NEEDS_REVIEW", occurredAt: "2026-11-04T17:10:03Z" },
          { status: "RECEIVED", occurredAt: "2026-11-04T17:10:00Z" },
          { status: "RESOLVING", occurredAt: "2026-11-04T17:10:00Z" },
        ],
      },
    ])
    expect(events.map((event) => [event.sequence, event.stage.status])).toEqual(
      [
        [1, "RECEIVED"],
        [2, "RESOLVING"],
        [3, "NEEDS_REVIEW"],
      ],
    )
    expect(events[2].sincePrevious).toBe(3000)
  })

  it("retains earlier failed attempts alongside later successful exchanges without mutating their stages", () => {
    const failed = {
      createdAt: "2026-11-04T17:10:00Z",
      stages: [
        {
          status: "FAILED" as const,
          occurredAt: "2026-11-04T17:10:03Z",
          detail: "Delivery unavailable",
        },
      ],
    }
    const events = exchangeTimeline([
      {
        createdAt: "2026-11-04T17:11:00Z",
        stages: [{ status: "DELIVERED", occurredAt: "2026-11-04T17:11:06Z" }],
      },
      failed,
    ])
    expect(events.map((event) => [event.attempt, event.sequence])).toEqual([
      [1, 1],
      [2, 2],
    ])
    expect(events[0].stage.detail).toBe("Delivery unavailable")
    expect(events[1].elapsed).toBe(6000)
    expect(failed.stages).toHaveLength(1)
  })

  it("matches source and generated documents by both IDs and excludes unrelated records", () => {
    const source = {
      sourceDocument: { documentSetId: "set", documentId: "doc" },
    }
    const generated = {
      generatedDocument: { documentSetId: "set", documentId: "doc" },
    }
    expect(
      documentExchanges(
        [
          source,
          generated,
          { sourceDocument: { documentSetId: "other", documentId: "doc" } },
        ],
        "set",
        "doc",
      ),
    ).toEqual([source, generated])
  })

  it("marks a status-only record honestly and handles missing or malformed timestamps", () => {
    const events = exchangeTimeline([
      { status: "STORING", updatedAt: "invalid" },
    ])
    expect(events[0]).toMatchObject({
      recordedStatusOnly: true,
      sequence: 1,
      elapsed: undefined,
    })
    expect(exchangeTimeline([])).toEqual([])
    expect(duration(12)).toBe("12 ms")
    expect(duration(1500)).toBe("1.5 s")
    expect(duration(61000)).toBe("1 min 1 s")
  })
})
