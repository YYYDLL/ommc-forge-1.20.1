package com.plusls.ommc.mixin.feature.disableBlocklistCheck;

import com.plusls.ommc.config.Configs;
import net.minecraft.client.gui.screens.social.PlayerSocialManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 关闭玩家黑名单检查（MC-218167 的变通）。
 *
 * <p>移植自 Fabric 版 {@code mixin/feature/disableBlocklistCheck/MixinSocialInteractionsManager}。
 *
 * <p><b>它解决的是什么问题</b>：{@code PlayerSocialManager.isBlocked(UUID)} 是「社交屏幕」
 * 里被拉黑的玩家判定。原版在<b>渲染线程</b>上对它做判定时会向服务端发起「这个 UUID 被拉黑了吗」
 * 的网络请求（mojang 的 player safety 服务），网络慢的时候会把渲染线程卡住 —— 表现就是
 * 看某些玩家时会莫名掉帧。把这个判定短路成 {@code false}，就不会再发请求。
 *
 * <p><b>代价</b>：拉黑功能在客户端失效（被拉黑玩家的消息在你这边照样显示）。
 * 所以这是个纯客户端、需要玩家自己权衡的开关，默认关闭。
 *
 * <p><b>只在 HEAD 注入并直接返回</b>，而不是注入到网络调用处：这样连「有没有缓存、缓存是否过期」
 * 那套逻辑都一并跳过，一次方法调用就出结果，最省。
 */
@Mixin(PlayerSocialManager.class)
public class MixinSocialInteractionsManager {

    @Inject(method = "isBlocked", at = @At("HEAD"), cancellable = true)
    private void ommc$disableBlocklistCheck(UUID uuid, CallbackInfoReturnable<Boolean> cir) {
        if (Configs.FeatureToggle.DISABLE_BLOCKLIST_CHECK.getBooleanValue()) {
            cir.setReturnValue(false);
        }
    }
}
