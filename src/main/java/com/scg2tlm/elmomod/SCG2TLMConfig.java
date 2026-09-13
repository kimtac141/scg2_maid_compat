package com.scg2tlm.elmomod;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SCG2TLMConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue GUN_ACCURACY;
    public static final ForgeConfigSpec.DoubleValue GUN_RANGE;

    public static final ForgeConfigSpec.DoubleValue GRENADE_RADIUS;
    public static final ForgeConfigSpec.IntValue GRENADE_COOLDOWN;
    public static final ForgeConfigSpec.IntValue GRENADE_POST_THROW_TIME;

    public static final ForgeConfigSpec.DoubleValue MELEE_RANGE;
    public static final ForgeConfigSpec.DoubleValue MELEE_SPEED;
    public static final ForgeConfigSpec.BooleanValue BAYONET_MELEE;
    public static final ForgeConfigSpec.IntValue FIRING_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue LINE_INFANTRY;
    public static final ForgeConfigSpec.IntValue VOLLEY_INTERVAL;
    public static final ForgeConfigSpec.IntValue FORMATION_SIZE;
    public static final ForgeConfigSpec.DoubleValue FORMATION_SPACING;

    public static final ForgeConfigSpec.IntValue TARGET_LOST_SIGHT_TICKS;
    public static final ForgeConfigSpec.IntValue TARGET_SEARCH_TICKS;
    public static final ForgeConfigSpec.DoubleValue TARGET_SEARCH_RANGE;
    public static final ForgeConfigSpec.BooleanValue TARGET_SEARCH_INTERRUPTIBLE;
    public static final ForgeConfigSpec.BooleanValue TARGET_SEARCH_RESUME;

    public static final ForgeConfigSpec.BooleanValue RELOAD_FREE_AMMO;
    public static final ForgeConfigSpec.ConfigValue<String> RELOAD_END_SOUND;

    public static final ForgeConfigSpec.DoubleValue SNIPER_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue RIFLE_MAGNUM_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SMG_SHOTGUN_PISTOL_SEARCH_RADIUS;

    public static final ForgeConfigSpec.BooleanValue LEAD_ALWAYS;
    public static final ForgeConfigSpec.DoubleValue LEAD_WEIGHT;

    public static final ForgeConfigSpec.BooleanValue ENABLE_PLAYER_FACTION;
    public static final ForgeConfigSpec.BooleanValue PREVENT_FACTION_FRIENDLY_FIRE;

    static {
        BUILDER.push("combat");

        BUILDER.comment("Accuracy for maid gun attacks (higher = more accurate). Spread = 5 * difficulty / this_value.");
        GUN_ACCURACY = BUILDER.defineInRange("gun_accuracy", 3.5, 0.0, 20.0);

        BUILDER.comment("Default gun engagement range in blocks");
        GUN_RANGE = BUILDER.defineInRange("gun_range", 64.0, 8.0, 256.0);

        BUILDER.comment("Grenade engagement radius in blocks");
        GRENADE_RADIUS = BUILDER.defineInRange("grenade_radius", 24.0, 4.0, 128.0);

        BUILDER.comment("Ticks between grenade throws (20 ticks = 1 second)");
        GRENADE_COOLDOWN = BUILDER.defineInRange("grenade_cooldown", 40, 10, 200);

        BUILDER.comment("Ticks to flee after throwing a grenade");
        GRENADE_POST_THROW_TIME = BUILDER.defineInRange("grenade_post_throw_time", 20, 0, 100);

        BUILDER.comment("Max distance for gun-butt melee attack in blocks");
        MELEE_RANGE = BUILDER.defineInRange("melee_range", 3.0, 1.0, 10.0);

        BUILDER.comment("Movement speed multiplier when closing for melee");
        MELEE_SPEED = BUILDER.defineInRange("melee_speed", 0.8, 0.1, 2.0);

        BUILDER.comment("When enabled, maids with a bayonet-equipped gun perform bayonet melee attacks with animation when the target is in arm's reach");
        BAYONET_MELEE = BUILDER.define("bayonet_melee", true);

        BUILDER.comment("Extra ticks added between each shot (0 = fire at the gun's natural rate, 20 = 1 shot per second). Higher values reduce ammo consumption");
        FIRING_INTERVAL = BUILDER.defineInRange("firing_interval", 0, 0, 200);

        BUILDER.comment("Line infantry mode: maids wielding antique weapons stand still, form a line formation around their owner facing the enemy, and fire in synchronized volleys");
        LINE_INFANTRY = BUILDER.define("line_infantry", false);

        BUILDER.comment("Ticks between each volley (20 ticks = 1 second). All line-infantry maids fire together on this cadence");
        VOLLEY_INTERVAL = BUILDER.defineInRange("volley_interval", 40, 10, 200);

        BUILDER.comment("Maximum number of maids in the line formation");
        FORMATION_SIZE = BUILDER.defineInRange("formation_size", 8, 1, 32);

        BUILDER.comment("Horizontal spacing in blocks between maids in the line formation");
        FORMATION_SPACING = BUILDER.defineInRange("formation_spacing", 2.5, 1.0, 8.0);

        BUILDER.comment("Ticks a maid keeps a target after losing sight of it before deciding what to do (20 ticks = 1 second). The target is only dropped after this delay, so brief cover flickers do not break the lock");
        TARGET_LOST_SIGHT_TICKS = BUILDER.defineInRange("target_lost_sight_ticks", 60, 20, 600);

        BUILDER.comment("Ticks a maid spends walking to the target's last seen position before giving up (0 = give up immediately instead of investigating). This only happens when NO other enemy is visible - if another enemy is in sight the maid drops the hidden target right away and switches to it. Default 400 = 20 seconds");
        TARGET_SEARCH_TICKS = BUILDER.defineInRange("target_search_ticks", 400, 0, 1200);

        BUILDER.comment("Max distance in blocks a maid may walk to the target's last seen position when investigating");
        TARGET_SEARCH_RANGE = BUILDER.defineInRange("target_search_range", 24.0, 4.0, 128.0);

        BUILDER.comment("Whether a new visible enemy may interrupt the search for a hidden target (the maid drops the hidden target and engages the new one). Disable to make the maid finish or give up the search first");
        TARGET_SEARCH_INTERRUPTIBLE = BUILDER.define("target_search_interruptible", true);

        BUILDER.comment("When the search IS interrupted by a new enemy, remember that target's last seen position and the remaining search time, then go back to search it once the fight is over (CQB room clearing). Disable to simply forget the old target");
        TARGET_SEARCH_RESUME = BUILDER.define("target_search_resume", true);

        BUILDER.pop();
        BUILDER.push("reload");

        BUILDER.comment("When enabled, maid reloads do not consume backpack ammo and ignore the need for ammo entirely");
        RELOAD_FREE_AMMO = BUILDER.define("reload_free_ammo", false);

        BUILDER.comment("Sound event played when a maid FINISHES reloading (empty = silent).",
                "Default is SC2's bolt-pull (拉栓) sound. The sound SC2 uses for the 'cock' field",
                "(scguns:item.pistol.cock) is a reload recording, so it sounds wrong at the end of a reload.",
                "Other unused-by-SC2 candidates:",
                "  scguns:item.bolt.bolt              (拉栓, longer/fuller bolt cycle)",
                "  scguns:item.bolt_release.bolt_release (放栓, pushing the bolt back in)",
                "  scguns:item.rack.rack              (上膛, mechanical rack)",
                "  scguns:item.reload_end.reload_end  (装填完毕, SC2's generic completion cue)",
                "  scguns:item.mag_in.mag_in / scguns:item.mag_out.mag_out (弹匣)");
        RELOAD_END_SOUND = BUILDER.define("reload_end_sound", "scguns:item.bolt_pull.bolt_pull");

        BUILDER.pop();
        BUILDER.push("search_radii");

        BUILDER.comment("Target search radius for sniper rifles");
        SNIPER_SEARCH_RADIUS = BUILDER.defineInRange("sniper_search_radius", 96.0, 8.0, 512.0);

        BUILDER.comment("Target search radius for rifles and magnums");
        RIFLE_MAGNUM_SEARCH_RADIUS = BUILDER.defineInRange("rifle_magnum_search_radius", 64.0, 8.0, 256.0);

        BUILDER.comment("Target search radius for SMGs, shotguns, pistols");
        SMG_SHOTGUN_PISTOL_SEARCH_RADIUS = BUILDER.defineInRange("smg_shotgun_pistol_search_radius", 48.0, 8.0, 128.0);

        BUILDER.comment("When enabled, maid gun AI always leads moving targets (SCG only does this on Hard difficulty)");
        LEAD_ALWAYS = BUILDER.define("lead_prediction_always", true);

        BUILDER.comment("How much the lead direction is mixed into the aim (SCG default 0.3). 1.0 = full prediction");
        LEAD_WEIGHT = BUILDER.defineInRange("lead_prediction_weight", 0.3, 0.0, 1.0);

        BUILDER.comment("SCG Extra 联动：把玩家和女仆编入独立阵营 \"player\"，让 SCG Extra 阵营怪主动攻击它们");
        ENABLE_PLAYER_FACTION = BUILDER.define("enable_player_faction", true);

        BUILDER.comment("SCG Extra 联动：同一阵营的怪互相射击时，子弹直接穿过友军不造成友伤（玩家/女仆的弹体不受影响）");
        PREVENT_FACTION_FRIENDLY_FIRE = BUILDER.define("prevent_faction_friendly_fire", true);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
