package com.plusls.ommc.mixin.feature.disablePistonPushEntity;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonMovingBlockEntity;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Collections;
import java.util.List;

/**
 * 禁止活塞推动实体（玩家除外）。
 *
 * <p>移植自 Fabric 版 {@code mixin/feature/disablePistonPushEntity/MixinPistonBlockEntity}。
 *
 * <p><b>它解决什么</b>：活塞推动实体时会做「碰撞箱挤压」计算，实体越多越卡；
 * 而在客户端这纯粹是预测性的视觉行为。把这个列表过滤成「只有玩家」，
 * 就能显著减少大范围活塞阵列（刷怪塔、农场）的卡顿。
 *
 * <p><b>为什么保留玩家</b>：玩家被活塞推动是有实际体验的（比如被推上台阶），
 * 也常常是玩家自己搭的装置需要的效果；过滤掉反而像「活塞穿模」。
 *
 * <p><b>与 Fabric 版的差异</b>：Fabric 用 MixinExtras 的 {@code @WrapOperation}
 * （需要额外的前置库），这里用原版 Mixin 自带的 {@link Redirect}，
 * 效果等价：把 {@code Level.getEntities(Entity, AABB)} 的返回值换掉。
 * 这样不引入对 MixinExtras 的依赖 —— Forge 47.x 虽然内置了它，但版本随 Forge 变动，
 * 少一个可变因素更稳。
 *
 * <p>三个前置条件缺一不可：客户端侧（{@code isClientSide}）、开关打开、
 * 以及玩家确实与活塞的挤压范围相交 —— 否则照常返回原列表，不影响正常推动。
 */
@Mixin(PistonMovingBlockEntity.class)
public class MixinPistonMovingBlockEntity {

    @Redirect(
            method = "moveCollidedEntities",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getEntities(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/phys/AABB;)Ljava/util/List;"))
    private static List<Entity> ommc$onlyPlayerCanBePushed(Level level, Entity except, AABB box) {
        if (!level.isClientSide || !Configs.FeatureToggle.DISABLE_PISTON_PUSH_ENTITY.getBooleanValue()) {
            return level.getEntities(except, box);
        }

        Entity player = Minecraft.getInstance().player;
        if (player != null && !player.isSpectator() && player.getBoundingBox().intersects(box)) {
            return List.of(player);
        }
        return Collections.emptyList();
    }
}
