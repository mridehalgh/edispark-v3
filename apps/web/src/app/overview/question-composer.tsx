"use client"

import dynamic from "next/dynamic"
import { createContext, useEffect, useRef, useState } from "react"
import { Sparkles } from "lucide-react"
import { Link, useLocation } from "react-router-dom"
import { authClient } from "@/lib/auth-client"
import { useAccess } from "@/lib/use-access"
import styles from "./dispatch.module.css"

export const DocumentTriage = createContext<(() => void) | undefined>(undefined)

const Chat = dynamic(() => import("./agent-chat"), { ssr: false })
export function QuestionComposer({ scope }: { scope: string }) {
  return <Chat scope={scope} prompts={["What needs attention?", "Which orders have arrived?"]} />
}

export function DispatchReturnLink() {
  const { state } = useLocation()
  const from = state?.dispatch
  return typeof from === "string" && from.startsWith("/overview?") ? (
    <Link to={from} className={styles.returnLink}>← Desk</Link>
  ) : null
}

export function RecordQuestion({ label, prompts, triageRequest = 0 }: { label: string; prompts: string[]; triageRequest?: number }) {
  const { data: session } = authClient.useSession()
  const { data: organisation } = authClient.useActiveOrganization()
  const { access } = useAccess()
  const { pathname, state } = useLocation()
  const [open, setOpen] = useState(Boolean(state?.agentThread))
  const handledTriage = useRef(0)
  const [activeTriage, setActiveTriage] = useState(0)
  useEffect(() => {
    if (triageRequest <= handledTriage.current) return
    handledTriage.current = triageRequest
    setActiveTriage(triageRequest)
    setOpen(true)
  }, [triageRequest])
  const panel = useRef<HTMLDialogElement>(null)
  const trigger = useRef<HTMLButtonElement>(null)
  useEffect(() => {
    const dialog = panel.current
    if (!open || !dialog) return
    const narrow = window.matchMedia("(max-width: 1179px)")
    const show = () => {
      if (dialog.open) dialog.close()
      if (narrow.matches) dialog.showModal()
      else dialog.show()
    }
    show()
    narrow.addEventListener("change", show)
    return () => {
      narrow.removeEventListener("change", show)
      dialog.close()
    }
  }, [open, pathname])
  if (!session || !organisation || !access) return null

  const scope = `${session.user.id}:${organisation.id}:${access.organizationRole}`
  function close() {
    setOpen(false)
    setActiveTriage(0)
    trigger.current?.focus()
  }
  return <div className={styles.recordQuestion}>
    <button ref={trigger} className={styles.textLink} aria-haspopup="dialog" aria-expanded={open} onClick={() => setOpen(true)}>
      <Sparkles size={16} aria-hidden="true" /> Ask about this
    </button>
    {open && <dialog ref={panel} className={styles.chatPanel} aria-label="Record assistant"
      onCancel={event => { event.preventDefault(); close() }}>
      <button className={styles.textLink} onClick={close}>← Back to record</button>
      <Chat key={scope + pathname + activeTriage} scope={scope} record={{ label, path: pathname }} prompts={prompts}
        initialQuestion={activeTriage ? "Triage this document." : undefined} />
    </dialog>}
  </div>
}
