package ar.hxi.tfbridge.chat;

import ar.hxi.tfbridge.BridgeTonekoFuji;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import eu.pb4.placeholders.api.node.TextNode;
import eu.pb4.placeholders.api.parsers.NodeParser;
import mod.fuji.core.auxiliary.minecraft.PlayerHelper;
import mod.fuji.core.auxiliary.minecraft.TextHelper;
import mod.fuji.core.config.handler.abst.BaseConfigurationHandler;
import mod.fuji.core.service.style_striper.StyleStriper;
import mod.fuji.module.initializer.chat.style.model.ChatFormatModel;
import mod.fuji.module.initializer.chat.style.model.ChatStyleConfigModel;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 复用 Fuji {@code chat.style} 模块的渲染管线。
 *
 * <p>Fuji 的 {@code ChatStyleInitializer.parseContentText(player, message)} 内部是：
 * <pre>
 *   style.content.formatted(message)            // 全局内容格式（默认 "%s"）
 *     → 玩家个人格式（chat.json，默认 "%message%"）里替换 %message%
 *     → StyleStriper.stripe(player, "chat", ..)  // 按权限剥离样式标签
 *     → MINI_MESSAGE_ONLY_PARSER 解析
 * </pre>
 * 它<strong>不解析占位符</strong>（占位符只在 sender 里生效）。桥接需要在内容里也支持
 * {@code %toneko:*%}，所以这里按同样的顺序复刻一遍，最后一步之前把「我们自己的」占位符
 * 用字符串替换填进去 —— 刻意不使用 global placeholders，否则玩家聊天内容里的
 * {@code %xxx%} 也会被解析（占位符注入）。
 */
public final class FujiText {

    private static final String DEFAULT_CONTENT_FORMAT = "%message%";
    private static final String CHAT_STYLE_TYPE = "chat";

    /** 只匹配本模组自己的两个命名空间。 */
    private static final Pattern OWN_PLACEHOLDER =
            Pattern.compile("%(toneko|justarod):([A-Za-z0-9_]+)%");

    /** 复用 Fuji 自己的 MiniMessage 解析器，保证语义完全一致。 */
    private static final NodeParser PARSER = TextHelper.Parsers.MINI_MESSAGE_ONLY_PARSER;

    private static final Map<Class<?>, BaseConfigurationHandler<?>> HANDLER_CACHE = new HashMap<>();

    private FujiText() {
    }

    /* ------------------------------------------------------------------ */
    /* Fuji 配置读取                                                        */
    /* ------------------------------------------------------------------ */

    @SuppressWarnings("unchecked")
    private static <T> @NotNull T model(@NotNull Class<T> type) {
        BaseConfigurationHandler<?> cached = HANDLER_CACHE.get(type);
        if (cached != null) {
            Object value = cached.model();
            if (type.isInstance(value)) {
                return (T) value;
            }
        }
        for (BaseConfigurationHandler<?> handler : BaseConfigurationHandler.getObjectConfigurationHandlers()) {
            Object value = handler.model();
            if (type.isInstance(value)) {
                HANDLER_CACHE.put(type, handler);
                return (T) value;
            }
        }
        throw new IllegalStateException("[bridge_toneko_fuji] Cannot find Fuji configuration model " + type.getName()
                + ". Is Fuji's `chat.style` module installed?");
    }

    public static @NotNull ChatStyleConfigModel.Style style() {
        return model(ChatStyleConfigModel.class).style;
    }

    public static @NotNull ChatFormatModel format() {
        return model(ChatFormatModel.class);
    }

    /** Fuji 的 {@code chat.style.style.sender} 原始格式串。 */
    public static @NotNull String senderFormat() {
        String sender = style().sender;
        return sender == null ? "" : sender;
    }

    /* ------------------------------------------------------------------ */
    /* 内容渲染                                                            */
    /* ------------------------------------------------------------------ */

    /**
     * 复刻 {@code ChatStyleInitializer#parseContentText}，并额外支持 {@code %toneko:*%}。
     *
     * @param player           说话玩家
     * @param preparedMessage 已经过 toNeko {@code Messaging.prepareMessage} 处理的内容
     */
    public static @NotNull Component parseContent(@NotNull ServerPlayer player, @NotNull String preparedMessage) {
        ChatStyleConfigModel.Style style = style();
        String contentFormat = style.content == null ? "%s" : style.content;

        String contentString;
        try {
            contentString = contentFormat.formatted(preparedMessage);
        } catch (Exception e) {
            // 管理员把 style.content 写坏了（比如多个 %s 但只给了一个参数）时不要炸掉聊天。
            BridgeTonekoFuji.LOGGER.warn("[bridge_toneko_fuji] Invalid chat.style.content format: {}", contentFormat, e);
            contentString = preparedMessage;
        }

        String playerName = PlayerHelper.getPlayerName(player);
        String perPlayerFormat = format().getFormat().getPlayer2format()
                .getOrDefault(playerName, DEFAULT_CONTENT_FORMAT);
        contentString = perPlayerFormat.replace("%message%", contentString);

        // 与 Fuji 相同的顺序：先按权限剥离样式标签，再替换占位符。
        contentString = StyleStriper.stripe(player, CHAT_STYLE_TYPE, contentString);
        contentString = substituteOwnPlaceholders(player, contentString);

        return PARSER.parseText(TextNode.of(contentString), PlaceholderContext.of(player).asParserContext());
    }

    /**
     * 把 {@code %toneko:xxx%} / {@code %justarod:xxx%} 用字符串替换填进去。
     *
     * <p>替换值里的 {@code <} / {@code >} 会被转义，避免被 MiniMessage 当成标签；
     * {@code §} 颜色码保持原样，客户端会解释。
     */
    private static @NotNull String substituteOwnPlaceholders(@NotNull ServerPlayer player, @NotNull String input) {
        PlaceholderContext context = PlaceholderContext.of(player);
        Matcher matcher = OWN_PLACEHOLDER.matcher(input);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String namespace = matcher.group(1);
            String path = matcher.group(2);
            String replacement = resolvePlaceholderValue(namespace, path, context);
            if (replacement == null) {
                matcher.appendReplacement(out, Matcher.quoteReplacement(matcher.group()));
            } else {
                matcher.appendReplacement(out, Matcher.quoteReplacement(escapeForMiniMessage(replacement)));
            }
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static @Nullable String resolvePlaceholderValue(@NotNull String namespace,
                                                            @NotNull String path,
                                                            @NotNull PlaceholderContext context) {
        try {
            PlaceholderResult result = Placeholders.parsePlaceholder(
                    ResourceLocation.fromNamespaceAndPath(namespace, path), null, context);
            if (result == null || !result.isValid()) {
                return null;
            }
            Component text = result.text();
            return text == null ? null : text.getString();
        } catch (Throwable e) {
            BridgeTonekoFuji.LOGGER.debug("[bridge_toneko_fuji] Placeholder {}/{} failed.", namespace, path, e);
            return null;
        }
    }

    private static @NotNull String escapeForMiniMessage(@NotNull String input) {
        if (input.isEmpty()) {
            return input;
        }
        return input.replace("\\", "\\\\").replace("<", "\\<");
    }
}
