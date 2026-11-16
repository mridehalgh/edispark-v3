import { SecretsManagerClient, GetSecretValueCommand } from '@aws-sdk/client-secrets-manager'

// Resolve secrets before importing Next or auth modules. Never put secret values in Terraform.
if (process.env.NODE_ENV !== 'production') throw new Error('The Web Function entry point requires production mode')
delete process.env.NEXT_PHASE
const secretArn = process.env.WEB_SECRET_ARN
if (!secretArn) throw new Error('WEB_SECRET_ARN is required')
const secrets = new SecretsManagerClient({ region: 'eu-west-2', maxAttempts: 2, requestHandler: { connectionTimeout: 1_000, requestTimeout: 3_000 } })
const response = await secrets.send(new GetSecretValueCommand({ SecretId: secretArn }))
const secret = JSON.parse(response.SecretString ?? '{}')
for (const name of ['BETTER_AUTH_SECRET', 'RESEND_API_KEY', 'EMAIL_FROM']) {
  if (typeof secret[name] !== 'string' || !secret[name]) throw new Error(`Missing runtime secret: ${name}`)
  process.env[name] = secret[name]
}
if (process.env.BETTER_AUTH_SECRET.length < 32) throw new Error('Invalid authentication secret')
for (const name of ['BETTER_AUTH_URL', 'DOCUMENTS_API_URL', 'MFT_API_URL', 'INTEGRATION_API_URL']) {
  if (!process.env[name] || new URL(process.env[name]).protocol !== 'https:') throw new Error(`HTTPS ${name} is required`)
}
const endpoint = process.env.AWS_LAMBDA_HTTP_ENDPOINT
if (!endpoint) throw new Error('AWS_LAMBDA_HTTP_ENDPOINT is required')
const address = new URL(endpoint.includes('://') ? endpoint : `http://${endpoint}`)
process.env.HOSTNAME = address.hostname
process.env.PORT = address.port || '80'
await import('./apps/web/server.js')
