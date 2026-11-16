import { defineConfig, globalIgnores } from "eslint/config"
import nextVitals from "eslint-config-next/core-web-vitals"

export default defineConfig([
  ...nextVitals,
  // Preserve the existing React 18 lint baseline. React Compiler is not enabled;
  // adopting its effect/memoization rules belongs with that separate migration.
  { rules: {
    "react-hooks/set-state-in-effect": "off",
    "react-hooks/preserve-manual-memoization": "off",
    "react-hooks/purity": "off",
  } },
  globalIgnores([".next/**", ".next-dev/**", "node_modules/**", "next-env.d.ts"]),
])
