package ar.hxi.tfbridge.chat;

import ar.hxi.tfbridge.BridgeConfig;
import ar.hxi.tfbridge.BridgeTonekoFuji;
import eu.pb4.placeholders.api.PlaceholderContext;
import eu.pb4.placeholders.api.PlaceholderHandler;
import eu.pb4.placeholders.api.PlaceholderResult;
import eu.pb4.placeholders.api.Placeholders;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.cneko.toneko.common.mod.entities.INeko;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * 注册 Fuji 自定义格式里可用的占位符。
 *
 * <p>命名空间约定：
 * <ul>
 *   <li>{@code %toneko:xxx%} —— toNeko 侧数据</li>
 *   <li>{@code %justarod:xxx%} —— JustARod 侧数据（JustARod 未安装时返回默认值）</li>
 * </ul>
 *
 * <p>这些占位符在 <b>{@code chat.style.sender}</b> 里由 Fuji 原生的 POWERFUL_PARSER 解析；
 * 在 <b>{@code chat.style.content}</b> 与玩家个人格式（{@code chat.json}）里由
 * {@link FujiText#parseContent} 用字符串替换的方式解析。
 */
public final class BridgePlaceholders {

    /** toNeko 命名空间。 */
    public static final String TONEKO_NS = "toneko";
    /** JustARod 命名空间。 */
    public static final String JUSTAROD_NS = "justarod";

    private BridgePlaceholders() {
    }

    public static void register() {
        /* ---------------- %toneko:*% ---------------- */

        // 完整前缀列表（[猫娘] + JustARod 的效果标签），保留 § 颜色码。
        player(TONEKO_NS, "prefix", (player, neko) ->
                TonekoChat.formatPrefixes(TonekoChat.prefixes(neko)));

        // 完整前缀列表的纯文本版本（去掉 § 颜色码）。
        player(TONEKO_NS, "prefix_raw", (player, neko) ->
                TonekoChat.stripLegacyCodes(TonekoChat.formatPrefixes(TonekoChat.prefixes(neko))));

        // toNeko 没有后缀概念，这里给管理员一个可配置的全局后缀值。
        constant(TONEKO_NS, "suffix", () -> BridgeConfig.suffix());

        // 猫娘昵称（带 §6~ 高亮）；无昵称时回落真实名。
        player(TONEKO_NS, "nickname", (player, neko) -> TonekoChat.nickname(neko));

        // 玩家真实名。
        player(TONEKO_NS, "name", (player, neko) -> TonekoChat.playerName(player));

        // 是否猫娘。
        player(TONEKO_NS, "is_neko", (player, neko) -> neko.isNeko() ? "true" : "false");

        // 猫娘等级（toNeko 的 NekoLevelRegistry 总值）。
        player(TONEKO_NS, "level", (player, neko) -> String.format("%.1f", neko.getNekoLevel()));

        // 猫娘能量 / 上限。
        player(TONEKO_NS, "energy", (player, neko) -> String.format("%.0f", neko.getNekoEnergy()));
        player(TONEKO_NS, "max_energy", (player, neko) -> String.format("%.0f", neko.getMaxNekoEnergy()));

        // 口癖 / 语气词。
        constant(TONEKO_NS, "phrase", TonekoChat::phrase);

        // 主人称谓（没有主人时为空）。
        player(TONEKO_NS, "owner", (player, neko) -> TonekoChat.ownerTitle(neko));

        // 区域聊天标记（非区域聊天时为空）。
        player(TONEKO_NS, "area", (player, neko) ->
                TonekoChat.isAreaChat(player) && BridgeConfig.keepAreaTag() ? TonekoChat.AREA_TAG : "");

        /* ---------------- %justarod:*% ---------------- */

        // 是否戴着口球（JustARod 未安装时恒为 false）。
        player(JUSTAROD_NS, "gagged", (player, neko) ->
                JustARodCompat.isGagged(player) ? "true" : "false");

        // 口球剩余秒数。
        player(JUSTAROD_NS, "ball_mouth", (player, neko) ->
                String.valueOf(JustARodCompat.ballMouth(player) / 20));

        BridgeTonekoFuji.LOGGER.info("[bridge_toneko_fuji] Registered {} placeholders: {} (JustARod present: {}).",
                names().size(), String.join(" ", names()), JustARodCompat.isAvailable());
    }

    /* ------------------------------------------------------------------ */
    /* 注册工具                                                            */
    /* ------------------------------------------------------------------ */

    private interface PlayerValue {
        @NotNull String get(@NotNull ServerPlayer player, @NotNull INeko neko);
    }

    private interface ConstantValue {
        @NotNull String get();
    }

    private static void player(String namespace, String path, PlayerValue value) {
        register(namespace, path, (context, argument) -> {
            ServerPlayer player = context.player();
            if (player == null) {
                return PlaceholderResult.invalid("no player");
            }
            if (!(player instanceof INeko neko)) {
                return PlaceholderResult.invalid("not a toNeko entity");
            }
            try {
                return PlaceholderResult.value(value.get(player, neko));
            } catch (Throwable e) {
                BridgeTonekoFuji.LOGGER.debug("[bridge_toneko_fuji] Placeholder {}/{} failed.", namespace, path, e);
                return PlaceholderResult.invalid("error");
            }
        });
    }

    private static void constant(String namespace, String path, ConstantValue value) {
        register(namespace, path, (context, argument) -> PlaceholderResult.value(value.get()));
    }

    private static void register(String namespace, String path, @NotNull PlaceholderHandler handler) {
        Placeholders.register(ResourceLocation.fromNamespaceAndPath(namespace, path), handler);
    }

    /** 供 Fuji 的内容替换路径复用。 */
    public static @Nullable String resolve(@NotNull String namespace, @NotNull String path, @NotNull PlaceholderContext context) {
        var result = Placeholders.parsePlaceholder(ResourceLocation.fromNamespaceAndPath(namespace, path), null, context);
        if (result == null || !result.isValid()) {
            return null;
        }
        return result.text() == null ? null : result.text().getString();
    }

    /** 便于文档/调试：列出所有已注册的占位符名。 */
    public static @NotNull List<String> names() {
        return List.of(
                "%toneko:prefix%", "%toneko:prefix_raw%", "%toneko:suffix%",
                "%toneko:nickname%", "%toneko:name%", "%toneko:is_neko%",
                "%toneko:level%", "%toneko:energy%", "%toneko:max_energy%",
                "%toneko:phrase%", "%toneko:owner%", "%toneko:area%",
                "%justarod:gagged%", "%justarod:ball_mouth%"
        );
    }
}
