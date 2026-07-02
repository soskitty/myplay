# MyPlay

给小朋友播放本地有声读物的 Android 应用，目标设备包含 MIUI 14。

## 功能

- 添加专辑：通过系统文件夹选择器选择手机上的音频目录。
- 专辑命名：默认使用文件夹名称，可在列表中点“改名”重命名。
- 播放进度：每个专辑分别保存当前集数和音频中间暂停位置。
- 文件排序：只按文件名排序，不读取 ID3 标签。
- 自然排序：支持 `第一集`、`第1集`、`第01集`、`第001集`、`1`、`01`、`001` 等常见命名。
- 音频格式：支持系统播放器可播放的常见格式，包括 mp3、m4a、aac、wav、flac、ogg、opus。

## 使用

1. 点击“添加专辑（选择文件夹）”。
2. 选择存放音频的一整个文件夹。
3. 点击专辑开始加载，点击“播放”。
4. 长按专辑可从 MyPlay 列表移除，不会删除手机里的音频文件。

## 构建

当前目录未包含 Gradle wrapper，且本机没有全局 `gradle` 命令。可用 Android Studio 打开 `MyPlay` 目录后构建，或在安装 Gradle 后执行：

```powershell
gradle assembleDebug
```

生成的 APK 通常位于：

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub 在线编译

已包含 GitHub Actions 配置：`.github/workflows/android-build.yml`。

推送到 GitHub 后，进入仓库的 `Actions` 页面，运行 `Android Build`，构建完成后在 workflow 的 `Artifacts` 下载 `MyPlay-debug-apk`。
