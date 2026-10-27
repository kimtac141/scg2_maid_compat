# 实施清单：让 YSM 模型识别 Scorched Guns 2 枪械并播放对应动画

> **给执行者的说明**：本清单是自包含的。你没有对话上下文，所有需要的事实都写在下面。
> 但**第 0 节的前置核对必须先做完**，本清单里 YSM 的类名/方法名来自特定版本，不能直接照抄到别的版本。

---

## 0. 前置核对（不要跳过）

### 0.1 确认目标版本，并据此重新核对 YSM 的类名

本清单中所有 YSM 事实来自：

```
仓库: https://github.com/YesSteveModel/YesSteveModel   （Apache-2.0）
分支: dev/1.20          （唯一分支，Forge / Minecraft 1.20.1）
```

**如果本次目标不是 1.20.1 Forge，或者目标机器的 YSM 是第三方分支（OpenYSM / ModernYSM / 未混淆变体），
必须对目标版本重新核对第 3 节的类名与方法签名，不能假设一致。**

核对方法（在目标版本的 YSM 源码或反编译产物里执行）：

```bash
# 1. 确认这些类是否存在
grep -r "class TACZCompat" .
grep -r "playGunMainAnimation" .
grep -r "class YsmMaidMainPredicate" .
grep -r "class GunFirePredicate" .

# 2. 确认方法签名（注意返回类型与参数）
#    playGunMainAnimation(LivingEntity, AnimationEvent<? extends CustomHumanoidEntity<?>>, String, LoopType)
# 3. 确认 YSM 自己的 geckolib3 包路径（不是 TLM 的，也不是上游 GeckoLib 的）
grep -r "com.elfmcys.ysm.geckolib3" . | head
```

### 0.2 确认依赖矩阵

需要明确本次要兼容的组合：

| 装了 TACZ？ | 装了卓越前线？ | 装了 YSM？ | 装了 TLM？ | 需要覆盖的场景 |
|---|---|---|---|---|
| ? | ? | 必须 | ? | 玩家手持 SC2 枪 / TLM 女仆手持 SC2 枪 |

**注意**：YSM 的枪械动画链路在**没装 TACZ 和卓越前线时不会挂载**（见 §3.4）。
如果要求「玩家只装 YSM + SC2 也能有枪械动画」，那么 §4 的门控处理是**必须项**，不是可选项。

---

## 1. 项目现状

模组：`scg2_maid_compat`（CurseForge 名 `Scorched Guns: Maid Compat`，中文 `[SG2_TLM]赤焦枪械：女仆兼容`）

- 包名：`com.scg2tlm.elmomod`
- 作用：让**车万女仆（Touhou Little Maid, TLM）**的女仆能用 **Scorched Guns 2 (SC2)** 的枪械
- **TLM 侧的 D 全部已完成**（这是本次工作的参照实现，见 §5.2）

已有的关键类：

| 类 | 作用 |
|---|---|
| `compat.SC2GunCompat` | SC2 枪械状态读取与操作的统一入口（见 §5.1） |
| `compat.LittleMaidCompat` | `implements ILittleMaid`，向 TLM 注册工作模式 |
| `compat.task.TaskSC2GunAttack` / `TaskSC2GrenadeAttack` | `implements IRangedAttackTask`，独立实现的工作模式 |
| `compat.task.MaidSC2GunShootTask` / `MaidSC2GunWalkToTarget` / `SC2GunStrafingTask` / `MaidSC2GrenadeThrowTask` | 自己的 AI 行为 |
| `mixin.TacCompatMixin` | **注入 TLM 的 `TacCompat`，让 SC2 枪在 TLM 眼里冒充 TACZ 枪** |
| `mixin.AnimationManagerMixin` | 注入 TLM 的 `AnimationManager`，驱动 `tac:*` 动画 |
| `mixin.ProjectileEntityMixin` / `LightningProjectileEntityMixin` / `WaraxeItemMixin` / `GunAttackGoalMixin` | 注入 scguns，修 4 个问题 |
| `mixin.EntityMaidTaskDataInvoker` / `MaidAttackStrafingTaskMixin` / `MaidPickupEntitiesTaskMixin` | 注入 TLM 的其他处理 |

---

## 2. 本次目标

让 **YSM 模型**在手持 SC2 枪械时，播放**该模型自带的 `tac:*` 动画**。

覆盖两种宿主：

1. 玩家穿戴 YSM 模型（第一/第三人称）
2. **TLM 女仆穿戴 YSM 模型**（YSM 自带 TLM 兼容，见 §3.3）

**范围外**（明确不做）：
- 不支持 YSM 本体的加密 `.ysm` 模型格式转换（那是另一个项目的工作量）
- 不修改任何模型资产
- 不去 TACZ 侧注册任何东西

---

## 3. 已核实的 YSM 代码事实（dev/1.20）

### 3.1 核心兼容类

```
com/elfmcys/ysm/client/compat/tacz/TACZCompat.java        ← 外层，可安全引用
com/elfmcys/ysm/client/compat/tacz/TacCompatInner.java    ← 内层，直接引用 com.tacz.guns.* 的类
com/elfmcys/ysm/client/compat/tacz/TacCtrlBinding.java    ← 把 TACZ 状态暴露成 Molang 变量
com/elfmcys/ysm/client/compat/swarfare/SWarfareCompat.java ← 卓越前线的同构实现（可作第二参照）
```

`TACZCompat` 的公开方法（**本次要注入的就是这些**）：

```java
public static boolean isInstalled();
public static void addBinding(CtrlBinding ctrlBinding);

@Nullable public static PlayState playGunMainAnimation(LivingEntity livingEntity,
        AnimationEvent<? extends CustomHumanoidEntity<?>> event, String animationName, LoopType loopType);
@Nullable public static PlayState playGunHoldAnimation(ItemStack mainHandItem,
        AnimationEvent<? extends CustomHumanoidEntity<?>> event);
@Nullable public static PlayState playGunOnceAnimation(ItemStack mainHandItem,
        AnimationEvent<? extends CustomHumanoidEntity<?>> event);

@Nullable public static ResourceLocation getGunId(ItemStack stack);
public static void renderOffsetHand(ItemStack offhandItem, AnimatedGeoModel geoModel,
        LivingEntity livingEntity, PoseStack poseStack, int packedLight, float partialTicks);
public static void openFlashShellRender(LivingEntity livingEntity, ItemStack mainHandItem);
public static void stopFlashShellRender(ItemStack mainHandItem);
```

**实现模式（关键）**：所有 `playGun*` 都是「先判 `isInstalled()`，再判 `TacCompatInner.isGun(item)`，
成立才委托给内层，否则 `return null`」：

```java
@Nullable
public static PlayState playGunMainAnimation(LivingEntity livingEntity,
        AnimationEvent<? extends CustomHumanoidEntity<?>> event, String animationName, LoopType loopType) {
    if (isInstalled() && TacCompatInner.isGun(livingEntity.getMainHandItem())) {
        return TacCompatInner.playGunMainAnimation(event, animationName, loopType);
    }
    return null;
}
```

**返回 `null` 的语义 = 「我不处理，交给调用者走兜底」**。这一点决定了注入方式（见 §4）。

### 3.2 调用链（玩家侧）

```java
// com/elfmcys/ysm/client/animation/predicate/PlayerMainPredicate.java
public PlayState test(AnimationEvent<CustomPlayerEntity> event, ExpressionEvaluator<?> evaluator) {
    ...
    String animationName = state.getAnimationName();          // 基础动画名：idle / walk / ...
    LoopType loopType = state.getLoopType();

    PlayState slashBladeAnimation = SlashBladeCompat.playMainAnimation(player, event, animationName, loopType);
    if (slashBladeAnimation != null) { return slashBladeAnimation; }

    PlayState gunMainAnimation = TACZCompat.playGunMainAnimation(player, event, animationName, loopType);
    if (gunMainAnimation == null) {
        gunMainAnimation = SWarfareCompat.playGunMainAnimation(player, event, animationName, loopType);
    }
    return Objects.requireNonNullElseGet(gunMainAnimation,
            () -> playAnimation(event, animationName, loopType));
}
```

### 3.3 调用链（TLM 女仆侧）—— **YSM 自带女仆兼容**

```
com/elfmcys/ysm/client/compat/touhoulittlemaid/
├── client/CustomYsmMaidEntity.java
├── client/animation/MaidControllerCollection.java
├── client/animation/GeoMaidAnimatedRegister.java
├── client/animation/molang/TLMBindingInner.java
└── client/animation/predicate/YsmMaidMainPredicate.java     ← 关键
```

```java
// com/elfmcys/ysm/client/compat/touhoulittlemaid/client/animation/predicate/YsmMaidMainPredicate.java
public PlayState test(AnimationEvent<CustomYsmMaidEntity> event, ExpressionEvaluator<?> evaluator) {
    EntityMaid maid = event.getAnimatableEntity().getEntity();
    if (maid == null || event.getAnimatableEntity() instanceof IPreviewEntity) return PlayState.STOP;
    if (maid.renderState != MaidRenderState.ENTITY) return PlayState.STOP;      // 雕像/手办不播
    Entity vehicle = maid.getVehicle();
    if (vehicle != null && vehicle.isAlive()) return PlayState.STOP;

    for (int i = Priority.HIGHEST; i <= Priority.LOWEST; i++) {
        for (AnimationState<EntityMaid, CustomYsmMaidEntity> state : DATA[i]) {
            if (state.getPredicate().test(maid, event)) {
                String animationName = state.getAnimationName();
                LoopType loopType = state.getLoopType();

                PlayState slashBladeAnimation = SlashBladeCompat.playMainAnimation(maid, event, animationName, loopType);
                if (slashBladeAnimation != null) { return slashBladeAnimation; }

                PlayState gunMainAnimation = TACZCompat.playGunMainAnimation(maid, event, animationName, loopType);
                return Objects.requireNonNullElseGet(gunMainAnimation,
                        () -> playAnimation(event, animationName, loopType));
            }
        }
    }
    return PlayState.STOP;
}
```

**重要结论：`TACZCompat.playGunMainAnimation` 的第一个参数是 `LivingEntity`，玩家侧与女仆侧共用同一个方法。
因此对这一个方法注入一次，两个宿主都会覆盖。这是最省事、也是唯一需要的中心注入点。**

### 3.4 门控：没装 TACZ / 卓越前线时整条链不存在

```java
// com/elfmcys/ysm/client/entity/...  GunFirePredicate
public class GunFirePredicate implements IAnimationPredicate<CustomHumanoidEntity<?>> {
    public PlayState test(...) { ... TACZCompat.playGunOnceAnimation(...) / SWarfareCompat... }
    public static boolean available() { return TACZCompat.isInstalled() || SWarfareCompat.isInstalled(); }
}
```

```java
// TACZCompat
private static boolean INSTALLED = false;
public static void init() {
    INSTALLED = ModList.get().isLoaded("tacz");
    if (INSTALLED) { TacCompatInner.registerEvent(); }
}
```

`GunFirePredicate.available()` 为 `false` 时，这个谓词不会被挂上。所以**只装 YSM + SC2 时，
枪械相关的谓词与动画入口可能整条不存在**。这一点必须实测确认（见 §6、§7）。

### 3.5 Molang 变量（**次要路径，不要作为主方案**）

`TacCtrlBinding` 把 TACZ 状态暴露成这些 Molang 变量：

```
tac_hold_gun, tac_gun_type, tac_gun_id, tac_is_fire, tac_is_aim,
tac_is_reload, tac_is_melee, tac_is_draw, tac_fire_mode
```

**为什么不能只靠它**：这些变量只有**模型作者已经在模型里写了引用逻辑**时才有用。
绝大多数 YSM 模型没有为 SC2 写逻辑；而「给每个模型注入 Molang」既不现实（模型是第三方资产、可能加密），
也不该做（修改他人资产）。**因此主方案必须是 §4 的动画名替换，不是 Molang 注入。**

（补充事实：没装 TACZ 时 `TACZCompat.addEmptyBinding` 仍会注册这 9 个变量但值全为 `false`/空串，
源码注释写着「否则会报错」——即变量存在性与取值是两回事。）

---

## 4. 注入点清单

注入原则：**只在 YSM 侧注入，绝不动 TACZ（`com.tacz.guns.*`）。**
判断标准：注入点必须在「读的人」那一侧。这样 TACZ 的类全程不会被调用，SC2 的枪不会「变成 TACZ 的枪」。

### 4.1 必做

| # | 目标类 | 目标方法 | 注入位置 | 行为 |
|---|---|---|---|---|
| 1 | `com.elfmcys.ysm.client.compat.tacz.TACZCompat` | `playGunMainAnimation` | HEAD，`cancellable = true` | 手持 SC2 枪时，自行播放对应 `tac:*` 动画并 `setReturnValue(CONTINUE)`；非 SC2 枪直接 return（不 cancel），让 YSM 走它自己的逻辑 |
| 2 | 同上 | `playGunHoldAnimation` | HEAD，`cancellable = true` | 同上（持握姿势） |
| 3 | 同上 | `playGunOnceAnimation` | HEAD，`cancellable = true` | 同上（单次动作，由 `GunFirePredicate` 调用） |
| 4 | 同上 | `getGunId` | HEAD，`cancellable = true` | 返回 SC2 枪映射出的 id，供 YSM 内部判断枪型/选择动画 |
| 5 | `com.elfmcys.ysm.client.animation.predicate.GunFirePredicate` | `available` | HEAD，`cancellable = true` | 让谓词在**没装 TACZ/卓越前线**时也返回 `true`，否则枪械动画入口根本挂不上 |

### 4.2 可选（先不做，验证阶段再决定）

| 目标 | 方法 | 说明 |
|---|---|---|
| `TACZCompat` | `renderOffsetHand` | 副手枪械的手部渲染偏移。玩家第一人称才有意义 |
| `TACZCompat` | `openFlashShellRender` / `stopFlashShellRender` | 抛壳特效。可跳过 |
| `TACZCompat` | `addBinding` | 想给**已支持 TACZ 的模型**补充 `tac_*` 变量时才需要。**默认不做**（见 §3.5） |

### 4.3 方法内部的判断顺序（照 §5.2 的 TLM 侧实现写）

```
1. event / animatable / entity 任一为 null → return（不 cancel）
2. entity 是 IPreviewEntity → return
3. 实体状态排除：睡觉 / 坐 / 乘载具 / 游泳 → return（照抄 TLM 侧 AnimationManagerMixin 的排除列表）
4. 手里不是 SC2 枪（SC2GunCompat.isSC2Gun(stack) 为 false）→ return（不 cancel）
5. 取该模型当前的动画文件：
       ResourceLocation modelId = animatable.getAnimationFileLocation();
       AnimationFile animationFile = GeckoLibCache.getInstance().getAnimations().get(modelId);
   （注：这是 YSM 自己的 GeckoLibCache，包路径见 §6 第一条，不要用 TLM 的）
6. 按状态拼出候选动画名（列表见 §4.4），逐个用 animationFile.animations().containsKey(name) 检查
7. 命中第一个存在的 → 设置动画 → cir.setReturnValue(PlayState.CONTINUE)
8. 一个都没命中 → return（不 cancel），交给 YSM 兜底
```

### 4.4 候选动画名（从 §5.2 的 TLM 侧实现提取，已实测可用）

前缀由状态决定：

| 状态 | prefix |
|---|---|
| 正在换弹（且不是机枪） | `tac:reload:` |
| 正在开火 | `tac:hold:fire:` |
| 冲刺中且在地面 | `tac:run:` |
| 交战/瞄准中 | `tac:aim:` |
| 其他 | `tac:hold:` |

后缀由枪型决定：`SC2GunCompat.getAnimationGunType(stack)` → `"rifle"` / `"pistol"` / `"rpg"`

**必须照抄的两条兜底规则**（TLM 侧已实测）：

1. `tac:reload:<type>` 不存在 → 降级为 `tac:hold:<type>`
2. `tac:aim:<type>` 不存在 → 降级为 `tac:hold:<type>`
3. 机枪特例：动画名是 `prefix` 去掉末尾冒号 + `"$tacz:minigun"`，例如 `tac:hold$tacz:minigun`。
   **注意这个后缀里的 `tacz` 是动画名的一部分，不是要装 TACZ。**

---

## 5. 参考资料

### 5.1 可用的 SC2 状态接口（本模组已有，直接用）

`com.scg2tlm.elmomod.compat.SC2GunCompat` 的公开方法（**已核实存在的签名**）：

```java
public static boolean isSC2Gun(ItemStack stack);
public static boolean isCCGun(ItemStack stack);            // scguns_cnc 命名空间
public static boolean isOregunizedGun(ItemStack stack);    // scguns_oregunized
public static ResourceLocation getGunId(ItemStack stack);
public static String getAnimationGunType(ItemStack stack); // "rifle" / "pistol" / "rpg"
public static boolean isMinigunGun(ItemStack stack);
public static boolean isRpgGun(ItemStack stack);
public static boolean isEnergyGun(ItemStack stack);
public static boolean needsReload(ItemStack stack);
public static int getMaxAmmo(ItemStack stack);
public static CompoundTag getTag(ItemStack stack);
public static WeaponType getWeaponType(ItemStack stack);

// 设置动画（TLM 侧用的就是这个，YSM 侧需要另写等价方法，见 §6）
public static boolean setAnimOnce(AnimationController<?> controller, String name, ILoopType loopType);
```

**注意**：`setAnimOnce` 的参数类型是 **TLM 的** `AnimationController` / `ILoopType`，
**不能直接用在 YSM 侧**（YSM 有自己的 geckolib3 fork）。YSM 侧需要写一个接受 YSM 类型的新方法。

另外，换弹状态通过同步 task data 读取（TLM 侧用法）：

```java
CompoundTag taskData = ((EntityMaidTaskDataInvoker) entity).invokeGetSyncTaskData();
boolean isReloading = taskData.getBoolean("scg2tlm:reloading");
```

### 5.2 TLM 侧的成品实现（**这是本次要照抄的模式**）

**`mixin/TacCompatMixin.java`** —— 让 SC2 枪在 TLM 眼里冒充 TACZ 枪：

```java
@Mixin(TacCompat.class)
public class TacCompatMixin {
    @Inject(method = "isGun", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onIsGun(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getGunId", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onGetGunId(ItemStack stack, CallbackInfoReturnable<ResourceLocation> cir) {
        if (SC2GunCompat.isSC2Gun(stack) && stack.getItem() instanceof GunItem) {
            // 机枪 -> tacz:minigun, 火箭筒 -> tacz:rpg, 其他 -> 自己的 id
            ...
            cir.setReturnValue(id);
        }
    }

    @Inject(method = "playGunMainAnimation", at = @At("HEAD"), cancellable = true, remap = false)
    private static void onPlayGunMainAnimation(IMaid maid, AnimationEvent<GeckoMaidEntity<?>> event,
                                              String animationName, ILoopType loopType,
                                              CallbackInfoReturnable<PlayState> cir) {
        ... 手持 SC2 枪 → 若模型有自己的 tac 动画则 cir.setReturnValue(PlayState.CONTINUE) ...
    }
}
```

**`mixin/AnimationManagerMixin.java`** —— 动画名解析与设置（**§4.4 的规则全部从这里提取**）：

```java
// 关键片段：多候选名 + 存在性检查 + 降级
ConditionTAC conditionTAC = ConditionManager.getTAC(modelId);
if (conditionTAC != null) {
    String name = conditionTAC.doTest(stack, prefix);
    if (StringUtils.isNoneBlank(name)) {
        SC2GunCompat.setAnimOnce(event.getController(), name, loopType);
        cir.setReturnValue(PlayState.CONTINUE);
        return;
    }
}

String animName;
if (SC2GunCompat.isMinigunGun(stack)) {
    animName = prefix.substring(0, prefix.length() - 1) + "$tacz:minigun";
    if (animationFile != null && animationFile.animations().containsKey(animName)) { ... }
}
animName = prefix + animType;
if (animationFile == null || !animationFile.animations().containsKey(animName)) {
    if (prefix.equals("tac:aim:")) {
        animName = "tac:hold:" + animType;
        if (animationFile != null && animationFile.animations().containsKey(animName)) { ... }
    }
    return;                        // 不 cancel，交回 TLM 兜底
}
SC2GunCompat.setAnimOnce(event.getController(), animName, loopType);
cir.setReturnValue(PlayState.CONTINUE);
```

### 5.3 可参考的第三方先例

| 项目 | 说明 |
|---|---|
| `HSZK2017/ysm_epicfight_compat`（MIT） | 完整实现了 YSM 解密 + 解析 + Molang 运行时 + 关节映射，并有 `mixin/eftlm/YsmMaidRendererMixin` 挂 TLM 女仆。**如果将来要做 YSM 模型格式转换，这是起点** |
| `YesSteveModel/YesSteveModel`（Apache-2.0） | YSM 官方源码。**本清单所有 YSM 事实的唯一权威来源** |

---

## 6. 陷阱清单（每条都会实际踩到）

1. **YSM 有自己 fork 的 geckolib3，类型不通用。**
   YSM 用的是 `com.elfmcys.ysm.geckolib3.core.PlayState`、`...core.builder.LoopType`、
   `...core.builder.AnimationBuilder`、`...core.event.predicate.AnimationEvent`。
   **不是** TLM 的 `com.github.tartaricacid.touhoulittlemaid.geckolib3.*`，**也不是** 上游 GeckoLib 的 `software.bernie.geckolib.*`。
   同理 `GeckoLibCache` 也要用 YSM 自己的那个。**混用会编译不过或运行期 ClassCastException。**

2. **`PlayState` 的返回语义**：YSM 的 `playGun*` 返回 `null` 表示「不处理」。
   你的注入如果不是 SC2 枪，**必须原样 `return`、不要 cancel**，否则会破坏 YSM 对其他枪械的处理。

3. **`TacCompatInner` 是隔离类**：它直接引用 `com.tacz.guns.*`，只在装了 TACZ 时才类加载。
   如果注入点选到内层，mixin 必须加 `require = 0`，否则没装 TACZ 时启动即崩。
   **推荐：只注入外层的 `TACZCompat`，避开这个问题。**

4. **女仆谓词只在装了 TLM 时类加载**（`com.elfmcys.ysm.client.compat.touhoulittlemaid.*`）。
   任何注入它的 mixin 也必须 `require = 0`。

5. **`isInstalled()` 门控**：不装 TACZ 时 `playGun*Animation` 直接 `return null`。
   所以**只装 YSM + SC2 的场景必须实测**（§7 的用例 C），很可能需要 §4.1 的第 5 项一起处理。

6. **不要动 Molang 作为主方案**（理由见 §3.5）。如果确实想给已支持 TACZ 的模型补变量，
   那是在 `TACZCompat.addBinding` 上做，属于**增强项**，不是主路径。

7. **模型加密不影响本方案**。整个方案只做两件事：查动画名表、替换播放的动画名。
   不读也不改模型的逻辑，所以 `.ysm` 加密包与明文目录包一视同仁。

8. **`getAnimationGunType` 的返回值只有 `"rifle"` / `"pistol"` / `"rpg"` 三种**
   （机枪归到 `"rifle"`，火箭筒归到 `"rpg"`，双手握持的手枪/马格南也归到 `"rifle"`）。
   拼动画名时不要自己发明第四种。

9. **不要注册成 TACZ 的 `IGun`，不要改 `IGun.getIGunOrNull`，不要改 TACZ 的任何类。**
   那会让 TACZ 的 `IGunOperator` 状态机接管瞄准/射击/换弹/弹药，并与 TACZ 自己的第一人称渲染器冲突，
   直接变成「枪械被 TACZ 抢走」——这正是要避免的结果。

10. **不要修改任何模型资产**（第三方 YSM 模型包）。本方案不需要。

---

## 7. 验收标准

必须实测以下用例，每个都要给出结果（通过 / 不通过 / 现象描述）：

| 用例 | 环境 | 期望 |
|---|---|---|
| A | YSM + TACZ + SC2，**玩家**手持 SC2 枪 | 播放该 YSM 模型自带对应的 `tac:*` 动画（持握/瞄准/开火/换弹/跑动） |
| B | YSM + TACZ + SC2 + TLM，**女仆**手持 SC2 枪 | 同上（走 `YsmMaidMainPredicate` 那条链） |
| C | YSM + SC2（**不装** TACZ / 卓越前线），玩家与女仆各自手持 SC2 枪 | 仍能播放 `tac:*` 动画（**这条是 §4.1 第 5 项是否必要、是否有效的判定依据**） |
| D | 装了 TACZ，玩家手持**真正的 TACZ 枪** | 行为与未安装本模组时**完全一致**（回归测试，确认没破坏原路径） |
| E | 手持原版物品（剑/弓/食物）与空手 | 行为与未安装本模组时一致 |
| F | 手持 SC2 的枪但模型**没有** `tac:*` 动画 | 不报错、不刷日志，正常退回 YSM 自己的兜底动画 |

日志检查：

- 无 `Mixin apply failed` / `NoSuchMethodError` / `ClassNotFoundException`
- 不出现针对不存在的动画名的报错刷屏（说明第 6 步的存在性检查生效）
- **不出现任何来自 `com.tacz.guns.*` 的调用栈**（说明没有把 TACZ 拖进来）

---

## 8. 交付物

1. 新增 `mixin` 类（建议命名 `mixin/ysm/` 子包下，与现有 `mixin/` 平级或作为子包）
2. 一个接受 **YSM 类型** 的等价 `setAnim` 方法（不要复用 §5.1 那个 TLM 签名的 `setAnimOnce`）
3. 若需要强制挂载枪械谓词：对 `GunFirePredicate.available()` 的注入
4. 一份测试结果表（§7 的 A–F）
5. **如果目标版本不是 1.20.1 Forge**：附上 §0.1 的重新核对结果（类名/签名是否一致，不一致处如何调整）
