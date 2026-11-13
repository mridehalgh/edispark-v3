"use client"

import type { ReadinessIssue } from "@edispark/integration-client"
import { groupDocumentFindings } from "@/lib/document-findings"

export function DocumentFindings({ findings }: { findings: ReadinessIssue[] }) {
  const groups = groupDocumentFindings(findings)
  const renderGroup = (group: typeof groups[number]) => <li key={group.key} className="py-3 first:pt-0 text-sm">
      <p className="font-medium">{group.label}</p>
      {group.lines.length > 0 && <p className="mt-0.5 text-xs text-muted-foreground">
        {group.lines.length === 1 ? "Line" : "Lines"} {group.lines.join(", ")}
      </p>}
      <p className="mt-1 max-w-[70ch] break-words leading-5">{group.message}</p>
      {group.action && <p className="mt-1 text-xs leading-5 text-muted-foreground">{group.action}</p>}
      <details className="mt-1">
        <summary className="min-h-10 cursor-pointer py-2 text-xs font-medium text-primary">
          Technical evidence · {group.locations.length} {group.locations.length === 1 ? "location" : "locations"}
        </summary>
        <ul className="space-y-2 border-l pl-3 text-xs">
          {group.locations.map((finding, index) => <li key={`${finding.path}:${index}`}>
            <p className="break-all font-mono">{finding.path}</p>
            <p className="mt-0.5 text-muted-foreground">{finding.code}</p>
          </li>)}
        </ul>
      </details>
    </li>
  return <div>
    <ul className="mt-3 divide-y">{groups.slice(0, 4).map(renderGroup)}</ul>
    {groups.length > 4 && <details className="mt-2">
      <summary className="min-h-10 cursor-pointer py-2 text-sm font-medium text-primary">Show {groups.length - 4} more fields</summary>
      <ul className="mt-3 divide-y">{groups.slice(4).map(renderGroup)}</ul>
    </details>}
  </div>
}
