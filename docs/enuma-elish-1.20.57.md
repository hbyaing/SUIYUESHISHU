# 天地乖离开辟之星 · 1.20.58

1.20.58：主体改成直径 7 格的着色器圆柱，最大长度 1000 格。以方块中心到射线的距离判定是否在半径 3.5 格内。服务端分段加载区块和破坏方块，远处地形可能晚于特效清除。遵循世界边界、出生点保护、Forge 破坏事件和不可破坏方块规则；不产生普通方块掉落。施法者死亡、离线或换维度时停止后续破坏。

手持乖离剑长按右键。三段剑身随蓄力加速，程序着色器在同一旋转坐标中绘制红色风压。两秒后播放吟唱音轨，剑尖金环产生红色光团。吟唱结束自动发射绯红冲流。提前松手、切换物品或离开世界会取消；潜行对空右键仍收回为王律之键。

发射参考[《绝对魔兽战线》片段](https://www.bilibili.com/video/BV1Fv411b79W/)约 44 秒的金白高亮核心和红色边浪；蓄力参考[闪恩对轰片段](https://www.bilibili.com/video/BV1B14y1d7Pz/)的红紫锐流。发射通过着色器绘制金白内芯和翻卷的红色圆柱外层。保留 ea-vfx 0.2.0 的流动图案和网络时序；原包许可随 JAR 保存在 `META-INF/ea-vfx`。

本地试玩包的三段声音取自用户指定的[「叙说原初」片段](https://www.bilibili.com/video/BV1RK4y1G7DN/)（提前惨案上传，原作为《绝对魔兽战线》[第 20 集](https://anime.fate-go.us/ep7-tv/tv/story/20.html)）。旋转声取 27–30 秒，削减中央对白后压成 2 秒循环，吟唱期间继续以较低音量播放；吟唱取 20.35–46.40 秒并提响，发射声从 46.40 秒开始截取。它们来自该片段混音，可能含背景声。

有可用的音轨时，将两段单声道 OGG Vorbis 分别放入资源包的 `assets/starcrowndoll/sounds/ea_chant.ogg`、`assets/starcrowndoll/sounds/ea_blast.ogg`。在该资源包的 `assets/starcrowndoll/sounds.json` 覆盖事件：

```json
{
  "ea_chant": {"replace":true,"sounds":[{"name":"starcrowndoll:ea_chant","stream":true}]},
  "ea_blast": {"replace":true,"sounds":["starcrowndoll:ea_blast"]}
}
```

在主机或服务端 `config/starcrowndoll-ea.toml` 把 `chantTicks` 改为吟唱音轨时长乘以 20 后取整；随包音轨用 521 tick（约 26 秒）。旧配置若仍是 120，需要改为 521。初始两秒不计入该值。在多人游戏中统一启用相同音频资源包。
