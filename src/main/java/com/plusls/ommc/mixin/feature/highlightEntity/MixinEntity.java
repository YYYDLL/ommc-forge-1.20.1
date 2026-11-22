package com.plusls.ommc.mixin.feature.highlightEntity;

import com.plusls.ommc.config.Configs;
import com.plusls.ommc.config.ListType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 高亮指定实体：让白名单里的实体（默认是流浪商人）自带发光描边。
 *
 * <p>移植自 Fabric 版 {@code mixin/feature/highlightEntity/MixinEntity}。
 *
 * <p><b>原理</b>：原版的发光描边完全由 {@code Entity#isCurrentlyGlowing()} 一个返回值决定 ——
 * 它返回 true 时渲染器就会画那圈轮廓，与「是否真的喝了发光药水」无关。
 * 所以只要在它返回之后再补一个判断，就能让任意符合条件的实体发光，且<b>纯客户端</b>、
 * 不需要服务端配合，也不需要改渲染代码。
 *
 * <p><b>与 Fabric 版的差异</b>：
 * <ul>
 *   <li>{@code BuiltInRegistries.ENTITY_TYPE} → {@link ForgeRegistries#ENTITY_TYPES}
 *       （1.20.1 里 BuiltInRegistries 已被 Forge 标记弃用，且 Forge 注册表才包含全部模组实体）；</li>
 *   <li>不再 {@code @Shadow} 私有字段 {@code world}，改为直接调 {@code ((Entity) this).level()} ——
 *       Forge 的 Mixin 注解处理器给不了字段的 refmap 条目（详见项目内其它 mixin 的说明），
 *       而 public 方法调用不需要 refmap。</li>
 * </ul>
 *
 * <p><b>为什么先判断 {@code cir.getReturnValue()}</b>：真发光的实体不必再比对名单，
 * 早退省掉每次渲染的字符串匹配 —— 这个方法每帧对每个可见实体都会被调用一次。
 */
@Mixin(Entity.class)
public abstract class MixinEntity {

    @Inject(method = "isCurrentlyGlowing", at = @At("RETURN"), cancellable = true)
    private void ommc$highlightEntity(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) {
            return;
        }

        Entity self = (Entity) (Object) this;
        if (!self.level().isClientSide) {
            return;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(self.getType());
        if (id == null) {
            return;
        }

        String entityId = id.toString();
        String entityName = self.getType().getDescription().getString();
        boolean matched = containsAny(Configs.Lists.HIGHLIGHT_ENTITY_WHITELIST.getStrings(), entityId, entityName);

        if (Configs.Lists.HIGHLIGHT_ENTITY_LIST_TYPE.getOptionListValue() == ListType.WHITELIST) {
            if (matched) {
                cir.setReturnValue(true);
            }
        } else if (!matched) {
            // 黑名单模式：不在名单里的一律发光
            cir.setReturnValue(true);
        }
    }

    /** 名单采用子串匹配：注册名（{@code minecraft:wandering_trader}）与本地化名（{@code 流浪商人}）任一命中即可。 */
    private static boolean containsAny(java.util.List<String> list, String... candidates) {
        for (String pattern : list) {
            for (String candidate : candidates) {
                if (candidate.contains(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }
}
