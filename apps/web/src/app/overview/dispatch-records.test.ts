import { expect, it } from "vitest"
import { dispatchRecords } from "./dispatch-records"

const sourceDocument = { documentSetId: "set", documentId: "order" }
const context = {
  sets: [{
    id: "set",
    metadata: { businessDocumentNumber: "PO-42", mftTransferId: "known-file", direction: "INBOUND" },
    documents: [{ id: "order", type: "ORDER" as const }],
  }],
  partners: [],
  connections: [],
}

it("links a failed exchange to its business record and hides privileged transfers from members", () => {
  const records = dispatchRecords({
    ...context, admin: false,
    exchanges: [{
      sourceDocument, direction: "INBOUND", status: "NEEDS_REVIEW",
      stages: [{ status: "NEEDS_REVIEW", detail: "A required line quantity is missing." }],
    }],
    transfers: [{ id: "unlinked-file", status: "FAILED" }],
  })
  expect(records).toMatchObject([{
    href: "/documents/set/order", type: "Order", reference: "PO-42", direction: "INBOUND",
    needsAttention: true, reason: "A required line quantity is missing.",
  }])
})

it("keeps receipt and functional acknowledgement separate and keeps independent transfer failures visible", () => {
  const records = dispatchRecords({
    ...context, admin: true,
    exchanges: [{
      sourceDocument, direction: "OUTBOUND", status: "AWAITING_RECEIPT", mftTransferId: "known-file",
      stages: [{
        status: "AWAITING_RECEIPT",
        detail: "AS2 transport receipt verified; awaiting functional acknowledgement. Business acceptance is not yet recorded.",
      }],
    }],
    transfers: [
      { id: "known-file", status: "DELIVERED" },
      { id: "failed-file", status: "NEEDS_REVIEW", failureReason: "The partner certificate has expired." },
      { id: "retry-file", status: "RETRY_SCHEDULED" },
    ],
  })
  expect(records).toMatchObject([
    { href: "/documents/set/order", status: "Awaiting functional acknowledgement", needsAttention: false },
    { href: "/files/transfers/failed-file", needsAttention: true, reason: "The partner certificate has expired." },
    { href: "/files/transfers/retry-file", needsAttention: false, scheduledRetry: true },
  ])
})

it("preserves an inbound processing failure even when transport has delivered the source file", () => {
  const records = dispatchRecords({
    ...context, admin: true,
    exchanges: [{
      direction: "INBOUND", status: "NEEDS_REVIEW", mftTransferId: "known-file",
      stages: [{ status: "NEEDS_REVIEW", detail: "MESSAGE_REJECTED: A required line quantity is missing." }],
    }],
    transfers: [{ id: "known-file", direction: "INBOUND", status: "DELIVERED" }],
  })
  expect(records.find((record) => record.href === "/files/transfers/known-file")).toMatchObject({
    needsAttention: true, status: "Processing needs review", reason: "A required line quantity is missing.",
  })
})
