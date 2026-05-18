const storageKey = (key: string) => `ops-console:${key}`

const readList = (key: string) => {
  if (typeof window === 'undefined') {
    return [] as string[]
  }

  const raw = window.localStorage.getItem(storageKey(key))
  if (!raw) {
    return [] as string[]
  }

  try {
    const parsed = JSON.parse(raw) as string[]
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

const writeList = (key: string, values: string[]) => {
  if (typeof window === 'undefined') {
    return
  }

  window.localStorage.setItem(storageKey(key), JSON.stringify(values.slice(0, 20)))
}

export const getKnownSchemaIds = () => readList('known-schema-ids')
export const getKnownDocumentSetIds = () => readList('known-document-set-ids')

export const rememberSchemaId = (schemaId: string) => {
  const next = [schemaId, ...getKnownSchemaIds().filter((value) => value !== schemaId)]
  writeList('known-schema-ids', next)
}

export const rememberDocumentSetId = (documentSetId: string) => {
  const next = [documentSetId, ...getKnownDocumentSetIds().filter((value) => value !== documentSetId)]
  writeList('known-document-set-ids', next)
}
