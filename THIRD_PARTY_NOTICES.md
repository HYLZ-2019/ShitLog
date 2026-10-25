# 第三方许可与素材来源

项目原创代码与原创图形采用 [Apache-2.0](LICENSE)。第三方组件保留各自许可，项目许可证不替代它们的版权声明和分发条件。

## 已核对的应用与测试组件

本次清单来自 0.1.1 已构建的 Android Lint 依赖模型，共 **106 个依赖组件**，覆盖 debug 应用、JVM 测试与 Android 测试，包含模型中的 provided 编译依赖。逐版本坐标、配置、官方 POM 地址、POM SHA-256 与许可元数据见 [dependencies.json](third_party/dependencies.json)。版本可能由传递依赖约束提升，不能只凭 Compose BOM 版本推断实际组件版本。

| 组件 | 已核对版本／范围 | 声明许可 |
|---|---|---|
| AndroidX Core、Activity、Lifecycle、Compose、Room、DataStore、SQLite、Test 等 | 84 个组件，逐项版本见清单 | Apache-2.0 |
| AndroidX DataStore external protobuf | 1.1.7 | BSD-3-Clause |
| Kotlin 标准库及 Android 运行库 | stdlib 2.2.10；jdk7/jdk8 1.8.0；Android extensions/parcelize 1.9.22 | Apache-2.0 |
| kotlinx.coroutines | 1.8.1，含 android、core-jvm、test-jvm | Apache-2.0 |
| kotlinx.serialization-core-jvm | 1.7.3 | Apache-2.0 |
| JetBrains annotations | 23.0.0 | Apache-2.0 |
| JSpecify | 1.0.0 | Apache-2.0 |
| Guava listenablefuture | 1.0 | Apache-2.0 |
| FindBugs jsr305 | 2.0.2 | POM 声明 Apache-2.0 |
| Okio JVM | 3.4.0，测试传递依赖 | Apache-2.0 |
| JavaWriter | 2.1.1，测试传递依赖 | Apache-2.0 |
| javax.inject | 1 | Apache-2.0，见下方源码证据 |
| JUnit | 4.13.2，仅测试 | EPL-1.0 |
| Hamcrest core、integration、library | 1.3，仅测试 | BSD-3-Clause（POM 称 New BSD License） |
| JSON-java | 20240303，仅 JVM 测试；Android 使用系统 `org.json` | Public Domain |

`javax.inject:javax.inject:1` 的官方 POM 没有许可字段。已核对其 Maven Central 官方 sources JAR 中 `javax/inject/Inject.java` 的 Apache-2.0 头部，并在 [license_overrides.json](third_party/license_overrides.json) 中记录这一例外；检查脚本不会把其他未知许可自动归为 Apache-2.0。

## 原文与来源

- [项目根目录 LICENSE](LICENSE) 是 Apache-2.0 完整原文，适用于采用该许可的组件；各组件的原始版权归属仍须保留。
- [third_party/licenses](third_party/licenses/) 保存从 JUnit/Hamcrest 官方 JAR、已构建的 Android 合并 Java 资源和官方上游获取的许可／通知原文，以及 javax.inject 的源码许可头。
- [notice_sources.json](third_party/notice_sources.json) 记录这些文件的来源、归档内部条目和 SHA-256。上游原文不改写为项目自己的声明。

DataStore external protobuf 的该版本 sources JAR 只包含归档元数据，未提供源码。其 POM 声明 BSD-3-Clause，因此另保留 protobuf 上游 BSD-3-Clause 原文；来源标签用于定位许可文件，不表示已确认内嵌 protobuf 的精确版本，详见来源清单。

清单基于官方 POM 及上述原文核对，不等同于逐行代码权属审查。尤其不能把 POM 元数据视为所有内嵌代码的完整许可证明。新增、升级或移除依赖时，应检查实际产物的许可证和 NOTICE，并更新清单。源码分发保留这些文件；正式 APK 发布前另行核对 release 变体和最终 APK，随包提供适用的第三方声明与许可原文。

## 构建工具

构建配置声明 Gradle 9.3.1、Android Gradle Plugin 9.1.0、Kotlin/Compose 编译器 2.2.10 和 KSP 2.3.6；这些项目的主要代码采用 Apache-2.0，其发行包包含的其他组件仍有各自条款。

仓库中的 Gradle Wrapper 脚本保留官方版权和 Apache-2.0 头部，Wrapper JAR 按官方发行方式保留。Gradle 发行 ZIP 由 Wrapper 单独下载，SHA-256 固定在 `gradle/wrapper/gradle-wrapper.properties`。Android SDK 与 JDK 不随仓库分发，使用者需遵守各自发行条款。应用／测试清单不包含构建工具的全部传递依赖。

## 图形与产品来源

- 循环应用图标使用本项目 `ic_cycle.xml` 的 VectorDrawable 路径；界面循环标记由 Compose Canvas 绘制。
- Bristol 七型选项的简图由本项目 Canvas 绘制，没有包含第三方成套 Bristol 图表图片。
- 界面使用 AndroidX Compose Material Icons 的图标，按该组件 Apache-2.0 条款使用。
- 项目不包含原微信小程序的截图、源代码、图标或抓包数据。计时和日历等功能参考不表示与原小程序存在合作或授权关系。

循环图形及本项目绘制的简图按项目 Apache-2.0 许可提供。新增图片、字体或图标时，应记录实际来源与许可，不应默认可复用。
