# 荒芜之噬 GPU 兼容修复 · 1.20.47

## 根因

提供的1.20.44错误报告在加载资源时崩溃，显卡为Intel UHD Graphics，OpenGL 4.6，驱动32.0.101.7088。错误来自本模组 `shaders/core/huangwu.fsh`，不是缺少前置：

```text
ERROR: 0:18: 'float' : overloaded functions must have the same return type
```

旧片元程序声明 `float noise3(vec3 p)`，与GLSL内置的 `vec3 noise3(genType x)` 冲突。同样的参数类型不能只通过返回类型重载。之前NVIDIA实机能启动，不能证明着色器符合跨驱动规则。

依据：[Khronos GLSL规范](https://registry.khronos.org/OpenGL/specs/gl/GLSLangSpec.3.30.pdf)，内置噪声函数及函数重载章节。

## 修复

- 自定义函数改为 `scHwNoise3D`，并将同文件其余辅助函数统一加上 `scHw` 前缀，避免内置或共享函数冲突。密度场、颜色、采样次数和原有运动算法不变。
- 继续使用GLSL150，不增加显卡品牌、驱动型号或更高OpenGL版本的要求。
- 着色器注册前清理旧的GPU缓冲及深度副本；编译/注册发生异常时记录日志，释放高级路径引用，保留模组和技能逻辑。
- 无可用高级着色器时，通过Minecraft原版position-color着色器绘制基础核心及蓝白/红紫光环。遵守场景深度并恢复渲染状态。备用路径画面更简单，不等同于完整体积烟雾。
- 后续着色器注册成功时可以恢复高级路径。没有按厂商名禁用特效，也没有替换系统驱动。

## 复现与验证方式

Khronos `glslangValidator` 能直接复现旧函数返回类型冲突。修复后的顶点、片元程序按GLSL150编译并链接通过。仓库验证脚本：

```sh
python tools/check-huangwu-shaders.py --validator <glslangValidator可执行文件>
```

脚本同时断言旧的命名冲突会被标准编译器拒绝，避免依赖宽松驱动验证。

QA独立客户端使用正式混淆JAR，在真实游戏中依次检查高级着色器启动、实际落地漩涡、通过资源提供器注入编译错误后的基础特效、再次正常注册后的恢复。故障注入与控制器只在QA模组中，正式JAR不含该测试代码。

当前电脑提供NVIDIA GPU，无法替代报告中那台Intel核显的实机复测。本版针对报告里已定位且能由标准编译器复现的问题修复，不宣称覆盖所有显卡或全部第三方光影组合。

## 本次实机结果

正式1.20.47 JAR、Forge47.4.28、NVIDIA GeForce RTX 5060 Laptop GPU：正常启动、完整特效绘制、注入编译错误后的备用绘制、恢复正常注册全部通过。三个绘制阶段OpenGL错误码均为0；故障期间世界继续运行，玩家仍存活。测试在独立存档进行。

- [完整特效截图](screenshots/shader-advanced.png)
- [编译失败后的基础特效截图](screenshots/shader-fallback.png)
- [恢复后的截图](screenshots/shader-restored.png)
- [客户端验证记录](client-results.txt)
