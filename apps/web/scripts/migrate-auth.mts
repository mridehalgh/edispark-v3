import { createHash } from "node:crypto"
import { readFile, readdir } from "node:fs/promises"
import { createAuthPool } from "../src/lib/auth-dsql"

// The deployment workflow serializes migration jobs per environment. The runtime role cannot run DDL.
if (process.env.BETTER_AUTH_DSQL_USER !== "edispark_migrator") throw new Error("Use the dedicated migration role")
const pool = createAuthPool()
try {
  await pool.query("CREATE TABLE IF NOT EXISTS auth.schema_migration (id TEXT PRIMARY KEY, checksum TEXT NOT NULL, completed_at TIMESTAMPTZ NOT NULL)")
  for (const file of (await readdir("migrations")).filter(name => /^\d+-[a-z-]+\.sql$/.test(name)).sort()) {
    const sql = await readFile(`migrations/${file}`, "utf8")
    const statements = sql.replace(/^--.*$/gm, "").split(";").map(value => value.trim()).filter(Boolean)
    for (const [step, statement] of statements.entries()) {
      const id = `${file}:${step}`
      const checksum = createHash("sha256").update(statement).digest("hex")
      const existing = await pool.query("SELECT checksum FROM auth.schema_migration WHERE id = $1", [id])
      if (existing.rows[0]) {
        if (existing.rows[0].checksum !== checksum) throw new Error(`Applied migration changed: ${id}`)
        continue
      }
      // Each DDL is its own transaction. A crash before recording completion requires review,
      // rather than swallowing duplicate-object errors from a possibly incompatible schema.
      const result = await pool.query(statement)
      if (/^CREATE (UNIQUE )?INDEX ASYNC/i.test(statement)) {
        const jobId = result.rows[0]?.job_id
        if (!jobId) throw new Error(`No async index job recorded for ${id}`)
        await pool.query("SELECT sys.wait_for_job($1)", [jobId])
      }
      await pool.query("INSERT INTO auth.schema_migration (id, checksum, completed_at) VALUES ($1, $2, $3)", [id, checksum, new Date().toISOString()])
    }
  }
} finally { await pool.end() }
