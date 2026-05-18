export const supportedMessageTypes = [
  'ORDERS',
  'ORDRSP',
  'DESADV',
  'RECADV',
  'INVOIC',
  'REMADV',
  'PAYORD',
  'PRICAT',
  'INVRPT',
  'SLSRPT',
] as const

export type SupportedMessageType = (typeof supportedMessageTypes)[number]

export type MessageFraming = {
  messageType: SupportedMessageType
  displayLabel: string
  businessIntent: string
}

export const messageFraming: MessageFraming[] = [
  { messageType: 'ORDERS', displayLabel: 'ORDERS', businessIntent: 'Inbound purchase orders from retail partners' },
  { messageType: 'ORDRSP', displayLabel: 'ORDRSP', businessIntent: 'Supplier responses and order acknowledgements' },
  { messageType: 'DESADV', displayLabel: 'DESADV', businessIntent: 'Despatch advice and shipment notice flows' },
  { messageType: 'RECADV', displayLabel: 'RECADV', businessIntent: 'Goods receipt and warehouse confirmation messages' },
  { messageType: 'INVOIC', displayLabel: 'INVOIC', businessIntent: 'Invoice validation and AP intake' },
  { messageType: 'REMADV', displayLabel: 'REMADV', businessIntent: 'Remittance advice and payment reconciliation' },
  { messageType: 'PAYORD', displayLabel: 'PAYORD', businessIntent: 'Payment order orchestration' },
  { messageType: 'PRICAT', displayLabel: 'PRICAT', businessIntent: 'Retail catalogue and product sync changes' },
  { messageType: 'INVRPT', displayLabel: 'INVRPT', businessIntent: 'Inventory reporting and stock visibility' },
  { messageType: 'SLSRPT', displayLabel: 'SLSRPT', businessIntent: 'Sales reporting and trade analytics' },
] as const
