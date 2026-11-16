import { verifyRelease } from './manifest.mjs'
if (!process.env.PUBLIC_RELEASE_KEY_PEM) throw new Error('A pinned public release key is required')
await verifyRelease(process.argv[2], {
  commit: process.env.GITHUB_SHA,
  repository: process.env.GITHUB_REPOSITORY,
}, process.env.PUBLIC_RELEASE_KEY_PEM)
console.log('Release identity, signature, artifact digests and scan freshness verified')
