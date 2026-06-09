import { mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'

import openapiTS from 'openapi-typescript'

const defaultBaseUrl = 'http://localhost:8080'
const openApiPath = '/api-docs'
const overrideBaseUrl = process.env.VITE_API_BASE_URL?.trim()
const resolvedBaseUrl = (overrideBaseUrl?.length ? overrideBaseUrl : defaultBaseUrl).replace(/\/+$/, '')
const schemaUrl = `${resolvedBaseUrl}${openApiPath}`
const outputFile = path.resolve('src/lib/api/generated/contract.d.ts')

await mkdir(path.dirname(outputFile), { recursive: true })

const output = await openapiTS(new URL(schemaUrl))
await writeFile(outputFile, output)

process.stdout.write(`Generated OpenAPI types from ${schemaUrl}\n`)
