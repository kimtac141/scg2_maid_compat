package com.scg2tlm.elmomod.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.scg2tlm.elmomod.compat.SC2HealCompat;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import top.ribs.scguns.entity.projectile.ProjectileEntity;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Mixin(ProjectileEntity.class)
public class ProjectileEntityMixin {

    /**
     * 治疗弹识别：{@code impactEffect == minecraft:instant_health}。
     *
     * <p>SC2 的医疗枪（十字军）配置里 {@code projectile.impactEffect} 就是
     * {@code minecraft:instant_health}，且 {@code damage=0.0}。用它作为判据，
     * 让治疗弹豁免下面的友军拦截。</p>
     *
     * <p><b>异常时返回 false</b>：宁可保守地拦住（保持原有的友伤保护），
     * 也不能因为判据出错而误伤友军。</p>
     */
    private boolean scg2tlm$isHealingProjectile() {
        try {
            ProjectileEntity self = (ProjectileEntity) (Object) this;
            var projectile = self.getProjectile();
            if (projectile == null) return false;
            ResourceLocation effect = projectile.getImpactEffect();
            return effect != null && SC2HealCompat.INSTANT_HEALTH.equals(effect);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 候选实体是不是「发射者自己」。
     *
     * <p>弹丸的出膛点常常落在射击者自己的碰撞箱内，而 SC2 是靠
     * {@code canHitEntity}（{@code PROJECTILE_TARGETS} 谓词）来排除射手的。
     * 但我们的治疗弹<b>绕过了友军过滤</b>，如果那个谓词本身不排除射手，
     * 就会出现「女仆的治疗弹打在自己身上」——弹药白费、主人反而治不到。</p>
     *
     * <p>因此这里显式排除射手。用<b>身份比较</b>（{@code ==}）而不是 {@code equals}，
     * 语义最严格：只有恰好是发射者本人才排除。</p>
     *
     * <p>注意：{@code getOwner()} 与 {@code getShooter()} 都试一遍，
     * 不同弹丸类二者可能只填一个。</p>
     */
    private boolean scg2tlm$isShooter(Entity candidate) {
        if (candidate == null) return false;
        try {
            ProjectileEntity self = (ProjectileEntity) (Object) this;
            return self.getOwner() == candidate || self.getShooter() == candidate;
        } catch (Throwable t) {
            return false;
        }
    }

    @Inject(method = "onHitEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private void scg2tlm$onHitEntity(Entity target, Vec3 hitPos, Vec3 startPos, Vec3 endPos, boolean headshot, CallbackInfo ci) {
        Entity shooter = ((ProjectileEntity) (Object) this).getOwner();

        // 治疗弹豁免：它的作用就是给友军回血，必须能命中友军
        boolean healing = scg2tlm$isHealingProjectile();

        // 但绝不作用于发射者自己（否则女仆给自己回血、白白浪费弹药与一次施法）
        if (healing && scg2tlm$isShooter(target)) {
            ci.cancel();
            return;
        }

        if (target instanceof Player && shooter instanceof EntityMaid) {
            if (!healing) {
                ci.cancel();
            }
            return;
        }

        if (!(target instanceof EntityMaid victim)) return;
        if (healing) return;   // 治疗弹对友方女仆同样放行

        if (shooter instanceof EntityMaid maid) {
            Optional<? extends LivingEntity> a = maid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            Optional<? extends LivingEntity> b = victim.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
            if (a.isPresent() && b.isPresent() && a.get() == b.get()) {
                ci.cancel();
            }
        } else if (shooter instanceof Player player) {
            if (player.getUUID().equals(victim.getOwnerUUID())) {
                ci.cancel();
            }
        }
    }

    @Redirect(method = {"findEntityOnPath", "findEntitiesOnPath"},
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;"),
            remap = false)
    private List<Entity> scg2tlm$filterFriendlyMaidEntities(Level level, Entity projectileEntity, AABB aabb, Predicate<? super Entity> original, ProjectileEntity self) {
        // 治疗弹不做友军过滤：否则弹丸会穿过需要治疗的主人/友方女仆。
        // 但必须排除发射者自己 —— 弹丸出膛点常落在女仆自己的碰撞箱内，
        // 否则治疗弹会先打中自己（弹药白费、主人反而治不到）。
        if (scg2tlm$isHealingProjectile()) {
            return level.getEntities(projectileEntity, aabb,
                    ((Predicate<Entity>) original).and(e -> !scg2tlm$isShooter(e)));
        }
        return level.getEntities(projectileEntity, aabb,
                ((Predicate<Entity>) original).and(e -> !isFriendlyMaid(self, e)));
    }

    private static boolean isFriendlyMaid(ProjectileEntity projectile, Entity candidate) {
        if (!(candidate instanceof EntityMaid victim)) return false;
        Entity shooter = projectile.getOwner();
        if (!(shooter instanceof EntityMaid shooterMaid)) {
            if (shooter instanceof Player player) {
                return victim.getOwnerUUID() != null && victim.getOwnerUUID().equals(player.getUUID());
            }
            return false;
        }
        if (victim.getOwnerUUID() != null && victim.getOwnerUUID().equals(shooterMaid.getOwnerUUID())) {
            return true;
        }
        Optional<? extends LivingEntity> a = shooterMaid.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        Optional<? extends LivingEntity> b = victim.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET);
        return a.isPresent() && b.isPresent() && a.get() == b.get();
    }
}
