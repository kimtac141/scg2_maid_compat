package com.scg2tlm.elmomod;

/**
 * scgextra 兼容辅助类：通过反射访问 scgextra 的 {@code Faction} 类。
 *
 * <h2>这个类刻意「零依赖」——连 Forge / Minecraft 都不引用</h2>
 * <p>它存在的意义是「跨可选依赖的边界」，所以它自己必须是<b>最后才会失败</b>的那一环。
 * 曾经它引用过 {@code net.minecraftforge.fml.ModList}（用来实现 {@code isLoaded()}），
 * 这带来两个隐患：</p>
 * <ol>
 *   <li>多一个类加载期的依赖，真出问题时更难判定是谁的问题；</li>
 *   <li>本类的<b>链接</b>因此会依赖 Forge 的类解析结果。</li>
 * </ol>
 * <p>实测教训：本类是<b>懒加载</b>的，它第一次被引用恰好是「女仆开火命中」那条热路径
 * （{@code LivingEntity.hurt} → {@code ForgeHooks.onLivingHurt} → 阵营友伤事件）。
 * 一旦本类加载失败，表现就是<b>开火即崩</b>，而不是安静降级——完全违背它作为
 * 「安全降级层」的设计意图。因此现在整个文件只使用 {@code java.lang.reflect}：
 * 不含任何 Forge / Minecraft 类型，也不含任何其它本模组的类，能失败的面被压到最小。</p>
 *
 * <p>{@code scgextra} 是否安装的判断已移到 {@code ExampleMod}（那里本来就有 Forge 上下文）。</p>
 */
public final class SCGExtraCompatHelper {

    /** scgextra 的阵营类。用字符串引用：scgextra 缺失时本类仍能正常加载。 */
    private static final String FACTION_CLASS = "net.zincstudios.scgextra.entity.Faction";

    private SCGExtraCompatHelper() {
    }

    /**
     * 反射调用 {@code Faction.isFriendlies(a, b)}。
     *
     * <p>刻意用 {@code Object} 而不是 {@code LivingEntity} 作为参数类型：
     * 这样本类的方法签名就不需要解析任何 Minecraft 类。调用方传进来的实例
     * 由反射在 scgextra 那侧完成类型检查。</p>
     *
     * @return 同一阵营返回 true；scgextra 缺失、方法不存在或调用失败一律返回 false（安全降级）
     */
    public static boolean isFriendlies(Object a, Object b) {
        if (a == null || b == null) return false;
        try {
            Class<?> factionClass = Class.forName(FACTION_CLASS);
            // 目标签名是 isFriendlies(LivingEntity, LivingEntity)；用具体参数类型找不到时
            // 退回按声明参数类型匹配，避免因编译期类型差异而静默失效。
            for (java.lang.reflect.Method m : factionClass.getMethods()) {
                if (!"isFriendlies".equals(m.getName()) || m.getParameterCount() != 2) continue;
                if (!java.lang.reflect.Modifier.isStatic(m.getModifiers())) continue;
                Class<?>[] pt = m.getParameterTypes();
                if (!pt[0].isInstance(a) || !pt[1].isInstance(b)) continue;
                m.setAccessible(true);
                Object r = m.invoke(null, a, b);
                return r instanceof Boolean bool && bool;
            }
            return false;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** 反射读取 {@code Faction.NO_FACTION} 静态字段。scgextra 缺失时返回 null。 */
    public static Object getNoFaction() {
        try {
            Class<?> factionClass = Class.forName(FACTION_CLASS);
            return factionClass.getField("NO_FACTION").get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }
}
