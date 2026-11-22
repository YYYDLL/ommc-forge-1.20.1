package com.plusls.ommc.mixin.feature.highlightWaypoint;

import com.plusls.ommc.feature.highlightWaypoint.HighlightWaypointHandler;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 截下客户端自己「发命令」的那一步，把 {@code /highlightWaypoint x y z} 变成纯本地操作。
 *
 * <p><b>为什么需要它</b>：Fabric 版用的是 Fabric 的客户端命令 API
 * （{@code ClientCommandManager} + {@code ClientCommandRegistrationCallback}），
 * 可以把命令注册在<b>客户端</b>命令树上，服务端根本不知道有这条命令。
 * Forge 1.20.1 没有等价的客户端命令 API ——
 * {@code ClientCommandHandler} 那一套在 1.13 之后就被移除了。
 *
 * <p>所以这里换了个等价的做法：点聊天里的坐标时照常发一条命令，
 * 但在 {@code ClientPacketListener.sendCommand} 真正发给服务端之前把它拦下来。
 * 对玩家而言行为完全一致（不报错、不发包），实现却只有几行。
 *
 * <p><b>为什么拦在这里而不是 {@code Minecraft.handleChatInput}</b>：
 * 只有 {@code sendCommand} 是所有命令的统一出口，手输命令、点聊天链接、宏模组触发的命令
 * 都会经过它；拦在更上层会漏掉非输入框来源的命令。
 */
@Mixin(ClientPacketListener.class)
public class MixinClientPacketListener {

    @Inject(method = "sendCommand", at = @At("HEAD"), cancellable = true)
    private void ommc$handleWaypointCommand(String command, CallbackInfo ci) {
        if (HighlightWaypointHandler.tryHandleCommand(command)) {
            ci.cancel();
        }
    }
}
