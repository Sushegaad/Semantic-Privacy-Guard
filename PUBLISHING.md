# Publishing to Maven Central — Step-by-Step Guide

This guide covers the one-time setup you need to do **on your own machine and GitHub** before the
automated `publish.yml` workflow can push releases to Maven Central.

---

## Prerequisites

| Tool | Check |
|---|---|
| GPG 2.x | `gpg --version` |
| Java 17 + Maven | `mvn -version` |
| GitHub CLI (optional) | `gh --version` |

---

## Step 1 — Generate a GPG key (one time)

```bash
gpg --full-generate-key
```

When prompted:
- **Key type:** RSA and RSA (default)
- **Key size:** 4096
- **Expiry:** 2y (two years is fine)
- **Real name:** Hemant Naik
- **Email:** hemant.naik@gmail.com
- **Passphrase:** choose a strong one — you will need it later

Confirm the key was created:

```bash
gpg --list-secret-keys --keyid-format=long
```

Note the **key ID** from the output — it looks like `3AA5C34371567BD2` on the `sec` line.

---

## Step 2 — Upload the public key to a keyserver

Maven Central verifiers look up your public key automatically.
Upload it to **keys.openpgp.org** (preferred) or **keyserver.ubuntu.com**:

```bash
gpg --keyserver keys.openpgp.org --send-keys YOUR_KEY_ID
```

Verify the upload:

```bash
gpg --keyserver keys.openpgp.org --recv-keys YOUR_KEY_ID
```

---

## Step 3 — Export the private key for GitHub Actions

The workflow needs your private key as an ASCII-armoured string stored in a GitHub secret.

```bash
gpg --armor --export-secret-keys YOUR_KEY_ID
```

Copy **the entire output** including the `-----BEGIN PGP PRIVATE KEY BLOCK-----` and
`-----END PGP PRIVATE KEY BLOCK-----` lines.

---

## Step 4 — Get your Maven Central token

1. Go to **https://central.sonatype.com** and sign in with your GitHub account
   (the namespace `io.github.sushegaad` was verified automatically for GitHub accounts).
2. Click your avatar (top-right) → **Account** → **Generate User Token**
3. Copy the **Username** and **Password** — these are your Central token credentials,
   not your login credentials.

---

## Step 5 — Add the four GitHub repository secrets

Go to your repository on GitHub:
**Settings → Secrets and variables → Actions → New repository secret**

Add these four secrets:

| Secret name | Value |
|---|---|
| `MAVEN_GPG_PRIVATE_KEY` | Full armoured private key from Step 3 |
| `MAVEN_GPG_PASSPHRASE` | The passphrase you chose when creating the key |
| `CENTRAL_USERNAME` | Token username from Step 4 |
| `CENTRAL_PASSWORD` | Token password from Step 4 |

---

## Step 6 — Verify the namespace on Central Portal (if not already verified)

1. Go to **https://central.sonatype.com/publishing/namespaces**
2. You should see `io.github.sushegaad` listed as **Verified**.
   - If it's pending: Central auto-verifies GitHub namespaces within minutes after
     you create a repository at `https://github.com/sushegaad/*`.

---

## Step 7 — Do a dry-run locally (optional but recommended)

```bash
# From inside the Semantic-Privacy-Guard directory:
mvn --batch-mode -P release \
    -Dgpg.passphrase=YOUR_PASSPHRASE \
    clean verify
```

This builds + signs all four artefacts (JAR, sources JAR, Javadoc JAR, POM)
without deploying. Check that `.asc` signature files appear in `target/`.

---

## Step 8 — Create your first GitHub Release

This triggers the `publish.yml` workflow automatically.

```bash
# Tag the commit
git tag v1.0.0
git push origin v1.0.0
```

Then on GitHub:
1. Go to **Releases → Draft a new release**
2. Choose tag `v1.0.0`
3. Title: `v1.0.0 — Initial release`
4. Write release notes
5. Click **Publish release**

The workflow will start within seconds.
Watch its progress at:
**Actions → Publish to Maven Central**

---

## Step 9 — Confirm publication

After the workflow succeeds (typically 3–10 minutes):

1. Check **https://central.sonatype.com/artifact/io.github.sushegaad/semantic-privacy-guard**
2. The artifact should appear with status **Published**.
3. It propagates to search.maven.org within ~30 minutes.

The Maven Central badge in the README will automatically update to show `1.0.0`:

```
[![Maven Central](https://img.shields.io/maven-central/v/io.github.sushegaad/semantic-privacy-guard)](...)
```

---

## Publishing future versions

1. Bump `<version>` in `pom.xml` (e.g., `1.1.0`)
2. Commit and push
3. Create a new GitHub Release with tag `v1.1.0`

The workflow handles everything else.

---

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `gpg: signing failed: No secret key` | Key not imported in CI | Re-check `MAVEN_GPG_PRIVATE_KEY` secret (include header/footer lines) |
| `401 Unauthorized` from Central | Wrong token credentials | Regenerate token on central.sonatype.com and update secrets |
| `Invalid signature` | Public key not on keyserver | Re-run Step 2 |
| `Namespace not verified` | `io.github.sushegaad` pending | Wait a few minutes or check Central Portal namespaces page |
| `autoPublish` times out | Large artifact / slow Central | Set `<waitUntil>uploaded</waitUntil>` in pom.xml and manually release on Central Portal |
