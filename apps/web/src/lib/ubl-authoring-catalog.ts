import type { AuthorableDocumentType } from "./ubl-authoring"

export type AuthoringCatalogField = {
  fieldKey: string
  ublKey: string
  label: string
  description: string
  inputType?: "text" | "date"
}

const common: AuthoringCatalogField[] = [
  { fieldKey: "document.uuid", ublKey: "UUID", label: "Document UUID", description: "A globally unique identifier for this document." },
  { fieldKey: "document.copyIndicator", ublKey: "CopyIndicator", label: "Copy indicator", description: "Whether this is a copy of the original document." },
]

export const authoringCatalog: Record<AuthorableDocumentType, AuthoringCatalogField[]> = {
  ORDER: [...common,
    { fieldKey: "order.typeCode", ublKey: "OrderTypeCode", label: "Order type code", description: "Partner-defined classification of the order." },
    { fieldKey: "order.customerReference", ublKey: "CustomerReference", label: "Customer reference", description: "Reference assigned by the customer." },
    { fieldKey: "order.pricingCurrency", ublKey: "PricingCurrencyCode", label: "Pricing currency", description: "Currency used for prices when different from document currency." },
  ],
  ORDER_RESPONSE: [...common,
    { fieldKey: "orderResponse.salesOrderId", ublKey: "SalesOrderID", label: "Sales order ID", description: "Seller-assigned sales order identifier." },
    { fieldKey: "orderResponse.customerReference", ublKey: "CustomerReference", label: "Customer reference", description: "Reference assigned by the customer." },
  ],
  DESPATCH_ADVICE: [...common,
    { fieldKey: "despatch.typeCode", ublKey: "DespatchAdviceTypeCode", label: "Despatch advice type", description: "Partner-defined classification of the ASN." },
    { fieldKey: "despatch.lineCount", ublKey: "LineCountNumeric", label: "Declared line count", description: "Declared number of despatch lines." },
  ],
  INVOICE: [...common,
    { fieldKey: "invoice.typeCode", ublKey: "InvoiceTypeCode", label: "Invoice type code", description: "Business classification such as commercial invoice." },
    { fieldKey: "invoice.dueDate", ublKey: "DueDate", label: "Due date", description: "Date by which payment is due.", inputType: "date" },
    { fieldKey: "invoice.taxPointDate", ublKey: "TaxPointDate", label: "Tax point date", description: "Date on which tax becomes applicable.", inputType: "date" },
    { fieldKey: "invoice.buyerReference", ublKey: "BuyerReference", label: "Buyer reference", description: "Reference supplied by the buyer." },
    { fieldKey: "invoice.accountingCost", ublKey: "AccountingCost", label: "Accounting cost code", description: "Buyer accounting or cost-centre reference." },
  ],
  REMITTANCE_ADVICE: [...common,
    { fieldKey: "remittance.typeCode", ublKey: "RemittanceAdviceTypeCode", label: "Remittance type", description: "Partner-defined classification of the advice." },
    { fieldKey: "remittance.payerReference", ublKey: "PayerReference", label: "Payer reference", description: "Reference supplied by the payer." },
    { fieldKey: "remittance.invoicingReference", ublKey: "InvoicingPartyReference", label: "Invoicing-party reference", description: "Reference assigned by the invoicing party." },
  ],
  RECEIPT_ADVICE: [...common,
    { fieldKey: "receipt.typeCode", ublKey: "ReceiptAdviceTypeCode", label: "Receipt advice type", description: "Partner-defined classification of the receipt advice." },
    { fieldKey: "receipt.lineCount", ublKey: "LineCountNumeric", label: "Declared line count", description: "Declared number of receipt lines." },
  ],
}
