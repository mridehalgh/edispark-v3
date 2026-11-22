import { Download, Upload } from 'lucide-react'
import { useRef, useState } from 'react'
import type { GetMappingExampleFilesResponse } from '@edispark/integration-client'

import { Button } from '@/components/ui/button'

export type TestFile = { name: string; text: string; kind: 'json' | 'edi' }
export type TestFiles = { source?: TestFile; expected?: TestFile }

export function exampleTestFiles(example: GetMappingExampleFilesResponse, allowRawInput: boolean): TestFiles {
  return {
    source: allowRawInput && example.sourceRaw ? { name: 'edi-example.edi', text: example.sourceRaw, kind: 'edi' }
      : example.sourceDocument ? { name: 'input-example.json', text: example.sourceDocument, kind: 'json' } : undefined,
    expected: example.targetRaw ? { name: 'expected-example.edi', text: example.targetRaw, kind: 'edi' }
      : example.targetDocument ? { name: 'expected-example.json', text: example.targetDocument, kind: 'json' } : undefined,
  }
}

// Carry private file contents across the in-app navigation without putting them in history or storage.
let newDraftFiles: { id: string; files: TestFiles } | undefined

export function stageDraftFiles(id: string, files: TestFiles) {
  newDraftFiles = { id, files }
}

export function takeDraftFiles(id: string) {
  if (newDraftFiles?.id !== id) return undefined
  const files = newDraftFiles.files
  newDraftFiles = undefined
  return files
}

export function MappingFileUpload({ label, allowEdi, allowJson, disabled, onLoad }: {
  label: string
  allowEdi: boolean
  allowJson: boolean
  disabled?: boolean
  onLoad: (file: TestFile) => void | Promise<void>
}) {
  const picker = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string>()
  const [reading, setReading] = useState(false)

  async function load(file: File) {
    setReading(true)
    setError(undefined)
    try {
      if (file.size > 1_048_576) throw new Error('Choose a file 1 MiB or smaller.')
      const text = new TextDecoder('utf-8', { fatal: true }).decode(await file.arrayBuffer())
      if (!text.trim() || text.includes('\0')) throw new Error('Choose a non-empty text file.')
      const kind = /^[{\[]/.test(text.trimStart()) ? 'json' : 'edi'
      if (kind === 'json') {
        if (!allowJson) throw new Error('Choose an EDI file for this format.')
        try { JSON.parse(text) } catch { throw new Error('Correct the JSON in the file and try again.') }
      } else if (!allowEdi || !/^(?:UNA|UNB\+|UNH\+|STX=|MHD=)/.test(text.trimStart())) {
        throw new Error(allowEdi ? 'Choose an EDIFACT or TRADACOMS text file.' : 'Choose a JSON document for this format.')
      }
      await onLoad({ name: file.name, text, kind })
    } catch (error) {
      setError(error instanceof TypeError ? 'Choose a UTF-8 text file.' : error instanceof Error ? error.message : 'The file could not be read.')
    } finally {
      setReading(false)
    }
  }

  return <div className="min-w-0">
    <input hidden ref={picker} type="file" aria-label={label} accept={[
      allowJson ? '.json,application/json' : '', allowEdi ? '.edi,.edifact,.tradacoms,.txt,text/plain' : '',
    ].filter(Boolean).join(',')} onChange={(event) => {
      const file = event.currentTarget.files?.[0]
      event.currentTarget.value = ''
      if (file) void load(file)
    }} />
    <Button type="button" variant="outline" size="sm" className="w-fit gap-2" disabled={disabled || reading} onClick={() => picker.current?.click()}>
      <Upload aria-hidden="true" className="h-4 w-4" />{reading ? 'Reading file…' : label}
    </Button>
    {error && <p role="alert" className="mt-2 break-words text-xs text-destructive">{error}</p>}
  </div>
}

export function DownloadMappingFile({ file, label }: { file: TestFile; label: string }) {
  function download() {
    const url = URL.createObjectURL(new Blob([file.text], { type: file.kind === 'json' ? 'application/json' : 'text/plain' }))
    const link = document.createElement('a')
    link.href = url
    link.download = file.name
    link.click()
    window.setTimeout(() => URL.revokeObjectURL(url), 1000)
  }
  return <Button type="button" variant="ghost" size="sm" className="gap-2" onClick={download}>
    <Download className="h-4 w-4" aria-hidden="true" />{label}
  </Button>
}
