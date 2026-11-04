import type { TradingAgreement } from "@edispark/integration-client"

interface DocumentMetadata {
  partnerId?: string
  mftPartnerId?: string
}

export function resolveSmartCopyTradingPartner(
  metadata: DocumentMetadata | undefined,
  agreements: TradingAgreement[] = [],
) {
  if (metadata?.partnerId) return metadata.partnerId
  const connectionId = metadata?.mftPartnerId
  if (!connectionId) return undefined
  const matches = new Set(agreements
    .filter((agreement) => agreement.status === "ACTIVE")
    .filter((agreement) => agreement.capabilities?.some((capability) => capability.as2ConnectionId === connectionId))
    .flatMap((agreement) => agreement.partnerId ? [agreement.partnerId] : []))
  return matches.size === 1 ? [...matches][0] : undefined
}
