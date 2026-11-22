package com.plusls.ommc.mixin.feature.highlightPersistentMob;

import com.plusls.ommc.config.Configs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.List;

/**
 * 高亮「不会消失的怪物」。
 *
 * <p>移植自 Fabric 版 {@code mixin/feature/highlightPersistentMob/MixinEntity}。
 * 与 {@code highlightEntity} 用的是同一个注入点（{@code Entity#isCurrentlyGlowing}），
 * 但判定依据是「这只怪会不会被刷掉」。
 *
 * <p><b>两种判定模式</b>：
 * <ol>
 *   <li><b>服务端数据模式</b>（默认）：看 {@code Mob#isPersistenceRequired()} 与
 *       {@code hasCustomName()}。前者是服务端真实字段（被打上 persistence 标记的怪永不消失），
 *       由服务端同步过来，最准。</li>
 *   <li><b>客户端推断模式</b>：服务端没同步 persistence 标记时（多数服务器不开），
 *       退而用「主手拿着非武器物品」或「有自定义名字」来推断。会误判，所以要手动开。</li>
 * </ol>
 *
 * <p><b>为什么主手要排除武器</b>：僵尸/骷髅捡起剑、弓、三叉戟、斧、鱼竿后主手会有物品，
 * 但它们仍然会被刷掉。真正被 player 用命名牌或 persistence 标记留下的怪，
 * 手里通常拿的是「战利品」或工具，而不是这些武器。这个启发式来自 Fabric 版，原样保留。
 *
 * <p><b>与 Fabric 版的差异</b>：
 * <ul>
 *   <li>去掉了 magiclib 的 {@code MiscUtil.cast} 与 malilib 的 {@code WorldUtils.getBestWorld}。
 *       后者用于「集成服务器里拿服务端那个世界的数据」，但本特性依赖的
 *       {@code isPersistenceRequired()} 在客户端实体上同样会被同步，取客户端世界即可；
 *       少一层跨线程取世界，也就少一类线程安全问题。</li>
 *   <li>取物品注册名改用 {@link ForgeRegistries#ITEMS}。</li>
 * </ul>
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    private static final List<String> ommc$itemBlackList =
            Arrays.asList("sword", "bow", "trident", "axe", "fishing_rod");

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void ommc$highlightPersistentMob(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue() || !Configs.FeatureToggle.HIGHLIGHT_PERSISTENT_MOB.getBooleanValue()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        if (!(self instanceof Mob mob)) {
            return;
        }

        // 服务端同步过来的「永不消失」标记，最可靠
        if (mob.isPersistenceRequired() || mob.hasCustomName()) {
            cir.setReturnValue(true);
            return;
        }

        if (!Configs.FeatureToggle.HIGHLIGHT_PERSISTENT_MOB_CLIENT_MODE.getBooleanValue()) {
            return;
        }

        // 客户端推断：主手拿着非武器物品，或有自定义名字
        ItemStack mainHand = mob.getMainHandItem();
        if (!mainHand.isEmpty()) {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(mainHand.getItem());
            String itemName = itemId == null ? "" : itemId.toString();
            if (ommc$itemBlackList.stream().noneMatch(itemName::contains)) {
                cir.setReturnValue(true);
                return;
            }
        }

        if (self.getCustomName() != null) {
            cir.setReturnValue(true);
        }
    }
}
