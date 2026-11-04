import type { BusinessCodeOption } from "@/lib/business-code-lists"

export function BusinessCodeSelect({
  value,
  options,
  onChange,
  emptyLabel,
  required,
  ariaLabel,
}: {
  value: string
  options: readonly BusinessCodeOption[]
  onChange: (value: string) => void
  emptyLabel?: string
  required?: boolean
  ariaLabel?: string
}) {
  const known = options.some((option) => option.value === value)
  return <select aria-label={ariaLabel} value={value} required={required} onChange={(event) => onChange(event.target.value)} className="h-10 w-full rounded-md border bg-background px-3 text-sm">
    {emptyLabel ? <option value="">{emptyLabel}</option> : null}
    {!known && value ? <option value={value}>{value}</option> : null}
    {options.map((option) => <option key={option.value} value={option.value}>{option.label} ({option.value})</option>)}
  </select>
}

