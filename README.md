# Endpoem

适用于 Fabric 的 Minecraft 终末之诗重播与自定义模组。

[Modrinth 页面](https://modrinth.com/mod/endpoem)

## 功能

- 使用 `/endpoem` 重播终末之诗，并支持目标选择器。
- 内置兼容 Minecraft 26.2 的中文终末之诗资源包。
- 可在游戏内编辑和预览自定义终末之诗。
- 可将终末之诗背景切换为原版、纯黑、深紫渐变或自定义 PNG 图片。
- 自定义背景支持铺满裁剪、保持比例和拉伸。
- 配置菜单中的命令权限等级仅对拥有修改权限的玩家显示，主菜单不会显示该选项。
- 默认使用 `K` 打开配置；旧版默认 `O` 键会自动迁移，其他自定义按键保持不变。

自定义文本位于 `config/endpoemfabric/end.txt`，自定义背景图片位于
`config/endpoemfabric/background.png`。

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
Poem. It includes an in-game text editor, configurable backgrounds, a built-in
Chinese resource pack, and permission-aware server command settings.
