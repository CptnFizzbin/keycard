#!/usr/bin/env -S npx tsx
/**
 * Prepares every implementation for release (`yarn prepare-release`, from the
 * repo root): runs `changeset version`, then - since changesets only
 * understands package.json - propagates impl/java's new version into pom.xml
 * and README.md, and prints the tags to push (see /RELEASING.md). Only edits
 * files; never commits, tags, or pushes.
 */
import { execSync } from "node:child_process"
import { readFileSync, writeFileSync } from "node:fs"
import { resolve } from "node:path"

const ROOT = resolve(import.meta.dirname, "..")

interface Impl {
  /** Directory relative to the repo root. */
  dir: string
  label: string
  syncVersionInto?: (version: string) => void
}

function readJson(path: string): { name: string, version: string } {
  return JSON.parse(readFileSync(path, "utf8"))
}

function replaceOrThrow(source: string, pattern: RegExp, replacement: string, file: string): string {
  if (!pattern.test(source)) {
    throw new Error(`prepare-release: expected to find ${pattern} in ${file}, but didn't - it may have been reworded.`)
  }
  return source.replace(pattern, replacement)
}

function syncJavaVersion(version: string): void {
  const pomPath = resolve(ROOT, "impl/java/pom.xml")
  const pomVersionPattern = /(<artifactId>keycard<\/artifactId>\s*\n\s*<version>)[^<]+(<\/version>)/
  writeFileSync(
    pomPath,
    replaceOrThrow(readFileSync(pomPath, "utf8"), pomVersionPattern, `$1${version}$2`, pomPath),
  )

  const readmePath = resolve(ROOT, "impl/java/README.md")
  let readme = readFileSync(readmePath, "utf8")
  readme = replaceOrThrow(readme, pomVersionPattern, `$1${version}$2`, readmePath)
  readme = replaceOrThrow(
    readme,
    /(implementation 'com\.cptnfizzbin:keycard:)[^']+(')/,
    `$1${version}$2`,
    readmePath,
  )
  writeFileSync(readmePath, readme)
}

const IMPLS: Impl[] = [
  {
    dir: "impl/java",
    label: "com.cptnfizzbin:keycard (Java, published to Maven Central)",
    syncVersionInto: syncJavaVersion,
  },
  {
    dir: "impl/js",
    label: "@cptn-fizzbin/keycard (JS, published to npm)",
  },
  {
    dir: "impl/js-knex",
    label: "@cptn-fizzbin/keycard-knex (JS Knex integration, published to npm)",
  },
]

function versionOf(impl: Impl): string {
  return readJson(resolve(ROOT, impl.dir, "package.json")).version
}

/** The release tag for `impl` at `version`: `<package.json name>@<version>`, which its publish-*.yml triggers on. */
function tagOf(impl: Impl, version: string): string {
  return `${readJson(resolve(ROOT, impl.dir, "package.json")).name}@${version}`
}

const before = new Map(IMPLS.map((impl) => [impl.dir, versionOf(impl)]))

console.log("Running `changeset version`...\n")
execSync("yarn changeset version", { cwd: ROOT, stdio: "inherit" })

const changed = IMPLS.map((impl) => ({ impl, from: before.get(impl.dir)!, to: versionOf(impl) })).filter(
  ({ from, to }) => from !== to,
)

if (changed.length === 0) {
  console.log("\nNo packages had pending changesets - nothing to release.")
  process.exit(0)
}

console.log("\nSyncing bumped versions into non-npm files...")
for (const { impl, from, to } of changed) {
  if (impl.syncVersionInto) {
    impl.syncVersionInto(to)
    console.log(`  ${impl.dir}: ${from} -> ${to} (pom.xml + README.md updated)`)
  } else {
    console.log(`  ${impl.dir}: ${from} -> ${to}`)
  }
}

console.log("\nReady to release. Next steps:")
console.log("  1. Review the diff (version bumps + CHANGELOG.md entries).")
console.log("  2. Commit it and merge to main.")
console.log("  3. Push the tag(s) below to trigger each package's publish workflow:\n")
for (const { impl, to } of changed) {
  const tag = tagOf(impl, to)
  console.log(`     git tag ${tag} && git push origin ${tag}`)
  console.log(`       # ${impl.label}`)
}
console.log()
