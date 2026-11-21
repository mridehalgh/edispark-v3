"use client"

import type { ExchangeResponse, TradingPartner } from "@edispark/integration-client"
import type { CertificateResponse, PartnerResponse, TransferResponse } from "@edispark/mft-client"
import { ArrowDownLeft, ArrowRight, ArrowUpRight, CheckCircle2, CircleAlert, Clock3, FilePlus, FileText, ShieldAlert } from "lucide-react"
import { useEffect, useMemo, useState, type ReactNode } from "react"
import { Link, useSearchParams } from "react-router-dom"

import { canManageOrganization, type AccessContext } from "@/lib/access"
import { adminApi, formatAdminDate } from "@/lib/admin-api"
import { authClient } from "@/lib/auth-client"
import { getDocumentSets } from "@/lib/documents-api"
import { useAccess } from "@/lib/use-access"
import { dispatchRecords, type DispatchRecord } from "./dispatch-records"
import { QuestionComposer } from "./question-composer"
import styles from "./dispatch.module.css"

const directions = [["all", "All directions"], ["inbound", "Inbound"], ["outbound", "Outbound"]] as const

function useDispatchData(admin: boolean, version: number) {
  const [data, setData] = useState<{
    documents?: Awaited<ReturnType<typeof getDocumentSets>>
    exchanges?: ExchangeResponse[]
    partners?: TradingPartner[]
    transfers?: TransferResponse[]
    connections?: PartnerResponse[]
    certificates?: CertificateResponse[]
    errors: string[]
    version: number
  }>()

  useEffect(() => {
    const controller = new AbortController()
    const init = { signal: controller.signal }
    void Promise.allSettled([
      getDocumentSets({ ...init, workspace: true, limit: 20 }),
      adminApi<ExchangeResponse[]>("/api/integration/exchanges", init),
      adminApi<TradingPartner[]>("/api/integration/partners", init),
      admin ? adminApi<TransferResponse[]>("/api/mft/transfers", init) : Promise.resolve(undefined),
      admin ? adminApi<PartnerResponse[]>("/api/mft/partners", init) : Promise.resolve(undefined),
      admin ? adminApi<CertificateResponse[]>("/api/mft/certificates", init) : Promise.resolve(undefined),
    ]).then((results) => {
      if (controller.signal.aborted) return
      const [documents, exchanges, partners, transfers, connections, certificates] = results
      const names = ["documents", "exchange history", "trading partners", "file transfers", "connections", "certificates"]
      setData({
        documents: documents.status === "fulfilled" ? documents.value : undefined,
        exchanges: exchanges.status === "fulfilled" ? exchanges.value : undefined,
        partners: partners.status === "fulfilled" ? partners.value : undefined,
        transfers: transfers.status === "fulfilled" ? transfers.value : undefined,
        connections: connections.status === "fulfilled" ? connections.value : undefined,
        certificates: certificates.status === "fulfilled" ? certificates.value : undefined,
        errors: names.filter((_, index) => results[index].status === "rejected"),
        version,
      })
    })
    return () => controller.abort()
  }, [admin, version])

  return data
}

export function OverviewPage() {
  const { access, loading } = useAccess()
  const { data: organisation, isPending } = authClient.useActiveOrganization()
  const { data: session } = authClient.useSession()
  if (loading || isPending) return <div className={styles.desk}><p role="status">Loading workspace…</p></div>
  if (organisation && !access) return <div className={styles.desk}>
    <h1>Desk</h1>
    <p role="status">Could not check your workspace access.</p>
    <div><button className={styles.outlineButton} type="button" onClick={() => window.location.reload()}>Try again</button></div>
  </div>
  if (!access || !organisation || !session) return <div className={styles.desk}>
    <h1>Desk</h1>
    <p>Choose a workspace to see its dispatch activity.</p>
    <Link className={styles.textLink} to="/workspace">Choose workspace <ArrowRight size={16} aria-hidden="true" /></Link>
  </div>
  const scope = `${session.user.id}:${organisation.id}`

  return <DispatchDesk key={scope + access.organizationRole} access={access} scope={scope} />
}

function DispatchDesk({ access, scope }: { access: AccessContext; scope: string }) {
  const admin = canManageOrganization(access.organizationRole)
  const [version, setVersion] = useState(0)
  const [showAll, setShowAll] = useState(false)
  const [search, setSearch] = useSearchParams()
  const data = useDispatchData(admin, version)
  const loading = data?.version !== version
  const records = useMemo(() => dispatchRecords({
    sets: data?.documents?.items ?? [], exchanges: data?.exchanges ?? [], partners: data?.partners ?? [],
    transfers: data?.transfers ?? [], connections: data?.connections ?? [], admin,
  }), [admin, data])
  const attention = records.filter((record) => record.needsAttention)
    .sort((left, right) => Number(Boolean(left.fileTransfer)) - Number(Boolean(right.fileTransfer)))
  const retrying = records.filter((record) => record.scheduledRetry).length
  const certificates = (data?.certificates ?? []).filter((certificate) => certificate.daysUntilExpiry !== undefined && certificate.daysUntilExpiry < 30)
  const direction = directions.find(([value]) => value === search.get("direction"))?.[0] ?? "all"
  const recent = records.filter((record) =>
    !record.fileTransfer && (direction === "all" || record.direction?.toLowerCase() === direction),
  ).slice(0, 8)
  const restrictedReview = !admin && data?.exchanges?.some((exchange) =>
    (exchange.status === "NEEDS_REVIEW" || exchange.status === "FAILED")
    && !exchange.sourceDocument && exchange.mftTransferId,
  )
  const returnTo = search.get("record")
  const showAllAttention = showAll || Boolean(returnTo?.startsWith("attention-"))
  const totalAttention = attention.length + certificates.length
  const visibleCertificates = showAllAttention ? certificates : certificates.slice(0, 1)

  useEffect(() => {
    if (loading || !returnTo) return
    const row = document.getElementById(returnTo)
    if (row) {
      row.focus({ preventScroll: true })
      row.scrollIntoView({ block: "nearest" })
    }
  }, [loading, returnTo])

  function recordLink(record: DispatchRecord, section: "attention" | "recent") {
    const id = `${section}-${record.href}`
    const back = new URLSearchParams({ direction, record: id })
    return { id, to: record.href, state: { dispatch: `/overview?${back}` } }
  }

  return (
    <div className={styles.desk}>
      {data?.errors.length ? <div className={styles.notice} role="status">
        Could not load {new Intl.ListFormat("en-GB").format(data.errors)}.
        <button className={styles.textLink} disabled={loading} onClick={() => setVersion((current) => current + 1)}>Try again</button>
      </div> : null}

      <QuestionComposer scope={scope} mcp={Boolean(access.mcpSetup)} />
      <div className={styles.topGrid}>
        <section className={styles.sheet} aria-labelledby="dispatch-attention" aria-busy={loading}>
          <SheetHeading icon={<CircleAlert size={17} aria-hidden="true" />} title="Needs attention" id="dispatch-attention">
            {totalAttention > 0 && <span className={styles.count} aria-label={`${totalAttention} items in this view`}>{totalAttention}</span>}
          </SheetHeading>
          {!data && loading ? <LoadingRows /> : totalAttention ? (
            <ul className={styles.attentionList}>
              {attention.slice(0, showAllAttention ? undefined : Math.max(0, 3 - visibleCertificates.length)).map((record) => (
                <li key={record.href}><Link {...recordLink(record, "attention")} className={styles.attentionRow}>
                  <div>
                    <span className={styles.attentionType}>{record.type} <span>·</span> {record.partner}</span>
                    <strong className={styles.reference}>{record.reference}</strong>
                    <span className={styles.reason}>{record.reason || record.status}</span>
                  </div>
                  <ArrowRight size={16} aria-hidden="true" />
                </Link></li>
              ))}
              {visibleCertificates.map((certificate) => <li key={certificate.id}>
                <Link to="/connections/certificates" className={styles.attentionRow}>
                  <div><span className={styles.attentionType}>Connection certificate</span><strong>{certificate.name ?? "Unnamed certificate"}</strong>
                    <span className={styles.reason}><ShieldAlert size={13} aria-hidden="true" />{certificate.daysUntilExpiry! < 0 ? "Expired" : "Expires"} · {formatAdminDate(certificate.notAfter)}</span></div>
                  <ArrowRight size={16} aria-hidden="true" />
                </Link>
              </li>)}
            </ul>
          ) : <p className={styles.empty}>{data?.documents && data.exchanges && (!admin || (data.transfers && data.certificates))
            ? (restrictedReview ? "An incoming file needs an administrator’s review." : "No issues found in the records shown.")
            : "Attention status is unavailable."}</p>}
          {restrictedReview && totalAttention > 0 && <p className={styles.note}>An incoming file also needs an administrator’s review.</p>}
          {totalAttention > 3 && !returnTo?.startsWith("attention-") ? <button aria-expanded={showAll} type="button" className={styles.sheetAction} onClick={() => setShowAll(!showAll)}>{showAll ? "Show fewer items" : "Show all items"} <ArrowRight size={15} aria-hidden="true" /></button> : null}
          {retrying > 0 && <Link className={styles.sheetAction} to="/files?status=RETRY_SCHEDULED"><Clock3 size={15} aria-hidden="true" />{retrying} {retrying === 1 ? "file has" : "files have"} a scheduled retry <ArrowRight size={15} aria-hidden="true" /></Link>}
        </section>


      <section className={styles.sheet} aria-labelledby="dispatch-recent" aria-busy={loading}>
        <SheetHeading icon={<FileText size={17} aria-hidden="true" />} title="Recent documents" id="dispatch-recent">
          <Link className={styles.primaryButton} to="/documents/new"><FilePlus size={16} aria-hidden="true" /> New document</Link>
        </SheetHeading>
        <div className={styles.filterBar}>
          <div>
            <label htmlFor="dispatch-direction">Direction</label>
            <select id="dispatch-direction" value={direction} onChange={(event) => setSearch({ direction: event.target.value }, { replace: true })}>
              {directions.map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select>
          </div>
          <Link className={styles.textLink} to="/documents">View all documents <ArrowRight size={15} aria-hidden="true" /></Link>
        </div>
        {!data && loading ? <LoadingRows /> : recent.length ? (
          <table className={styles.records} role="table">
            <thead role="rowgroup"><tr role="row"><th scope="col">Document / reference</th><th scope="col">Trading partner</th><th scope="col">Direction</th><th scope="col">Updated</th><th scope="col">Recorded status</th></tr></thead>
            <tbody role="rowgroup">
              {recent.map((record) => <tr key={record.href} role="row">
                <td role="cell"><Link {...recordLink(record, "recent")} className={styles.recordLink}>
                  <FileText size={21} aria-hidden="true" /><span>{record.type}</span><small>{record.reference}</small>
                </Link></td>
                <td role="cell"><span className={styles.mobileLabel}>Partner</span>{record.partner}</td>
                <td role="cell"><span className={styles.mobileLabel}>Direction</span><Direction record={record} /></td>
                <td role="cell"><span className={styles.mobileLabel}>Updated</span><time dateTime={record.updatedAt}>{formatAdminDate(record.updatedAt)}</time></td>
                <td role="cell"><span className={styles.mobileLabel}>Status</span><Status record={record} /></td>
              </tr>)}
            </tbody>
          </table>
        ) : <p className={styles.empty}>{data?.documents ? "No documents to show for this selection." : "Recent documents are unavailable."}</p>}
        <p className={styles.note}>Transport delivery, technical acknowledgement and retailer acceptance are separate outcomes. Open a record to see what was recorded.</p>
        {data?.documents?.hasNext && <p className={styles.note}>Document activity covers the latest document sets. Open Documents to find older records.</p>}
      </section>
      </div>
    </div>
  )
}

function SheetHeading({ icon, id, title, children }: { icon: ReactNode; id: string; title: string; children?: ReactNode }) {
  return <div className={styles.sheetHeading}>{icon}<h2 id={id}>{title}</h2>{children && <div className={styles.headingAction}>{children}</div>}</div>
}

function LoadingRows() {
  return <div className={styles.loadingRows} role="status"><span className="sr-only">Loading records…</span><i /><i /><i /></div>
}

function Direction({ record }: { record: DispatchRecord }) {
  const Icon = record.direction === "INBOUND" ? ArrowDownLeft : ArrowUpRight
  return <span className={styles.direction}>{record.direction ? <><Icon size={14} aria-hidden="true" />{record.direction === "INBOUND" ? "Inbound" : "Outbound"}</> : "Unrecorded"}</span>
}

function Status({ record }: { record: DispatchRecord }) {
  const Icon = record.tone === "success" ? CheckCircle2 : record.tone === "error" ? CircleAlert : Clock3
  return <span className={styles.status} data-tone={record.tone}><Icon size={14} aria-hidden="true" />{record.status}</span>
}
