import { ChevronDown, CopyPlus } from "lucide-react"
import { Link } from "react-router-dom"

import { Button } from "@/components/ui/button"
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from "@/components/ui/dropdown-menu"
import { authoringDefinitions, type AuthorableDocumentType } from "@/lib/ubl-authoring"
import { smartCopyTransitions } from "@/lib/ubl-smart-copy"

export function CreateNextDocument({ setId, documentId, sourceType }: {
  setId: string
  documentId: string
  sourceType: AuthorableDocumentType
}) {
  const targets = smartCopyTransitions[sourceType]
  if (targets.length === 0) return null
  return <DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline" size="sm"><CopyPlus className="mr-2 h-4 w-4" aria-hidden="true" />Create related<ChevronDown className="ml-2 h-3.5 w-3.5 text-muted-foreground" aria-hidden="true" /></Button></DropdownMenuTrigger><DropdownMenuContent align="end" className="w-64">{targets.map((target) => <DropdownMenuItem key={target} asChild><Link className="flex min-h-10 items-center" to={`/documents/new?type=${target}&fromSet=${encodeURIComponent(setId)}&fromDocument=${encodeURIComponent(documentId)}`}>Create {authoringDefinitions[target].label.toLowerCase()}</Link></DropdownMenuItem>)}</DropdownMenuContent></DropdownMenu>
}
