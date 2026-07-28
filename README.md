# Endpoem

适用于 Fabric 的 Minecraft 终末之诗重播与自定义模组。

[Modrinth 页面](https://modrinth.com/mod/endpoem)

## 功能

- 使用 `/endpoem` 重播终末之诗，并支持目标选择器。
- 内置兼容 Minecraft 26.2 的中文终末之诗资源包。
- 可在游戏内编辑和预览自定义终末之诗。
- 可在游戏内维护自定义贡献名单，新增、修改、删除贡献者并调整播放顺序。
- 可独立显示或隐藏原版贡献名单；自定义名单可关闭，或放在诗前、指定诗段后、诗后且原版名单前，以及原版名单后。
- 可将终末之诗背景切换为原版、纯黑、深紫渐变或自定义图片。
- 可单独关闭终末之诗界面四边的原版暗角效果。
- 自定义背景支持 PNG、JPG、JPEG、BMP 和 GIF（首帧），可选择铺满裁剪、保持比例、拉伸以及按百分比四边裁切。
- 可在配置菜单中选择原版曲目或自定义终末之诗背景音乐；自定义音乐支持 OGG/Vorbis、WAV 和 MP3。
- 可在配置菜单中切换终末之诗滚动速度，并直接播放当前文本、背景和速度进行本地预览。
- 配置菜单中的命令权限等级仅对拥有修改权限的玩家显示，主菜单不会显示该选项。
- 默认使用 `K` 打开配置；旧版默认 `O` 键会自动迁移，其他自定义按键保持不变。

自定义文本位于 `config/endpoemfabric/end.txt`。自定义背景图片放在
`config/endpoemfabric/`，文件名使用 `background` 和受支持的扩展名。

自定义贡献名单位于 `config/endpoemfabric/contributors.json`，默认通过游戏内编辑器维护。
文件中的 `entries` 数组顺序就是实际播放顺序；每项包含必填的 `name` 和可选的 `role`。
编辑名单只会修改内容与顺序，不会自动启用自定义名单播放。原版名单可独立显示或隐藏；
自定义名单可关闭，或选择诗前、诗中、诗后（原版名单前）和原版名单后。
写入名单时使用原子替换；读取或校验失败时会保留原文件，并按当前设置使用可用的原版名单。
名单与其他自定义素材一样属于客户端本地配置；服务器触发播放时，每位玩家读取自己的名单。
选择“诗中”时，可从当前实际播放的诗文段落中指定插入点。配置保存的是归一化段落进度，
因此更换语言、资源包或修改诗文后，名单仍会插入到大致相同的位置。主菜单的官方贡献名单不受影响。

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
music (OGG/Vorbis, WAV, and MP3), independently toggleable vanilla credits,
and editable and reorderable custom credits. Custom credits can be disabled or
placed before the poem, after a selected paragraph, after the poem but before
vanilla credits, or after vanilla credits. Editing the custom list changes its
contents and order but does not enable playback. The mod also includes a
built-in Chinese resource pack and
permission-aware server command settings. Place custom music in
`config/endpoemfabric/music/` as `music.ogg`, `music.wav`, or `music.mp3`.
