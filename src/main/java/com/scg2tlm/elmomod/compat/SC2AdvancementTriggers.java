package com.scg2tlm.elmomod.compat;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitTrigger;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

public final class SC2AdvancementTriggers {
    public static final String FIRST_SHOT = "scg2_first_shot";
    public static final String KILL_MOB = "scg2_kill_mob";
    public static final String LASER_MUSKET_100_ILLAGER = "scg2_laser_musket_100_illager";
    public static final String KILL_1000 = "scg2_kill_1000";
    public static final String RELOAD_DING = "scg2_reload_ding";

    public static void triggerForMaidOwner(EntityMaid maid, String event) {
        MinecraftServer server = maid.getServer();
        if (server == null) return;
        UUID ownerUuid = maid.getOwnerUUID();
        if (ownerUuid == null) return;
        ServerPlayer player = server.getPlayerList().getPlayer(ownerUuid);
        if (player != null) {
            InitTrigger.MAID_EVENT.trigger(player, event);
        }
    }

    private SC2AdvancementTriggers() {}
}
