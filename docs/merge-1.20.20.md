# 1.20.20：合并19版与紧凑排版

以提供的1.20.19成品JAR（SHA256 50683ec43d08aa2caa1baccb54c2ff85c79e068a0a157c6e37d2fb8c316fbc96）核对并恢复未上传的客户端源码及资源，再合入本机1.20.17的阅读优化。

保留19版：Modern UI文字引擎检测、整行字形排版及顶点渐变着色、字体缓存刷新；神性面板搜索框复用与焦点保持；余乐新简介“美七德兮天地清”；新鱼贴图、独立鱼图层和手部遮挡；世界玩偶抱鱼姿态。恢复后的DollRenderer正式字节码指令与19版逐条相同（忽略指令地址和常量池编号）。

合入17版：两个玩偶统一11px行距，分页共用该高度，保留原字号；成然同功能说明合段、去掉多余空行，功能标题独立色组和粗体；余乐去掉说明后的空行，保留美德名称粗体。两个字体引擎均保留这些层次；使用19版更饱和的流动渐变，保留17版各玩偶配色。

版本合并不改神性、七美德、伤害防护、音乐、网络协议等玩法。Modern UI为可选兼容，不内置或强制依赖其JAR。19版仅有成品，新增类由其字节码恢复，既有类针对确定的差异进行合并，无法恢复原作者注释与提交历史。

## 验收

Forge47.4.26 / Java17 独立正式客户端，两次测试分别不安装Modern UI、安装ModernUI-Forge-1.20.1-3.12.0.1。验证字体引擎实际状态分别为false与true，两玩偶简略/展开、Shift锁定及关闭、固定状态滚轮翻页、动态插图均通过，截图已查看。

PASS Chengran + Yule compact/expanded, Shift pin and pagination, fish pose cycle; modernui=false

PASS Chengran + Yule compact/expanded, Shift pin and pagination, fish pose cycle; modernui=true

常规构建与reobfJar通过，正式JAR与两个实测包条目内容完全一致，不包含QA附加包或Modern UI代码。与19版相比，玩法核心类字节内容保持一致，恢复资源逐字节一致。没有新增19版玩法全量回归测试，本次重点是客户端合并验证。
