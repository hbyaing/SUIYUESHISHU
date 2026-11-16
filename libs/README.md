# 本地构建依赖

- `geckolib-forge-1.20.1-4.8.4-mesh.9.jar`：本项目模型使用的指定 GeckoLib 构建，未在 Maven 发布。客户端与服务器均需在 mods 安装此版本。
- `mclib-20.jar`：上述构建公开方法所需的数学类型，仅作为 compileOnly 编译依赖；运行时由指定 GeckoLib 提供，不嵌入以神戴冠 JAR。

构建使用 Java 17、Gradle 8.8。首次构建需要下载 ForgeGradle 和 Forge 依赖；缓存齐全后可使用 `gradlew.bat --offline build`。QA 代码需要显式添加 `-Pqa`，通过 `qaJar` 单独构建，正式 JAR 不包含 QA 类。
