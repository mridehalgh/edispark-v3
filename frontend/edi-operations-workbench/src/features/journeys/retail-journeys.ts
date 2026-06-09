import type { DocumentSetResponse } from '@/integration/documents-api-client'
import type { components } from '@/lib/api/generated/contract'

type DocumentType = components['schemas']['DocumentType']

export type RetailJourneyKey = 'orders' | 'desadv' | 'recadv' | 'invoic'

export type JourneyStepState = 'available' | 'planned' | 'empty'

export type JourneyStepViewModel = {
  key: string
  label: string
  state: JourneyStepState
  description: string
  linkedRecordCount: number
  relatedDocumentTypes: DocumentType[]
  actionPath?: string
}

export type JourneyRecordViewModel = {
  documentSetId: string
  createdAt: string
  createdBy: string
  matchedDocumentTypes: DocumentType[]
  documentCount: number
}

export type JourneyViewModel = {
  key: RetailJourneyKey
  title: string
  headline: string
  summary: string
  relatedDocumentTypes: DocumentType[]
  recommendedActions: string[]
  emptyDescription: string
  steps: JourneyStepViewModel[]
  linkedRecords: JourneyRecordViewModel[]
}

type JourneyStepDefinition = {
  key: string
  label: string
  description: string
  kind: 'supported' | 'planned'
  relatedDocumentTypes: DocumentType[]
  actionPath?: string
}

type JourneyDefinition = {
  key: RetailJourneyKey
  title: string
  headline: string
  summary: string
  relatedDocumentTypes: DocumentType[]
  recommendedActions: string[]
  emptyDescription: string
  steps: JourneyStepDefinition[]
}

const retailJourneyDefinitions: JourneyDefinition[] = [
  {
    key: 'orders',
    title: 'ORDERS',
    headline: 'Purchase order control tower',
    summary: 'Track inbound purchase orders, confirm schema readiness, and plan order-response follow-up.',
    relatedDocumentTypes: ['ORDER', 'APPLICATION_RESPONSE'],
    recommendedActions: [
      'Review inbound purchase orders captured in live document sets.',
      'Confirm the target schema before progressing intake workflows.',
      'Prepare for order-response coverage once broader workflow endpoints land.'
    ],
    emptyDescription: 'Add document sets containing ORDER payloads to populate this purchase-order journey.',
    steps: [
      {
        key: 'order-intake',
        label: 'Order intake evidence',
        description: 'Linked purchase-order records sourced from the live Documents API.',
        kind: 'supported',
        relatedDocumentTypes: ['ORDER'],
        actionPath: '/document-sets'
      },
      {
        key: 'order-schema',
        label: 'Schema alignment',
        description: 'Review schema readiness before operators add further order content.',
        kind: 'supported',
        relatedDocumentTypes: ['ORDER'],
        actionPath: '/schemas'
      },
      {
        key: 'order-response',
        label: 'Order response handling',
        description: 'Planned acknowledgement and amendment workflows remain outside the current backend surface.',
        kind: 'planned',
        relatedDocumentTypes: ['ORDER']
      }
    ]
  },
  {
    key: 'desadv',
    title: 'DESADV',
    headline: 'Despatch advice readiness',
    summary: 'Surface despatch advice evidence and highlight the next retail shipment checks.',
    relatedDocumentTypes: ['DESPATCH_ADVICE'],
    recommendedActions: [
      'Inspect despatch advice records attached to live document sets.',
      'Keep shipment schemas aligned for every outbound logistics feed.',
      'Plan carrier milestone support for later platform iterations.'
    ],
    emptyDescription: 'Add DESPATCH_ADVICE document sets to show shipment evidence in this journey.',
    steps: [
      {
        key: 'despatch-evidence',
        label: 'Despatch advice evidence',
        description: 'Shipment notices already captured through the document-set API.',
        kind: 'supported',
        relatedDocumentTypes: ['DESPATCH_ADVICE'],
        actionPath: '/document-sets'
      },
      {
        key: 'despatch-schema',
        label: 'Shipment schema readiness',
        description: 'Check the active schema set that underpins despatch advice payloads.',
        kind: 'supported',
        relatedDocumentTypes: ['DESPATCH_ADVICE'],
        actionPath: '/schemas'
      },
      {
        key: 'despatch-milestones',
        label: 'Carrier milestone confirmation',
        description: 'Planned transport milestones and delivery checkpoints are not yet available from the backend.',
        kind: 'planned',
        relatedDocumentTypes: ['DESPATCH_ADVICE']
      }
    ]
  },
  {
    key: 'recadv',
    title: 'RECADV',
    headline: 'Receipt advice assurance',
    summary: 'Follow warehouse receipt evidence while discrepancy handling remains planned.',
    relatedDocumentTypes: ['RECEIPT_ADVICE'],
    recommendedActions: [
      'Review goods-received evidence already stored in document sets.',
      'Keep inbound receipt schemas ready for retailers and suppliers.',
      'Prepare discrepancy workflows for future operational extensions.'
    ],
    emptyDescription: 'Add RECEIPT_ADVICE records to populate this receiving and reconciliation journey.',
    steps: [
      {
        key: 'receipt-evidence',
        label: 'Receipt advice evidence',
        description: 'Warehouse receipt messages linked to live document sets.',
        kind: 'supported',
        relatedDocumentTypes: ['RECEIPT_ADVICE'],
        actionPath: '/document-sets'
      },
      {
        key: 'receipt-schema',
        label: 'Receipt schema readiness',
        description: 'Schema checks for receipt payload variations and retailer-specific guidance.',
        kind: 'supported',
        relatedDocumentTypes: ['RECEIPT_ADVICE'],
        actionPath: '/schemas'
      },
      {
        key: 'receipt-discrepancy',
        label: 'Discrepancy resolution',
        description: 'Planned mismatch and shortage workflows need backend support before becoming executable.',
        kind: 'planned',
        relatedDocumentTypes: ['RECEIPT_ADVICE']
      }
    ]
  },
  {
    key: 'invoic',
    title: 'INVOIC',
    headline: 'Invoice evidence and follow-up',
    summary: 'Inspect invoice records while settlement orchestration remains a planned capability.',
    relatedDocumentTypes: ['INVOICE', 'REMITTANCE_ADVICE'],
    recommendedActions: [
      'Review invoice payloads already loaded into the Documents API.',
      'Confirm the governing invoice schema before adding further versions.',
      'Track payment and settlement orchestration as a planned extension.'
    ],
    emptyDescription: 'Add INVOICE document sets to populate invoice handling evidence and follow-up actions.',
    steps: [
      {
        key: 'invoice-evidence',
        label: 'Invoice evidence',
        description: 'Invoice document sets already backed by live backend data.',
        kind: 'supported',
        relatedDocumentTypes: ['INVOICE'],
        actionPath: '/document-sets'
      },
      {
        key: 'invoice-schema',
        label: 'Invoice schema readiness',
        description: 'Schema review for invoice and settlement payload structures.',
        kind: 'supported',
        relatedDocumentTypes: ['INVOICE'],
        actionPath: '/schemas'
      },
      {
        key: 'invoice-settlement',
        label: 'Settlement orchestration',
        description: 'Payment matching and remittance orchestration remain planned until backend workflow support exists.',
        kind: 'planned',
        relatedDocumentTypes: ['INVOICE']
      }
    ]
  }
]

function filterDocumentSets(documentSets: DocumentSetResponse[], relatedDocumentTypes: DocumentType[]) {
  return documentSets.filter((documentSet) =>
    documentSet.documents.some((document) => relatedDocumentTypes.includes(document.type))
  )
}

function projectLinkedRecords(documentSets: DocumentSetResponse[], relatedDocumentTypes: DocumentType[]) {
  return filterDocumentSets(documentSets, relatedDocumentTypes).map((documentSet) => ({
    documentSetId: documentSet.id,
    createdAt: documentSet.createdAt,
    createdBy: documentSet.createdBy,
    matchedDocumentTypes: documentSet.documents
      .map((document) => document.type)
      .filter((documentType) => relatedDocumentTypes.includes(documentType)),
    documentCount: documentSet.documents.length
  }))
}

function projectStep(documentSets: DocumentSetResponse[], definition: JourneyStepDefinition): JourneyStepViewModel {
  const linkedRecordCount = filterDocumentSets(documentSets, definition.relatedDocumentTypes).length

  return {
    key: definition.key,
    label: definition.label,
    description: definition.description,
    state: definition.kind === 'planned' ? 'planned' : linkedRecordCount > 0 ? 'available' : 'empty',
    linkedRecordCount,
    relatedDocumentTypes: definition.relatedDocumentTypes,
    actionPath: definition.actionPath
  }
}

function projectJourney(documentSets: DocumentSetResponse[], definition: JourneyDefinition): JourneyViewModel {
  return {
    key: definition.key,
    title: definition.title,
    headline: definition.headline,
    summary: definition.summary,
    relatedDocumentTypes: definition.relatedDocumentTypes,
    recommendedActions: definition.recommendedActions,
    emptyDescription: definition.emptyDescription,
    steps: definition.steps.map((step) => projectStep(documentSets, step)),
    linkedRecords: projectLinkedRecords(documentSets, definition.relatedDocumentTypes)
  }
}

export function projectRetailJourneys(documentSets: DocumentSetResponse[]) {
  return retailJourneyDefinitions.map((definition) => projectJourney(documentSets, definition))
}

export function getRetailJourney(journeyKey: string, documentSets: DocumentSetResponse[]) {
  const definition = retailJourneyDefinitions.find((candidate) => candidate.key === journeyKey)

  return definition ? projectJourney(documentSets, definition) : undefined
}
