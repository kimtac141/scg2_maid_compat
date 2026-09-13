package com.scg2tlm.elmomod;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitEntities;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.ModList;
import org.spongepowered.asm.mixin.Mixins;
import java.util.Optional;
import java.util.UUID;

@Mod(ExampleMod.MODID)
public class ExampleMod {
    public static final String MODID = "scg2_maid_compat";

    public ExampleMod(FMLJavaModLoadingContext context) {
        context.registerConfig(ModConfig.Type.COMMON, SCG2TLMConfig.SPEC);
        MinecraftForge.EVENT_BUS.register(this);

        // scgextra 是可选的：只有装了 scgextra 才注册其专属 mixin 配置，避免缺失类导致崩溃
        if (ModList.get().isLoaded("scgextra")) {
            Mixins.addConfiguration("scg2_maid_compat_scgextra.mixins.json");
        }

        if (FMLEnvironment.dist.isClient()) {
            ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                    () -> new ConfigScreenHandler.ConfigScreenFactory((modContainer, screen) ->
                            ModList.get().isLoaded("cloth_config")
                                    ? com.scg2tlm.elmomod.client.SCG2TLMClothConfig.createScreen(screen)
                                    : null));
        }
    }

    @SubscribeEvent
    public void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof top.ribs.scguns.entity.projectile.ProjectileEntity)) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof EntityMaid shooter)) {
            return;
        }

        if (event.getEntity() instanceof Player
                && shooter.getOwnerUUID() != null && shooter.getOwnerUUID().equals(event.getEntity().getUUID())) {
            event.setCanceled(true);
            return;
        }

        if (event.getEntity() instanceof EntityMaid victim) {
            Optional<? extends LivingEntity> a = shooter.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            Optional<? extends LivingEntity> b = victim.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            if (a.isPresent() && b.isPresent() && a.get() == b.get()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onMaidHurtTargetBack(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof EntityMaid maid)) {
            return;
        }
        if (maid.level().isClientSide) {
            return;
        }
        if (!SC2GunCompat.isSC2Gun(maid.getMainHandItem())) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        if (attacker == maid || !attacker.isAlive()) {
            return;
        }
        if (attacker instanceof EntityMaid) {
            return;
        }
        UUID owner = maid.getOwnerUUID();
        if (owner != null && attacker.getUUID().equals(owner)) {
            return;
        }
        maid.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, attacker);
    }

    // SCG 本体 bug 修复：Viventrum 没有攻击黑名单，会索敌主人名下的其他随从（宠物）。取消该索敌。
    @SubscribeEvent
    public void onLivingChangeTarget(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof top.ribs.scguns.entity.monster.ViventrumEntity viventrum)) {
            return;
        }
        if (!(event.getNewTarget() instanceof TamableAnimal targetPet)) {
            return;
        }
        LivingEntity owner = viventrum.getOwner();
        if (owner != null && owner == targetPet.getOwner()) {
            event.setNewTarget(null);
        }
    }

    // SCG Extra 阵营友伤防护：同一阵营的怪互相攻击时取消伤害（覆盖所有 SCG 弹体类型 + 近战/爆炸）。
    // 放行玩家/女仆（"player" 阵营）发起的伤害，保留原有行为。
    @SubscribeEvent
    public void onFactionFriendlyFire(LivingHurtEvent event) {
        if (!SCG2TLMConfig.PREVENT_FACTION_FRIENDLY_FIRE.get()) {
            return;
        }
        // scgextra 未安装时直接跳过。这里刻意不放进 SCGExtraCompatHelper：
        // 那个类要保持「零依赖、纯反射」，才能在最坏情况下安全降级。
        if (!net.minecraftforge.fml.ModList.get().isLoaded("scgextra")) {
            return;
        }
        if (event.getEntity().level().isClientSide) {
            return;
        }
        Entity attacker = event.getSource().getEntity();
        if (attacker == null) {
            attacker = event.getSource().getDirectEntity();
        }
        if (attacker instanceof Projectile projectile) {
            attacker = projectile.getOwner();
        }
        if (!(attacker instanceof LivingEntity attackerLiving)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        EntityType<?> type = attackerLiving.getType();
        if (type == EntityType.PLAYER || type == InitEntities.MAID.get()) {
            return;
        }
        if (SCGExtraCompatHelper.isFriendlies(attackerLiving, victim)) {
            event.setCanceled(true);
        }
    }
}
