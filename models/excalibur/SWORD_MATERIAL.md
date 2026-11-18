# 誓约胜利之剑材质

剑的外观以《Fate/stay night》的 Excalibur 为依据。用万代藏品的正面照核对金色护手两翼的蓝色折线、剑根的蓝金嵌纹和蓝色握柄，用 Aniplex 的斜视产品照核对边缘和侧面。银刃上的短铭文按 Hobby JAPAN 展示图中的位置、大小和笔势重新绘制。这里的符号是美术重绘，未作文字释读。[万代 TAMASHII NATIONS](https://tamashiiweb.com/item/12379/) · [Aniplex 产品展示](https://store.aniplexusa.com/fsn/excaliber-standard-edition/) · [Hobby JAPAN 零件实拍](https://hjweb.jp/article/525262/)

游戏使用一张 512 × 1024 的 RGB 静态图集。原创笔画、曲线蒙版、金属拉丝和珐琅光泽由同目录的 `paint_excalibur.py` 绘制。所有像素在制作时生成，附带的游戏资源和 Blender 工程使用绘制好的贴图。顶点决定刃面转折和外轮廓，凹槽、铭文和灰蓝色的钢面反光绘于图内。正反两面共用 UV，握柄 UV 沿周向展开。

护手的金面绘有顺着弧面过渡的宽反射，边沿带有香槟色亮线和深琥珀色回光。Blender 中，金面的金属度为 0.83、粗糙度为 0.225，深蓝珐琅槽分别为 0.20 和 0.32。两者在同一护手材质内按图集颜色取值。剑尖的贴图按模型横向位置投影，刃脊、磨面和两侧刃线在纹理中一起收束到顶点。

| 部件 | 图集矩形，左上至右下（含端点） | 方向 |
| --- | --- | --- |
| 银刃 | (8, 8) – (215, 1015) | 上为剑尖。主刃段各截面占满宽度，末段自 y 约 161 起朝图集顶部正交收尖 |
| 金色护手 | (225, 8) – (507, 166) | 正面投影，上方朝剑尖 |
| 蓝金剑根 | (230, 180) – (413, 504) | 正面投影，上方朝剑尖 |
| 蓝色握柄 | (226, 520) – (507, 842) | 上方朝护手，左右两边为周向接缝 |
| 八条辅色 | 中心 x 为 242、278、314、350、386、422、458、494，y 为 861–1015 | 按顺序为亮金、金、暗金、蓝、亮银、银、暗银、深蓝 |

在仓库根目录使用装有 Pillow 和 NumPy 的 Python 运行 `python models/excalibur/paint_excalibur.py`。默认写入 `src/main/resources/assets/starcrowndoll/textures/item/excalibur_sword_atlas.png`。也可用 `--output` 指定预览图路径。图集共 524,288 个像素，按 RGBA8 换算，基础层占 2 MiB，连同第 1 至第 4 级 mipmap 合计约 2.66 MiB。实际游戏与其他物品共用 Minecraft 图集。
