package com.plusls.ommc.mixin.feature.blockModelNoOffset;

import com.plusls.ommc.feature.blockModelNoOffset.BlockModelNoOffsetUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 方块模型没有偏移：取消花草这类方块的随机位置抖动。
 *
 * <p>对应 Fabric 版的 {@code MixinBlockModelRenderer}（挂在渲染器的 {@code getOffset} 调用处）。
 *
 * <h2>为什么改挂 {@code BlockStateBase#getOffset} 而不是渲染器</h2>
 * 原移植版本挂在 {@code ModelBlockRenderer} 上，结果无法注入：在 1.20.1 里带
 * {@code getOffset} 调用的只有 Forge 新增的那个 12 参 {@code tesselateBlock} 重载，
 * 而这个重载<b>在官方映射里没有 SRG 名</b>，Mixin 注解处理器生不出 refmap 条目，
 * 启动时直接 {@code InjectionError} 崩客户端（项目内 mixins.json 的注释记录了这次踩坑）。
 *
 * <p>换个角度就绕开了：既然渲染器最终只是调 {@code state.getOffset(level, pos)} 拿偏移量，
 * 那直接让这个方法返回零向量即可 —— 它是原版方法、在所有映射里都有名字，
 * 而且<b>所有</b>渲染路径（原版、Forge 的扩展重载、以及别的模组自己算偏移）都会受益，
 * 覆盖面反而比只改渲染器更全。
 *
 * <p>用 {@code @Inject(RETURN)} + {@code setReturnValue} 而不是 {@code @Overwrite}：
 * 只在「本来就非零」时才改写，原版返回零的情况直接放过，省掉名单匹配。
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class MixinBlockStateBase {

    @Inject(method = "getOffset", at = @At("RETURN"), cancellable = true)
    private void ommc$blockModelNoOffset(BlockGetter level, BlockPos pos, CallbackInfoReturnable<Vec3> cir) {
        if (Vec3.ZERO.equals(cir.getReturnValue())) {
            return;
        }
        BlockState state = (BlockState) (Object) this;
        if (BlockModelNoOffsetUtil.shouldNoOffset(state)) {
            cir.setReturnValue(Vec3.ZERO);
        }
    }
}
