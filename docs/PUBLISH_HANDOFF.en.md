# ZestFlow Maven Central Machine-Swap Release Handoff (2026-06-03)

> **Version** 0.1.0 · **Updated** 2026-06-08 · **Language** English · [简体中文](PUBLISH_HANDOFF.md) · **Type** Handoff · [← Documentation hub](README.en.md)  
> Complete Central first release on the **machine where the GPG private key was generated**. This document summarizes completed work and next-day steps.

---

## I. Completed (Code / Configuration)

| Item | Status |
|------|--------|
| Sonatype namespace | `cn.zestflow.www` registered |
| GPG public key | Uploaded to keyserver; Key ID `5B28B71AF1128C97`; UID `zestflow <zestcc@126.com>` |
| Version | All modules `0.1.0` |
| Release plugin | `central-publishing-maven-plugin` 0.10.0 (OSSRH retired, no nexus-staging) |
| Unpublished modules | `zestflow-admin`, `zestflow-demo` (deploy skip) |
| developer | `zestflow` / `zestcc@126.com` |
| release verification | `scripts/maven/verify-release.ps1` passed (JDK 17) |
| Scripts | `scripts/maven/verify-release.ps1`, `scripts/maven/publish-central.ps1` |
| settings template | `maven/settings.xml.example` (server id = **`central`**) |

### Nine Artifacts to Publish

`zestflow`, `zestflow-common`, `zestflow-executor`, `zestflow-starter`, `zestflow-collector`, `collector-core`, `collector-jdbc`, `collector-kafka`, `collector-rabbitmq`

### Business Dependency Coordinates

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## II. GPG Key Information (Do Not Commit to Git)

| Item | Value |
|------|-------|
| Key ID | `5B28B71AF1128C97` |
| Fingerprint | `3C3D03110B28D04E5C92B6075B28B71AF1128C97` |
| UID | `zestflow <zestcc@126.com>` |
| Expires | 2029-06-02 |

Private key lives in gpg keyring on **machine where key was generated**; not included in this repository.

---

## III. Next-Day Machine-Swap Steps (~20 Minutes)

### 1. Environment

```powershell
# JDK 17 (adjust path for new machine)
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"   # Example

# Gpg4win (if missing)
winget install -e --id GnuPG.Gpg4win
# After reopening PowerShell:
gpg --version
```

### 2. Pull Code

```powershell
git clone https://gitee.com/zestcc/zestflow.git
cd zestflow
# Or existing directory: git pull
```

### 3. Confirm Private Key (original machine should have sec)

```powershell
gpg --list-secret-keys --keyid-format LONG
# Expected: sec ... 5B28B71AF1128C97 ... zestflow <zestcc@126.com>
```

If no private key but `.asc` backup exists: `gpg --import zestflow-secret.asc`

### 4. Central Portal User Token

1. https://central.sonatype.com/usertoken → **Generate User Token**
2. Token **shown once only**—do not send to chat; revoke and regenerate if leaked

### 5. Configure `%USERPROFILE%\.m2\settings.xml`

Copy `maven/settings.xml.example`; **`<id>` must be `central`**:

```xml
<servers>
    <server>
        <id>central</id>
        <username>Token username</username>
        <password>Token password</password>
    </server>
</servers>
<profiles>
    <profile>
        <id>gpg</id>
        <properties>
            <gpg.executable>C:\Program Files\GnuPG\bin\gpg.exe</gpg.executable>
            <gpg.passphrase>GPG passphrase</gpg.passphrase>
        </properties>
    </profile>
</profiles>
<activeProfiles>
    <activeProfile>gpg</activeProfile>
</activeProfiles>
```

Aliyun mirror may remain (does not conflict with central).

### 6. Verify + Publish

```powershell
cd <project root>

# Optional: verify first (skip GPG signing)
powershell -File scripts/maven/verify-release.ps1

# Official publish (requires private key + Token, do not add gpg.skip)
powershell -File scripts/maven/publish-central.ps1
```

Equivalent command:

```powershell
mvn clean deploy -Prelease -DskipTests
```

### 7. After Publish

1. https://central.sonatype.com/publishing/deployments → status **Published** (`autoPublish=true`)
2. Wait for index: https://search.maven.org/search?q=g:cn.zestflow.www
3. Tag: `git tag v0.1.0 && git push origin v0.1.0`
4. Gitee Release notes (optional)

---

## IV. FAQ

| Issue | Resolution |
|-------|------------|
| `gpg` not found | Reopen terminal, or `$env:PATH = "C:\Program Files\GnuPG\bin;$env:PATH"` |
| Token creation fails | Try different browser/incognito; mailto central-support@sonatype.com |
| 401 auth failure | Is settings id `central`? Is Token freshly generated? |
| Cannot sign without private key | Must deploy on machine with `sec`, or import `.asc` |
| JDK not 17 | Set `JAVA_HOME` to JDK 17 |

---

## V. Related Documentation

- [RELEASE_READINESS.en.md](./RELEASE_READINESS.en.md) §8
- [ARCHITECTURE.en.md](./ARCHITECTURE.en.md)
- [maven/settings.xml.example](../maven/settings.xml.example)

---

## VI. Post-Release Development Version Suggestion

After successful first release, next dev iteration may use `0.1.1-SNAPSHOT` (separate commit, not required for this release).
