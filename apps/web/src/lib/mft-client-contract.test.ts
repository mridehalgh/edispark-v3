import { describe, expect, it } from "vitest"
import { createMftClient } from "@edispark/mft-client"

describe("MFT response contracts", () => {
  it("accepts transfers created without orchestration provenance", async () => {
    const client = clientReturning([
      {
        provenance: null,
      },
    ])

    const { data } = await client.listMftTransfers({ throwOnError: true })

    expect(data[0]?.provenance).toBeNull()
  })

  it("accepts partners without certificate rollover or asynchronous receipt settings", async () => {
    const client = clientReturning([
      {
        previousPartnerSigningCertificateId: null,
        signingCertificateGraceUntil: null,
        asynchronousMdnEndpoint: null,
      },
    ])

    const { data } = await client.listMftPartners({ throwOnError: true })

    expect(data[0]?.previousPartnerSigningCertificateId).toBeNull()
    expect(data[0]?.signingCertificateGraceUntil).toBeNull()
    expect(data[0]?.asynchronousMdnEndpoint).toBeNull()
  })
})

function clientReturning(body: unknown) {
  const fetch: typeof globalThis.fetch = async () => new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  })
  return createMftClient({ accessToken: "test-token", baseUrl: "https://mft.test", fetch })
}
