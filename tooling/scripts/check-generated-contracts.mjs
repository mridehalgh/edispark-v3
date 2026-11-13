import { execFileSync } from "node:child_process"

const paths = ["contracts", ...["documents", "mft", "integration"]
  .map((module) => `packages/${module}-client/src/generated`)]

// git diff alone misses newly generated files and changes already staged locally.
const changes = execFileSync("git", ["status", "--porcelain", "--untracked-files=all", "--", ...paths],
  { encoding: "utf8" })
if (changes) {
  console.error("API contracts or generated SDKs are out of date. Regenerate and commit these files:\n" + changes)
  process.exitCode = 1
} else {
  console.log("API contracts and generated SDKs match the committed files.")
}
