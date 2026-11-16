import { describe, expect, it, vi } from "vitest"
import type { AuroraDSQLPool } from "@aws/aurora-dsql-node-postgres-connector"
import { createAuthPool, createDsqlAccessStore } from "./auth-dsql"

describe("production authentication persistence", () => {
  it("rejects administrator connections and databases outside London", () => {
    expect(() => createAuthPool({ BETTER_AUTH_DSQL_HOST: "example.dsql.eu-west-1.on.aws", BETTER_AUTH_DSQL_USER: "edispark_auth" })).toThrow()
    expect(() => createAuthPool({ BETTER_AUTH_DSQL_HOST: "example.dsql.eu-west-2.on.aws", BETTER_AUTH_DSQL_USER: "admin" })).toThrow()
  })
  it("binds both organization and user when authorizing membership", async () => {
    const query = vi.fn().mockResolvedValue({ rows: [] })
    const store = createDsqlAccessStore({ query } as unknown as AuroraDSQLPool)
    expect(await store.membership("organization-a", "user-b")).toBeUndefined()
    expect(query.mock.calls[0][1]).toEqual(["organization-a", "user-b"])
    expect(query.mock.calls[0][0]).toContain('"organizationId" = $1 AND "userId" = $2')
  })
  it("returns the existing tenant when concurrent allocation encounters an existing organization", async () => {
    const query = vi.fn().mockResolvedValueOnce({ rows: [] }).mockResolvedValueOnce({ rows: [{ tenant_id: "existing-tenant" }] })
    const transaction = vi.fn(callback => callback({ query }))
    const store = createDsqlAccessStore({ transaction } as unknown as AuroraDSQLPool)
    expect(await store.resolveTenantId("organization-a", "candidate-tenant")).toBe("existing-tenant")
    expect(query.mock.calls[0][0]).toContain("ON CONFLICT (organization_id) DO NOTHING")
  })
})
