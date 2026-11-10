# OP调用路径研究与通用防御 1.20.22

本次研究依据用户提供的0.7.10文件与两个公开的Forge 1.20系列项目，只归纳调用模式，不把样本视为整个市场，也不复制第三方防御/攻击实现。

## 输入与提炼结果

| 输入 | 核对结果 |
|---|---|
| ArcaneVortex [Alpha]-0.7.10-1.20.1-Forge.jar | SHA256 `92bb03ac9bddcd07f8e34d1bb39a3689fc930c1ad04326f3f4466f0e9743d79a`；2062个class，无Java源文件 |
| ArcaneVortex-0.7.10-着色器与特效资源包.zip | SHA256 `c10eb21d19387c25153e0772f4b8372b62bc15ecb8181cf31c7969735ad10aa5`；355个Java文件，偏重driver-java、渲染和特效，不是完整玩法源码 |

JAR中没有普通的mixins数组配置。静态反编译显示其使用自定义NativeMixin/ASM机制，相关命名下提炼58个类、286处NativeMixin/ModifyReturn/表达式或WrapOperation注解声明。原生代理、方法名扫描和运行时变换与Sponge Mixin的能力/生命周期不同，不能直接复制注解到本项目。

本项目保存注解声明索引与文件清单用于追溯；不提交整份反编译源码，不加载该JAR执行原生代理。本次也没有将其资源包安装进游戏。

| 类别 | 样本中对应位置 | 可迁移的设计 |
|---|---|---|
| 伤害/生命 | ProtectDamageMixin、ProtectHealthMixin、HealthMixin | 标准hurt、内部扣血、同步生命写入分别设防，显示血量不等同于内部数据安全 |
| 死亡/战利品 | ProtectDeathMixin、EntityDeath、ForceDie | 防死亡调用之外还处理dead标志、死亡计时、姿态和直接掉落，避免血量没掉却先丢背包 |
| 移除/更新 | ProtectRemovalMixin、ProtectLifecycleMixin | 保留退出/维度迁移；分离恶意移除、正常分区移动、对象生命周期与停tick |
| 装备/属性 | ProtectDisarmMixin、ProtectAttributeMixin | 跟踪来源及归属，临时守护与永久成长分开，不劫持全游戏的所有Map/字段 |
| 客户端 | ProtectClientMixin、ProtectionSync、渲染类 | 同步与表现分开；客户端显示并不构成服务端生存保证 |

## 公开样本与通用入口

没有一个被所有OP模组共同采用的“OP接口”。实际共用的是Minecraft原生方法、Forge事件/钩子、同步数据和实体容器；自定义血池、原生代理各自实现。

- [Re-Avaritia InfinitySwordItem，锁定提交ad0bccd](https://github.com/Nova-Committee/Re-Avaritia/blob/ad0bccdf4b6fb6a7aca8e14781824a8a390c136c/src/main/java/committee/nova/mods/avaritia/common/item/tools/infinity/InfinitySwordItem.java)：攻击入口可直接写生命；其自定义死亡流程会访问Forge死亡钩子、设置死亡状态并调用战利品方法。因此只拦截普通伤害事件或die方法不足以覆盖这类路径。
- [Draconic Evolution ModularArmorEventHandler，1.20提交4e7fc31](https://github.com/Draconic-Inc/Draconic-Evolution/blob/4e7fc310f025a255b685c9e9754d2369f8d7b561/src/main/java/com/brandon3055/draconicevolution/handlers/ModularArmorEventHandler.java)：护盾/保命在LivingAttack、LivingDamage与LivingDeath等阶段处理。可学习的是多阶段校验和模块化分层，并非直接依赖其护盾私有实现。

这些是源码研究结果，不代表本次已安装或实测上述模组。

## 1.20.22实现

在1.20.21原生hurt/actuallyHurt/死亡计时等防御上补充：

1. 同步生命的普通set、批量assignValue和直接DataItem.setValue都检查。仅为服务端玩家的生命数据项绑定弱引用归属，无全实体Map扫描；其他同步字段照常写入，取下立即释放。
2. Mixin拦截LivingEntity.dropAllDeathLoot、Player.dropEquipment与死亡姿态，防止自定义死亡流程绕开die先丢装备。服务端看门狗修复原生dead标志、死亡时间和停tick字段；原生字段桥只解析LivingEntity自身的开发/SRG字段名一次，不扫描第三方类。
3. 在共享ForgeHooks.onLivingDeath/onPlayerAttackTarget设边界，避免按每把武器的实现类逐个注入。维持权柄没收的实际方向和原始NBT。
4. 实体EntitySection、EntityLookup、EntityTickList移除入口转为必需注入；原生remove与setRemoved分别验证。正常分区移动沿用生命周期豁免，不能用无条件重加玩家或伪造isRemoved=false来掩盖掉线。
5. 拒绝对受保护玩家调用canUpdate(false)，并修复原生停tick状态。摘下后正常开关仍有效。
6. 移除旧ArcaneGuard0～23、ArcaneWeaponGuard、对应Mixin配置，以及按秘法私有Map类名清理的代码，包括余乐维护中的该专项调用。不新增任何秘法版本、包名或武器名判断。余乐既有通用机制保留；未宣称移除专项后可覆盖第三方全部特例。
7. 提供可选合作接口 `dev.starcrown.api.DollDefense`，供其他代码主动查询保护/拒绝伤害、死亡、移除。它是本项目公开接口，不是行业标准，也不能强制所有第三方遵守。

已有Goety AI专项控制属于旧领域功能，本轮不扩展该逻辑；新的防御链只面向原生/Forge类型。

## 验收与边界

源码签名检查使用包含Forge补丁的映射JAR，100组必需注入均需命中；纯原版SRG JAR没有Forge添加的canUpdate方法，不能拿它单独验证Forge扩展。正式运行验收使用重混淆发布包与独立Forge47.4.26实例。

测试仅以原生/Forge接口构造对抗，不调用秘法、无尽或龙研的私有类。测试范围包括直接/批量同步生命、非有限值、原始死亡标志、装备掉落、Forge攻击/死亡、实体索引、移动和取下反向对照。测试结果另附。

### 已完成验证

- build、reobfJar、qaJar成功，包含Forge补丁的映射JAR中100组必需方法描述符命中。
- 独立Forge47.4.26 / Java17、正式重混淆JAR，在隔离世界的真实在线玩家上执行GenericDefenseTests及1.20.21原生防御回归，ALL_PASS。
- 覆盖DataItem直接0/NaN写入、批量生命写入、无关吸收字段正常修改、原始dead/停tick字段修复、直接死亡掉落/装备释放、Forge死亡与攻击钩子、分区/索引/tick列表移除、实际跨分区移动及取下后反向控制。
- 发布JAR无ArcaneGuard/ArcaneWeaponGuard、DivineCombat$ArcaneMaps与QA类，主源码不再引用秘法包名。其他未经测试的第三方运行时不在此结论内。
- 曾发现protected字段不能用Shadow别名绑定的问题，改用一次解析的原生字段桥，并通过正式重映射运行复验。自动验收直接从服务端在线玩家启动，避免依赖客户端就绪回调。

### 可选合作接口

以下是本项目为其他实现提供的接口，不需要硬依赖某个第三方模组：

```java
DollDefense.isProtected(entity);
if (DollDefense.rejectDamage(target, source, amount)) return;
if (DollDefense.rejectDeath(target)) return;
if (DollDefense.rejectRemoval(entity, reason)) return;
```

这些查询/拒绝入口用于服务端；死亡和移除遵守本模组实际佩戴、防拆及生命周期规则。不要通过API长期缓存“受保护”布尔值，正常取下后应重新查询。

无法保证抵挡任意JVM/native代理重写后的返回值、Unsafe内存覆写、任意数据结构破坏、断开网络连接或阻止服务器进程终止。没有把这些边界通过“针对某模组的私有Map清除”伪装成通用防御。未进行0.7.10完整联机/原生代理对抗实测；本次不作TPS/FPS提升保证。
