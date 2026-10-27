package com.scg2tlm.elmomod;

import net.neoforged.neoforge.common.ModConfigSpec;

public class SCG2TLMConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue GUN_ACCURACY;
    public static final ModConfigSpec.DoubleValue GUN_RANGE;

    public static final ModConfigSpec.DoubleValue GRENADE_RADIUS;
    public static final ModConfigSpec.IntValue GRENADE_COOLDOWN;
    public static final ModConfigSpec.IntValue GRENADE_POST_THROW_TIME;

    public static final ModConfigSpec.DoubleValue MELEE_RANGE;
    public static final ModConfigSpec.DoubleValue MELEE_SPEED;
    public static final ModConfigSpec.ConfigValue<String> RELOAD_END_SOUND;

    public static final ModConfigSpec.DoubleValue SNIPER_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue RIFLE_MAGNUM_SEARCH_RADIUS;
    public static final ModConfigSpec.DoubleValue SMG_SHOTGUN_PISTOL_SEARCH_RADIUS;

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

        BUILDER.comment("Sound event played when a maid FINISHES reloading (empty = silent).",
                "  scguns:item.bolt_pull.bolt_pull / scguns:item.bolt.bolt / scguns:item.rack.rack /",
                "  scguns:item.reload_end.reload_end / scguns:item.mag_in.mag_in");
        RELOAD_END_SOUND = BUILDER.define("reload_end_sound", "scguns:item.bolt_pull.bolt_pull");

        BUILDER.pop();
        BUILDER.push("search_radii");

        BUILDER.comment("Target search radius for sniper rifles");
        SNIPER_SEARCH_RADIUS = BUILDER.defineInRange("sniper_search_radius", 96.0, 8.0, 512.0);

        BUILDER.comment("Target search radius for rifles and magnums");
        RIFLE_MAGNUM_SEARCH_RADIUS = BUILDER.defineInRange("rifle_magnum_search_radius", 64.0, 8.0, 256.0);

        BUILDER.comment("Target search radius for SMGs, shotguns, pistols");
        SMG_SHOTGUN_PISTOL_SEARCH_RADIUS = BUILDER.defineInRange("smg_shotgun_pistol_search_radius", 48.0, 8.0, 128.0);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
