"use client"

import { useAgent } from "@copilotkit/react-core/v2/headless"
import { CopilotKitContext, CopilotKitCoreReact, EMPTY_SET } from "@copilotkit/react-core/v2/context"
import { ArrowRight, ArrowUp, FileText, History, Plus, X } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { Link, useLocation, useSearchParams } from "react-router-dom"
import Markdown from "react-markdown"
import Logo from "@/components/logo/logo"
import styles from "./dispatch.module.css"

type RecordContext = { label: string; path: string }
type Thread = { id: string; createdAt: string }
type Evidence = { path: string; label: string; guidance?: string; status?: string; detail?: string }
type Turn = { id: string; question: string; answer: string; links: Evidence[] }
type Props = { scope: string; record?: RecordContext; prompts: string[]; initialQuestion?: string }

export default function AgentChat(props: Props) {
  const { state } = useLocation()
  const [search, setSearch] = useSearchParams()
  const fullPage = !props.record && search.get("chat") === "open"
  function expandChat() {
    if (props.record || fullPage || !window.matchMedia("(max-width: 700px)").matches) return
    setSearch(current => { current.set("chat", "open"); return current })
  }
  const [runtime] = useState(() => ({
    copilotkit: new CopilotKitCoreReact({ runtimeUrl: "/api/copilotkit" }),
    executingToolCallIds: EMPTY_SET,
  }))
  const activeKey = "edispark:agent:" + props.scope + ":" + (props.record?.path ?? "workspace")
  const [thread, setThread] = useState(() => {
    const supplied = state?.agentThread ?? sessionStorage.getItem(activeKey)
    return typeof supplied === "string" && /^[a-f0-9-]{36}$/.test(supplied) ? supplied : crypto.randomUUID()
  })
  useEffect(() => { sessionStorage.setItem(activeKey, thread) }, [activeKey, thread])
  const [initialQuestion, setInitialQuestion] = useState(props.initialQuestion)
  const [recent, setRecent] = useState<Thread[]>([])
  const [error, setError] = useState("")
  const [historyOpen, setHistoryOpen] = useState(false)
  async function showHistory() {
    setHistoryOpen(!historyOpen)
    if (historyOpen) return
    try {
      const response = await fetch("/api/agent/threads", { cache: "no-store" })
      if (!response.ok) throw Error()
      setRecent(await response.json())
      setError("")
    } catch { setError("Saved chats are unavailable.") }
  }
  return <section className={styles.composer} data-conversation={fullPage ? "open" : undefined} aria-label="Ask EDI Spark">
    {fullPage && <button className={styles.textLink} onClick={() => setSearch(current => {
      current.delete("chat")
      return current
    }, { replace: true })}>← Back to Desk</button>}
    <div className={styles.composerHeading}><span className={styles.iconTile} aria-hidden="true"><Logo width={25} height={25} /></span>{props.record ? <h2>Ask EDI Spark</h2> : <h1>Ask EDI Spark</h1>}</div>
    <div className={styles.chatToolbar}>
      <UsageDisclosure />
      <button className={styles.textLink} aria-expanded={historyOpen} onClick={showHistory}><History size={15} aria-hidden="true" />Recent chats</button>
      <button className={styles.textLink} onClick={() => { setThread(crypto.randomUUID()); setHistoryOpen(false) }}><Plus size={15} aria-hidden="true" />New chat</button>
    </div>
    {historyOpen && <div>{error || (!recent.length && "No saved conversations.")}{recent.map(t =>
      <button key={t.id} className={styles.textLink} onClick={() => {setThread(t.id); setHistoryOpen(false); expandChat()}}>{new Date(t.createdAt).toLocaleString("en-GB")}</button>)}</div>}
    <CopilotKitContext.Provider value={runtime}>
      <Conversation key={thread} {...props} initialQuestion={initialQuestion} thread={thread} onInitialSend={() => setInitialQuestion(undefined)} onSend={expandChat} />
    </CopilotKitContext.Provider>
  </section>
}

function UsageDisclosure() {
  const [open, setOpen] = useState(false)
  const [description, setDescription] = useState("")
  useEffect(() => {
    if (!open) return
    const controller = new AbortController()
    setDescription("Loading AI credits…")
    void fetch("/api/agent/usage", { signal: controller.signal, cache: "no-store" })
      .then(async response => {
        if (!response.ok) throw Error()
        const { monthlyCredits, remainingCredits, resetsAt } = await response.json()
        if (!Number.isFinite(monthlyCredits) || !Number.isFinite(remainingCredits)) throw Error()
        const credits = new Intl.NumberFormat("en-GB", { maximumFractionDigits: 2 })
        const reset = new Date(resetsAt).toLocaleDateString("en-GB", { day: "numeric", month: "long", timeZone: "UTC" })
        if (!controller.signal.aborted) setDescription(`${credits.format(Math.floor(remainingCredits * 100) / 100)} of ${credits.format(monthlyCredits)} credits left. Resets on ${reset} (UTC).`)
      }).catch(() => { if (!controller.signal.aborted) setDescription("Could not load AI credits.") })
    return () => controller.abort()
  }, [open])
  return <details className={styles.usage} onToggle={e => setOpen(e.currentTarget.open)}>
    <summary>AI credits</summary>
    {open && <div><strong>Organisation credits</strong><p role="status">{description}</p>
      <p>Your organisation shares these credits. Questions use credits based on the work involved. Keep passwords, access keys and card details out of chat.</p></div>}
  </details>
}

function Conversation({ thread, scope, record, prompts, initialQuestion, onInitialSend, onSend }: Props & { thread: string; onSend: () => void; onInitialSend: () => void }) {
  const { agent, isReady } = useAgent({ agentId: "desk-" + thread, runtimeAgentId: "default", threadId: thread })
  const { messages, isRunning: isLoading } = agent
  const scrollArea = useRef<HTMLDivElement>(null)
  const input = useRef<HTMLTextAreaElement>(null)
  const follow = useRef(true)
  const [links, setLinks] = useState<Evidence[]>([])
  const lastAttempt = useRef<{ id: string; content: string } | null>(null)
  const [attached, setAttached] = useState(record)
  const key = "edispark:question:" + scope + ":" + thread
  const [draft, setDraft] = useState(() => {
    try { return String(JSON.parse(sessionStorage.getItem(key) ?? "{}").draft ?? "") } catch { return "" }
  })
  const [status, setStatus] = useState("")
  const [unavailable, setUnavailable] = useState(false)
  const [saved, setSaved] = useState<Turn[]>([])
  const [failed, setFailed] = useState(false)
  const refusal = useRef("")
  useEffect(() => {
    if (isLoading) return
    const controller = new AbortController()
    void fetch("/api/agent/threads/" + thread, { signal: controller.signal, cache: "no-store" })
      .then(async response => {
        if (controller.signal.aborted) return
        setUnavailable(response.status === 403 || response.status === 404 || response.status === 401)
        if (response.ok) {
          const turns = await response.json()
          if (!controller.signal.aborted) setSaved(turns)
        }
      }).catch(() => { if (!controller.signal.aborted) setStatus("Could not load the conversation.") })
    return () => controller.abort()
  }, [thread, isLoading])
  useEffect(() => {
    const subscription = agent?.subscribe({
      onStepStartedEvent: ({ event }) => setStatus(
        event.stepName === "prepare_invoice_draft" ? "Preparing invoice for review…" :
          event.stepName === "prepare_document_correction" ? "Preparing correction for review…" : "Checking workspace records…",
      ),
      onRunErrorEvent: ({ event }) => {
        if (event.code?.startsWith("AI_")) refusal.current = event.message
        setFailed(true)
        setStatus(refusal.current || "The agent is unavailable. Try again.")
      },
      onRunFailed: () => { setFailed(true); setStatus(refusal.current || "The agent is unavailable. Try again.") },
      onCustomEvent: ({ event }) => {
        if ((event.name === "deskArtifacts" || event.name === "documentReferences") && Array.isArray(event.value)) {
          setLinks(event.value.filter((ref): ref is Evidence =>
            ref && typeof ref.label === "string" && /^\/(documents|files|connections)\//.test(ref.path)))
        }
      },
      onRunFinishedEvent: () => {
        setStatus("")
        if (input.current?.value.trim() === lastAttempt.current?.content) {
          setDraft("")
          sessionStorage.removeItem(key)
        }
      },
    })
    return () => subscription?.unsubscribe()
  }, [agent, key])
  useEffect(() => {
    const area = scrollArea.current
    if (area && follow.current) area.scrollTop = area.scrollHeight
  }, [messages, saved, links])
  function update(value: string) {
    setDraft(value)
    try { sessionStorage.setItem(key, JSON.stringify({ draft: value })) } catch {}
  }
  async function send(question = draft) {
    if (!question.trim() || isLoading) return
    follow.current = true
    onSend()
    refusal.current = ""
    setStatus("Investigating…")
    setFailed(false)
    try {
      const content = question.trim()
      const retry = failed && lastAttempt.current?.content === content
      const attempt = retry && lastAttempt.current ? lastAttempt.current : { id: crypto.randomUUID(), content }
      lastAttempt.current = attempt
      if (retry) agent.setMessages(agent.messages.slice(0, agent.messages.findIndex(m => m.id === attempt.id)))
      agent.addMessage({ role: "user", ...attempt })
      await agent.runAgent({ forwardedProps: { record: attached?.path } })
    } catch { setStatus(refusal.current || "The agent could not complete this question. Try again."); setFailed(true) }
  }
  const sentInitial = useRef(false)
  useEffect(() => {
    if (!initialQuestion || sentInitial.current || !isReady || isLoading || unavailable) return
    sentInitial.current = true
    onInitialSend()
    if (!draft.trim()) update(initialQuestion)
    void send(initialQuestion)
  })

  const references = new Map([...saved.flatMap(t => t.links), ...links].map(ref => [ref.path, ref]))
  const visible = new Set(messages.map(m => m.id))
  return <>
    <div className={styles.thread} ref={scrollArea} onScroll={event => {
      const area = event.currentTarget
      follow.current = area.scrollHeight - area.scrollTop - area.clientHeight < 40
    }} role="region" aria-label="Conversation" tabIndex={messages.length || saved.length ? 0 : undefined}>
      {saved.filter(t => !visible.has(t.id)).map(t => <div key={t.id}><strong>You</strong><p>{t.question}</p><strong>EDI Spark</strong><Answer text={t.answer} /></div>)}
      {messages.map(m => (m.role === "user" || m.role === "assistant") && typeof m.content === "string" && <div key={m.id}><strong>{m.role === "user" ? "You" : "EDI Spark"}</strong>{m.role === "user" ? <p>{m.content}</p> : <Answer text={m.content} />}</div>)}
      {references.size > 0 && <ul className={styles.artifacts} aria-label="Records and actions">
        {[...references.values()].map(ref => (
          <li key={ref.path} className={ref.guidance ? styles.artifactAction : styles.artifactRow}>
            {ref.guidance ? <>
              <p>{ref.guidance}</p>
              <Link className={styles.primaryButton} to={ref.path}>{ref.label}<ArrowRight size={16} aria-hidden="true" /></Link>
            </> : <>
              <Link to={ref.path} className={styles.artifactLink} state={{ agentThread: thread }}>
                <FileText size={16} aria-hidden="true" />
                <span>{record?.path === ref.path ? record.label : ref.label}</span>
                <ArrowRight size={14} aria-hidden="true" />
              </Link>
              {ref.status && <span className={styles.artifactStatus}>{ref.status}</span>}
              {ref.detail && <span className={styles.artifactDetail}>{ref.detail}</span>}
            </>}
          </li>
        ))}
      </ul>}
    </div>
    <form className={styles.questionForm} onSubmit={e => { e.preventDefault(); void send() }}>
      {attached && <div className={styles.context}>Attached record <strong>{attached.label}</strong><button aria-label="Remove attached record" type="button" onClick={() => setAttached(undefined)}><X size={16} aria-hidden="true" /></button></div>}
      <textarea ref={input} aria-label="Your question" maxLength={4000} placeholder="Ask about orders, invoices or an exchange…" value={draft} onChange={e => update(e.target.value)} />
      <div className={styles.composerActions}><span role="status">{unavailable ? "Chat is unavailable in this workspace." : status}</span>
        {isLoading ? <button type="button" className={styles.outlineButton} onClick={() => { agent.abortRun(); setStatus("Stopped.") }}>Stop</button>
          : <button className={styles.primaryButton} disabled={unavailable || !isReady || !draft.trim()}>{failed ? "Retry" : "Send"}<ArrowUp size={16} aria-hidden="true" /></button>}
      </div>
    </form>
    <div className={styles.prompts}>{prompts.map(p => <button className={styles.prompt} key={p} onClick={() => update(p)}>{p}</button>)}</div>
  </>
}

function Answer({ text }: { text: string }) {
  return <Markdown skipHtml unwrapDisallowed allowedElements={["p", "ul", "ol", "li", "strong", "em", "code", "br"]}>{text}</Markdown>
}
