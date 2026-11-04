type AttentionStage = { detail?: string | null }

type AttentionRecord = {
  status?: string | null
  failureReason?: string | null
  stages?: AttentionStage[] | null
  sourceDocument?: { documentSetId?: string | null; documentId?: string | null } | null
  updatedAt?: string | null
}

const ATTENTION_STATUSES = new Set(["FAILED", "NEEDS_REVIEW", "RETRY_SCHEDULED"])

export function attentionReason(record: AttentionRecord): string | undefined {
  if (!ATTENTION_STATUSES.has(record.status ?? "")) return undefined
  const failureReason = record.failureReason?.trim()
  if (failureReason) return failureReason
  return record.stages?.findLast((stage) => Boolean(stage.detail?.trim()))?.detail?.trim()
}

export function latestDocumentAttentionReason(records: AttentionRecord[], documentSetId: string, documentId: string) {
  const latest = records
    .filter((record) => record.sourceDocument?.documentSetId === documentSetId
      && record.sourceDocument.documentId === documentId)
    .sort((left, right) => (right.updatedAt ?? "").localeCompare(left.updatedAt ?? ""))
    .at(0)
  return latest ? attentionReason(latest) : undefined
}
