export interface components {
  schemas: {
    SchemaFormat: 'XSD' | 'JSON_SCHEMA' | 'RELAXNG'
    Format: 'XML' | 'JSON' | 'PDF' | 'EDI'
    DocumentType:
      | 'INVOICE'
      | 'ORDER'
      | 'CREDIT_NOTE'
      | 'DEBIT_NOTE'
      | 'QUOTATION'
      | 'DESPATCH_ADVICE'
      | 'RECEIPT_ADVICE'
      | 'STATEMENT'
      | 'REMITTANCE_ADVICE'
      | 'CATALOGUE'
      | 'APPLICATION_RESPONSE'
    CreateSchemaRequest: {
      name: string
      format: components['schemas']['SchemaFormat']
    }
    AddSchemaVersionRequest: {
      versionIdentifier: string
      definition: string
    }
    CreateDocumentSetRequest: {
      documentType: components['schemas']['DocumentType']
      schemaId: string
      schemaVersion: string
      content: string
      createdBy: string
      metadata?: Record<string, string>
    }
    DocumentSetPageResponse: {
      items: components['schemas']['DocumentSetResponse'][]
      pageSize: number
      hasPrevious: boolean
      hasNext: boolean
      previousToken?: string
      nextToken?: string
      previousUrl?: string
      nextUrl?: string
    }
    SchemaVersionSummary: {
      id: string
      versionIdentifier: string
      createdAt: string
      deprecated: boolean
    }
    SchemaResponse: {
      id: string
      name: string
      format: components['schemas']['SchemaFormat']
      versions: components['schemas']['SchemaVersionSummary'][]
    }
    SchemaVersionResponse: {
      id: string
      versionIdentifier: string
      createdAt: string
      deprecated: boolean
    }
    DocumentSummary: {
      id: string
      type: components['schemas']['DocumentType']
      versionCount: number
    }
    DocumentSetResponse: {
      id: string
      createdAt: string
      createdBy: string
      metadata?: Record<string, string>
      documents: components['schemas']['DocumentSummary'][]
    }
    ErrorResponse: {
      code: string
      message: string
      timestamp: string
      details?: Record<string, unknown>
    }
  }
}

type JsonResponse<T> = {
  content: {
    'application/json': T
  }
}

export interface paths {
  '/api/schemas': {
    post: {
      requestBody: JsonResponse<components['schemas']['CreateSchemaRequest']>
      responses: {
        201: JsonResponse<components['schemas']['SchemaResponse']>
        400: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
  '/api/schemas/{id}': {
    get: {
      responses: {
        200: JsonResponse<components['schemas']['SchemaResponse']>
        404: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
  '/api/schemas/{schemaId}/versions': {
    post: {
      requestBody: JsonResponse<components['schemas']['AddSchemaVersionRequest']>
      responses: {
        201: JsonResponse<components['schemas']['SchemaVersionResponse']>
        400: JsonResponse<components['schemas']['ErrorResponse']>
        404: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
  '/api/schemas/{schemaId}/versions/{versionId}': {
    get: {
      responses: {
        200: JsonResponse<components['schemas']['SchemaVersionResponse']>
        404: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
  '/api/document-sets': {
    get: {
      responses: {
        200: JsonResponse<components['schemas']['DocumentSetPageResponse']>
        400: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
    post: {
      requestBody: JsonResponse<components['schemas']['CreateDocumentSetRequest']>
      responses: {
        201: JsonResponse<components['schemas']['DocumentSetResponse']>
        400: JsonResponse<components['schemas']['ErrorResponse']>
        404: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
  '/api/document-sets/{id}': {
    get: {
      responses: {
        200: JsonResponse<components['schemas']['DocumentSetResponse']>
        404: JsonResponse<components['schemas']['ErrorResponse']>
      }
    }
  }
}
