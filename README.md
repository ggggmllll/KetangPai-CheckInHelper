# 课堂派签到助手 Android

使用 Kotlin + Jetpack Compose 开发的原生 Android 应用，直接调用课堂派 API，支持在一台手机上管理多个账号。

## 功能

- 账号池：添加账号、更新密码、启用或停用账号；优先同步本学期课程，课程列表默认折叠，展开后可逐门启用或停用。
- 考勤刷新：前台每轮查询后等待 15 秒，回到前台立即刷新；接口异常时保留受影响账号的上次结果并显示错误。
- 数字签到：自动获取数字码，也可手动输入。
- GPS 签到：使用手机当前位置、手动输入经纬度，或选择已保存地点。
- GPS 地点列表：命名保存地点，支持手动输入和采样手机当前位置，显示位置来源和精度。
- 二维码签到：相机扫描或粘贴链接，WeChat/OpenCV 与 ML Kit 并行识别，支持自动缩放、手动缩放、点击对焦、补光和扫码诊断。
- 本机记录：分别展示各账号的签到结果。

账号、密码、登录 token 和地点加密保存在本机。首次添加账号需要联网。当前未实现后台持续监控，锁屏或切换到其他应用后不保证持续刷新。

课程同步先通过课堂派 `/courseApi/semesterList` 读取学期，再使用学年和学期参数查询 `/CourseApi/semesterCourseList`。优先选择当前日期对应的学期（8 月至次年 1 月为第一学期，2 至 7 月为第二学期）；没有匹配项时采用接口返回的最近学期，账号卡片显示实际选择的学期。仅查询所选学期的考勤，同步保留同一课程原来的启用状态。

采样位置时，同时请求可用的系统融合、网络和 GPS 定位，不依赖 Google Play 服务。30 秒内、精度在 100 米以内的缓存可直接使用；其他情况最多给精度改善预留 2.5 秒，已有精度在 1 千米以内的近期位置就返回，仍无可用结果时最多等待 12 秒。不会使用超过 60 秒的位置，低精度结果会提示核对。系统定位关闭时直接提示；失败保留输入，手动编辑或选择保存地点会取消正在进行的采样。首次卫星定位的速度仍受室内环境、网络及系统定位服务影响。

## 开发与构建

使用 Android Studio 打开此目录，安装 JDK 17 和 Android SDK 36。最低支持 Android 8.0（API 26）。SDK 路径可通过 `ANDROID_HOME` 环境变量或根目录 `local.properties` 的 `sdk.dir` 配置。

Windows 构建：

```powershell
.\gradlew.bat :android-app:assembleDebug
.\gradlew.bat :android-app:assembleRelease
```

macOS / Linux 构建：

```sh
./gradlew :android-app:assembleDebug
./gradlew :android-app:assembleRelease
```

构建产物：

- Debug：`android-app/build/outputs/apk/debug/android-app-debug.apk`
- Release：配置本地签名后输出 `android-app/build/outputs/apk/release/android-app-release.apk`；未配置签名时输出同目录下的 `android-app-release-unsigned.apk`。Release 默认只包含 `arm64-v8a`，启用 R8 和 native 库压缩，当前约 16MB；适合 ARM64 真机。Debug 版本也只包含 `arm64-v8a`，模拟器或 32 位 ARM 设备不在此 APK 的支持范围内。

需要签名 Release 时，将根目录的 `keystore.properties.example` 复制为 `keystore.properties`，填写自己的密钥路径、别名和密码后再构建。`storeFile` 建议使用绝对路径，Windows 路径使用正斜杠（例如 `C:/keys/release.jks`）。签名配置、密钥和构建产物均被 Git 忽略，不会提交。

连接手机后安装 Debug 版本（保留已有数据）：

```powershell
adb install -r android-app/build/outputs/apk/debug/android-app-debug.apk
```

## 项目结构

```text
android-app/
  src/main/
    AndroidManifest.xml
    java/com/ketangpai/checkin/
      MainActivity.kt    # Compose 页面
      DeviceLocation.kt  # 多来源定位、缓存与精度选择
      LocationSampler.kt # 采样状态与取消
      QrScanner.kt       # CameraX 预览、对焦与诊断
      QrFrameAnalyzer.kt # ML Kit / WeChat 并行识别
      QrLuminance.kt     # 相机亮度平面读取
      WeChatQrDecoder.kt # OpenCV 模型与小码识别
      DirectApi.kt       # 课堂派 API、账号与本机数据存储
      CourseSemester.kt  # 学期选择与展示
      Theme.kt           # 界面主题
    res/                # Android 资源
  build.gradle.kts
gradle/wrapper/         # Gradle Wrapper
build.gradle.kts
settings.gradle.kts
gradle.properties
gradlew
gradlew.bat
```

## 许可

保留原项目的 MIT 版权及许可声明，见 [LICENSE](LICENSE)。

扫码采用与 Ketangpai_Batch_Signer 相同的 WeChatQRCode 2.4.0 引擎及模型。相关许可及第三方声明位于 `android-app/src/main/assets/licenses/`，随 APK 一同打包。
