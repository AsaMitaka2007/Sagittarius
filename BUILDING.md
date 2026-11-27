# 构建说明

本仓库是单模块 Android 项目（`:app`），使用 Gradle 8.7 + AGP 8.6.0 + Kotlin 1.9.24。
以下步骤已在 Windows 11 上实测通过。

## 环境要求

| 项目 | 要求 |
|---|---|
| JDK | **17**（`JavaVersion.VERSION_17`，`jvmTarget = 17`） |
| Android SDK | Platform 34、Build-Tools 34（`compileSdk = 34`） |
| Gradle | 8.7，由 `gradlew` 自动下载，无需本机安装 |
| 最低运行版本 | Android 8.0（API 26） |

> **不要用 JDK 21/24/25。** Gradle 8.7 不支持 JDK 25，会直接报
> `Unsupported class file major version`。构建失败时先检查 `JAVA_HOME`。

## Windows（PowerShell）

```powershell
cd E:\Sagittarius
$env:JAVA_HOME        = 'C:\Program Files\Java\jdk-17'
$env:ANDROID_HOME     = 'C:\Android\Sdk'
$env:ANDROID_SDK_ROOT = 'C:\Android\Sdk'

.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

## macOS / Linux

```bash
cd /path/to/Sagittarius
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
export ANDROID_SDK_ROOT=$ANDROID_HOME

./gradlew :app:assembleDebug --console=plain
./gradlew :app:testDebugUnitTest --console=plain
```

## 常见问题

1. **`JAVA_HOME` 指向了系统默认 JDK。** 本机可能装着多个 JDK，务必显式指向 17。
2. **`ANDROID_HOME` 为空导致找不到 SDK。** 命令行构建不会读 IDE 配置，必须设环境变量。
3. **不要照抄 `VERIFICATION.md` 里的构建命令。** 其中的
   `-Pandroid.aapt2FromMavenOverride=/opt/aapt2-shim/aapt2` 和 `/opt/android-sdk`
   是历史 Linux 环境的绕行方案，Windows / 标准环境不要使用。
4. **`local.properties` 缺失不影响命令行构建。** 用 Android Studio 打开时由 IDE 生成，
   该文件已被 gitignore，不要提交。
5. **不要用 `gradlew clean` 当日常手段。** 全量构建约 2 分钟，增量构建通常几秒。

## 验证清单

提交前至少跑通：

```powershell
.\gradlew.bat :app:assembleDebug --console=plain
.\gradlew.bat :app:testDebugUnitTest --console=plain
```

可选静态检查（当前基线：0 error / 394 warning，不要为消 warning 大改代码）：

```powershell
.\gradlew.bat :app:lintDebug --console=plain
```

## 版本号规则

- `versionCode`：**单调递增，只增不减**，每次对外发布至少 +1。
- `versionName`：语义化版本 `MAJOR.MINOR.PATCH`；功能新增升 MINOR，修复升 PATCH。
- 当前基线：`versionCode = 63`、`versionName = "1.1.0"`（1.1 已发布版本；alpha-2.0 测试包保持此版本号，正式 1.2 发布时升到 64/1.2.0）。
- 改版本号时两处必须同时改，并在发布前核对 `dist/` 下 APK 与 `build.gradle.kts` 一致。

## 发布签名

- `app/build.gradle.kts` 会读取 `keystore/keystore.properties`（已被 `.gitignore` 忽略），
  里面包含 `storeFile`、`storePassword`、`keyAlias`、`keyPassword`。
- 本地有该文件时 `assembleRelease` 自动签名；CI 没有该文件时只构建 debug，不产出 release 包。
- **不要**把 keystore、密码或 `keystore.properties` 提交到 git。

## 数据库改动

- 当前 Room 版本为 **11**，schema 已导出到 `app/schemas/`。
- 改实体必须同时：`version + 1`、编写 `Migration`、重新导出 schema、补迁移测试。
- **禁止** `fallbackToDestructiveMigration`，会清空真实用户数据。

## 分支约定

- `main` 保持可发布状态，日常开发在 `dev` 分支。
- 一次提交只做一件事，commit message 用中文，前缀
  `feat:` / `fix:` / `refactor:` / `chore:` / `docs:`。
