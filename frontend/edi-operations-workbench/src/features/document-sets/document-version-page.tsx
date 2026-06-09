import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'

import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { createDownloadDescriptor, projectValidationEvidence } from '@/features/document-sets/document-workflow'
import { ErrorState, LoadingState } from '@/components/workbench-states'
import type { DocumentVersionResponse, ValidationResultResponse } from '@/integration/documents-api-client'
import { useIntegration } from '@/integration/integration-provider'

export function DocumentVersionPage() {
  const params = useParams<{ setId: string; docId: string; versionNumber: string }>()
  const { runRequest } = useIntegration()
  const [version, setVersion] = useState<DocumentVersionResponse>()
  const [validationResult, setValidationResult] = useState<ValidationResultResponse>()
  const [error, setError] = useState<string>()
  const [downloadError, setDownloadError] = useState<string>()
  const [loading, setLoading] = useState(true)
  const setId = params.setId ?? ''
  const docId = params.docId ?? ''
  const versionNumber = Number(params.versionNumber ?? '0')

  async function loadVersion() {
    setLoading(true)
    setError(undefined)
    const result = await runRequest(`documentVersion:${setId}:${docId}:${versionNumber}`, (client) => client.getVersion(setId, docId, versionNumber))
    setLoading(false)

    if (result.status !== 'succeeded') {
      setError(result.status === 'failed' ? result.reason : 'The document-version request did not complete.')
      return
    }

    setVersion(result.data)
  }

  useEffect(() => {
    void loadVersion()
  }, [setId, docId, versionNumber])

  async function validateVersion() {
    const result = await runRequest(`validateDocument:${setId}:${docId}:${versionNumber}`, (client) => client.validateDocument(setId, docId, versionNumber))
    if (result.status === 'succeeded') {
      setValidationResult(result.data)
      setDownloadError(undefined)
    } else {
      setValidationResult(undefined)
      setDownloadError(result.status === 'failed' ? result.reason : 'The validation request did not complete.')
    }
  }

  async function downloadContent() {
    setDownloadError(undefined)
    const result = await runRequest(`downloadDocumentContent:${setId}:${docId}:${versionNumber}`, (client) => client.getVersionContent(setId, docId, versionNumber))

    if (result.status !== 'succeeded') {
      setDownloadError(
        `Content for ${docId} version ${versionNumber} is unavailable right now. ${result.status === 'failed' ? result.reason : 'The request did not complete.'}`
      )
      return
    }

    const descriptor = createDownloadDescriptor(result.data)
    const blob = new Blob([descriptor.bytes.buffer as ArrayBuffer], { type: descriptor.mediaType })
    const objectUrl = URL.createObjectURL(blob)
    const anchor = document.createElement('a')
    anchor.href = objectUrl
    anchor.download = descriptor.fileName
    anchor.click()
    URL.revokeObjectURL(objectUrl)
  }

  if (loading) {
    return <LoadingState title="Loading document version" description="Fetching live version metadata before inspection actions render." />
  }

  if (!version || error) {
    return <ErrorState title="Document version detail is unavailable" description="The version inspection view could not load its live backend response." details={error} onRetry={() => void loadVersion()} />
  }

  const validationEvidence = validationResult ? projectValidationEvidence(validationResult) : undefined

  return (
    <div className="space-y-6">
      <Card className="border-border/70 bg-card/95 shadow-sm">
        <CardHeader>
          <Badge className="w-fit">Document version detail</Badge>
          <CardTitle>{version.id}</CardTitle>
          <CardDescription>
            Document {docId} in set {setId} · version {version.versionNumber}
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4 text-sm text-muted-foreground">
          <div className="flex flex-wrap gap-2">
            <Badge className="border-border bg-transparent">{version.format}</Badge>
            <Badge className="border-border bg-transparent">{version.parseStatus ?? 'No parse status'}</Badge>
            {version.messageType ? <Badge className="border-border bg-transparent">{version.messageType}</Badge> : null}
          </div>
          <p>Created by {version.createdBy} on {new Date(version.createdAt).toLocaleString()}</p>
          <p>Content hash: {version.contentHash}</p>
          {version.parseErrors.length > 0 ? <pre className="rounded-2xl bg-muted p-4 text-xs">{JSON.stringify(version.parseErrors, null, 2)}</pre> : <p>No parse errors were returned for this version.</p>}
          <div className="flex flex-wrap gap-2">
            <Button onClick={() => void validateVersion()} type="button" variant="outline">Validate version</Button>
            <Button onClick={() => void downloadContent()} type="button">Download raw content</Button>
            <Button asChild type="button" variant="outline"><Link to={`/document-sets/${setId}`}>Back to document set</Link></Button>
          </div>
        </CardContent>
      </Card>

      {validationEvidence ? (
        <Card className="border-border/70 bg-card/95 shadow-sm">
          <CardHeader>
            <CardTitle>Validation outcome</CardTitle>
            <CardDescription>{validationEvidence.valid ? 'This version passed validation.' : 'This version failed validation.'}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4 text-sm text-muted-foreground">
            <div className="flex flex-wrap gap-2">
              <Badge className={validationEvidence.valid ? 'border-transparent bg-primary text-primary-foreground' : 'border-transparent bg-destructive text-destructive-foreground'}>
                {validationEvidence.statusLabel}
              </Badge>
            </div>
            {validationEvidence.errors.length > 0 ? <pre className="rounded-2xl bg-muted p-4 text-xs">{JSON.stringify(validationEvidence.errors, null, 2)}</pre> : null}
            {validationEvidence.warnings.length > 0 ? <pre className="rounded-2xl bg-muted p-4 text-xs">{JSON.stringify(validationEvidence.warnings, null, 2)}</pre> : null}
          </CardContent>
        </Card>
      ) : null}

      {downloadError ? (
        <ErrorState
          title="Raw content could not be downloaded"
          description="This is recoverable. Retry the same document version after the backend content becomes available again."
          details={downloadError}
          onRetry={() => void downloadContent()}
          retryLabel="Retry download"
        />
      ) : null}
    </div>
  )
}
