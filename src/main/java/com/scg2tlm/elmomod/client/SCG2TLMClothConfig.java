package com.scg2tlm.elmomod.client;

import com.scg2tlm.elmomod.SCG2TLMConfig;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import me.shedaniel.clothconfig2.impl.builders.SubCategoryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class SCG2TLMClothConfig {
    public static Screen createScreen(Screen parent) {
        ConfigBuilder root = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.literal("赤焦枪械：女仆兼容"));
        root.setGlobalized(true);
        root.setGlobalizedExpanded(false);
        // 点「保存」时把值写回 config/scg2_maid_compat-common.toml。
        // 不设这个的话 setSaveConsumer 只改内存里的值：本局立即生效，但重启后会被文件里的旧值覆盖。
        root.setSavingRunnable(SCG2TLMConfig.SPEC::save);
        addEntries(root, root.entryBuilder());
        return root.build();
    }

    /** 向任意 Cloth 配置界面添加本 mod 的配置项（独立界面和 TLM AddClothConfigEvent 共用）：一个大类下分多个小组。 */
    public static void addEntries(ConfigBuilder root, ConfigEntryBuilder entry) {
        ConfigCategory main = root.getOrCreateCategory(Component.literal("赤焦枪械：女仆兼容"));

        SubCategoryBuilder combat = entry.startSubCategory(Component.literal("战斗"));
        combat.setExpanded(true);
        combat.add(entry.startDoubleField(Component.literal("枪械精度 Gun Accuracy"), SCG2TLMConfig.GUN_ACCURACY.get())
                .setDefaultValue(SCG2TLMConfig.GUN_ACCURACY.getDefault())
                .setMin(0.0).setMax(20.0)
                .setTooltip(Component.literal("女仆枪械攻击精度（越高越准）。散布 = 5 × 难度 / 该值。"))
                .setSaveConsumer(i -> SCG2TLMConfig.GUN_ACCURACY.set(i)).build());
        combat.add(entry.startDoubleField(Component.literal("交战距离 Gun Range"), SCG2TLMConfig.GUN_RANGE.get())
                .setDefaultValue(SCG2TLMConfig.GUN_RANGE.getDefault())
                .setMin(8.0).setMax(256.0)
                .setTooltip(Component.literal("默认枪械交战距离（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.GUN_RANGE.set(i)).build());
        combat.add(entry.startDoubleField(Component.literal("手雷交战半径 Grenade Radius"), SCG2TLMConfig.GRENADE_RADIUS.get())
                .setDefaultValue(SCG2TLMConfig.GRENADE_RADIUS.getDefault())
                .setMin(4.0).setMax(128.0)
                .setTooltip(Component.literal("手雷交战半径（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.GRENADE_RADIUS.set(i)).build());
        combat.add(entry.startIntSlider(Component.literal("手雷冷却 Grenade Cooldown"), SCG2TLMConfig.GRENADE_COOLDOWN.get(), 10, 200)
                .setDefaultValue(SCG2TLMConfig.GRENADE_COOLDOWN.getDefault())
                .setTooltip(Component.literal("两次手雷投掷间隔（tick，20 = 1 秒）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.GRENADE_COOLDOWN.set(i)).build());
        combat.add(entry.startIntSlider(Component.literal("投雷后撤离 Grenade Flee Time"), SCG2TLMConfig.GRENADE_POST_THROW_TIME.get(), 0, 100)
                .setDefaultValue(SCG2TLMConfig.GRENADE_POST_THROW_TIME.getDefault())
                .setTooltip(Component.literal("投掷手雷后撤离的 tick 数。"))
                .setSaveConsumer(i -> SCG2TLMConfig.GRENADE_POST_THROW_TIME.set(i)).build());
        combat.add(entry.startDoubleField(Component.literal("近战距离 Melee Range"), SCG2TLMConfig.MELEE_RANGE.get())
                .setDefaultValue(SCG2TLMConfig.MELEE_RANGE.getDefault())
                .setMin(1.0).setMax(10.0)
                .setTooltip(Component.literal("枪托/刺刀近战最大距离（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.MELEE_RANGE.set(i)).build());
        combat.add(entry.startDoubleField(Component.literal("近战接近速度 Melee Speed"), SCG2TLMConfig.MELEE_SPEED.get())
                .setDefaultValue(SCG2TLMConfig.MELEE_SPEED.getDefault())
                .setMin(0.1).setMax(2.0)
                .setTooltip(Component.literal("近战接近时的移动速度倍率。"))
                .setSaveConsumer(i -> SCG2TLMConfig.MELEE_SPEED.set(i)).build());
        combat.add(entry.startBooleanToggle(Component.literal("刺刀近战 Bayonet Melee"), SCG2TLMConfig.BAYONET_MELEE.get())
                .setDefaultValue(SCG2TLMConfig.BAYONET_MELEE.getDefault())
                .setTooltip(Component.literal("启用后，目标在近战范围内时女仆用刺刀横扫近战（带动画）。"))
                .setSaveConsumer(b -> SCG2TLMConfig.BAYONET_MELEE.set(b)).build());
        combat.add(entry.startIntSlider(Component.literal("射击间隔 Firing Interval"), SCG2TLMConfig.FIRING_INTERVAL.get(), 0, 200)
                .setDefaultValue(SCG2TLMConfig.FIRING_INTERVAL.getDefault())
                .setTooltip(Component.literal("每枪之间额外增加的 tick（0 = 按枪自然射速，20 = 每秒 1 发）。越高越省弹药。"))
                .setSaveConsumer(i -> SCG2TLMConfig.FIRING_INTERVAL.set(i)).build());
        combat.add(entry.startBooleanToggle(Component.literal("线列步兵模式 Line Infantry"), SCG2TLMConfig.LINE_INFANTRY.get())
                .setDefaultValue(SCG2TLMConfig.LINE_INFANTRY.getDefault())
                .setTooltip(Component.literal("线列步兵：持古董武器的女仆站定，围绕主人在主人面前列队，齐射。"))
                .setSaveConsumer(b -> SCG2TLMConfig.LINE_INFANTRY.set(b)).build());
        combat.add(entry.startIntSlider(Component.literal("齐射间隔 Volley Interval"), SCG2TLMConfig.VOLLEY_INTERVAL.get(), 10, 200)
                .setDefaultValue(SCG2TLMConfig.VOLLEY_INTERVAL.getDefault())
                .setTooltip(Component.literal("两次齐射间隔（tick）。所有线列步兵按此节奏一起开火。"))
                .setSaveConsumer(i -> SCG2TLMConfig.VOLLEY_INTERVAL.set(i)).build());
        combat.add(entry.startIntSlider(Component.literal("编队人数 Formation Size"), SCG2TLMConfig.FORMATION_SIZE.get(), 1, 32)
                .setDefaultValue(SCG2TLMConfig.FORMATION_SIZE.getDefault())
                .setTooltip(Component.literal("线列编队最多女仆数。"))
                .setSaveConsumer(i -> SCG2TLMConfig.FORMATION_SIZE.set(i)).build());
        combat.add(entry.startDoubleField(Component.literal("编队间距 Formation Spacing"), SCG2TLMConfig.FORMATION_SPACING.get())
                .setDefaultValue(SCG2TLMConfig.FORMATION_SPACING.getDefault())
                .setMin(1.0).setMax(8.0)
                .setTooltip(Component.literal("编队女仆之间的水平间距（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.FORMATION_SPACING.set(i)).build());
        main.addEntry(combat.build());

        SubCategoryBuilder reload = entry.startSubCategory(Component.literal("换弹"));
        reload.setExpanded(true);
        reload.add(entry.startBooleanToggle(Component.literal("免费弹药 Reload Free Ammo"), SCG2TLMConfig.RELOAD_FREE_AMMO.get())
                .setDefaultValue(SCG2TLMConfig.RELOAD_FREE_AMMO.getDefault())
                .setTooltip(Component.literal("启用后女仆换弹不消耗背包弹药，完全无视弹药需求。"))
                .setSaveConsumer(b -> SCG2TLMConfig.RELOAD_FREE_AMMO.set(b)).build());
        reload.add(entry.startStrField(Component.literal("换弹完成音效 Reload End Sound"), SCG2TLMConfig.RELOAD_END_SOUND.get())
                .setDefaultValue(SCG2TLMConfig.RELOAD_END_SOUND.getDefault())
                .setTooltip(Component.literal("换弹<b>完成</b>那一刻播放的音效 id（留空 = 静音）。\n"
                        + "原来用的是枪械的 cock 字段，但 SC2 的 item/pistol/cock.ogg 本身就是一段换弹录音，\n"
                        + "所以听起来像又换了一次弹。默认改用拉栓音（汉化包字幕「拉栓」）：\n"
                        + "  scguns:item.bolt_pull.bolt_pull\n"
                        + "其它候选（SC2 自己没用到的闲置音效）：\n"
                        + "  scguns:item.bolt.bolt（拉栓·更长）、scguns:item.bolt_release.bolt_release（放栓）\n"
                        + "  scguns:item.rack.rack（上膛）、scguns:item.reload_end.reload_end（装填完毕）\n"
                        + "  scguns:item.mag_in.mag_in / scguns:item.mag_out.mag_out（弹匣）"))
                .setErrorSupplier(s -> {
                    if (s == null || s.isBlank()) return java.util.Optional.empty();
                    net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(s.trim());
                    if (id == null || !net.minecraftforge.registries.ForgeRegistries.SOUND_EVENTS.containsKey(id)) {
                        return java.util.Optional.of(Component.literal("找不到这个音效 id"));
                    }
                    return java.util.Optional.empty();
                })
                .setSaveConsumer(s -> SCG2TLMConfig.RELOAD_END_SOUND.set(s == null ? "" : s.trim())).build());
        main.addEntry(reload.build());

        SubCategoryBuilder search = entry.startSubCategory(Component.literal("索敌范围"));
        search.setExpanded(true);
        search.add(entry.startDoubleField(Component.literal("狙击枪索敌范围 Sniper"), SCG2TLMConfig.SNIPER_SEARCH_RADIUS.get())
                .setDefaultValue(SCG2TLMConfig.SNIPER_SEARCH_RADIUS.getDefault())
                .setMin(8.0).setMax(512.0)
                .setTooltip(Component.literal("狙击步枪的索敌半径（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.SNIPER_SEARCH_RADIUS.set(i)).build());
        search.add(entry.startDoubleField(Component.literal("步枪/马格南索敌范围 Rifle/Magnum"), SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.get())
                .setDefaultValue(SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.getDefault())
                .setMin(8.0).setMax(256.0)
                .setTooltip(Component.literal("步枪和马格南的索敌半径（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.RIFLE_MAGNUM_SEARCH_RADIUS.set(i)).build());
        search.add(entry.startDoubleField(Component.literal("冲锋枪/霰弹枪/手枪索敌范围 SMG/Shotgun/Pistol"), SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.get())
                .setDefaultValue(SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.getDefault())
                .setMin(8.0).setMax(128.0)
                .setTooltip(Component.literal("冲锋枪、霰弹枪、手枪的索敌半径（格）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.SMG_SHOTGUN_PISTOL_SEARCH_RADIUS.set(i)).build());
        search.add(entry.startBooleanToggle(Component.literal("全难度提前量 Lead Always"), SCG2TLMConfig.LEAD_ALWAYS.get())
                .setDefaultValue(SCG2TLMConfig.LEAD_ALWAYS.getDefault())
                .setTooltip(Component.literal("启用后女仆在所有难度都会预判移动目标（SCG 原本只有困难难度）。"))
                .setSaveConsumer(b -> SCG2TLMConfig.LEAD_ALWAYS.set(b)).build());
        search.add(entry.startDoubleField(Component.literal("提前量权重 Lead Weight"), SCG2TLMConfig.LEAD_WEIGHT.get())
                .setDefaultValue(SCG2TLMConfig.LEAD_WEIGHT.getDefault())
                .setMin(0.0).setMax(1.0)
                .setTooltip(Component.literal("提前量方向混入瞄准的权重（SCG 默认 0.3，1.0 = 全预判）。"))
                .setSaveConsumer(i -> SCG2TLMConfig.LEAD_WEIGHT.set(i)).build());
        main.addEntry(search.build());

        // 目标与跟丢：跟丢之后的处理（换目标 / 长搜索 / 放弃）
        SubCategoryBuilder target = entry.startSubCategory(Component.literal("目标与跟丢 Target Handling"));
        target.setExpanded(true);
        target.add(entry.startIntField(Component.literal("失去视野判定 Lost Sight Ticks"), SCG2TLMConfig.TARGET_LOST_SIGHT_TICKS.get())
                .setDefaultValue(SCG2TLMConfig.TARGET_LOST_SIGHT_TICKS.getDefault())
                .setMin(20).setMax(600)
                .setTooltip(Component.literal("看不见目标多久之后才开始处理（tick，20 = 1 秒）。\n"
                        + "这段时间内只是等，敌人短暂掠过掩体不会掉锁。默认 60 = 3 秒。"))
                .setSaveConsumer(i -> SCG2TLMConfig.TARGET_LOST_SIGHT_TICKS.set(i)).build());
        target.add(entry.startIntField(Component.literal("搜索时长 Lost Target Search Ticks"), SCG2TLMConfig.TARGET_SEARCH_TICKS.get())
                .setDefaultValue(SCG2TLMConfig.TARGET_SEARCH_TICKS.getDefault())
                .setMin(0).setMax(1200)
                .setTooltip(Component.literal("走向「最后目击点」并停留搜索的时长（tick）。0 = 不搜索、直接放弃。\n"
                        + "只有周围没有别的可见敌人时才会搜索；有别人的话会立刻换目标。\n"
                        + "默认 400 = 20 秒。"))
                .setSaveConsumer(i -> SCG2TLMConfig.TARGET_SEARCH_TICKS.set(i)).build());
        target.add(entry.startDoubleField(Component.literal("搜索距离 Search Range"), SCG2TLMConfig.TARGET_SEARCH_RANGE.get())
                .setDefaultValue(SCG2TLMConfig.TARGET_SEARCH_RANGE.getDefault())
                .setMin(4.0).setMax(128.0)
                .setTooltip(Component.literal("允许为搜索走出的最大距离（格）。超过就不去，直接放弃目标。"))
                .setSaveConsumer(i -> SCG2TLMConfig.TARGET_SEARCH_RANGE.set(i)).build());
        target.add(entry.startBooleanToggle(Component.literal("允许被新敌人打断 Interruptible"), SCG2TLMConfig.TARGET_SEARCH_INTERRUPTIBLE.get())
                .setDefaultValue(SCG2TLMConfig.TARGET_SEARCH_INTERRUPTIBLE.getDefault())
                .setTooltip(Component.literal("搜索躲起来的敌人时，旁边出现别的可见敌人是否放弃原目标改打新敌人。\n"
                        + "关掉 = 先搜完（或放弃）再说，最贴近「专心 CQB 推进」。"))
                .setSaveConsumer(b -> SCG2TLMConfig.TARGET_SEARCH_INTERRUPTIBLE.set(b)).build());
        target.add(entry.startBooleanToggle(Component.literal("记住并回头搜 Resume Search"), SCG2TLMConfig.TARGET_SEARCH_RESUME.get())
                .setDefaultValue(SCG2TLMConfig.TARGET_SEARCH_RESUME.getDefault())
                .setTooltip(Component.literal("被打断时记住原目标的最后目击点和剩余搜索时间，\n"
                        + "打完眼前这波之后自己走回去补搜（CQB 清房间不半途而废）。\n"
                        + "关掉 = 打断即忘记（1.1.24 的行为）。"))
                .setSaveConsumer(b -> SCG2TLMConfig.TARGET_SEARCH_RESUME.set(b)).build());
        main.addEntry(target.build());

        SubCategoryBuilder integration = entry.startSubCategory(Component.literal("联动"));
        integration.setExpanded(true);
        integration.add(entry.startBooleanToggle(Component.literal("玩家/女仆阵营 Player Faction"), SCG2TLMConfig.ENABLE_PLAYER_FACTION.get())
                .setDefaultValue(SCG2TLMConfig.ENABLE_PLAYER_FACTION.getDefault())
                .setTooltip(Component.literal("SCG Extra 联动：把玩家和女仆编入独立阵营 \"player\"，SCG Extra 阵营怪会主动攻击它们。"))
                .setSaveConsumer(b -> SCG2TLMConfig.ENABLE_PLAYER_FACTION.set(b)).build());
        integration.add(entry.startBooleanToggle(Component.literal("友军免伤 Prevent Friendly Fire"), SCG2TLMConfig.PREVENT_FACTION_FRIENDLY_FIRE.get())
                .setDefaultValue(SCG2TLMConfig.PREVENT_FACTION_FRIENDLY_FIRE.getDefault())
                .setTooltip(Component.literal("SCG Extra 联动：同一阵营的怪互相射击时，子弹直接穿过友军不造成友伤\n（玩家/女仆的弹体不受影响）。"))
                .setSaveConsumer(b -> SCG2TLMConfig.PREVENT_FACTION_FRIENDLY_FIRE.set(b)).build());
        main.addEntry(integration.build());
    }
}
