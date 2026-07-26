# Endpoem

适用于 Fabric 的 Minecraft 终末之诗重播与自定义模组。

[Modrinth 页面](https://modrinth.com/mod/endpoem)

## 功能

- 使用 `/endpoem` 重播终末之诗，并支持目标选择器。
- 内置兼容 Minecraft 26.2 的中文终末之诗资源包。
- 可在游戏内编辑和预览自定义终末之诗。
- 可将终末之诗背景切换为原版、纯黑、深紫渐变或自定义图片。
- 可单独关闭终末之诗界面四边的原版暗角效果。
- 自定义背景支持 PNG、JPG、JPEG、BMP 和 GIF（首帧），可选择铺满裁剪、保持比例、拉伸以及按百分比四边裁切。
- 可在配置菜单中选择原版曲目或自定义终末之诗背景音乐；自定义音乐支持 OGG/Vorbis、WAV 和 MP3。
- 可在配置菜单中切换终末之诗滚动速度，并直接播放当前文本、背景和速度进行本地预览。
- 配置菜单中的命令权限等级仅对拥有修改权限的玩家显示，主菜单不会显示该选项。
- 默认使用 `K` 打开配置；旧版默认 `O` 键会自动迁移，其他自定义按键保持不变。

自定义文本位于 `config/endpoemfabric/end.txt`。自定义背景图片放在
`config/endpoemfabric/`，文件名使用 `background` 和受支持的扩展名。

自定义音乐位于 `config/endpoemfabric/music/`。在“背景音乐”中选择“自定义文件”后，
将单个音乐文件命名为 `music.ogg`（Vorbis）、`music.wav` 或 `music.mp3` 放入该目录；
点击“刷新”可立即更新文件状态；每次打开终末之诗也会重新读取文件，播放结束后会自动循环。

## 开发环境

- Minecraft `26.2`
- Java `25`
- Fabric Loader `0.19.3`
- Fabric API `0.155.2+26.2`
- Mod Menu `20.0.1`

## 构建

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## English

Endpoem is a Fabric mod for Minecraft 26.2 that replays and customizes the End
Poem. It includes an in-game text editor, configurable backgrounds and scroll
speed, local playback previews, configurable vanilla or custom background
music (OGG/Vorbis, WAV, and MP3), a built-in Chinese resource pack, and
permission-aware server command settings. Place custom music in
`config/endpoemfabric/music/` as `music.ogg`, `music.wav`, or `music.mp3`.
