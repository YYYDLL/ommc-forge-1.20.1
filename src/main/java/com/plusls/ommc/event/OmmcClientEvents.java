package com.plusls.ommc.event;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.feature.highlightWaypoint.HighlightWaypointHandler;
import com.plusls.ommc.feature.highlightWaypoint.HighlightWaypointRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * OMMC 在 Forge「游戏总线」（{@code Bus.FORGE}）上的客户端事件。
 *
 * <p>这里只放<b>运行期</b>事件（渲染、进出世界）。初始化期的事件（客户端 setup、
 * 资源重载监听器注册）属于 MOD 总线，在 {@link OhMyMinecraftClient.ClientEvents} 里。
 *
 * <p>两条总线不能混用：把 MOD 总线的事件订阅在 FORGE 总线上，订阅方法永远不会被调用，
 * 而且不会有任何报错 —— 这是 Forge 上最常见的「功能静默失效」原因。
 */
@Mod.EventBusSubscriber(modid = OhMyMinecraftClient.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class OmmcClientEvents {

    private OmmcClientEvents() {
    }

    /** 世界渲染尾段：画高亮坐标点（光柱 + 图标 + 文本）。 */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        HighlightWaypointRenderer.render(event);
    }

    /**
     * 退出世界时清掉高亮位置。
     *
     * <p>不清的话，进下一个存档会沿用上一个存档的坐标 —— 那个点在这边毫无意义，
     * 还可能把光柱画在别人的家里。对应 Fabric 版的
     * {@code ClientPlayConnectionEvents.DISCONNECT}。
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HighlightWaypointHandler.getInstance().clearHighlightPos();
    }
}
