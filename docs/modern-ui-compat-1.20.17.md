# 1.20.17 Modern UI 显示兼容

适配范围：成然与余乐物品提示、Shift固定/翻页说明、H神性面板、标题/神性/霓虹渐变、药水搜索框。原人物、边框、半透明背景、双动作与能力参数保留。

- 两个玩偶在Forge提示框Pre最高优先级接管自己的绘制，阻止Modern UI在较低优先级再画一层原版说明；其他物品继续使用Modern UI原有提示。
- 检测到Modern UI文字引擎时，整行白色文字交由其排版器处理字距、连字、字体回退和双向文本；在文字顶点阶段叠加动态颜色，避免把每帧变化的颜色写入文字布局键。取消此路径的逐像素文字裁切。
- 神性面板的动态标题与说明同样使用整行排版。搜索变化时复用原EditBox，避免重建输入控件导致光标/选择状态丢失；字体度量、GUI缩放或资源变化后清理本模组布局缓存。
- Modern UI是可选兼容，不增加强制依赖；未安装或文字引擎关闭时使用既有渲染路径。

验证环境：Minecraft1.20.1 / Forge47.4.26 / Modern UI Forge3.12.0.1，另带现有JEI15.74.0.225和IMBlocker5.6.2.1。独立实例测试两玩偶简略/展开、Shift固定与关闭、滚轮分页、H面板、搜索框中间插入中文、实际GUI倍率2→3→2、连续渐变缓存；未安装Modern UI的独立客户端回退检查通过。这里的搜索测试为程序化输入，不声称覆盖每一种输入法。

正式游戏目录只更新以神戴冠兼容版；Modern UI下载与测试副本位于工作目录，未自动添加到用户正式模组列表。

上游参考：
- https://github.com/BloCamLimb/ModernUI-MC/tree/1.20.1 （检查时commit 0bd05cea050c44f037ad92468a043eab5585ee85）
- https://github.com/BloCamLimb/ModernUI-MC/blob/1.20.1/forge/src/main/java/icyllis/modernui/mc/forge/UIManagerForge.java
- https://github.com/BloCamLimb/ModernUI-MC/blob/1.20.1/common/src/main/java/icyllis/modernui/mc/text/FormattedLayoutKey.java
- 测试发行包：https://modrinth.com/mod/modern-ui/version/6ksT8CWg
