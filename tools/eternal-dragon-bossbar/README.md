# 永恒恶龙血条素材

`assets/dragon_head_64.png` 是定稿的 64×64 透明龙头，保持原模型侧面朝向；`assets/left_wing.png` 是左翼像素源图，右翼水平镜像生成。

运行 `python tools/eternal-dragon-bossbar/export_textures.py`（需要 Pillow）重新生成 `src/main/resources/assets/starcrowndoll/textures/gui/eternal_dragon/` 下的三张 960×240 PNG：

- `frame.png`：边框和空血槽。
- `fill.png`：完整红色生命层，范围 x=92、y=124、宽 776、高 20，由实时生命比例裁切。
- `decoration.png`：龙头与双翼，独立置于生命层上方。

名字由 Minecraft 字体绘制，不烘焙在图片里。定稿设计预览见 `docs/eternal-dragon-1.20.42/approved-design.png`；实机效果见同目录的 `bossbar-*.png`。
