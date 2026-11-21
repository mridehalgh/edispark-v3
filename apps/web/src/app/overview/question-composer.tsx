"use client"

import { ArrowUpRight, Clipboard, FileText, Plus, Sparkles, X } from "lucide-react"
import { useId, useRef, useState } from "react"
import { Link, useLocation } from "react-router-dom"

import { authClient } from "@/lib/auth-client"
import { useAccess } from "@/lib/use-access"
import Logo from "@/components/logo/logo"
import styles from "./dispatch.module.css"

type RecordContext = { label: string; path: string }
const workspacePrompts = ["What needs attention today?", "Which orders have arrived?"]

export function QuestionComposer({ scope, record, prompts = workspacePrompts, mcp }: {
  scope: string
  record?: RecordContext
  prompts?: string[]
  mcp: boolean
}) {
  const Heading = record ? "h2" : "h1"
  const field = useRef<HTMLTextAreaElement>(null)
  const id = useId()
  const storageKey = `edispark:question:${scope}:${record?.path ?? "workspace"}`
  const [saved, setSaved] = useState<{ draft: string; detached: boolean }>(() => {
    try {
      const stored = JSON.parse(sessionStorage.getItem(storageKey) ?? "null")
      if (typeof stored?.draft === "string") return { draft: stored.draft, detached: stored.detached === true }
    } catch { /* Storage can be unavailable. */ }
    return { draft: "", detached: false }
  })
  const { draft, detached } = saved
  const context = detached ? undefined : record
  const [feedback, setFeedback] = useState("")

  function update(value: string, removeContext = detached) {
    const next = { draft: value, detached: removeContext }
    setSaved(next)
    setFeedback("")
    try {
      if (value || removeContext) sessionStorage.setItem(storageKey, JSON.stringify(next))
      else sessionStorage.removeItem(storageKey)
    } catch { /* Draft remains usable in this view. */ }
  }

  function startFresh() {
    update("", Boolean(record))
    field.current?.focus()
  }

  async function copyQuestion() {
    if (!draft.trim()) return
    try {
      const question = draft.trim()
      await navigator.clipboard.writeText(context ? `${question}\n\nRecord: ${context.label}\n${new URL(context.path, window.location.origin).href}` : question)
      setFeedback("Question copied.")
    } catch {
      setFeedback("Copy is unavailable. Select and copy your question from the field.")
    }
  }

  return (
    <section className={styles.composer} aria-labelledby={id + "-title"}>
      <div className={styles.composerHeading}>
        <div className={styles.iconTile}><Logo width={25} height={25} /></div>
        <Heading id={id + "-title"}>Ask EDI Spark</Heading>
        <button className={styles.quietButton} type="button" onClick={startFresh} disabled={!draft && !context}>
          <Plus size={16} aria-hidden="true" /> New question
        </button>
      </div>
      <p className={styles.availability} id={id + "-availability"}>
        {mcp ? <>Use a <Link className={styles.textLink} to="/ai#connect">connected assistant<ArrowUpRight size={13} aria-hidden="true" /></Link> to get answers from this workspace.</>
          : "Chat is unavailable in this workspace. You can draft and copy a question."}
      </p>
      <form className={styles.questionForm} onSubmit={(event) => { event.preventDefault(); void copyQuestion() }}>
        <label className="sr-only" htmlFor={id}>Your question</label>
        {context ? <div className={styles.context}>
          <FileText size={14} aria-hidden="true" />
          <span>Attached record <strong title={context.label}>{context.label}</strong></span>
          <button className={styles.quietButton} type="button" aria-label="Remove attached record" onClick={() => { update(draft, true); field.current?.focus() }}>
            <X size={15} aria-hidden="true" />
          </button>
        </div> : record ? <button type="button" className={`${styles.quietButton} ${styles.attachRecord}`} onClick={() => { update(draft, false); field.current?.focus() }}>
          <FileText size={14} aria-hidden="true" /> Attach this record
        </button> : null}
        <textarea ref={field} value={draft} onChange={(event) => update(event.target.value)} id={id}
          aria-describedby={id + "-availability"} maxLength={4000} placeholder={context ? "Ask a question about this record…" : "Ask about orders, invoices or an exchange…"} rows={2} />
        <div className={styles.composerActions}>
          <span role="status">{feedback}</span>
          <button type="submit" className={styles.primaryButton} disabled={!draft.trim()}>
            <Clipboard size={15} aria-hidden="true" /> Copy question
          </button>
        </div>
      </form>
      <div className={styles.prompts} aria-label="Suggested questions" role="group">
        {(context || !record ? prompts : workspacePrompts).map((prompt) => <button type="button" key={prompt} className={styles.prompt} onClick={() => { update(prompt); field.current?.focus() }}>
          {prompt}<ArrowUpRight size={13} aria-hidden="true" />
        </button>)}
      </div>
    </section>
  )
}

export function DispatchReturnLink() {
  const { state } = useLocation()
  const from = state?.dispatch
  return typeof from === "string" && from.startsWith("/overview?") ? (
    <Link to={from} className={styles.returnLink}>← Desk</Link>
  ) : null
}

export function RecordQuestion({ label, prompts }: { label: string; prompts: string[] }) {
  const { data: session } = authClient.useSession()
  const { data: organisation } = authClient.useActiveOrganization()
  const { access } = useAccess()
  const { pathname } = useLocation()
  if (!session || !organisation || !access) return null

  const scope = `${session.user.id}:${organisation.id}`
  return <details key={scope + pathname} className={styles.recordQuestion}>
    <summary><Sparkles size={16} aria-hidden="true" /> Ask about this</summary>
    <QuestionComposer scope={scope} record={{ label, path: pathname }} prompts={prompts} mcp={Boolean(access.mcpSetup)} />
  </details>
}
