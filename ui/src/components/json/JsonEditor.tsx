'use client'

import { useEffect, useState } from "react"

import { Button } from "@/components/ui/button"

interface JsonEditorProps {
  data: object
  onChange: (updatedJson: object) => void
}

export default function JsonEditor({ data, onChange }: JsonEditorProps) {
  const [value, setValue] = useState(() => JSON.stringify(data, null, 2))
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    setValue(JSON.stringify(data, null, 2))
  }, [data])

  const applyChanges = () => {
    try {
      const nextValue = JSON.parse(value) as object
      onChange(nextValue)
      setError(null)
    } catch {
      setError("The JSON could not be applied. Check commas, quotes, and brackets, then try again.")
    }
  }

  return (
    <div className="space-y-3">
      <label htmlFor="workflow-json" className="text-sm font-medium">Workflow JSON</label>
      <textarea
        id="workflow-json"
        value={value}
        onChange={(event) => setValue(event.target.value)}
        spellCheck={false}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? "workflow-json-error" : undefined}
        className="min-h-[28rem] w-full rounded-md border bg-foreground p-4 font-mono text-xs leading-5 text-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      />
      {error && <p id="workflow-json-error" role="alert" className="text-sm text-destructive">{error}</p>}
      <Button type="button" onClick={applyChanges}>Apply changes</Button>
    </div>
  )
}
