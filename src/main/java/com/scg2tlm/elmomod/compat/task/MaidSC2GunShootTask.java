package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.SCG2TLMConfig;
import com.scg2tlm.elmomod.compat.SC2AdvancementTriggers;
import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.scg2tlm.elmomod.mixin.EntityMaidTaskDataInvoker;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.IChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.chatbubble.implement.TextChatBubbleData;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.tartaricacid.touhoulittlemaid.init.InitSounds;
import com.google.common.collect.ImmutableMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;
import top.ribs.scguns.item.GunItem;

import java.util.Optional;

public class MaidSC2GunShootTask extends Behavior<EntityMaid> {
    private int attackCooldown = 0;
    private int seeTime = 0;
    private int reloadTimer = 0;
    private boolean isReloading = false;
    private int reloadFailCooldown = 0;
    private long lastChatBubbleGameTime = 0;
    private long lastFireChatGameTime = 0;
    private BlockPos savedRestrictCenter;
    private float savedRestrictRadius;
    private boolean restrictionExpanded;

    public MaidSC2GunShootTask() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
        ), 60, Integer.MAX_VALUE);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        if (maid.isMaidInSittingPose()) return false;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return false;
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected boolean canStillUse(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return false;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return false;
        if (!maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) return false;
        LivingEntity target = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).get();
        if (!target.isAlive() || target.isRemoved()) {
            maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
            maid.setTarget(null);
            return false;
        }
        return true;
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return;
        ItemStack gun = maid.getMainHandItem();
        gun.getOrCreateTag().remove("scg2tlm:reloading");
        ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(new CompoundTag());
        if (maid.hasRestriction() && !restrictionExpanded) {
            savedRestrictCenter = maid.getRestrictCenter();
            savedRestrictRadius = maid.getRestrictRadius();
            int newRadius = Math.min(Math.max(48, (int) savedRestrictRadius * 3), 96);
            maid.restrictTo(savedRestrictCenter, newRadius);
            restrictionExpanded = true;
        }
        maid.setSwingingArms(true);
        maid.getMainHandItem().getOrCreateTag().putBoolean("scg2tlm:in_combat", true);
        if (gameTime - lastChatBubbleGameTime >= 600) {
            lastChatBubbleGameTime = gameTime;
            String[] keys = {
                    "chat_bubble.scg2_maid_compat.spot.0",
                    "chat_bubble.scg2_maid_compat.spot.1",
                    "chat_bubble.scg2_maid_compat.spot.2"
            };
            String key = keys[maid.getRandom().nextInt(keys.length)];
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(600, Component.translatable(key), IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY));
            SoundEvent sound = InitSounds.MAID_FIND_TARGET.get();
            if (sound != null) {
                maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        if (restrictionExpanded) {
            maid.restrictTo(savedRestrictCenter, (int) savedRestrictRadius);
            restrictionExpanded = false;
        }
        ItemStack gun = maid.getMainHandItem();
        if (SC2GunCompat.isSC2Gun(gun) && SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun)) {
            showNoAmmoChat(maid, gameTime);
        }
        seeTime = 0;
        reloadTimer = 0;
        isReloading = false;
        reloadFailCooldown = 0;
        maid.setSwingingArms(false);
        maid.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
        gun.getOrCreateTag().remove("scg2tlm:reloading");
        gun.getOrCreateTag().remove("scg2tlm:in_combat");
        gun.getOrCreateTag().remove("scg2tlm:firing");
        ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(new CompoundTag());
    }

    @Override
    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (maid.isMaidInSittingPose()) return;
        ItemStack gun = maid.getMainHandItem();
        if (!SC2GunCompat.isSC2Gun(gun)) return;

        SC2GunCompat.tickEnergyRecharge(gun);

        if (isReloading) {
            maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
                if (target.isAlive()) {
                    BehaviorUtils.lookAtEntity(maid, target);
                }
            });
            reloadTimer--;
            if (reloadTimer <= 0) {
                boolean reloaded = SC2GunCompat.reloadFromInventory(maid, gun);
                isReloading = false;
                gun.getOrCreateTag().remove("scg2tlm:reloading");
                ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(new CompoundTag());
                if (reloaded) {
                    ResourceLocation gunId = ForgeRegistries.ITEMS.getKey(gun.getItem());
                    if (gunId != null && "scguns".equals(gunId.getNamespace())
                            && ("grandle_og".equals(gunId.getPath()) || "grandle".equals(gunId.getPath()))) {
                        SC2AdvancementTriggers.triggerForMaidOwner(maid, SC2AdvancementTriggers.RELOAD_DING);
                    }
                    SC2GunCompat.playCockSound(maid, gun);
                    attackCooldown = 5;
                    maid.setSwingingArms(true);
                } else {
                    reloadFailCooldown = 100;
                    maid.setSwingingArms(false);
                    showNoAmmoChat(maid, gameTime);
                }
            }
            return;
        }

        if (reloadFailCooldown > 0) {
            reloadFailCooldown--;
            if (reloadFailCooldown <= 0) {
                if (SC2GunCompat.hasAmmoInInventory(maid, gun)) {
                    reloadFailCooldown = 0;
                } else {
                    showNoAmmoChat(maid, gameTime);
                }
            }
            return;
        }

        boolean needsReload = SC2GunCompat.needsReload(gun);
        boolean hasAmmoInInv = SC2GunCompat.hasAmmoInInventory(maid, gun);

        if (tryBayonetMelee(maid, gun)) {
            return;
        }

        if (needsReload && !hasAmmoInInv) {
            showNoAmmoChat(maid, gameTime);
            return;
        }

        if (needsReload) {
            if (hasAmmoInInv) {
                if (gun.getTag() != null && gun.getTag().contains("scg2tlm:reloading")) {
                    gun.getTag().remove("scg2tlm:reloading");
                    ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(new CompoundTag());
                    return;
                }
                startReload(maid, gun, gameTime);
            } else {
                maid.setSwingingArms(false);
            }
            return;
        }

        maid.setSwingingArms(true);
        maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).ifPresent(target -> {
            if (!target.isAlive() || maid.distanceToSqr(target) > getMaxTargetRangeSqr()) {
                maid.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
                maid.setTarget(null);
                return;
            }
            maid.setTarget(target);
            maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());

            boolean canSee = maid.canSee(target);
            if (canSee != (seeTime > 0)) {
                seeTime = 0;
            }
            if (canSee) {
                seeTime++;
            } else {
                seeTime--;
            }

            if (attackCooldown > 0) {
                if (--attackCooldown > 0) {
                    return;
                }
            }

            if (seeTime >= -60 && canSee) {
                maid.swing(InteractionHand.MAIN_HAND);
                attackCooldown = doRangedAttack(maid, target);
                if (attackCooldown <= 0) {
                    attackCooldown = 5;
                }
                if (gameTime - lastFireChatGameTime >= 600) {
                    lastFireChatGameTime = gameTime;
                    String[] keys = {
                            "chat_bubble.scg2_maid_compat.fire.0",
                            "chat_bubble.scg2_maid_compat.fire.1",
                            "chat_bubble.scg2_maid_compat.fire.2",
                            "chat_bubble.scg2_maid_compat.fire.3"
                    };
                    String key = keys[maid.getRandom().nextInt(keys.length)];
                    maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(600, Component.translatable(key), IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY));
                    SoundEvent sound = InitSounds.MAID_RANGE_ATTACK.get();
                    if (sound != null) {
                        maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
                    }
                }
            }
        });
    }

    private void startReload(EntityMaid maid, ItemStack gun, long gameTime) {
        SC2GunCompat.playPreReloadSound(maid, gun);
        SC2GunCompat.playReloadSound(maid, gun);
        reloadTimer = SC2GunCompat.getReloadTime(gun);
        isReloading = true;
        maid.setSwingingArms(false);
        gun.getOrCreateTag().putInt("scg2tlm:reloading", reloadTimer);
        CompoundTag taskData = new CompoundTag();
        taskData.putBoolean("scg2tlm:reloading", true);
        ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(taskData);
        if (gameTime - lastChatBubbleGameTime >= 600) {
            lastChatBubbleGameTime = gameTime;
            String[] keys = {
                    "chat_bubble.scg2_maid_compat.reload.0",
                    "chat_bubble.scg2_maid_compat.reload.1",
                    "chat_bubble.scg2_maid_compat.reload.2"
            };
            String key = keys[maid.getRandom().nextInt(keys.length)];
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(600, Component.translatable(key), IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY));
            SoundEvent sound = InitSounds.MAID_AI_CHAT.get();
            if (sound != null) {
                maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
            }
        }
    }

    private void showNoAmmoChat(EntityMaid maid, long gameTime) {
        if (gameTime - lastChatBubbleGameTime < 600) return;
        lastChatBubbleGameTime = gameTime;
        String[] keys = {
                "chat_bubble.scg2_maid_compat.no_ammo.0",
                "chat_bubble.scg2_maid_compat.no_ammo.1",
                "chat_bubble.scg2_maid_compat.no_ammo.2",
                "chat_bubble.scg2_maid_compat.no_ammo.3"
        };
        String key = keys[maid.getRandom().nextInt(keys.length)];
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.create(600, Component.translatable(key), IChatBubbleData.TYPE_2, IChatBubbleData.DEFAULT_PRIORITY));
        SoundEvent sound = InitSounds.MAID_AI_CHAT.get();
        if (sound != null) {
            maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
        }
    }

    private int doRangedAttack(EntityMaid maid, LivingEntity target) {
        try {
            return SC2GunCompat.performGunAttack(maid, target, maid.getMainHandItem());
        } catch (Exception e) {
            return 100;
        }
    }

    private double getMaxTargetRangeSqr() {
        double range = SC2GunCompat.getGunRange();
        return range * range;
    }

    private boolean tryBayonetMelee(EntityMaid maid, ItemStack gun) {
        if (!SCG2TLMConfig.BAYONET_MELEE.get()) return false;
        if (!SC2GunCompat.hasBayonet(gun)) return false;
        Optional<LivingEntity> targetOpt = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        if (targetOpt.isEmpty()) return false;
        LivingEntity target = targetOpt.get();
        if (!target.isAlive()) return false;
        if (maid.distanceTo(target) > SC2GunCompat.getMeleeReach(gun)) return false;

        if (attackCooldown > 0) {
            attackCooldown--;
        } else {
            maid.swing(InteractionHand.MAIN_HAND);
            attackCooldown = SC2GunCompat.performMeleeAttack(maid, target, gun);
            if (attackCooldown <= 0) {
                attackCooldown = 5;
            }
        }
        maid.getLookControl().setLookAt(target.getX(), target.getEyeY(), target.getZ());
        return true;
    }
}
