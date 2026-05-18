import type { DocumentType, Format, MutationDebugState, SchemaFormat } from '@/lib/models'
import type { SupportedMessageType } from '@/lib/message-types'

type MetadataInput = string

export type CreateSchemaFormInput = {
  name: string
  format: SchemaFormat
}

export type AddSchemaVersionFormInput = {
  schemaId: string
  versionIdentifier: string
  definitionText: string
}

export type CreateDocumentSetFormInput = {
  documentType: DocumentType
  schemaId: string
  schemaVersion: string
  createdBy: string
  messageType: SupportedMessageType
  contentText: string
  metadataText: MetadataInput
}

export type AddDocumentFormInput = {
  setId: string
  documentType: DocumentType
  schemaId: string
  schemaVersion: string
  createdBy: string
  messageType: SupportedMessageType
  relatedDocumentId: string
  contentText: string
}

export type AddVersionFormInput = {
  setId: string
  docId: string
  createdBy: string
  messageType: SupportedMessageType
  contentText: string
}

export type CreateDerivativeFormInput = {
  setId: string
  docId: string
  sourceVersionNumber: number
  targetFormat: Format
}

export type ValidateDocumentFormInput = {
  setId: string
  docId: string
  versionNumber: number
}

const encodeBytes = (rawText: string) => {
  const bytes = new TextEncoder().encode(rawText)
  let binary = ''
  bytes.forEach((byte) => {
    binary += String.fromCharCode(byte)
  })
  return btoa(binary)
}

const parseMetadata = (metadataText: MetadataInput) =>
  metadataText
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .reduce<Record<string, string>>((metadata, line) => {
      const [key, ...valueParts] = line.split('=')
      if (!key) {
        return metadata
      }

      metadata[key.trim()] = valueParts.join('=').trim()
      return metadata
    }, {})

export const encodePayload = (rawText: string) => ({
  rawText,
  base64: encodeBytes(rawText),
  byteLength: new TextEncoder().encode(rawText).length,
})

export const buildCreateSchemaRequest = (input: CreateSchemaFormInput) => ({
  name: input.name,
  format: input.format,
})

export const buildAddSchemaVersionRequest = (input: AddSchemaVersionFormInput) => ({
  versionIdentifier: input.versionIdentifier,
  definition: encodePayload(input.definitionText).base64,
})

export const buildCreateDocumentSetRequest = (input: CreateDocumentSetFormInput) => ({
  documentType: input.documentType,
  schemaId: input.schemaId,
  schemaVersion: input.schemaVersion,
  content: encodePayload(input.contentText).base64,
  createdBy: input.createdBy,
  metadata: parseMetadata(input.metadataText),
})

export const buildAddDocumentRequest = (input: AddDocumentFormInput) => ({
  documentType: input.documentType,
  schemaId: input.schemaId,
  schemaVersion: input.schemaVersion,
  content: encodePayload(input.contentText).base64,
  createdBy: input.createdBy,
  relatedDocumentId: input.relatedDocumentId || undefined,
})

export const buildAddVersionRequest = (input: AddVersionFormInput) => ({
  content: encodePayload(input.contentText).base64,
  createdBy: input.createdBy,
})

export const buildCreateDerivativeRequest = (input: CreateDerivativeFormInput) => ({
  sourceVersionNumber: input.sourceVersionNumber,
  targetFormat: input.targetFormat,
})

export const buildValidateDocumentState = <TInput, TRequest, TResponse>(
  input: TInput,
  requestPreview?: TRequest,
  response?: TResponse,
): MutationDebugState<TInput, TRequest, TResponse> => ({
  input,
  requestPreview,
  response,
  status: 'idle',
})
