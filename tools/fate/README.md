# Fate 物品与解析特效资源

运行版本与玩法见 [接入说明](../../docs/fate-key-ea-1.20.51.md)。游戏直接读取 `src/main/resources` 中已导出的资源，不需要安装 Blender 或 Python。

## 资源检查与重新导出

在仓库根目录执行 `python tools/fate/validate_assets.py`，检查两件物品的三角流、单位法线、UV、贴图引用和解析路径。

若持有本次原始文件，可用 Blender 4.5 后台重新导出：

```text
blender -b --python tools/fate/export_assets.py -- --sources <本次 fate-msh 目录>
```

该目录须包含 `outputs/王律之键_Bab_ilu.blend`、已确认的三面解析工程，以及 `work/ea_source/fbx/sword-of-rupture-enuma-elish-ea` 下用户提供的 FBX 和纹理。脚本只改导出会话，不覆盖原工程。运行时将钥匙网格缩放为导出坐标的 0.4 倍，适配手握尺寸；GUI 单独补偿比例。

## 独立 GPU 预览

`gpu_preview.py --output <目录>` 需要 Python 的 `moderngl`、`numpy`、`Pillow`。它通过本机 OpenGL 离屏编译并绘制生产 GLSL，生成解析、金色亮面和碎屏图像。预览的棋盘背景只用于辨认碎片之间的空隙，不是游戏内场景；它不能代替 Forge 实机验收。

## 隔离游戏验收

Java 17、Gradle 8.8，现有依赖缓存完整时：

```text
gradle --offline --console plain -Pqa -PqaGameTestNamespace=fateqa -PqaGameDir=<独立测试目录> runGameTestServer
```

可选视觉验收使用 `-Pqa -PfateVisual -PfateGameDir=<独立客户端目录> runClient`。先准备名为 `FateQA` 的测试存档及 `enable-fate-qa` 标记；客户端必须关闭 Forge 提前窗口（该目录 `config/fml.toml` 的 `earlyWindowProvider=""`）。QA Mixin 从创建时隐藏窗口，不切换前台。验收会修改测试世界、背包、时间和相机；应始终使用独立目录。输出 `fate-visual-results.txt` 与截图，覆盖主手第一人称、真实资源重载、副手第三人称。QA 源码只在显式 `-Pqa` 时加入独立模组，不进入正式 JAR。
