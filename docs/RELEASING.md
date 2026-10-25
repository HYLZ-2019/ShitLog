# 开源与发布检查

当前版本为 0.1.1 实验版。源码公开和正式 APK 发布分别检查。

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
- 当前 debug APK 只适合测试。正式签名通常无法直接覆盖原 debug 安装，应先导出 JSON，安装正式版后再导入；先确认备份可恢复。
- 更新版本号、发布说明、已知限制、APK SHA-256，并核对最终 APK 中的权限、调试标记及第三方通知。不要发布调试构建作为正式版。
- CI 的测试报告不包含完整 APK，安装包由维护者单独审查后发布。不要将本机 `dist/` 强制加入 Git。

## 依赖与许可清单更新

在项目根目录运行：

```powershell
.\gradlew.bat :app:lintDebug --console=plain
python scripts/dependency_inventory.py
```

输出位于 `app/build/reports/oss/dependencies.json`，只含 Maven 坐标、配置、官方 POM 地址、摘要和许可元数据，不写入本机绝对路径。脚本基于 Lint 已解析的 debug 应用及测试依赖（含 provided 编译依赖），不包含正式 release 产物和构建工具。读取官方 Google Maven / Maven Central POM，缺失模型、网络失败或缺失许可时检查失败；缓存齐全可加 `--offline`。

人工复核新增的版权及 NOTICE 要求后，将报告复制到 `third_party/dependencies.json`，同步更新 `THIRD_PARTY_NOTICES.md` 与许可证原文。`python scripts/dependency_inventory.py --check` 可比较当前解析结果与已提交清单。正式 release 发布时须另外检查该变体及最终 APK，不能直接把 debug 清单视为全部 release 依赖。

生成清单不等同于完整法律审查。Gradle/POM 的许可证字段有时不涵盖内嵌代码；分发 APK 时也要检查最终产物和随依赖提供的通知。
