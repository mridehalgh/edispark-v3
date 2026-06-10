import type {
  HttpMethod,
  OpenApiDocument,
  OpenApiOperationObject,
  OpenApiReferenceObject,
  OpenApiRequestBodyObject,
  OpenApiResponseObject,
  OpenApiSchemaObject
} from '@/integration/openapi-contract'

export type ApiOperationSummary = {
  operationId: string
  method: Uppercase<HttpMethod>
  path: string
  summary: string
  tag?: string
}

export type ApiSchemaMetadata = {
  contentType: string
  schemaSummary: string
  schemaRef?: string
  required?: boolean
}

export type ApiResponseDetail = {
  statusCode: string
  description?: string
  schemas: ApiSchemaMetadata[]
}

export type ApiOperationDetail = ApiOperationSummary & {
  description?: string
  requestSchemas: ApiSchemaMetadata[]
  responseSchemas: ApiResponseDetail[]
}

export type ApiExplorerProjection = {
  title: string
  version: string
  sourceLabel: 'live-openapi'
  operations: ApiOperationSummary[]
  operationDetails: Record<string, ApiOperationDetail>
}

type DescribedSchema = {
  schemaSummary: string
  schemaRef?: string
}

const supportedMethods: HttpMethod[] = ['get', 'post', 'put', 'patch', 'delete', 'options', 'head']

function isReferenceObject(value: unknown): value is OpenApiReferenceObject {
  return typeof value === 'object' && value !== null && '$ref' in value
}

function createOperationId(path: string, method: HttpMethod, operation: OpenApiOperationObject) {
  return operation.operationId ?? `${method.toUpperCase()} ${path}`
}

function describeSchema(schema?: OpenApiSchemaObject | OpenApiReferenceObject): DescribedSchema {
  if (!schema) {
    return { schemaSummary: 'No schema metadata provided' }
  }

  if (isReferenceObject(schema)) {
    const ref = schema.$ref.split('/').pop() ?? schema.$ref

    return {
      schemaSummary: ref,
      schemaRef: schema.$ref
    }
  }

  if (schema.title) {
    return {
      schemaSummary: schema.title
    }
  }

  if (schema.enum?.length) {
    return {
      schemaSummary: `enum(${schema.enum.join(', ')})`
    }
  }

  if (schema.type === 'array') {
    const itemDescription: DescribedSchema = describeSchema(schema.items)

    return {
      schemaSummary: `array<${itemDescription.schemaSummary}>`,
      schemaRef: itemDescription.schemaRef
    }
  }

  if (schema.type) {
    const typeDetails = [schema.type, schema.format].filter(Boolean).join(' ')

    return {
      schemaSummary: typeDetails
    }
  }

  return {
    schemaSummary: 'object'
  }
}

function projectRequestSchemas(requestBody?: OpenApiRequestBodyObject | OpenApiReferenceObject) {
  if (!requestBody) {
    return []
  }

  if (isReferenceObject(requestBody)) {
    const schemaDescription = describeSchema(requestBody)

    return [
      {
        contentType: 'application/json',
        required: false,
        ...schemaDescription
      }
    ]
  }

  return Object.entries(requestBody.content ?? {}).map(([contentType, mediaType]) => ({
    contentType,
    required: requestBody.required ?? false,
    ...describeSchema(mediaType.schema)
  }))
}

function projectResponseSchemas(responses: Record<string, OpenApiResponseObject | OpenApiReferenceObject> | undefined) {
  return Object.entries(responses ?? {}).map(([statusCode, response]) => {
    if (isReferenceObject(response)) {
      return {
        statusCode,
        schemas: [
          {
            contentType: 'application/json',
            ...describeSchema(response)
          }
        ]
      }
    }

    return {
      statusCode,
      description: response.description,
      schemas: Object.entries(response.content ?? {}).map(([contentType, mediaType]) => ({
        contentType,
        ...describeSchema(mediaType.schema)
      }))
    }
  })
}

function projectOperationDetail(path: string, method: HttpMethod, operation: OpenApiOperationObject): ApiOperationDetail {
  const operationId = createOperationId(path, method, operation)

  return {
    operationId,
    method: method.toUpperCase() as Uppercase<HttpMethod>,
    path,
    summary: operation.summary ?? operationId,
    tag: operation.tags?.[0],
    description: operation.description,
    requestSchemas: projectRequestSchemas(operation.requestBody),
    responseSchemas: projectResponseSchemas(operation.responses)
  }
}

export function projectApiExplorer(document: OpenApiDocument): ApiExplorerProjection {
  const details = Object.entries(document.paths)
    .flatMap(([path, pathItem]) =>
      supportedMethods.flatMap((method) => {
        const operation = pathItem[method]

        return operation ? [projectOperationDetail(path, method, operation)] : []
      })
    )
    .sort((left, right) => left.path.localeCompare(right.path) || left.method.localeCompare(right.method))

  const operations = details.map(({ operationId, method, path, summary, tag }) => ({
    operationId,
    method,
    path,
    summary,
    tag
  }))

  return {
    title: document.info.title,
    version: document.info.version,
    sourceLabel: 'live-openapi',
    operations,
    operationDetails: Object.fromEntries(details.map((detail) => [detail.operationId, detail]))
  }
}

export function getApiOperationDetail(projection: ApiExplorerProjection, operationId: string) {
  return projection.operationDetails[operationId]
}
