package com.plusls.ommc.mixin.feature.worldEaterMineHelper;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.feature.worldEaterMineHelper.WorldEaterMineHelperUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 世吞挖矿助手（第二部分：渲染）：把命中白名单、且暴露在空气中的方块换成「镜像模型」。
 *
 * <p>对应 Fabric 版的 {@code MixinBlockRenderContext} / {@code MixinTerrainRenderContext}
 * （分别对接 FRAPI 与 Sodium 的渲染入口）。<b>Forge 上没有 FRAPI，也没有 Sodium</b>，
 * 所以这里挂的是 Forge/原版自己的区块渲染入口：{@code BlockRenderDispatcher.renderBatched}。
 *
 * <h2>为什么 remap = false（这条很关键）</h2>
 * 9 参 {@code renderBatched} 是 <b>Forge 新增的重载</b>，官方映射里没有它的 SRG 名，
 * Mixin 注解处理器因此写不出 refmap 条目，编译期直接报
 * {@code Unable to locate obfuscation mapping for @Inject target}。
 * 加 {@code remap = false} 让 Mixin 原样使用这个字面名字 —— 运行时 Forge 加的方法本来就叫
 * {@code renderBatched}（它不存在于原版 SRG 表里，也就没有被重命名），所以这样能正确命中。
 * 描述符里的类名也不用重映射：SRG 只改方法名与字段名，类名与官方名一致。
 *
 * <h2>为什么挂 9 参重载</h2>
 * 1.20.1 里 {@code renderBatched} 有两个重载：原版的 7 参与 Forge 新增的 9 参
 * （多的两个参数是 {@code ModelData} 与 {@code RenderType}）。7 参那个<b>只是把调用转给 9 参</b>
 * （{@code ModelData.EMPTY, null}），真正的绘制全在 9 参里，所以注在 9 参既覆盖全部调用、
 * 也不会因为同名重载产生歧义。
 *
 * <h2>为什么挂 renderBatched</h2>
 * 区块重建时每个方块都会走一次 {@code renderBatched}，它内部就是
 * 「取模型 → {@code ModelBlockRenderer.tesselateBlock} 生成顶点」。
 * 在入口处拦下来，命中时用镜像模型重新跑一遍这套流程再取消原流程，
 * 效果与 Fabric 版「在 emitBlockQuads 里换模型」等价，而且不用碰模型 bake 之后的缓存。
 *
 * <h2>与 Fabric 版的差异</h2>
 * <ul>
 *   <li>用 {@code customFullModels}（镜像 + 原模型）而不是 {@code customModels}：
 *       Forge 这条路径是<b>整体替换</b>而不是「额外补发一批顶点」，
 *       只给镜像会让方块本体消失。</li>
 *   <li>不去改 {@code BlockState} 的 {@code lightEmission} 来「打亮」镜像
 *       （Fabric 版的做法）。那条路要写一个可变的 state 字段，风险大；
 *       这里直接沿用原亮度 —— 镜像是给人「看轮廓」用的，亮度不是关键。</li>
 * </ul>
 *
 * <p><b>性能</b>：{@link WorldEaterMineHelperUtil#shouldUseCustomModel} 会向上扫最多 20 格，
 * 所以第一件事就是判断总开关；关着的时候每方块只多一次 boolean 读取。
 */
@Mixin(BlockRenderDispatcher.class)
public class MixinBlockRenderDispatcher {

    @Inject(method = "renderBatched(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/BlockAndTintGetter;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZLnet/minecraft/util/RandomSource;Lnet/minecraftforge/client/model/data/ModelData;Lnet/minecraft/client/renderer/RenderType;)V", at = @At("HEAD"), cancellable = true, remap = false)
    private void ommc$renderWorldEaterMirror(BlockState state, BlockPos pos, BlockAndTintGetter level,
                                             PoseStack poseStack, VertexConsumer consumer, boolean checkSides,
                                             RandomSource random, ModelData modelData, RenderType renderType,
                                             CallbackInfo ci) {
        if (!Configs.FeatureToggle.WORLD_EATER_MINE_HELPER.getBooleanValue()) {
            return;
        }
        if (!WorldEaterMineHelperUtil.shouldUseCustomModel(state, pos)) {
            return;
        }

        BakedModel customModel = WorldEaterMineHelperUtil.customFullModels.get(state.getBlock());
        if (customModel == null) {
            return;
        }

        BlockRenderDispatcher self = (BlockRenderDispatcher) (Object) this;
        self.getModelRenderer().tesselateBlock(level, customModel, state, pos, poseStack, consumer,
                checkSides, random, state.getSeed(pos), net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);

        ci.cancel();
    }
}
