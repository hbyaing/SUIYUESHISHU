# 誓约胜利之剑与阿瓦隆的特效、音效来源

三套特效素材均由 Kenney Vleugels 发布，下载档案内各自的许可证均标为 CC0 1.0。1.20.56 公开版的圣剑音轨也由这些素材混制，JAR 与实机演示里听到的是两段无人声蓄力音效。游戏从本地 JAR 加载贴图与声音，离线游玩也可以触发。金色光束、聚光、剑风与护界的形状和时序在本模组内组合。

| 素材包 | 作者页面与下载档 | 下载 ZIP 的 SHA-256 |
|---|---|---|
| Particle Pack | [Kenney 发布页](https://kenney.nl/assets/particle-pack) · [作者上传的下载档](https://opengameart.org/sites/default/files/kenney_particlePack.zip) | 163b839ba7f62f8a420da93a62beaa80013588d5d6b1eb30841e1a553635ad70 |
| Sci-Fi Sounds | [Kenney 发布页](https://kenney.nl/assets/sci-fi-sounds) · [作者上传的下载档](https://opengameart.org/sites/default/files/sci-fi_sounds.zip) | 119340f351a5098ad814f78719438c0da355a9ce8a4c8a3af6a8d48aa3d49e04 |
| RPG Sounds | [作者发布页](https://opengameart.org/content/50-rpg-sound-effects) · [作者上传的下载档](https://opengameart.org/sites/default/files/RPGsounds_Kenney.zip) | 3ae398ad63e293f9c450bda22d5d81c3af69c74df66fc1400f33c012c0bbc231 |

Particle Pack 的滤镜模板另署名 Indigo Ray、Craig Nisbet、Zoltan Erdokovy、Heliagon、ThreeDee、Killst4r、Tim2501。三个下载档所附许可原文已收录在源码和 JAR 的目录 `META-INF/licenses/excalibur`。[CC0 1.0 法律文本](https://creativecommons.org/publicdomain/zero/1.0/legalcode)

## 贴图

《Fate/stay night [Unlimited Blade Works]》第 7 话官网的 Saber 近景里，紧握的手套前能看见夜景，手套下方只有几缕灰白空气。ufotable 的 Saber 番宣短片提供了挥动与交锋的节奏参考。Good Smile 的「約束された勝利の剣」与 Parfom Saber 商品图展示从侧面绕行剑轴的长卷边，以及风带之间的空隙。[动画第 7 话与剧照](https://www.fate-sn.com/ubw/story/08.html) · [ufotable 的 Saber 番宣](https://www.youtube.com/watch?v=jyH7wWmjcpQ) · [Good Smile 1/7 Saber](https://www.goodsmile.info/en/product/2780/) · [Parfom Saber](https://www.goodsmile.info/en/product/6183/)

模组用 Kenney 的 `slash_03.png`、`trace_02.png`、`trace_06.png` 和 `flame_03.png` 制作白色风压。手持时，两股分叉的细流贴着不可见剑身往剑尖卷动，透过风带的间隙可以看到背景。两层风壳合计 288 个四边形，左右手和两种人称均沿用圣剑的模型变换。挥动到最快的一段时，额外显出两条逐渐收尖的短风痕，共 36 个四边形。两种状态共用一张每帧 128×128、共 36 帧的动画纹理和一个材质。向生成脚本传入原素材包的 PNG 目录，即可重建动画和网格。[风压资源生成脚本](../tools/fate/excalibur_wind.py)

按住聚风时，三股灰白气流围着手中实际剑轴收紧。风王铁槌释放后，压缩气流脱离剑身向准星方向前进，前缘分成三段扩开的弧线消散。聚风每帧最多 27 个四边形，风王铁槌每次最多 51 个四边形，沿用已有的世界特效图集与联机释放事件。

从 Particle Pack 的 PNG 原图提取亮度作为透明度，避免四周出现黑色方块。将选定图形缩放到 128×128，拼成 512×512 图集，运行时用顶点颜色着金、象牙白、薄荷白或灰白色。成品位于 `assets/starcrowndoll/textures/effect/excalibur.png`。

| 格号 | 原文件 | 游戏中的用途 |
|---|---|---|
| 0 | trace_07.png | 从中心裁切一条连续横截面，再旋转拉伸，用于圣剑光束、风轨和护界光环的软边 |
| 1 | trace_02.png | 旋转为横向 streak，作为光束落点的飞散光线 |
| 2 | slash_01.png | 风压近战命中 |
| 3 | star_06.png | 剑锋闪光、光束落点 |
| 4 | circle_05.png | 聚光核心与落点外晕 |
| 5 | twirl_03.png | 风王结界切换时的旋涡 |
| 6 | circle_02.png | 圣剑显形时的光圈 |
| 7 | fire_01.png | 光束前端的爆发纹理 |
| 8 | scratch_01.png | 显形圣剑近战命中 |
| 9 | magic_01.png | 阿瓦隆护界的金色纹章 |
| 10 | magic_05.png | 聚魔力的光点、飞散碎光与阿瓦隆治疗 |
| 11 | smoke_09.png | 剑身切换时的气流与真名解放的冲击纹 |
| 12–15 | 原创程序纹理 | 同一幅 128×128 的周期金丝纹横向重复四次，在举剑冲天光的闭合表面沿剑轴向上流动 |

## 真名解放的落点

演出参考了《崩坏：星穹铁道》官方 Saber 角色前瞻中的终结技动图。动图后段先出现十字闪芒与圆顶外缘，随后金色光带缠绕亮柱升起。模组使用上表的 Kenney 纹理和本地生成的网格重新制作了这段演出。[官方 Saber 角色前瞻](https://www.hoyolab.com/article/39875225) · [官方《走近星穹》Saber 演示](https://www.youtube.com/watch?v=yPxdsZ36LXc)

1.20.56 使用本机《崩坏：星穹铁道》4.7.0 的两份资料校准。游戏内联动指南 `StreamingAssets/Video/Windows/Guide/Activity_Fate_Guide_SaberShowVideo.usm` 的第 150–256 帧展示了剑面闪芒、上宽下尖的金色光焰、横向斩光和命中后的大金环。这段画面以 30 帧/秒播放。设计数据 `Persistent/DesignData/Windows/73b3d10f574211fe5df50448d5b2ecb7.bytes` 内的 `Skill03` 将武器、命中、预爆、主爆和环境亮光分别挂在不同节点。首个命中特效、预爆、主爆和最后的镜头闪光依次位于原动作的 62.85%、63.55%、67.99% 和 69.71%。Forge 客户端按原顺序触发各层，用同一套模组图集重绘形状，视角由玩家控制。

显形圣剑举起时，脚下七格半径内最多有 48 枚光点，使用剑身的同一格 `magic_05.png`、金色与象牙白。圆盘按面积均匀取样，光点从实际地面错落上升，玩家移动时，已出现的光点留在原地，后续光点在新位置周围生成。第一、第三人称都能看到这个光场。

第一段蓄力声对应准备循环。第 103 个蓄力刻（约 5.15 秒）开始举剑。第三人称从举高的剑尖沿世界竖直轴喷出最高 26 格的光柱，第一人称从剑刃上半段开始膨出。主光柱与两道外溢金焰都有封闭截面，保持到两段音效之间的停顿结束。第二段开始后，剑面点亮，护手闪出四角星，扇形光焰张开后短暂收暗，再次聚成白金色剑刃。客户端把第一次命中特效对齐服务端的第 232 个蓄力刻。

剑光沿准星掠出，弧形金刃、白芯和短暂的青白刃缘形成一次斩击。服务端同步碰撞面和光束终点，客户端先在该处展开薄光罩，再点燃金白光柱。细金带绕柱上升，宽扁金环在柱身上方展开，锥形碎光从落点向外飞散。墙面的冲击纹贴墙展开，光柱向墙外倾斜。空中终点使用放射状光环和碎光。方块落点最多提交 93 个四边形，空中落点最多 28 个，均复用光束的图集与绘制批次。沿途受到伤害的目标会分别出现命中闪光，每次释放至多显示八处。

## 声音

下表中的音效均为 44.1 kHz 单声道 Ogg Vorbis，可依游戏内位置进行声像定位。混音对采样作了剪辑、变调、分频、增益包络和部分回声处理。文件位于 `assets/starcrowndoll/sounds/excalibur`。音效事件、字幕键信息见同一资源包的 `sounds.json`。

| 成品文件 | Sci-Fi Sounds 采样 | RPG Sounds 采样 | 用途 |
|---|---|---|---|
| veil.ogg | thrusterFire_001, forceField_002 | — | 风王结界隐匿 |
| reveal.ogg | forceField_001 | drawKnife2 | 解除结界 |
| wind_charge.ogg | thrusterFire_000 | cloth2 | 风压起势 |
| charge.ogg | forceField_004, spaceEngineLow_001 | — | 魔力聚集起势 |
| wind.ogg | thrusterFire_003, lowFrequency_explosion_001 | knifeSlice | 风王铁槌冲击 |
| beam.ogg | laserLarge_001, lowFrequency_explosion_000, thrusterFire_004, forceField_004 | — | 从剑前释放的金色光束 |
| impact.ogg | explosionCrunch_002, lowFrequency_explosion_000 | — | 光束末端的轰鸣 |
| slash.ogg | impactMetal_002 | knifeSlice2 | 命中与弹开投射物 |
| sanctuary.ogg | forceField_000, forceField_004, spaceEngineLow_002 | — | 阿瓦隆展开 |
| heal.ogg | forceField_003 | handleCoins2 | 治疗生效时的短提示音 |

## 公开版的双段蓄力声

公开版的 [`chant.ogg`](../src/main/resources/assets/starcrowndoll/sounds/excalibur/chant.ogg) 由同目录的 `charge.ogg`、`sanctuary.ogg` 与 `reveal.ogg` 混制。第一段从低沉的蓄力声上升，举剑时叠入护界的高音。5.77 至 7.77 秒之间静默两秒，随后蓄势声再次响起，11.60 秒下劈发射时可听见金属亮音。导出格式为 48 kHz 单声道 Ogg Vorbis，全长 583,369 个采样帧（12.15352 秒）。JAR 的音频署名随附在 `META-INF/licenses/excalibur/Excalibur-Audio.txt`。

在仓库根目录运行下列命令可重建公开音轨。脚本读取已随源码提供的三项空间音效。[双段音效生成脚本](../tools/fate/excalibur_public_audio.py)

```powershell
python tools/fate/excalibur_public_audio.py
```

## 个人日语资源包

若自行备有获准个人使用的两份 WAV，可以在本地生成用于 Minecraft 1.20.1 的日语资源包。录音来自《崩坏：星穹铁道》与《Fate/stay night [Unlimited Blade Works]》联动中 Saber 的两条角色档案语音，配音为川澄绫子（川澄綾子）。文件编号用于核对输入，录音及其使用权归相应权利方。公开仓库、JAR 和演示视频均使用上节的 Kenney 音效。[联动官方声优信息](https://www.hoyolab.com/article/39914593)

| 顺序 | 档案事件 | WEM ID | 已核对的 4.7.0 来源 | 单声道 PCM 帧数 |
|---|---|---|---|---:|
| 1 | 选择终结技 | `432944184` | `VoBanks7.pck` | 282,853 |
| 2 | 普通速度释放终结技 | `877602325` | `VoBanks5.pck` | 216,649 |

1. 准备两段 48 kHz、16 位单声道 PCM WAV，并安装 Python 3 与支持 libvorbis 的 FFmpeg。
2. 在仓库根目录运行下面的命令，将输入路径替换为自己的 WAV。脚本会核对音频格式、帧数和 PCM 的 SHA-256，然后写出 ZIP。[本地资源包生成脚本](../tools/fate/excalibur_chant.py)

   ```powershell
   python tools/fate/excalibur_chant.py 432944184.wav 877602325.wav --resource-pack Saber-JP.zip
   ```

3. 在 Minecraft 客户端进入「选项 → 资源包 → 打开资源包文件夹」，把 `Saber-JP.zip` 复制进去，在资源包列表中启用。若其他资源包也替换了圣剑音轨，将本包移至其上方。停用本包便恢复公开版音效。

个人资源包保持原语速，将第二份 WAV 放在第 366,720 帧，首段结束与呼名起声之间恰有两秒。两种音轨都使用同一个声音事件，光束均在蓄力开始后 11.60 秒发射。
