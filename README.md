# photo-guide

一个面向拍照新手的 Android MVP：选择场景，查看构图、光线与拍前建议；选择内置姿势示意，开启可拖动悬浮窗，并通过系统相机拍摄。悬浮窗可切换姿势或关闭，通知也提供关闭入口。照片由系统相机处理，应用不读取或保存照片。

## 构建

需要 JDK 17、Android SDK Platform 35 和 Build Tools 35.0.0。设置 `JAVA_HOME`、`ANDROID_HOME`，或在 `local.properties` 中设置 `sdk.dir`，然后运行：

```bash
./gradlew assembleDebug
```

APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。GitHub Actions 在推送 `main` 时构建 debug APK，发布到 GitHub Release，并直接附加 `photo-guide-v0.0.N.apk`。也可以在 `main` 上手动触发工作流，重试当前提交的发行。

发行版本从 `v0.0.1` 开始，按已有的 `v0.0.N` tag 最大 patch 加一；`versionName` 为对应的 `0.0.N`，`versionCode` 为 N。工作流串行排队并通过推送 tag 预留版本；同一提交重跑会复用 tag 和 Release。预留后若构建失败，重新运行该提交的工作流即可继续发行。队列最多容纳 100 个等待中的运行，超出 GitHub 上限的运行需手动重试。请勿移动或删除发行 tag，也不要在同一提交上手动创建多个发行 tag。

本地构建默认使用 `0.0.1` / `1`，也可传入 `-PappVersionName=0.0.2 -PappVersionCode=2`。这些 APK 使用 debug 签名，不适合正式上架。

## 安装与测试

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

在手机上打开应用，选择场景和姿势，点击开启；首次按提示允许悬浮窗，返回后再次点击，再允许通知。系统相机打开后可拖动悬浮窗顶部、切换姿势或关闭；也可从通知或应用中关闭。检查系统相机能正常拍摄并保存照片。

在 OPPO / ColorOS 上请检查应用的“显示在其他应用上层”和通知权限；不同系统可能有额外的悬浮窗或后台限制。若相机不接受悬浮窗，请检查系统设置或手动打开相机。姿势为示意图，不识别画面、不自动分析光线；悬浮窗仅供参考，部分相机界面或系统策略可能遮挡或隐藏它。通知被拒绝时需先到系统设置中允许通知。仅提供 debug 构建，未经目标真机验证。
