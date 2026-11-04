import { describe, expect, it } from "vitest"

import { authoringDefinitions, buildUblDocument, type AuthorableDocumentType } from "./ubl-authoring"

const types = Object.keys(authoringDefinitions) as AuthorableDocumentType[]

describe("UBL authoring", () => {
  it.each(types)("builds an OASIS UBL 2.1 %s envelope", (documentType) => {
    const definition = authoringDefinitions[documentType]
    const result = buildUblDocument({ documentType, id: "DOC-1", issueDate: "2026-11-04", reference: "REF-1", senderName: "[name]", senderEndpoint: "[partner_identifier]", recipientName: "[name]", recipientEndpoint: "[tenant_identifier]", currency: "GBP", note: "Test document", lines: [{ id: "1", itemName: "Test item", itemIdentifier: "ITEM-1", quantity: "2", unitCode: "EA", unitPrice: "3.50" }] }) as Record<string, unknown>

    expect(result._D).toBe(definition.namespace)
    expect(result[definition.root]).toBeDefined()
  })
})
