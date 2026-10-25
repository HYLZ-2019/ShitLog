# 轮回记录

仅 Android 的本地生活记录应用，当前提供排便记录。首页一张拉屎卡片，一键开始、结束计时；支持 Bristol 七型、身体感受、月历、趋势，以及 JSON 备份恢复和 CSV 导出。

**当前为 0.1.1 实验版。** 已有构建、单元测试与静态检查记录，设备完整流程和视觉验收尚未完成。请阅读 [验证记录与已知限制](docs/VERIFICATION.md)。

项目原创代码和图形采用 [Apache-2.0](LICENSE)，第三方组件保留各自许可，见 [第三方说明](THIRD_PARTY_NOTICES.md)。

## 构建

- JDK 21，可使用兼容的 Android Studio 自带 JBR。
- Android SDK Platform 36.1；准备 Build Tools 36.0.0 和 36.1.0。
- Gradle 9.3.1，Android Gradle Plugin 9.1.0，内置 Kotlin 2.2.10，KSP 2.3.6。
- Compose BOM 2024.09.00，Room 2.8.4，DataStore 1.1.7。
- minSdk 26（Android 8.0），targetSdk 36。
- Python 3.11+ 用于仓库文件与许可清单检查；应用构建本身不依赖 Python。

推荐用 Android Studio 打开项目，设置 Gradle JDK 为 21，通过 SDK Manager 安装上述 SDK。也可以使用命令行；不需要单独安装 Gradle，始终使用项目 Wrapper。

将 `JAVA_HOME` 指向 JDK 21、`ANDROID_HOME` 指向 Android SDK，并将 SDK 的 `platform-tools` 和 `cmdline-tools/latest/bin` 加入 PATH。也可在项目根目录创建仅供本机使用的 `local.properties`，填写 `sdk.dir`；此文件已忽略，不应提交。

Windows PowerShell（路径按本机安装位置替换）：

```powershell
$env:JAVA_HOME='C:\Java\jdk-21'
$env:ANDROID_HOME='C:\Android\Sdk'
sdkmanager 'platforms;android-36.1' 'build-tools;36.0.0' 'build-tools;36.1.0'
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

Linux / macOS（路径按本机安装位置替换）：

```sh
export JAVA_HOME=/opt/jdk-21
export ANDROID_HOME=/opt/android-sdk
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH"
sdkmanager 'platforms;android-36.1' 'build-tools;36.0.0' 'build-tools;36.1.0'
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleDebugAndroidTest --console=plain
```

首次使用命令行 SDK 时运行 `sdkmanager --licenses`，阅读并接受所需的 Android SDK 条款。首次构建需要联网下载依赖；缓存齐全时可添加 `--offline`。默认采用进程内 Kotlin 编译，避免另起 Kotlin daemon。

生成 APK：`app/build/outputs/apk/debug/app-debug.apk`，包名 `com.lunhui.log`。使用 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 安装。`dist/` 仅为本机交付目录，不随 Git 提供。

debug 包使用构建机器的调试签名。只有签名一致时才能直接覆盖安装并保留数据；从其他机器构建的包或正式签名版通常无法覆盖现有 debug 安装。更换签名前先导出 JSON 并确认可恢复。正式签名材料由维护者在仓库外管理。

有在线设备或模拟器时再执行设备测试：

```sh
./gradlew :app:connectedDebugAndroidTest --console=plain
```

Windows 使用 `.\gradlew.bat`。`assembleDebugAndroidTest` 仅编译测试包，不代表设备流程通过。GitHub Actions 执行仓库文件检查、构建、单元测试、Lint、测试包编译和依赖许可清单检查，暂不运行模拟器。当前文档中的本机验证不能替代远程 CI 首次运行结果。

## 使用

1. 打开首页，在拉屎卡片上点「开拉」。
2. 完成后点「结束」，时间立即保存。
3. 详情页仅显示时长，填写后保存，或者稍后补充。新记录的五个身体感受胶囊默认否，选中为是；没有填写的详情在数据库中仍为未知。正常计时不需填写结束时间。
4. 在历史月历查看、补记、修改或删除记录。
5. 设置中导出完整 JSON 备份，或导出 CSV 自行分析。

手工补记或修正时，在时长右侧点「填写／修改」，选择开始时间并输入分钟、秒；结束时间自动推算。表单按屏幕宽度紧凑排布，小屏、放大字体或键盘展开时可以滚动。

不申请联网、通知或广泛存储权限，没有广告、账号或遥测。文件导入导出通过系统文件选择器；选择云盘时可能由该提供方同步。备份和 CSV 均为明文；数据库没有应用层加密，卸载可能删除应用本地数据。系统云备份和设备转移已在配置中排除，具体厂商行为仍需真机验证。完整 JSON 包含软删除记录。

## 结构

- `core/model`：公共事件、排便详情、来源和统计纯函数。
- `core/database`：Room 实体关系、DAO、导出的 v1 schema。
- `core/time`：墙上时钟、设备启动标识与单调时钟计时。
- `core/backup`：版本化 JSON、严格校验、冲突预览、事务合并、CSV。
- `feature/bowel`：计时、表单与时间编辑。
- `feature/history`：月历和当天记录。
- `feature/trends`：日期范围、次数、时长中位数、性状与症状统计。
- `feature/settings`：主题、导入导出。

公共 `records` 加模块详情表的结构用于以后扩展其他记录类型。当前不实现其他功能卡片、桌面快捷方式、小组件和旧微信数据抓取。

## 数据规则

- 开始／结束通过 Room 事务完成，重复操作保持幂等。
- 计时持久化；同次开机使用单调时钟。重启或无法确认启动一致时回退墙上时钟并标记需检查。
- 历史日期固定为发生时的本地日期，跨午夜归开始日。
- 五个身体感受字段允许 null，不将未知当作否。
- 只统计已完成且未删除的记录。统计分母仅包含该字段有值的记录。
- v1 是首个数据库版本，没有已发布的旧版本迁移。未启用破坏性迁移；以后变更必须增加显式迁移和迁移测试。

完整产品约定见 [PROJECT_PLAN.md](PROJECT_PLAN.md)，备份格式见 [docs/BACKUP_FORMAT.md](docs/BACKUP_FORMAT.md)，实际验证与限制见 [docs/VERIFICATION.md](docs/VERIFICATION.md)。

## 参与和发布

- [贡献指南](CONTRIBUTING.md)：开发约定、数据兼容规则与提交前检查。
- [安全与隐私报告](SECURITY.md)：避免在公开反馈中泄露健康记录或凭据。
- [发布检查](docs/RELEASING.md)：首次公开、依赖清单和正式 APK 发布流程。
- [第三方许可与素材来源](THIRD_PARTY_NOTICES.md)：组件版本、许可及随包通知。

提交前运行 `python scripts/check_repository.py`（Linux/macOS 为 `python3`），暂存后可加 `--staged` 检查准确的暂存内容。脚本不会读取被忽略的私人目录，只能辅助发现常见问题，仍需人工审查提交内容和截图。
