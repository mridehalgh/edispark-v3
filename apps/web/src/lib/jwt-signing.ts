import { createHash } from "node:crypto"

import { KMSClient, SignCommand, SigningAlgorithmSpec } from "@aws-sdk/client-kms"
import type { JWTPayload } from "better-auth"

const JWT_ALGORITHM = "ES256"
const SHA_256_DIGEST_LENGTH = 32
const ES256_COMPONENT_LENGTH = 32

export interface RemoteJwtSigningConfig {
  jwksUrl: string
  keyId: string
  kmsKeyId: string
  region: string
}

function requiredEnvironmentVariable(name: string): string {
  const value = process.env[name]?.trim()
  if (!value) {
    throw new Error(`${name} is required when JWT remote signing is enabled`)
  }
  return value
}

export function getRemoteJwtSigningConfig(): RemoteJwtSigningConfig | null {
  if (
    process.env.NODE_ENV !== "production"
    || process.env.NEXT_PHASE === "phase-production-build"
  ) {
    return null
  }

  const jwksUrl = requiredEnvironmentVariable("BETTER_AUTH_JWT_JWKS_URL")
  const parsedJwksUrl = new URL(jwksUrl)
  if (parsedJwksUrl.protocol !== "https:") {
    throw new Error("BETTER_AUTH_JWT_JWKS_URL must use HTTPS")
  }

  return {
    jwksUrl,
    keyId: requiredEnvironmentVariable("BETTER_AUTH_JWT_KID"),
    kmsKeyId: requiredEnvironmentVariable("BETTER_AUTH_JWT_KMS_KEY_ID"),
    region: requiredEnvironmentVariable("AWS_REGION"),
  }
}

function readDerLength(bytes: Uint8Array, offset: number): { length: number; nextOffset: number } {
  const first = bytes[offset]
  if (first === undefined) throw new Error("Invalid DER signature length")
  if ((first & 0x80) === 0) return { length: first, nextOffset: offset + 1 }

  const byteCount = first & 0x7f
  if (byteCount < 1 || byteCount > 2) throw new Error("Unsupported DER signature length")

  let length = 0
  for (let index = 0; index < byteCount; index += 1) {
    const byte = bytes[offset + 1 + index]
    if (byte === undefined) throw new Error("Truncated DER signature length")
    length = (length << 8) | byte
  }
  return { length, nextOffset: offset + 1 + byteCount }
}

function readDerInteger(bytes: Uint8Array, offset: number): { value: Uint8Array; nextOffset: number } {
  if (bytes[offset] !== 0x02) throw new Error("Invalid DER ECDSA integer")
  const { length, nextOffset } = readDerLength(bytes, offset + 1)
  const endOffset = nextOffset + length
  if (length < 1 || endOffset > bytes.length) throw new Error("Truncated DER ECDSA integer")

  let value = bytes.slice(nextOffset, endOffset)
  while (value.length > 1 && value[0] === 0) value = value.slice(1)
  if (value.length > ES256_COMPONENT_LENGTH) throw new Error("Invalid ES256 signature component")
  return { value, nextOffset: endOffset }
}

export function derEcdsaSignatureToJose(signature: Uint8Array): Uint8Array {
  if (signature[0] !== 0x30) throw new Error("Invalid DER ECDSA signature")
  const sequence = readDerLength(signature, 1)
  if (sequence.nextOffset + sequence.length !== signature.length) {
    throw new Error("Invalid DER ECDSA sequence length")
  }

  const r = readDerInteger(signature, sequence.nextOffset)
  const s = readDerInteger(signature, r.nextOffset)
  if (s.nextOffset !== signature.length) throw new Error("Unexpected DER ECDSA signature data")

  const joseSignature = new Uint8Array(ES256_COMPONENT_LENGTH * 2)
  joseSignature.set(r.value, ES256_COMPONENT_LENGTH - r.value.length)
  joseSignature.set(s.value, joseSignature.length - s.value.length)
  return joseSignature
}

export function createKmsJwtSigner(config: RemoteJwtSigningConfig) {
  const kms = new KMSClient({ region: config.region })

  return async (payload: JWTPayload): Promise<string> => {
    const protectedHeader = Buffer.from(JSON.stringify({
      alg: JWT_ALGORITHM,
      kid: config.keyId,
      typ: "JWT",
    })).toString("base64url")
    const encodedPayload = Buffer.from(JSON.stringify(payload)).toString("base64url")
    const signingInput = `${protectedHeader}.${encodedPayload}`
    const digest = createHash("sha256").update(signingInput, "utf8").digest()

    if (digest.length !== SHA_256_DIGEST_LENGTH) throw new Error("Unexpected JWT digest length")

    const result = await kms.send(new SignCommand({
      KeyId: config.kmsKeyId,
      Message: digest,
      MessageType: "DIGEST",
      SigningAlgorithm: SigningAlgorithmSpec.ECDSA_SHA_256,
    }))

    if (!result.Signature) throw new Error("AWS KMS returned no JWT signature")
    if (result.SigningAlgorithm !== SigningAlgorithmSpec.ECDSA_SHA_256) {
      throw new Error("AWS KMS used an unexpected JWT signing algorithm")
    }

    const signature = derEcdsaSignatureToJose(result.Signature)
    return `${signingInput}.${Buffer.from(signature).toString("base64url")}`
  }
}
