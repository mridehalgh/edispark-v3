import type {
  DocumentResponse,
  DocumentSetResponse,
  ValidationResultResponse,
  VersionContentResponse
} from '@/integration/documents-api-client'

export type CreateDocumentSetFormModel = {
  schemaId: string
  schemaVersion: string
  contentText: string
  createdBy: string
}

export type AddDocumentFormModel = CreateDocumentSetFormModel

export type AddVersionFormModel = {
  contentText: string
  createdBy: string
}

export function validateCreateDocumentSetForm(form: CreateDocumentSetFormModel) {
  return [
    form.schemaId.trim() ? undefined : 'Schema ID is required.',
    form.schemaVersion.trim() ? undefined : 'Schema version is required.',
    form.contentText.trim() ? undefined : 'Document content is required.',
    form.createdBy.trim() ? undefined : 'Created by is required.'
  ].filter(Boolean) as string[]
}

export function validateAddDocumentForm(form: AddDocumentFormModel) {
  return validateCreateDocumentSetForm(form)
}

export function validateAddVersionForm(form: AddVersionFormModel) {
  return [form.contentText.trim() ? undefined : 'Version content is required.', form.createdBy.trim() ? undefined : 'Created by is required.'].filter(Boolean) as string[]
}

export function validateDerivativeSourceVersion(sourceVersionNumber: string) {
  const parsed = Number(sourceVersionNumber)
  return Number.isInteger(parsed) && parsed >= 1 ? [] : ['Source version number must be 1 or greater.']
}

export function projectDocumentSetEvidence(documentSet: DocumentSetResponse, documents: DocumentResponse[]) {
  return {
    documentSetId: documentSet.id,
    documentCount: documents.length,
    documents: documents.map((document) => ({
      documentId: document.id,
      documentType: document.type,
      schemaId: document.schemaRef.schemaId,
      schemaVersion: document.schemaRef.version,
      currentVersionId: document.currentVersion.id,
      currentVersionNumber: document.currentVersion.versionNumber,
      derivativeCount: document.derivatives.length,
      format: document.currentVersion.format,
      parseStatus: document.currentVersion.parseStatus,
      parseErrors: document.currentVersion.parseErrors
    }))
  }
}

export function projectValidationEvidence(result: ValidationResultResponse) {
  return {
    valid: result.valid,
    statusLabel: result.valid ? 'Pass' : 'Fail',
    errors: result.errors,
    warnings: result.warnings
  }
}

export function createDownloadDescriptor(response: VersionContentResponse) {
  return {
    bytes: response.bytes,
    fileName: response.fileName,
    mediaType: response.mediaType,
    documentSetId: response.documentSetId,
    documentId: response.documentId,
    versionNumber: response.versionNumber,
    contentHash: response.contentHash,
    format: response.format
  }
}
