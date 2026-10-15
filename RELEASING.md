# Releasing geotrack

This document is for maintainers. It describes how to publish `geotrack` to
Maven Central and how to cut a GitHub release.

## 1. Register the namespace on Maven Central

Maven Central is operated through the **Sonatype Central Portal**:
<https://central.sonatype.com>.

1. Sign in with GitHub.
2. Create a namespace `com.boyicloud`.
3. Verify ownership of `boyicloud.com` by adding the `TXT` record the portal
   shows (or the dedicated verification record) to your DNS.
4. Wait for the namespace to be marked as *verified*.

## 2. Prepare GPG signing

Artifacts on Maven Central must be GPG-signed. Generate a key and publish it:

```bash
gpg --gen-key
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>
```

Export the private key so CI can use it:

```bash
gpg --armor --export-secret-keys <KEY_ID> > private.key
```

## 3. Configure credentials

`pom.xml` already declares the `ossrh` server id in `distributionManagement`.
Either configure it locally in `~/.m2/settings.xml`:

```xml
<servers>
  <server>
    <id>ossrh</id>
    <username>YOUR_CENTRAL_TOKEN_USERNAME</username>
    <password>YOUR_CENTRAL_TOKEN</password>
  </server>
</servers>
```

Or (recommended) store these as GitHub Actions secrets and let CI deploy:

| Secret              | Value                                   |
| ------------------- | --------------------------------------- |
| `OSSRH_USERNAME`    | Central Portal token username           |
| `OSSRH_PASSWORD`    | Central Portal token                    |
| `GPG_PRIVATE_KEY`   | contents of `private.key`               |
| `GPG_PASSPHRASE`    | passphrase of the GPG key               |

## 4. Deploy

Locally:

```bash
mvn clean deploy -Dgpg.passphrase=YOUR_PASSPHRASE
```

Only sign without publishing:

```bash
mvn verify                       # sign the artifacts
mvn -Dgpg.skip=true verify       # skip signing for a local build
```

## 5. Cut a GitHub release

Push an annotated tag; the `CI` workflow builds and attaches the jars
(`geotrack-*.jar`, `-sources.jar`, `-javadoc.jar`) to the release page:

```bash
git tag -a vX.Y.Z -m "geotrack X.Y.Z"
git push origin vX.Y.Z
```

## Version checklist

- [ ] `version` in `pom.xml` bumped
- [ ] `mvn clean verify` green
- [ ] changelog / release notes drafted
- [ ] tag pushed, CI release job green
- [ ] artifacts visible on Central Portal
