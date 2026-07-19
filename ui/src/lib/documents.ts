export type DocumentStatus = "delivered" | "ready" | "attention" | "processing"

export interface ExchangeDocument {
  id: string
  reference: string
  relatedReference?: string
  type: "Purchase order" | "Order response" | "Invoice" | "Credit note"
  retailer: string
  direction: "Inbound" | "Outbound"
  status: DocumentStatus
  receivedAt: string
  issue?: string
}

export const exchangeDocuments: ExchangeDocument[] = [
  {
    id: "doc-1",
    reference: "PO-10482",
    type: "Purchase order",
    retailer: "Tesco",
    direction: "Inbound",
    status: "ready",
    receivedAt: "09:21",
  },
  {
    id: "doc-2",
    reference: "OR-10481",
    relatedReference: "PO-10481",
    type: "Order response",
    retailer: "Tesco",
    direction: "Outbound",
    status: "delivered",
    receivedAt: "09:11",
  },
  {
    id: "doc-3",
    reference: "INV-8821",
    relatedReference: "PO-10477",
    type: "Invoice",
    retailer: "ASDA",
    direction: "Outbound",
    status: "attention",
    receivedAt: "08:47",
    issue: "Invoice total does not match the purchase order.",
  },
  {
    id: "doc-4",
    reference: "PO-10476",
    type: "Purchase order",
    retailer: "Sainsbury’s",
    direction: "Inbound",
    status: "ready",
    receivedAt: "08:38",
  },
  {
    id: "doc-5",
    reference: "OR-10475",
    relatedReference: "PO-10475",
    type: "Order response",
    retailer: "Sainsbury’s",
    direction: "Outbound",
    status: "delivered",
    receivedAt: "08:15",
  },
  {
    id: "doc-6",
    reference: "INV-8818",
    relatedReference: "PO-10472",
    type: "Invoice",
    retailer: "Morrisons",
    direction: "Outbound",
    status: "attention",
    receivedAt: "07:42",
    issue: "A required delivery date is missing.",
  },
  {
    id: "doc-7",
    reference: "PO-10471",
    type: "Purchase order",
    retailer: "B&Q",
    direction: "Inbound",
    status: "processing",
    receivedAt: "07:36",
  },
  {
    id: "doc-8",
    reference: "INV-8816",
    relatedReference: "PO-10468",
    type: "Invoice",
    retailer: "Waitrose",
    direction: "Outbound",
    status: "attention",
    receivedAt: "07:09",
    issue: "The unit price differs from the accepted order.",
  },
  {
    id: "doc-9",
    reference: "PO-10467",
    type: "Purchase order",
    retailer: "John Lewis",
    direction: "Inbound",
    status: "delivered",
    receivedAt: "06:55",
  },
]

export const documentStatusLabels: Record<DocumentStatus, string> = {
  delivered: "Delivered",
  ready: "Ready to process",
  attention: "Action required",
  processing: "Processing",
}
