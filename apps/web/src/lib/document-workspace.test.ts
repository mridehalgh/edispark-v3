import { describe, expect, it } from "vitest"
import {
  groupDocuments,
  lineageRows,
  workspaceDocuments,
  type WorkspaceDocument,
} from "./document-workspace"

function document(
  id: string,
  targets: string[] = [],
  setId = "set",
): WorkspaceDocument {
  return {
    key: `${setId}:${id}`,
    setId,
    id,
    type: id === "order" ? "ORDER" : "INVOICE",
    reference: id,
    partner: "Sample retailer",
    direction: "OUTBOUND",
    status: "STORED",
    relations: targets.map((targetDocumentId) => ({
      targetDocumentId,
      type: "RELATED_TO",
    })),
  }
}

describe("Documents workspace", () => {
  it("groups explicit cross-file links while preserving each file identity and business reference", () => {
    const sets = [
      {
        id: "order-set",
        metadata: { businessDocumentNumber: "PO-42" },
        workspaceDocuments: [{ id: "order", type: "ORDER" as const }],
      },
      {
        id: "invoice-set",
        metadata: { businessDocumentNumber: "INV-42" },
        workspaceDocuments: [
          {
            id: "invoice",
            type: "INVOICE" as const,
            relations: [
              {
                type: "INVOICES" as const,
                targetDocumentId: "order",
                targetDocumentSetId: "order-set",
              },
            ],
          },
        ],
      },
    ]
    const documents = workspaceDocuments(sets, [], new Map())
    const groups = groupDocuments(documents)
    expect(groups).toHaveLength(1)
    expect(
      lineageRows(groups[0]).map((row) => [row.document.reference, row.depth]),
    ).toEqual([
      ["PO-42", 0],
      ["INV-42", 1],
    ])
    expect(documents.find((item) => item.id === "invoice")?.setId).toBe(
      "invoice-set",
    )
  })
  it("groups recorded links without combining unrelated documents in the same file", () => {
    const groups = groupDocuments([
      document("invoice", ["order"]),
      document("order"),
      document("unlinked"),
    ])
    expect(groups.map((group) => group.documents.length)).toEqual([2, 1])
    expect(groups[0].root.id).toBe("order")
  })

  it("preserves dispatch receipt branches and shows a shared child once", () => {
    const group = groupDocuments([
      document("order"),
      document("dispatch", ["order"]),
      document("receipt", ["dispatch"]),
      document("invoice", ["order", "dispatch"]),
    ])[0]
    const rows = lineageRows(group)
    expect(rows.find((row) => row.document.id === "receipt")?.depth).toBe(2)
    expect(rows.filter((row) => row.document.id === "invoice")).toHaveLength(1)
  })

  it("does not invent cross-set links and safely handles cycles or missing targets", () => {
    expect(
      groupDocuments([
        document("invoice", ["order"]),
        document("order", [], "other"),
      ]),
    ).toHaveLength(2)
    const group = groupDocuments([
      document("a", ["b", "missing"]),
      document("b", ["a"]),
    ])[0]
    expect(lineageRows(group)).toHaveLength(2)
  })

  it("uses latest delivery evidence, keeps draft state and avoids repeating a source reference on follow-ups", () => {
    const documents = workspaceDocuments(
      [
        {
          id: "set",
          metadata: { businessDocumentNumber: "PO-42", partnerId: "partner" },
          documents: [
            { id: "order", type: "ORDER" },
            { id: "invoice", type: "INVOICE" },
          ],
          workspaceDocuments: [
            {
              id: "invoice",
              type: "INVOICE",
              lifecycleStatus: "DRAFT",
              relations: [{ type: "INVOICES", targetDocumentId: "order" }],
            },
            { id: "order", type: "ORDER" },
          ],
        },
      ],
      [
        {
          sourceDocument: { documentSetId: "set", documentId: "order" },
          status: "FAILED",
          updatedAt: "2026-01-01",
        },
        {
          sourceDocument: { documentSetId: "set", documentId: "order" },
          status: "DELIVERED",
          updatedAt: "2026-01-02",
        },
      ],
      new Map([["partner", "Sample retailer"]]),
    )
    expect(documents.find((item) => item.id === "order")).toMatchObject({
      reference: "PO-42",
      status: "DELIVERED",
      partner: "Sample retailer",
    })
    expect(documents.find((item) => item.id === "invoice")).toMatchObject({
      status: "DRAFT",
      reference: "Invoice · invoice",
    })
  })
})
