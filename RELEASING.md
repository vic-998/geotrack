# Releasing geotrack

This document is for maintainers. It describes how `geotrack` is published to
Maven Central and how to cut a GitHub release.

## 1. Verify the namespace on the Central Portal

Publishing is handled by the **Sonatype Central Portal**:
<https://central.sonatype.com>. Before the first release:

1. Sign in with GitHub.
2. Open **Namespaces** → **Add Namespace** and add `com.boyicloud`.
3. Add the `TXT` record the portal shows to the DNS of `boyicloud.com` and click
   **Verify**. Without this step every deploy fails with
   `Namespace 'com.boyicloud' is not allowed`.
4. Generate a **User Token** (Account → Generate User Token). It yields a
   username / password pair.

## 2. Configure credentials

The `central-publishing-maven-plugin` in `pom.xml` reads credentials from the
`central` server id. Put them in `~/.m2/settings.xml` (never commit this file):

```xml
<servers>
  <server>
    <id>central</id>
    <username>USER_TOKEN_USERNAME</username>
    <password>USER_TOKEN_PASSWORD</password>
  </server>
</servers>
```

For CI, store `OSSRH_USERNAME`, `OSSRH_PASSWORD`, `GPG_PRIVATE_KEY` and
`GPG_PASSPHRASE` as repository secrets instead.

## 3. GPG signing

Artifacts must be GPG-signed and the public key must be published to a
keyserver so Central can verify the signatures:

```bash
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

If `gpg` is not on `PATH` (e.g. the copy bundled with Git for Windows), point
Maven at it:

```bash
mvn clean deploy -Dgpg.executable="D:\Git\usr\bin\gpg.exe"
```

## 4. Deploy

```bash
mvn clean deploy            # sign, upload and auto-publish (autoPublish=true)
mvn clean verify            # sign only, no upload
mvn clean package -Dgpg.skip=true   # local build without signing
```

On success the artifacts become available at
`https://repo1.maven.org/maven2/com/boyicloud/geotrack/`.

## 5. Cut a GitHub release

Push an annotated tag; the `CI` workflow builds and attaches the jars to the
release page:

```bash
git tag -a vX.Y.Z -m "geotrack X.Y.Z"
git push origin vX.Y.Z
```

## Version checklist

- [ ] `version` in `pom.xml` bumped
- [ ] `mvn clean verify` green
- [ ] namespace verified, token valid
- [ ] release notes drafted
- [ ] tag pushed, CI release job green
- [ ] artifacts visible on Maven Central
