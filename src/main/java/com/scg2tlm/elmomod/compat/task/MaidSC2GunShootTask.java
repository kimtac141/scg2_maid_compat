package com.scg2tlm.elmomod.compat.task;

import com.scg2tlm.elmomod.compat.SC2GunCompat;
import com.scg2tlm.elmomod.mixin.EntityMaidTaskDataInvoker;
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
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.item.ItemStack;

public class MaidSC2GunShootTask extends Behavior<EntityMaid> {
    private int attackCooldown = -1;
    private int seeTime = 0;
    private int reloadTimer = 0;
    private boolean isReloading = false;
    private long lastChatBubbleGameTime = 0;
    private long lastFireChatGameTime = 0;

    public MaidSC2GunShootTask() {
        super(ImmutableMap.of(
                MemoryModuleType.LOOK_TARGET, MemoryStatus.REGISTERED,
                MemoryModuleType.ATTACK_TARGET, MemoryStatus.VALUE_PRESENT
        ), 1200);
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
        return maid.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET);
    }

    @Override
    protected void start(ServerLevel level, EntityMaid maid, long gameTime) {
        maid.setSwingingArms(true);
        maid.getMainHandItem().getOrCreateTag().putBoolean("scg2tlm:in_combat", true);
        if (gameTime - lastChatBubbleGameTime >= 100) {
            lastChatBubbleGameTime = gameTime;
            String[] keys = {
                    "chat_bubble.scg2_maid_compat.spot.0",
                    "chat_bubble.scg2_maid_compat.spot.1",
                    "chat_bubble.scg2_maid_compat.spot.2"
            };
            String key = keys[maid.getRandom().nextInt(keys.length)];
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type1(Component.translatable(key)));
            SoundEvent sound = InitSounds.MAID_FIND_TARGET.get();
            if (sound != null) {
                maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        ItemStack gun = maid.getMainHandItem();
        if (SC2GunCompat.isSC2Gun(gun) && SC2GunCompat.needsReload(gun) && !SC2GunCompat.hasAmmoInInventory(maid, gun)) {
            showNoAmmoChat(maid, gameTime);
        }
        seeTime = 0;
        attackCooldown = -1;
        reloadTimer = 0;
        isReloading = false;
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

        if (isReloading) {
            reloadTimer--;
            if (reloadTimer <= 0) {
                SC2GunCompat.reloadFromInventory(maid, gun);
                isReloading = false;
                attackCooldown = 5;
                maid.setSwingingArms(true);
                gun.getOrCreateTag().remove("scg2tlm:reloading");
                ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(new CompoundTag());
            }
            return;
        }

        boolean needsReload = SC2GunCompat.needsReload(gun);
        boolean hasAmmoInInv = SC2GunCompat.hasAmmoInInventory(maid, gun);

        if (needsReload && !hasAmmoInInv) {
            showNoAmmoChat(maid, gameTime);
            return;
        }

        if (needsReload) {
            if (hasAmmoInInv) {
                if (gun.getTag() != null && gun.getTag().contains("scg2tlm:reloading")) {
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
                attackCooldown--;
                return;
            }

            if (seeTime >= -60 && canSee) {
                maid.swing(InteractionHand.MAIN_HAND);
                attackCooldown = doRangedAttack(maid, target);
                if (attackCooldown <= 0) {
                    attackCooldown = 5;
                }
                if (gameTime - lastFireChatGameTime >= 200) {
                    lastFireChatGameTime = gameTime;
                    String[] keys = {
                            "chat_bubble.scg2_maid_compat.fire.0",
                            "chat_bubble.scg2_maid_compat.fire.1",
                            "chat_bubble.scg2_maid_compat.fire.2",
                            "chat_bubble.scg2_maid_compat.fire.3"
                    };
                    String key = keys[maid.getRandom().nextInt(keys.length)];
                    maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type1(Component.translatable(key)));
                    SoundEvent sound = InitSounds.MAID_RANGE_ATTACK.get();
                    if (sound != null) {
                        maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
                    }
                }
            }
        });
    }

    private void startReload(EntityMaid maid, ItemStack gun, long gameTime) {
        reloadTimer = SC2GunCompat.getReloadTime(gun);
        isReloading = true;
        maid.setSwingingArms(false);
        gun.getOrCreateTag().putInt("scg2tlm:reloading", reloadTimer);
        CompoundTag taskData = new CompoundTag();
        taskData.putBoolean("scg2tlm:reloading", true);
        ((EntityMaidTaskDataInvoker) maid).invokeSetSyncTaskData(taskData);
        if (gameTime - lastChatBubbleGameTime >= 100) {
            lastChatBubbleGameTime = gameTime;
            String[] keys = {
                    "chat_bubble.scg2_maid_compat.reload.0",
                    "chat_bubble.scg2_maid_compat.reload.1",
                    "chat_bubble.scg2_maid_compat.reload.2"
            };
            String key = keys[maid.getRandom().nextInt(keys.length)];
            maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type1(Component.translatable(key)));
            SoundEvent sound = InitSounds.MAID_AI_CHAT.get();
            if (sound != null) {
                maid.playSound(sound, 1.0f, 0.8f + maid.getRandom().nextFloat() * 0.4f);
            }
        }
    }

    private void showNoAmmoChat(EntityMaid maid, long gameTime) {
        if (gameTime - lastChatBubbleGameTime < 100) return;
        lastChatBubbleGameTime = gameTime;
        String[] keys = {
                "chat_bubble.scg2_maid_compat.no_ammo.0",
                "chat_bubble.scg2_maid_compat.no_ammo.1",
                "chat_bubble.scg2_maid_compat.no_ammo.2",
                "chat_bubble.scg2_maid_compat.no_ammo.3"
        };
        String key = keys[maid.getRandom().nextInt(keys.length)];
        maid.getChatBubbleManager().addChatBubble(TextChatBubbleData.type1(Component.translatable(key)));
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
}
