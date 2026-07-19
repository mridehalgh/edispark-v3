import { AlertCircle, CheckCircle2, Clock3, Inbox } from "lucide-react"

import { cn } from "@/lib/utils"
import { DocumentStatus as Status, documentStatusLabels } from "@/lib/documents"

const statusStyles: Record<Status, string> = {
  delivered: "text-success-foreground",
  ready: "text-primary",
  attention: "text-destructive",
  processing: "text-warning-foreground",
}

const statusIcons = {
  delivered: CheckCircle2,
  ready: Inbox,
  attention: AlertCircle,
  processing: Clock3,
}

export function DocumentStatus({ status, className }: { status: Status; className?: string }) {
  const Icon = statusIcons[status]

  return (
    <span className={cn("inline-flex items-center gap-1.5 whitespace-nowrap text-xs font-medium", statusStyles[status], className)}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />
      {documentStatusLabels[status]}
    </span>
  )
}
