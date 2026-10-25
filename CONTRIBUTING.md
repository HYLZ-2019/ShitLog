# 参与贡献

欢迎提交问题和改进。项目处于实验阶段，当前优先保证 Android 本地排便记录、计时可靠性和备份恢复。

## 开始前

按 [README](README.md) 配置 JDK 21、Android SDK 和 Gradle Wrapper。较大的功能或数据格式变更，请先通过 Issue 说明使用场景和方案，避免重复工作。

提交代码即表示你有权提交该内容，并同意按本项目的 Apache-2.0 许可证提供贡献。不需要单独签署 CLA。第三方代码和素材必须注明来源、版本、许可证及必要的版权声明，不能将它们重新声明为项目原创。

## 修改约定

- 默认离线，不添加广告、遥测、账号或联网行为。确有必要时先讨论产品范围。
- 公共事件与具体功能详情分开，避免把其他记录类型塞进排便详情表。
- 未知值保留为 `null`，不能把导入记录的缺失值解释为否。
- 数据库变更须递增版本、提供显式迁移和迁移测试，不使用破坏性迁移。
- 修改备份格式时同步更新格式版本、兼容规则、虚构示例与相关测试。
- 界面保持简洁，优先考虑单手操作、字体缩放和小屏可滚动。
- 不提交真实健康记录、抓包、访问凭据、签名材料、本机路径或未经检查的截图。问题复现使用虚构数据。

## 提交前检查

```powershell
python scripts/check_repository.py
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Linux/macOS 将 `python` 换成 `python3`，将 `.\gradlew.bat` 换成 `./gradlew`。有设备时运行 `:app:connectedDebugAndroidTest`。仅编译设备测试不能宣称设备流程已通过。

PR 请说明问题、用户可见变化、验证结果和已知限制；视觉修改可附经检查的截图。依赖更新需核对传递依赖及许可，并更新第三方清单。单元测试应验证实际行为，不必为纯排版修改增加重复测试。

现有 GitHub Actions 会检查仓库文件、构建、单元测试、Lint、设备测试编译和依赖许可清单；不会上传 APK、签名文件或健康数据。
