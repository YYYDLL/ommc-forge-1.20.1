package com.plusls.ommc.feature.highlightWaypoint;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

/**
 * 取「准星图标」这张贴图，供坐标点标记绘制用。
 *
 * <p>移植自 Fabric 版 {@code HighlightWaypointResourceLoader}。
 *
 * <h2>为什么不在渲染时现取</h2>
 * 贴图并进图集（atlas）之后才能拿到 {@link TextureAtlasSprite}，而图集是在资源重载时重新拼的。
 * 如果在渲染时每帧 {@code getTextureAtlas(...).apply(id)}，资源重载后会一直拿到<b>旧图集</b>里的精灵，
 * 变成紫黑格子。所以必须挂一个重载监听器，在<b>每次重载后</b>重新解析一次并缓存。
 *
 * <p>贴图本身来自本模组的资源包：{@code assets/ommc/textures/block/target.png}，
 * 配合 {@code assets/minecraft/atlases/blocks.json} 把它并进方块图集
 * （Forge 1.20.1 与 Fabric 一样认这个文件，所以不用改）。
 */
public class WaypointSprites extends SimplePreparableReloadListener<Void> {

    public static final ResourceLocation TARGET_ID = new ResourceLocation("ommc", "block/target");

    @Nullable
    private static TextureAtlasSprite targetSprite;

    /** 取准星图标；资源还没加载完时返回 {@code null}（调用方跳过绘制）。 */
    @Nullable
    public static TextureAtlasSprite getTargetSprite() {
        return targetSprite;
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void unused, ResourceManager resourceManager, ProfilerFiller profiler) {
        // 图集在方块模型之后拼接，这里能拿到的是本次重载后的新图集
        targetSprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(TARGET_ID);
    }
}
