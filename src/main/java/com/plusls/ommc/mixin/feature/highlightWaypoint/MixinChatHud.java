package com.plusls.ommc.mixin.feature.highlightWaypoint;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.feature.highlightWaypoint.HighlightWaypointHandler;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 从聊天里解析坐标点，把坐标变成可点击的链接。
 *
 * <p>移植自 Fabric 版 {@code mixin/feature/highlightWaypoint/MixinChatHud}。
 *
 * <p><b>注入点为什么是 {@code logChatMessage}</b>：它是「一条消息进聊天框」的必经之路，
 * 服务端消息、系统消息、玩家自己发的命令回显都会走这里。
 * 若挂在 {@code addMessage} 上，那些内部直接调用 {@code logChatMessage} 的路径会被漏掉。
 *
 * <p><b>用 {@code @ModifyVariable} 而不是在原对象上就地改</b>：{@code Component} 是不可变树，
 * 就地改需要拿私有字段并处理 translatable 的展开缓存；直接替换成重写后的新树，
 * 语义更干净，也不会有「改了一半」的中间态。
 *
 * <p>优先级 999（比默认的 1000 更早处理）：保证我们在其它会读消息内容的模组之前
 * 完成替换，例如聊天记录类模组拿到的是已经带好点击事件的版本。
 */
@Mixin(value = ChatComponent.class, priority = 999)
public class MixinChatHud {

    @ModifyVariable(method = "logChatMessage", at = @At("HEAD"), argsOnly = true, index = 1)
    private Component ommc$parseWaypointFromChat(Component message) {
        if (!Configs.Generic.PARSE_WAYPOINT_FROM_CHAT.getBooleanValue()) {
            return message;
        }
        return HighlightWaypointHandler.getInstance().rewrite(message);
    }
}
