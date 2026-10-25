# 固定签名与升级

从 0.1.3 起，轮回记录使用维护者掌握的固定签名。应用 ID 始终为 `com.lunhui.log`；`release-signing.json` 固定公开证书的 SHA-256。发布工具使用 Android `apksigner` 验证实际 APK 指纹，任何不一致都阻止发布。

## 本机文件

`.signing/` 已加入忽略规则与仓库检查黑名单，包含：

- `lunhui-release.jks`：专用发布私钥库（JKS / RSA 3072），不可公开。
- `passwords.env`：本机密码与 alias，供备份和本地构建使用。
- `github-secrets.env`：四项 GitHub Secrets 导入文件，含 keystore Base64，等同于私钥和密码，必须保密。
- `certificate.der`：导出的公开证书，保留在本机供校验。

**在首次发布前，将整个 `.signing/` 目录安全备份，和 Git 仓库分开保存。** GitHub Secrets 不提供私钥导出，不能替代本地备份。密钥遗失后无法为此渠道正常签署升级包；不要每个版本重新生成密钥，也不要把旧 debug 密钥当成专用发布密钥。

当前维护者的本机密钥已生成。脚本 `scripts/create_release_key.py` 遇到已有 `.signing/` 或 `release-signing.json` 会拒绝覆盖；其他贡献者构建 debug 包不需要这把密钥。不要在克隆仓库后删除公开身份文件并替换它来发布同名升级。

## GitHub 配置

仓库 **Settings → Secrets and variables → Actions → Repository secrets** 需要这四项：

| Secret 名称 | 内容 |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | 专用 keystore 的 Base64 |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 密码 |
| `ANDROID_KEY_ALIAS` | 签名条目 alias |
| `ANDROID_KEY_PASSWORD` | 签名私钥密码 |

这些值已经保存在本机 `.signing/github-secrets.env`，无需重新生成或在聊天中粘贴。安装 GitHub CLI 并通过 `gh auth login` 登录后，可一次性导入：

```powershell
gh secret set --env-file .signing/github-secrets.env --repo HYLZ-2019/ShitLog
```

若未安装 CLI，可使用 `winget install --id GitHub.cli -e` 后重新打开终端；也可在 GitHub 页面逐项填写。检查四个 Secret 名称存在即可，不要在 CI 或终端中打印值。Release 本身使用自动提供的 `GITHUB_TOKEN`，无需另存个人访问令牌。

标签构建时将 keystore 还原到 runner 临时目录，密码只通过步骤环境变量传递，构建结束清理文件。不会把密钥写进发布源码、附件或 Gradle configuration cache。保护仓库写权限，避免向恶意修改的工作流推送版本标签；有标签写权限的人能触发使用签名密钥的工作流。

## 本地签名构建

配置 JDK 21 与 Android SDK 后，在 PowerShell 使用本机已有密钥：

```powershell
$env:ANDROID_KEYSTORE_PATH = (Resolve-Path .signing/lunhui-release.jks).Path
Get-Content .signing/passwords.env | ForEach-Object {
    $pair = $_ -split '=', 2
    [Environment]::SetEnvironmentVariable($pair[0], $pair[1], 'Process')
}
.\gradlew.bat :app:assembleRelease :app:lintRelease --no-configuration-cache
```

不要在共享终端或日志中输出环境变量。release 构建必须具备全部签名环境变量，不会自动退回 debug 或 unsigned 包。debug 构建不需要配置任何 Secret。

## 升级规则

首次从原 debug 版切换时：导出 JSON → 确认备份文件保存可靠 → 卸载旧版 → 安装固定签名版 → 导入 JSON。卸载前必须完成备份，因为卸载可能清除应用数据。

此后每次发布保持应用 ID、密钥与证书不变，递增 `versionCode` 和 `versionName`。用户直接安装新 APK，即可覆盖升级并保留数据库。数据库结构变化仍需显式迁移和迁移测试；签名一致并不能替代数据库升级兼容性。

当前源码仍为实验版、数据库仍为 v1。固定签名解决安装升级身份问题，不表示已通过所有设备和数据恢复验收。
