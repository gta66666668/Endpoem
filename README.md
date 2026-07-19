https://modrinth.com/mod/endpoem

# Endpoem

适用于 Fabric 的 Minecraft 终末之诗重播与自定义模组。

## 功能

- 使用 `/endpoem` 重播终末之诗，并支持目标选择器。
- 内置中文终末之诗资源包。
- 可在游戏内编辑自定义终末之诗。
- 纯文本模式会原样显示并编辑 `§` 格式代码；预览模式以游戏效果渲染，且保持只读。

## 开发环境

- Minecraft `26.1.2`
- Java `25`
- Fabric Loader `0.19.3`
- Fabric API `0.151.0+26.1.2`

Gradle 会自动发现本机可用的 Java 25；不再依赖某个固定磁盘路径。

## 构建

```powershell
.\gradlew.bat build
```

构建产物位于 `build/libs/`。

## 仓库结构

- `src/`：当前 Fabric 源码
- `gradle/`、`build.gradle`、`gradle.properties`：构建配置

## English

Endpoem is a Fabric mod for replaying and customizing Minecraft's End Poem. The current Fabric source and build configuration are kept directly at the repository root.
