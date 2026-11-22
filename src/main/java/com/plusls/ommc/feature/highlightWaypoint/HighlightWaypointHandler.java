package com.plusls.ommc.feature.highlightWaypoint;

import com.plusls.ommc.OhMyMinecraftClient;
import com.plusls.ommc.config.Configs;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 高亮坐标点：从聊天里认出坐标、把它变成可点击的链接，并记录「当前要高亮哪个点」。
 *
 * <p>移植自 Fabric 版 {@code impl/generic/highlightWaypoint/HighlightWaypointHandler}。
 * 这个特性在 Fabric 版里由三部分组成（见 {@link HighlightWaypointRenderer} 与
 * {@code MixinClientPacketListener}），本类是其中的「状态 + 文本处理」。
 *
 * <h2>三个职责</h2>
 * <ol>
 *   <li><b>解析坐标</b>：用正则从任意一段文本里找出 {@code 123 64 -456}、{@code x:123, y:64, z:-456}
 *       这类写法，得到坐标点与它在原文里的下标。</li>
 *   <li><b>重写聊天消息</b>：把消息里每一段坐标换成带下划线、绿色、可点击的组件。
 *       点击后执行 {@code /highlightWaypoint x y z} —— 该「命令」并不会真的发给服务端，
 *       而是被 {@code MixinClientPacketListener} 在 {@code sendCommand} 处截下来本地处理。</li>
 *   <li><b>记住高亮位置，并按维度换算</b>：主世界与下界的坐标是 8:1 关系，
 *       所以同一个点要同时存两份，玩家在哪一边就高亮哪一份（见 {@link #setHighlightPos}）。</li>
 * </ol>
 *
 * <h2>与 Fabric 版的差异</h2>
 * <ul>
 *   <li>不再依赖 magiclib 的 {@code ComponentUtil} / {@code ComponentCompat}，
 *       改为用 {@link AccessorComponent} 读 {@code Component} 的本层内容后自己重写；
 *       行为与 Fabric 版一致（保留原有样式与子节点，只替换命中的那几段文本）。</li>
 *   <li>没有 Fabric 的客户端命令 API，改为「发命令时本地拦截」（详见 mixin 的说明）。</li>
 *   <li>{@code DimensionWrapper} 换成直接比较 {@link Level#dimension()} 的 key。</li>
 * </ul>
 */
public class HighlightWaypointHandler {

    private static final HighlightWaypointHandler INSTANCE = new HighlightWaypointHandler();

    /** 客户端「命令」名。点击聊天里的坐标时，会发一条以它开头的命令，然后被本地拦截。 */
    public static final String COMMAND_NAME = "highlightWaypoint";

    /**
     * 坐标匹配。
     *
     * <p>要能同时吃下这几种写法（都来自实际聊天内容）：
     * <pre>
     *     123 64 -456
     *     x:123, y:64, z:-456
     *     123.5 64 -456.5d
     * </pre>
     * 三个分组：{@code x} 必选，{@code y} 与 {@code z} 可选 —— 这样「只有 x 和 z 的二维坐标」
     * （例如小地图插件输出的 {@code 123, -456}）也能识别，y 默认取 64。
     */
    private static final Pattern PATTERN = Pattern.compile(
            "(?:(?:x\\s*:\\s*)?(?<x>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)"
                    + "(?:(?:(?:\\s*[,，]\\s*(?:y\\s*:\\s*)?)|(?:\\s+))(?<y>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)?"
                    + "(?:(?:(?:\\s*[,，]\\s*(?:z\\s*:\\s*)?)|(?:\\s+))(?<z>(?:[+-]?\\d+)(?:\\.\\d+)?)(?:[df])?)",
            Pattern.CASE_INSENSITIVE);

    /** 主世界侧的位置（在主世界点出来的那个点） */
    @Nullable
    private BlockPos overworldPos;
    /** 下界侧的位置（与 {@link #overworldPos} 是 8:1 换算关系） */
    @Nullable
    private BlockPos netherPos;

    /** 光柱要显示到什么时候（毫秒时间戳）。为 0 表示不显示光柱，只显示图标。 */
    private long beamUntil;

    private HighlightWaypointHandler() {
    }

    public static HighlightWaypointHandler getInstance() {
        return INSTANCE;
    }

    // ==================== 状态 ====================

    /**
     * 设置高亮位置。
     *
     * <p>同时算出「另一个维度里的对应点」：主世界按下时除以 8 得下界坐标，下界按下时乘 8 得主世界坐标。
     * 这样玩家穿传送门过去后，高亮的还是同一个真实地点。
     *
     * @param direct true 表示直接展示光柱（来自「再看一次」的点击），false 表示只在位置变化时才亮光柱
     */
    public void setHighlightPos(@NotNull BlockPos pos, boolean direct) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean posChanged;
        if (player.level().dimension().equals(Level.OVERWORLD)) {
            posChanged = this.setHighlightBlockPos(pos, new BlockPos(pos.getX() / 8, pos.getY(), pos.getZ() / 8));
        } else if (player.level().dimension().equals(Level.NETHER)) {
            posChanged = this.setHighlightBlockPos(new BlockPos(pos.getX() * 8, pos.getY(), pos.getZ() * 8), pos);
        } else {
            posChanged = this.setHighlightBlockPos(pos, pos);
        }

        // 位置没变（重复点同一个点）时也重新亮光柱，否则玩家再点一次会以为没反应
        if (direct || !posChanged) {
            this.beamUntil = System.currentTimeMillis() + Configs.Generic.HIGHLIGHT_BEAM_TIME.getIntegerValue() * 1000L;
        }
    }

    @Nullable
    public BlockPos getHighlightPos() {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        return player.level().dimension().equals(Level.NETHER) ? this.netherPos : this.overworldPos;
    }

    private boolean setHighlightBlockPos(@NotNull BlockPos overworldPos, @NotNull BlockPos netherPos) {
        if (overworldPos.equals(this.overworldPos) && netherPos.equals(this.netherPos)) {
            return false;
        }
        this.overworldPos = overworldPos;
        this.netherPos = netherPos;
        return true;
    }

    /** 清空高亮（退出世界时也会调用） */
    public void clearHighlightPos() {
        this.overworldPos = null;
        this.netherPos = null;
        this.beamUntil = 0L;
    }

    public boolean shouldRenderBeam() {
        return this.beamUntil >= System.currentTimeMillis();
    }

    // ==================== 命令拦截 ====================

    /**
     * 尝试把一条命令当成「设置高亮点」来本地处理。
     *
     * <p>由 {@code MixinClientPacketListener} 在 {@code sendCommand} 的 HEAD 处调用。
     *
     * @return true 表示已处理（调用方应当取消原命令，不要发往服务端）
     */
    public static boolean tryHandleCommand(@Nullable String command) {
        if (command == null) {
            return false;
        }
        String body = command.startsWith("/") ? command.substring(1) : command;
        if (!body.startsWith(COMMAND_NAME)) {
            return false;
        }

        String[] parts = body.substring(COMMAND_NAME.length()).trim().split("\\s+");
        // 形如 "highlightWaypoint" 或 "highlightWaypoint 1 2 3"
        if (parts.length == 1 && parts[0].isEmpty()) {
            return false;
        }
        if (parts.length != 3) {
            // 参数不对就交给服务端，让它照常报「未知命令」，比静默吞掉更容易发现问题
            return false;
        }

        try {
            BlockPos pos = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            getInstance().setHighlightPos(pos, false);
            return true;
        } catch (NumberFormatException e) {
            OhMyMinecraftClient.LOGGER.warn("无法解析高亮坐标: {}", body, e);
            return false;
        }
    }

    // ==================== 聊天消息重写 ====================

    /**
     * 把一条聊天消息里所有坐标改写成可点击的链接。
     *
     * <p>策略是「重建而非就地修改」：{@code Component} 是不可变的树，就地改本层文本需要访问
     * {@code TextComponent} 的私有字段并处理缓存（原版还会缓存 translatable 的展开结果），
     * 重建一份等价的新树更稳妥。
     *
     * <p>每一层都做三件事：
     * <ol>
     *   <li>本层是纯文本且含坐标 → 拆成「普通片段 + 链接片段」；</li>
     *   <li>本层是 translatable → 把字符串参数里的坐标也换成组件（对应 Fabric 版对参数的处理）；</li>
     *   <li>递归处理子节点，样式一律继承原节点。</li>
     * </ol>
     *
     * @return 重写后的消息；没有任何坐标时返回原对象（调用方据此判断是否需要替换）
     */
    public Component rewrite(@NotNull Component in) {
        MutableComponent out;
        ComponentContents contents = in.getContents();

        if (contents instanceof LiteralContents plain) {
            MutableComponent split = this.splitText(plain.text(), in.getStyle());
            out = split != null ? split : in.copy();
        } else if (contents instanceof TranslatableContents translatable) {
            Object[] args = translatable.getArgs();
            Object[] newArgs = null;

            for (int i = 0; i < args.length; i++) {
                if (args[i] instanceof Component component) {
                    Component rewritten = this.rewrite(component);
                    if (rewritten != component) {
                        if (newArgs == null) {
                            newArgs = args.clone();
                        }
                        newArgs[i] = rewritten;
                    }
                } else if (args[i] instanceof String text) {
                    MutableComponent replaced = this.splitText(text, in.getStyle());
                    if (replaced != null) {
                        if (newArgs == null) {
                            newArgs = args.clone();
                        }
                        newArgs[i] = replaced;
                    }
                }
            }

            out = newArgs == null
                    ? in.copy()
                    : Component.translatable(translatable.getKey(), newArgs);
        } else {
            out = in.copy();
        }

        out.setStyle(in.getStyle());
        for (Component sibling : in.getSiblings()) {
            out.append(this.rewrite(sibling));
        }
        return out;
    }

    /**
     * 把一段纯文本拆成「普通片段 + 坐标链接」。
     *
     * @return 拆好的组件；这段文本里没有坐标时返回 null（表示「不需要改」）
     */
    @Nullable
    private MutableComponent splitText(String text, Style baseStyle) {
        List<ParseResult> positions = this.parsePositions(text);
        if (positions.isEmpty()) {
            return null;
        }

        MutableComponent container = Component.empty();
        int prevIndex = 0;

        for (ParseResult result : positions) {
            if (result.start() > prevIndex) {
                container.append(Component.literal(text.substring(prevIndex, result.start())).withStyle(baseStyle));
            }
            container.append(this.createWaypointLink(result, baseStyle));
            prevIndex = result.start() + result.text().length();
        }

        if (prevIndex < text.length()) {
            container.append(Component.literal(text.substring(prevIndex)).withStyle(baseStyle));
        }
        return container;
    }

    /**
     * 生成一个坐标链接：绿字 + 下划线 + 点击跳转 + 悬停提示。
     *
     * <p><b>点击事件优先保留原有的</b>：很多服务器的聊天消息本身就带点击事件
     * （例如「[点击传送]」），直接覆盖会破坏服务器功能。只有玩家显式打开
     * {@code forceParseWaypointFromChat} 时才强制改成自己的。
     */
    private MutableComponent createWaypointLink(ParseResult result, Style baseStyle) {
        BlockPos pos = result.pos();
        ClickEvent originalClick = baseStyle.getClickEvent();

        ClickEvent click = (originalClick != null
                && !Configs.Generic.FORCE_PARSE_WAYPOINT_FROM_CHAT.getBooleanValue())
                ? originalClick
                : new ClickEvent(ClickEvent.Action.RUN_COMMAND,
                        String.format("/%s %d %d %d", COMMAND_NAME, pos.getX(), pos.getY(), pos.getZ()));

        Style style = baseStyle
                .withColor(ChatFormatting.GREEN)
                .withUnderlined(true)
                .withClickEvent(click)
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.translatable("ommc.highlight_waypoint.tooltip")));

        return Component.literal(result.text()).withStyle(style);
    }

    @NotNull
    private List<ParseResult> parsePositions(@NotNull String message) {
        List<ParseResult> results = new ArrayList<>();
        Matcher matcher = PATTERN.matcher(message);

        while (matcher.find()) {
            ParseResult result = this.parsePosition(matcher);
            if (result != null) {
                results.add(result);
            }
        }

        results.sort(Comparator.comparingInt(ParseResult::start));
        return results;
    }

    /**
     * 从一次匹配里取出坐标。
     *
     * <p>y 缺省取 64：只给了 x/z 时，玩家通常只关心「在哪一片区域」，
     * 取个海平面高度比默认 0 更不容易把光柱埋进地下深处。
     */
    @Nullable
    private ParseResult parsePosition(@NotNull Matcher matcher) {
        String xStr = matcher.group("x");
        String yStr = matcher.group("y");
        String zStr = matcher.group("z");

        Integer x;
        Integer z;
        int y = 64;

        try {
            x = toInt(xStr);
            z = toInt(zStr);
            if (yStr != null) {
                y = toInt(yStr);
            }
        } catch (NumberFormatException e) {
            // 例如 "1.2.3" 这种畸形数字：跳过这一处匹配，不影响同一行里其它坐标
            OhMyMinecraftClient.LOGGER.debug("跳过无法解析的坐标片段: {}", matcher.group());
            return null;
        }

        if (x == null || z == null) {
            return null;
        }
        return new ParseResult(matcher.group(), new BlockPos(x, y, z), matcher.start());
    }

    /** 整数或小数都接受，小数按截断取整（与 Fabric 版一致）。 */
    private static int toInt(String value) {
        return value.contains(".") ? (int) Double.parseDouble(value) : Integer.parseInt(value);
    }

    /** 一处命中的坐标：原文片段、坐标、在原文里的起始下标 */
    public record ParseResult(String text, BlockPos pos, int start) {
    }
}
