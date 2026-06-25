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

    public static final ForgeConfigSpec.DoubleValue SNIPER_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue RIFLE_MAGNUM_SEARCH_RADIUS;
    public static final ForgeConfigSpec.DoubleValue SMG_SHOTGUN_PISTOL_SEARCH_RADIUS;

    static {
        BUILDER.push("combat");

        BUILDER.comment("Accuracy spread for maid gun attacks (lower = more accurate)");
        GUN_ACCURACY = BUILDER.defineInRange("gun_accuracy", 2.0, 0.0, 20.0);

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
