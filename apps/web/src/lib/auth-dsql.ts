import { AuroraDSQLPool } from "@aws/aurora-dsql-node-postgres-connector"

/** No credentials are persisted: the connector signs each new connection using its IAM role. */
export function createAuthPool(env: Record<string, string | undefined> = process.env) {
  const host = env.BETTER_AUTH_DSQL_HOST?.trim()
  const user = env.BETTER_AUTH_DSQL_USER?.trim()
  if (!host || !/^[a-z0-9.-]+\.dsql\.eu-west-2\.on\.aws$/.test(host)) {
    throw new Error("BETTER_AUTH_DSQL_HOST must identify a London DSQL cluster")
  }
  if (!user || user === "admin" || !/^[a-z_][a-z0-9_]*$/.test(user)) {
    throw new Error("BETTER_AUTH_DSQL_USER must be a scoped database role")
  }
  return new AuroraDSQLPool({
    host, user, database: "postgres", region: "eu-west-2",
    options: "-c search_path=auth",
    ssl: { rejectUnauthorized: true },
    max: 3, connectionTimeoutMillis: 5_000, idleTimeoutMillis: 30_000,
    maxLifetimeSeconds: 900,
    retry: { maxRetries: 3, baseDelayMs: 10, maxDelayMs: 200 },
  })
}

export function createDsqlAccessStore(pool: AuroraDSQLPool) {
  return {
    async membership(organizationId: string, userId: string) {
      const result = await pool.query<{ role: string }>(
        'SELECT role FROM auth.member WHERE "organizationId" = $1 AND "userId" = $2',
        [organizationId, userId],
      )
      return result.rows[0]
    },
    async platformPrincipal(userId: string) {
      const result = await pool.query<{ role: "platform_admin" }>(
        "SELECT role FROM auth.platform_principal WHERE user_id = $1", [userId],
      )
      return result.rows[0]
    },
    async resolveTenantId(organizationId: string, newTenantId: string) {
      // A retry contains database work only. Concurrent callers converge on the unique organization key.
      return pool.transaction(async (client) => {
        await client.query(
          "INSERT INTO auth.organization_tenant (organization_id, tenant_id, created_at) VALUES ($1, $2, $3) ON CONFLICT (organization_id) DO NOTHING",
          [organizationId, newTenantId, new Date().toISOString()],
        )
        const result = await client.query<{ tenant_id: string }>(
          "SELECT tenant_id FROM auth.organization_tenant WHERE organization_id = $1", [organizationId],
        )
        if (!result.rows[0]) throw new Error("Unable to provision the organization tenant")
        return result.rows[0].tenant_id
      })
    },
  }
}
