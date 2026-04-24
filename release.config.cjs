// REQ-019 Phase 2 — semantic-release configuration
// Generates version.txt (semver, e.g. "0.1.0") and version_full.txt
// (semver + branch + sha for RC builds, e.g. "0.1.0-feature.req-019.abcdef1").
//
// Uses GL_TOKEN (Developer role, read+write) to push git tags.
// Plugins: commit-analyzer, release-notes-generator, exec (writes version files).

const branch = process.env.CI_COMMIT_REF_NAME || 'local';
const sha = (process.env.CI_COMMIT_SHORT_SHA || 'local').slice(0, 8);

module.exports = {
  branches: [
    'main',
    { name: 'feature/*', prerelease: 'feature' },
    { name: 'hotfix/*', prerelease: 'hotfix' },
    { name: 'req/*', prerelease: 'req' },
  ],
  plugins: [
    ['@semantic-release/commit-analyzer', {
      preset: 'conventionalcommits',
    }],
    ['@semantic-release/release-notes-generator', {
      preset: 'conventionalcommits',
    }],
    ['@semantic-release/exec', {
      // verifyReleaseCmd writes both files so downstream image jobs can pick the right tag
      verifyReleaseCmd:
        'echo "${nextRelease.version}" > version.txt && ' +
        `echo "${'${nextRelease.version}'}-${branch.replace(/[^a-zA-Z0-9.-]/g, '-')}.${sha}" > version_full.txt`,
    }],
  ],
};
