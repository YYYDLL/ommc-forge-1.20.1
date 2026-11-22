package com.plusls.ommc.feature.highlightWaypoint;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.NotNull;

/**
 * 在世界里画出高亮坐标点：一根光柱 + 一个准星图标 + 一行「x:y:z(distance)」文本。
 *
 * <p>移植自 Fabric 版 {@code HighlightWaypointRenderer}（原版通过 magiclib 的
 * {@code RenderLevelListener} 挂在世界渲染的尾部），这里改挂 Forge 的
 * {@link RenderLevelStageEvent}，阶段取 {@code AFTER_TRANSLUCENT_BLOCKS}：
 * 此时半透明方块已画完、天气与手部还没开始，画「穿透一切都能看见的标记」正合适。
 *
 * <h2>几个容易踩的点</h2>
 * <ul>
 *   <li><b>坐标要减去相机位置</b>：世界渲染的坐标系原点在相机处，直接用方块坐标会把标记画到天边去。</li>
 *   <li><b>关掉深度测试</b>：标记要「穿墙可见」，否则被方块挡住就看不见了 —— 这是本特性的核心体验。
 *       画完必须恢复，不然会污染后续的渲染阶段。</li>
 *   <li><b>距离超过视距时夹住</b>：目标很远时，把标记就地推到「视距边缘」而不是不画，
 *       这样玩家至少能看到方向。尺寸还会随距离变大，不然远处只剩一个像素。</li>
 *   <li><b>文本画两遍</b>：先画一层黑色底纹再画白色字，否则在亮背景（天空、雪地）上完全看不清。</li>
 * </ul>
 */
public class HighlightWaypointRenderer {

    /** 信标光束贴图，与原版信标用的是同一张 */
    private static final ResourceLocation BEACON_BEAM_LOCATION =
            new ResourceLocation("textures/entity/beacon_beam.png");

    public static void render(@NotNull RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }

        BlockPos waypointPos = HighlightWaypointHandler.getInstance().getHighlightPos();
        if (waypointPos == null) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        float partialTick = event.getPartialTick();
        Vec3 cameraPos = event.getCamera().getPosition();
        Vec3 target = Vec3.atCenterOf(waypointPos);
        double distance = target.distanceTo(cameraPos);

        // 超出视距就把标记推到视距边缘，保留「那个方向有点」的提示
        double maxDistance = (double) mc.options.renderDistance().get() * 16.0D;
        double renderDistance = distance;
        if (distance > maxDistance) {
            Vec3 direction = target.subtract(cameraPos).normalize().scale(maxDistance);
            target = cameraPos.add(direction);
            renderDistance = maxDistance;
        }

        Vec3 offset = target.subtract(cameraPos);
        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource bufferSource = mc.renderBuffers().bufferSource();

        poseStack.pushPose();
        poseStack.translate(offset.x, offset.y, offset.z);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();

        if (HighlightWaypointHandler.getInstance().shouldRenderBeam()) {
            poseStack.pushPose();
            // 光柱的绘制原点在方块角上（而非中心），下移半格对齐
            poseStack.translate(-0.5D, -0.5D, -0.5D);
            renderBeam(poseStack, bufferSource, level, partialTick);
            poseStack.popPose();
        }

        poseStack.pushPose();
        // 让标记始终正对相机
        poseStack.mulPose(event.getCamera().rotation());
        float scale = (float) ((renderDistance > 8.0D ? renderDistance - 8.0D : 0.0D) * 0.2D + 1.0D) * 0.0265F;
        poseStack.scale(scale, -scale, -scale);

        poseStack.pushPose();
        poseStack.translate(0.0D, 5.0D, 0.0D);
        renderText(poseStack, bufferSource, String.format("x:%d, y:%d, z:%d (%dm)",
                waypointPos.getX(), waypointPos.getY(), waypointPos.getZ(), (int) distance));
        poseStack.popPose();

        renderIcon(poseStack, bufferSource);

        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
        poseStack.popPose();
    }

    /** 红色光柱：颜色数组是 {@code [r, g, b]} 的 16 倍色值，{@code {1,0,0}} 即纯红。 */
    private static void renderBeam(PoseStack poseStack, MultiBufferSource bufferSource, Level level, float partialTick) {
        BeaconRenderer.renderBeaconBeam(
                poseStack,
                bufferSource,
                BEACON_BEAM_LOCATION,
                partialTick,
                1.0F,
                level.getGameTime(),
                -128,
                256,
                new float[]{1.0F, 0.0F, 0.0F},
                0.2F,
                0.25F);
        // 光束顶点已经进 batch，立刻提交，避免和其它使用同一 buffer 的内容混在一起
        if (bufferSource instanceof MultiBufferSource.BufferSource batched) {
            batched.endBatch();
        }
    }

    private static void renderText(PoseStack poseStack, MultiBufferSource bufferSource, String text) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        int halfWidth = font.width(text) / 2;

        // 先黑底再白字：深色背景在亮场景（天空、雪原）里保证可读，与 Fabric 版一致
        for (int background : new int[]{Integer.MIN_VALUE, 0}) {
            font.drawInBatch(text, (float) -halfWidth, 0.0F, 0xFFFFFF, false,
                    poseStack.last().pose(), bufferSource, Font.DisplayMode.SEE_THROUGH,
                    background, 0xF000F0);
        }
        if (bufferSource instanceof MultiBufferSource.BufferSource batched) {
            batched.endBatch();
        }
    }

    /** 准星图标：一个红色半透明的方片，贴图取 {@link WaypointSprites#getTargetSprite()}。 */
    private static void renderIcon(PoseStack poseStack, MultiBufferSource bufferSource) {
        TextureAtlasSprite sprite = WaypointSprites.getTargetSprite();
        if (sprite == null) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, InventoryMenu.BLOCK_ATLAS);

        float half = 10.0F;
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        var matrix = poseStack.last().pose();
        builder.vertex(matrix, -half, -half, 0.0F).uv(sprite.getU0(), sprite.getV0()).color(1.0F, 0.0F, 0.0F, 0.5F).endVertex();
        builder.vertex(matrix, -half, half, 0.0F).uv(sprite.getU0(), sprite.getV1()).color(1.0F, 0.0F, 0.0F, 0.5F).endVertex();
        builder.vertex(matrix, half, half, 0.0F).uv(sprite.getU1(), sprite.getV1()).color(1.0F, 0.0F, 0.0F, 0.5F).endVertex();
        builder.vertex(matrix, half, -half, 0.0F).uv(sprite.getU1(), sprite.getV0()).color(1.0F, 0.0F, 0.0F, 0.5F).endVertex();

        tesselator.end();
    }
}
