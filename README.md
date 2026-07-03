# MyPlay

> Local audiobook player for kids. Lightweight, no ID3 tags, natural filename sorting.  
> 给小朋友听有声读物的 Android 播放器。轻量、不读 ID3、文件名自然排序。

---

## Features / 功能

- **Album management / 专辑管理** — pick any folder via system picker, auto-named by folder name, long-press to remove  
  通过系统选择器添加文件夹作为专辑，自动以文件夹名命名，长按移除
- **Per-album progress / 独立进度** — each album remembers which track and exact position (milliseconds)  
  每个专辑分别记住当前集数和毫秒级暂停位置
- **Natural sorting / 自然排序** — sorts by filename only (no ID3 tags), handles `第一集`, `第01集`, `1`, `001` etc.  
  仅按文件名排序，不读 ID3；支持 `第一集`、`第01集`、`1`、`001` 等中式命名
- **Track list / 选集** — tap current track to open list and jump to any episode  
  点击当前曲目弹出列表，任选一集跳转
- **Drag to reorder / 拖动排序** — long-press and drag albums to rearrange  
  长按专辑拖动调整顺序
- **Supported formats / 支持格式** — mp3, m4a, aac, wav, flac, ogg, opus
- **Pause / play colors** — orange for play, light orange for pause, no shadow  
  播放橙色、暂停浅橙、无阴影

## Screenshots / 截图

![icon](icon.png)

## Download / 下载

[Download latest APK (v1.0)](https://github.com/soskitty/myplay/releases/latest) — or grab it from Actions artifacts.

## Build / 构建

```bash
git clone https://github.com/soskitty/myplay.git
cd myplay
gradle wrapper --gradle-version=8.5
./gradlew assembleDebug
```

APK at `app/build/outputs/apk/debug/MyPlay.apk`.

Or open with Android Studio and build.

## License

MIT
