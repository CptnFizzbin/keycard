# Releasing

Each KeyCard implementation is released independently, on its own version
number, by pushing a `<lang>-vX.Y.Z` tag. Adding a new implementation (Rust, C#,
Python, ...) means following the same shape, not inventing a new one -
see [Adding a new implementation](#adding-a-new-implementation).

## Implementations

| Implementation | Package                           | Tag format    | 
|----------------|-----------------------------------|---------------|
| Java           | Maven - `com.cptnfizzbin:keycard` | `java-vX.Y.Z` |
| JS/TS          | NPM - `@cptn-fizzbin/keycard`     | `js-vX.Y.Z`   | 

## Adding a new implementation

There's nothing implementation-specific in the release tooling itself
(changesets, `prepare-release`, the tag-triggered publish pattern) - it's all
driven by a per-implementation checklist:

1. **Build the implementation** under `impl/<lang>/`, with its own toolchain,
   exactly as Java and JS already do. Nothing about it needs to know about the
   other implementations or the root tooling.
2. **Give it a `package.json`** at `impl/<lang>/package.json` purely so
   changesets can track its version and write its `CHANGELOG.md` - it is never
   installed or published as an npm package. Model it on
   [`impl/java/package.json`](impl/java/package.json): a name, a starting
   version, and `"private": true`. (Skip this step if the implementation's own
   ecosystem already uses `package.json` as its real manifest, e.g. a JS-based
   one - use that instead, as `impl/js` does.)
3. **Add it to the root `package.json`'s `"workspaces"` array** so changesets
   discovers it.
4. **Pick its tag prefix** (`<lang>-v`) and add
   `.github/workflows/publish-<lang>.yml`, modeled on
   [`publish-java.yml`](.github/workflows/publish-java.yml) /
   [`publish-js.yml`](.github/workflows/publish-js.yml): parse the version out
   of the pushed tag, set it in the implementation's real build file, and run
   that ecosystem's native publish command (`cargo publish`,
   `dotnet nuget push`, `twine upload`, ...) using registry credentials from
   repository secrets.
5. **If the implementation's build file carries its own version separate from
   `package.json`** (a Java `pom.xml`, a Rust `Cargo.toml`, a C#
   `.csproj`, a Python `pyproject.toml`, ...), add a sync step to
   [`scripts/prepare-release.ts`](scripts/prepare-release.ts) mirroring
   `syncJavaVersion`, and register it on that implementation's entry in the
   `IMPLS` array - this is what keeps that file in step with whatever changesets
   bumps `package.json` to.
6. **Document its registry's one-time setup** (account, token/key generation,
   which secrets to add) in a new subsection under
   [Per-registry one-time setup](#per-registry-one-time-setup) below, following
   the existing Maven Central / npm subsections as a template.
7. **Add a row** to the [Implementations](#implementations) table above.

## Versioning convention

- Keep the **major.minor** in sync across every implementation.
- **Patch** versions can advance independently per implementation (e.g. a
  Java-only bugfix can go out as `java-v1.2.4` while JS stays at
  `js-v1.2.3`).
- The version baked into a release comes from the **tag**, not from the
  implementation's own build file - every `publish-<lang>.yml` overwrites the
  committed version with the one parsed out of the tag before publishing. The
  version committed in each build file is just what changesets last bumped it to
  (see below).

## Recording changes ([Changesets](https://github.com/changesets/changesets))

This is a real Yarn workspace: the root `package.json`'s `"workspaces"`
field lists every implementation, and there is a single root `yarn.lock`
governing installs for all of them (`impl/js`'s dependencies included - it no
longer has its own separate lockfile). Non-JS implementations, like
`impl/java`, only get a `package.json` for changesets to track their version and
`CHANGELOG.md` against; Yarn otherwise has nothing to install for them.

```bash
corepack enable && yarn install   # once, from the repo root
```

Versions and `CHANGELOG.md` files, for every implementation, are managed
centrally with `@changesets/cli`. Every PR that should be part of a release adds
a changeset describing it:

```bash
yarn changeset   # interactive: pick implementation(s) + bump type, write a summary
```

This writes a small Markdown file under `.changeset/` - commit it with the PR. A
PR that shouldn't trigger a release (docs, CI-only, etc.)
doesn't need one.

## Cutting a release

```bash
yarn install          # if you haven't already
yarn prepare-release
```

[`scripts/prepare-release.ts`](scripts/prepare-release.ts):

1. Runs `changeset version`, which consumes every pending changeset and, for
   each implementation that has one, bumps its `package.json` version and
   updates its `CHANGELOG.md`.
2. For implementations whose real build file doesn't read `package.json`
   (currently just Java's `pom.xml`), propagates the version changesets just
   wrote into that build file and any docs that quote it. This is the one place
   that's genuinely implementation-specific - see
   [Adding a new implementation](#adding-a-new-implementation), step 5.
3. Prints the `git tag` command (s) to run next, one per implementation that
   changed.

Then:

```bash
git add -A && git commit -m "Release: <summary>"
# open a PR, merge to main, then on main:
git tag java-v1.2.3 && git push origin java-v1.2.3   # if Java changed
git tag js-v1.2.3   && git push origin js-v1.2.3     # if JS changed
```

Pushing a tag is what actually triggers that implementation's
`publish-<lang>.yml` (see below) - running `prepare-release` and merging it does
not publish anything by itself.

## Per-registry one-time setup

Each registry below needs a one-time, account-level setup before its
implementation can release for the first time - done by a human with access to
the relevant accounts/DNS, not something this repo can automate. Adding a new
implementation means adding a new subsection here for its registry
(see [Adding a new implementation](#adding-a-new-implementation), step 6).

### Maven Central (Java)

1. **Create a Sonatype Central account** at
   [central.sonatype.com](https://central.sonatype.com) and add the
   `com.cptnfizzbin` namespace.
2. **Verify the namespace.** Since `com.cptnfizzbin` is a reverse-domain
   groupId, verification is done by adding a TXT record to the
   `cptnfizzbin.com` DNS zone with the value Central gives you (Central walks
   you through this in the "Add Namespace" flow). This confirms you control the
   domain the groupId is derived from.
3. **Generate a User Token** for publishing (Central Portal → your account →
   *Generate User Token*). This gives you a username/password pair — used as
   `MAVEN_CENTRAL_USERNAME` / `MAVEN_CENTRAL_PASSWORD` below (these are portal
   tokens, not your account login).
4. **Generate a GPG key pair** to sign release artifacts (Central requires all
   releases to be signed):
   ```bash
   gpg --full-generate-key
   gpg --armor --export-secret-keys <KEY_ID> > private.asc
   gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
   ```
   (Publish the public key to a keyserver so Central can verify signatures.)
5. Add these repository secrets under **Settings → Secrets and variables →
   Actions**:
    - `MAVEN_CENTRAL_USERNAME` — from the user token in step 3
    - `MAVEN_CENTRAL_PASSWORD` — from the user token in step 3
    - `GPG_PRIVATE_KEY` — contents of `private.asc` from step 4
    - `GPG_PASSPHRASE` — the passphrase for that key

### npm (JS/TS)

1. **Create/use an npm account** with publish access to the
   `@cptn-fizzbin` org/scope on [npmjs.com](https://www.npmjs.com).
2. **Generate an Automation token**: npmjs.com → Access Tokens → *Generate New
   Token* → *Automation* (automation tokens work with 2FA enabled on the
   account, which classic tokens don't).
3. Add it as the repository secret `NPM_AUTH_TOKEN`.

Once an implementation's secrets are in place, pushing its tag is all that's
needed to release it.

## What each publish workflow does

**`publish-java.yml`** (on `java-v*` tag push):

1. Sets the Maven project version from the tag.
2. Runs `mvn -B -Prelease deploy`, which (via the `release` profile in
   `impl/java/pom.xml`) builds the jar, sources jar, and javadoc jar, signs all
   three with GPG, and uploads + auto-publishes them to Maven Central via the
   `central-publishing-maven-plugin`.

**`publish-js.yml`** (on `js-v*` tag push):

1. Installs, builds, and runs the test suite as a release gate.
2. Sets the `package.json` version from the tag.
3. Runs `yarn npm publish --access public` (package is scoped, so publish access
   must be explicit).

A future `publish-<lang>.yml` follows the same three-part shape: derive the
version from the tag, set it in the implementation's build file, run that
ecosystem's native publish command.

Everyday CI (`java.yml`, `js.yml`, and any future `<lang>.yml`) is unaffected by
any of this - the `release` Maven profile and every
`publish-<lang>.yml` only run from their own tag-triggered workflow, never on a
normal push or PR.
