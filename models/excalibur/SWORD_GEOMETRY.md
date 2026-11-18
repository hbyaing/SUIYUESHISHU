# 誓约胜利之剑的 Blender 工程

`Excalibur_and_Avalon.blend` 收有六个可独立编辑的圣剑网格。剑脊、刃肩、翼形护手、两侧剑根金叉、握柄和前后柄环均有真实截面。蓝色镶饰、金边、刻铭和材质明暗共用一张内嵌的 512 × 1024 不透明图集。

## 参考与校准

- Aniplex 的 1∶1 PROPLICA 正面持剑照片用于描出两道护手圆弧、外端斜切线和剑根长短叉。剑面朝观察者、剑尖朝下时，照片里的护手约从横坐标 266 延伸到 457。模型局部 X 轴横跨护手，Z 轴从柄首指向剑尖。[Aniplex 实物图集](https://store.aniplexusa.com/fsn/excaliber-standard-edition/)
- 同一授权道具的侧放全长照片用于校准握柄收腰、剑身外轮廓与最后一段收尖。模型范围为 0 到 626 个局部建模单位，护手中心靠柄一侧在约 121 处、剑根尖端在 226 处。每面的六条纵向刃面在末端共用一个顶点。[Aniplex 日本商品页](https://online.aniplex.co.jp/itemRtMFLDDe.html)
- Good Smile 的《Fate/stay night》Saber 后视斜角照片可复核护手往剑身方向前掠及金边相对蓝面的高度。图中的双手遮住了握柄中段。握柄背面和剑脊厚度依正面、斜面与阿瓦隆现有内腔共同推定。[Good Smile 双手持剑照片](https://www.goodsmile.info/en/product/2780/Saber%2BTriumphant%2BExcalibur.html)

模型以原游戏握点对齐。护手最大横向跨度约 145 个局部单位。Blender 对剑身、蓝金剑根与阿瓦隆内腔分别作了完整网格相交检查，合鞘位置均为零个相交面。

剑尖最后 74 个局部单位按同一条连续曲线同步减薄、收窄。沿着每条纵向刃面，环段接头共享相同的切向法线。Forge OBJ 保留分面之间各自的角点法线。剑尖与图集中的收尖色面通过正交投影配准。护手的蓝珐琅和抛光金面使用各自的金属度、粗糙度，游戏中的金面反光绘在同一张图集上。

## 重建和导出

在安装 Blender 的终端内进入本目录，运行以下命令。工程会重建圣剑、保存可编辑模型，并导出明剑、合鞘 OBJ 和共用 MTL。打开本文件夹内的 .blend 即可直接编辑。

```shell
blender -b Excalibur_and_Avalon.blend -P rebuild_excalibur.py -- --publish
```

使用 `--draft "本地预览.blend"` 可以保存独立的模型检查稿。单独重画图集时可运行 `paint_excalibur.py`。成品明剑具有 981 个顶点与 1068 个 Forge 烘焙面。空鞘具有 376 个面，含剑柄的合鞘状态具有 1128 个面。
