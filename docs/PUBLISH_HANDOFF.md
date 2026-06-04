# ZestFlow Maven Central 换机发版交接（2026-06-03）

> 在**生成 GPG 私钥的那台电脑**上完成 Central 首发。本文档汇总已完成工作与明日步骤。

---

## 一、已完成（代码 / 配置）

| 项 | 状态 |
|----|------|
| Sonatype 命名空间 | `cn.zestflow.www` 已注册 |
| GPG 公钥 | keyserver 已上传；Key ID `5B28B71AF1128C97`；UID `zestflow <zestcc@126.com>` |
| 版本 | 全模块 `0.1.0` |
| 发布插件 | `central-publishing-maven-plugin` 0.10.0（OSSRH 已下线，不用 nexus-staging） |
| 不发布模块 | `zestflow-admin`、`zestflow-demo`（deploy skip） |
| developer | `zestflow` / `zestcc@126.com` |
| release 验证 | `scripts/maven/verify-release.ps1` 已通过（JDK 17） |
| 脚本 | `scripts/maven/verify-release.ps1`、`scripts/maven/publish-central.ps1` |
| settings 模板 | `maven/settings.xml.example`（server id = **`central`**） |

### 将发布的 9 个 artifact

`zestflow`、`zestflow-common`、`zestflow-executor`、`zestflow-starter`、`zestflow-collector`、`collector-core`、`collector-jdbc`、`collector-kafka`、`collector-rabbitmq`

### 业务方依赖坐标

```xml
<dependency>
    <groupId>cn.zestflow.www</groupId>
    <artifactId>zestflow-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

---

## 二、GPG 密钥信息（勿提交 Git）

| 项 | 值 |
|----|-----|
| Key ID | `5B28B71AF1128C97` |
| Fingerprint | `3C3D03110B28D04E5C92B6075B28B71AF1128C97` |
| UID | `zestflow <zestcc@126.com>` |
| 过期 | 2029-06-02 |

私钥在**生成密钥的原电脑** gpg 钥匙环中，本仓库不包含私钥。

---

## 三、明日换机步骤（约 20 分钟）

### 1. 环境

```powershell
# JDK 17（路径按新机调整）
$env:JAVA_HOME = "D:\IT\JDK17\jdk-17.0.19+10"   # 示例

# Gpg4win（若无）
winget install -e --id GnuPG.Gpg4win
# 重开 PowerShell 后：
gpg --version
```

### 2. 拉代码

```powershell
git clone https://gitee.com/zestcc/zestflow.git
cd zestflow
# 或已有目录：git pull
```

### 3. 确认私钥（原电脑应有 sec）

```powershell
gpg --list-secret-keys --keyid-format LONG
# 期望：sec ... 5B28B71AF1128C97 ... zestflow <zestcc@126.com>
```

若无私钥但有多余 `.asc` 备份：`gpg --import zestflow-secret.asc`

### 4. Central Portal User Token

1. https://central.sonatype.com/usertoken → **Generate User Token**
2. Token **只显示一次**，勿发到聊天；若曾泄露须先 revoke 再新建

### 5. 配置 `%USERPROFILE%\.m2\settings.xml`

复制 `maven/settings.xml.example`，**`<id>` 必须是 `central`**：

```xml
<servers>
    <server>
        <id>central</id>
        <username>Token用户名</username>
        <password>Token密码</password>
    </server>
</servers>
<profiles>
    <profile>
        <id>gpg</id>
        <properties>
            <gpg.executable>C:\Program Files\GnuPG\bin\gpg.exe</gpg.executable>
            <gpg.passphrase>GPG口令</gpg.passphrase>
        </properties>
    </profile>
</profiles>
<activeProfiles>
    <activeProfile>gpg</activeProfile>
</activeProfiles>
```

可保留阿里云 mirror（与 central 不冲突）。

### 6. 验证 + 发布

```powershell
cd <项目根目录>

# 可选：先验证（跳过 GPG 签名）
powershell -File scripts/maven/verify-release.ps1

# 正式发布（需要私钥 + Token，勿加 gpg.skip）
powershell -File scripts/maven/publish-central.ps1
```

等价命令：

```powershell
mvn clean deploy -Prelease -DskipTests
```

### 7. 发布后

1. https://central.sonatype.com/publishing/deployments → 状态 **Published**（`autoPublish=true`）
2. 等待索引：https://search.maven.org/search?q=g:cn.zestflow.www
3. 打 tag：`git tag v0.1.0 && git push origin v0.1.0`
4. Gitee 发 Release 说明（可选）

---

## 四、常见问题

| 问题 | 处理 |
|------|------|
| `gpg` 找不到 | 重开终端，或 `$env:PATH = "C:\Program Files\GnuPG\bin;$env:PATH"` |
| Token 创建失败 | 换浏览器/无痕；mailto central-support@sonatype.com |
| 401 认证失败 | settings 里 id 是否为 `central`；Token 是否新生成 |
| 无私钥无法签名 | 必须在有 `sec` 的机器上 deploy，或 import `.asc` |
| JDK 不是 17 | 设置 `JAVA_HOME` 指向 JDK 17 |

---

## 五、相关文档

- [RELEASE_READINESS.md](./RELEASE_READINESS.md) §8
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [maven/settings.xml.example](../maven/settings.xml.example)

---

## 六、发版后开发版本建议

首发成功后下一开发迭代可改为 `0.1.1-SNAPSHOT`（另开 commit，非本次必须）。
