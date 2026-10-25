# 开源与发布检查

当前版本为 0.1.3 实验版，从此版本起配置固定签名升级渠道。固定签名不表示已经完成真机验收，因此自动 Release 仍标为 Pre-release。

## 一次性配置固定签名

详见 [签名与升级](SIGNING.md)。本机的 `.signing/` 保存专用发布 keystore 与 GitHub Secrets 导入文件，已被 Git 忽略；`release-signing.json` 只记录公开的应用 ID 和证书指纹，需要提交。

将 `.signing/` 完整备份到安全位置，再将其四项配置导入仓库的 GitHub Actions Secrets。安装并登录 GitHub CLI 后，可以在项目根目录执行：

```powershell
gh secret set --env-file .signing/github-secrets.env --repo HYLZ-2019/ShitLog
```

这是唯一一次所需的密钥配置，后续版本继续复用。不要把该文件提交、发到聊天或作为 Release 附件。未设置 Secrets 时，普通 CI 不受影响；标签发布会失败，不生成其他签名的替代包。

## 自动发布

普通分支提交和 PR 运行只读检查；推送 `v…` 标签后，工作流依次执行：

1. 仓库检查、发布工具测试、debug 构建、JVM 测试、Lint、设备测试包编译和依赖许可清单核对。
2. 在独立任务中从 Secrets 恢复固定密钥，构建并 Lint 检查非调试的 release APK；结束时删除 runner 临时密钥文件。
3. 使用 `apksigner` 验证签名，并核对仓库固定证书指纹、包名、标签版本、源码提交和 `versionCode`。release 实际依赖必须包含在已核对的许可清单内，否则拒绝发布。
4. 上传 APK、源码 ZIP、发布清单与 SHA-256；只有最后的发布任务有 `contents: write` 权限。先创建草稿并上传附件，全部成功再公开为预发布，不设为 Latest。

不需要额外创建用于 Release 的个人令牌，工作流使用内置 `GITHUB_TOKEN`。组织策略如果禁止 Actions 写入 Releases，需要管理员允许该工作流的发布权限。

先提交并推送全部配置，确认 Actions 通过，再发布首个固定签名版本：

```powershell
git tag -a v0.1.3 -m "轮回记录 0.1.3：固定签名升级渠道"
git push origin v0.1.3
```

标签必须与实际 APK 的 `versionName` 完全相等（前缀为 `v`）；`versionCode` 必须大于历史所有版本标签对应的代码。新版本先递增 `app/build.gradle.kts` 中的两个版本字段，更新说明并提交，再推送新标签。不要改写已发布标签。

Release 附件：

- `LunhuiLog-<版本>.apk`：固定签名的 release 构建。
- `LunhuiLog-<版本>-source.zip`：`git archive` 从标签提交导出的源码，包含许可证和第三方原文，不包含本机未跟踪文件。
- `RELEASE_MANIFEST.json`：版本、提交、公开证书指纹和实际 release 依赖。
- `SHA256SUMS.txt`：以上三个附件的 SHA-256。

失败时查看 Actions；附件上传失败可能留下未公开草稿，检查后可删除该草稿再重跑。已存在的 Release 不会自动覆盖。普通分支的工作流重跑不会发布，标签必须指向包含自动发布配置的提交。

首次从旧 debug 包切换到 0.1.3 固定签名包，需先导出 JSON 并确认可恢复，再卸载旧包、安装新包和恢复。之后保持相同包名、密钥和更大的 `versionCode`，直接安装新 APK 即可升级并保留本机记录，不需要重复卸载。

## Actions 成功但没有 APK

确认该次运行包含 `checks`、`Build with fixed signing identity` 和 `Publish signed prerelease` 三个任务，并且全部成功。仅 `checks` 成功不表示发布成功；普通分支推送和手动运行不会发布。

标签必须指向已提交签名和发布配置的提交。2026-10-25 排查发现，现有 `v0.1.2` 指向 `d6f6316`（仅旧版 CI，应用版本仍为 0.1.1），对应 Actions 仅执行 `checks`，公开 Releases API 返回空列表。页面显示的 Source code ZIP／TAR.GZ 是标签源码下载，不是 APK。重复运行旧提交仍不会执行本地尚未提交的发布流程。

保留现有 `v0.1.2` 标签；提交并推送当前配置后，使用新的 `v0.1.3` 标签。发布完成后，应在该 Release 的 Assets 中看到 `LunhuiLog-0.1.3.apk`。

## 首次公开仓库

1. 检查 `LICENSE`、`NOTICE`、第三方清单和许可证原文。新增依赖须同时核对传递依赖及随包通知。
2. 运行 `python scripts/check_repository.py`，逐项审查 `git status --short` 和 `git diff --cached --stat`；暂存后再运行 `python scripts/check_repository.py --staged`。
3. 只提交源码、构建配置和公开文档。确认没有抓包、模拟器镜像、本机配置、密钥、真实备份或私人截图。不直接上传整个工作目录压缩包。
4. 按 README 构建，记录实际验证结果；不要把设备测试编译通过表述为设备测试通过。
5. 创建远程仓库后检查源码页面，启用私密漏洞报告和平台提供的秘密扫描功能。将 CI 检查设为默认分支合并要求。
6. 如需发布源码标签，确认标签对应的提交与许可、版本和验证记录一致。

## APK 正式发布前

- 在可用真机上验证开始、结束、后台恢复、进程终止、设备重启、日历、主题、字体缩放、文件选择器导出与恢复。备份恢复使用独立测试数据。
- 使用维护者掌握的正式签名；私钥和密码保存在仓库外，不随源码分发。为签名材料做离线备份。
- 原 debug APK 只适合测试，不能直接覆盖安装新的固定签名包。首次迁移按上述备份／重装流程执行。
- 更新版本号、发布说明、已知限制、APK SHA-256，并核对最终 APK 中的权限、调试标记及第三方通知。不要发布调试构建作为正式版。
- 普通 CI 的测试报告不含 APK；版本标签会自动发布固定签名的实验版 APK。正式可用性仍需上述设备验收，不要将本机 `dist/` 强制加入 Git。

## 依赖与许可清单更新

在项目根目录运行：

```powershell
.\gradlew.bat :app:lintDebug --console=plain
python scripts/dependency_inventory.py
```

输出位于 `app/build/reports/oss/dependencies.json`，只含 Maven 坐标、配置、官方 POM 地址、摘要和许可元数据，不写入本机绝对路径。脚本基于 Lint 已解析的 debug 应用及测试依赖（含 provided 编译依赖），不包含正式 release 产物和构建工具。读取官方 Google Maven / Maven Central POM，缺失模型、网络失败或缺失许可时检查失败；缓存齐全可加 `--offline`。

人工复核新增的版权及 NOTICE 要求后，将报告复制到 `third_party/dependencies.json`，同步更新 `THIRD_PARTY_NOTICES.md` 与许可证原文。`python scripts/dependency_inventory.py --check` 可比较当前解析结果与已提交清单。正式 release 发布时须另外检查该变体及最终 APK，不能直接把 debug 清单视为全部 release 依赖。

生成清单不等同于完整法律审查。Gradle/POM 的许可证字段有时不涵盖内嵌代码；分发 APK 时也要检查最终产物和随依赖提供的通知。
