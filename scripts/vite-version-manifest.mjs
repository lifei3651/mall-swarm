import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

export const createVersionManifestPlugin = ({ repoRoot, application }) => {
  const version = readFileSync(resolve(repoRoot, 'VERSION'), 'utf8').trim()
  const gitCommit = String(process.env.RELEASE_GIT_COMMIT || '').trim() || null
  const buildId = String(process.env.RELEASE_BUILD_ID || '').trim() || null

  return {
    name: `release-version-manifest-${application}`,
    generateBundle() {
      this.emitFile({
        type: 'asset',
        fileName: 'version.json',
        source: `${JSON.stringify({ version, application, gitCommit, buildId }, null, 2)}\n`,
      })
    },
  }
}
