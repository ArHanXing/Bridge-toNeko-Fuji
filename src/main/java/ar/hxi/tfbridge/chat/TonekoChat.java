package ar.hxi.tfbridge.chat;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.cneko.toneko.common.mod.entities.INeko;
import org.cneko.toneko.common.mod.events.ToNekoNetworkEvents;
import org.cneko.toneko.common.mod.misc.Messaging;
import org.cneko.toneko.common.util.LanguageUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * 读取 toNeko 的聊天相关数据（全部走 toNeko 的公开 API，不复制它的格式化逻辑）。
 *
 * <p>toNeko 的「最终聊天格式」由 {@code ConfigUtil.getChatFormat()} + {@code Messaging.format(...)}
 * 负责；桥接接管后这套格式被 Fuji 的 {@code chat.style} 取代，本类只负责把 toNeko 侧的
 * <em>数据</em>（前缀列表、昵称、口癖、主人称谓、内容预处理、区域频道、口球）取出来。
 */
public final class TonekoChat {

    /** toNeko {@code Messaging.PREFIX_FORMAT} 的同款写法：[§a前缀§f§r]。 */
    private static final String PREFIX_FORMAT = "[§a%s§f§r]";
    /** toNeko 区域聊天的行首标记（{@code CommonChatEvent.onChatMessage} 原文）。 */
    public static final String AREA_TAG = "§a[§f区域§a]§r ";

    private TonekoChat() {
    }

    /** 玩家对象在装了 toNeko 时一定实现了 {@link INeko}（由 PlayerEntityMixin 注入）。 */
    public static @NotNull INeko neko(@NotNull Player player) {
        return (INeko) player;
    }

    public static boolean isNeko(@NotNull Player player) {
        return player instanceof INeko neko && neko.isNeko();
    }

    /** 完整前缀列表：toNeko 自身的 [猫娘] + 所有 CREATE_CHAT_PREFIXES 监听者（含 JustARod 的效果标签）。 */
    public static @NotNull List<String> prefixes(@NotNull INeko neko) {
        return Messaging.getChatPrefixes(neko);
    }

    /** 与 toNeko {@code Messaging.formatPrefixes} 等价的字符串（保留 § 颜色码）。 */
    public static @NotNull String formatPrefixes(@NotNull List<String> prefixes) {
        if (prefixes.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (String prefix : prefixes) {
            builder.append(String.format(PREFIX_FORMAT, prefix));
        }
        return builder.toString();
    }

    /** 与 toNeko {@code Messaging.format} 中 nickname 的算法一致。 */
    public static @NotNull String nickname(@NotNull INeko neko) {
        String nickname = neko.getNickName();
        if (nickname == null || nickname.isBlank()) {
            return neko.getEntity().getName().getString();
        }
        return "§6~§f" + nickname;
    }

    /** 口癖 / 猫叫语气词。 */
    public static @NotNull String phrase() {
        String key = LanguageUtil.phrase;
        if (key == null || key.isEmpty()) {
            return "";
        }
        return LanguageUtil.translatable(key);
    }

    /** 主人称谓；玩家没有主人时为空串。 */
    public static @NotNull String ownerTitle(@NotNull INeko neko) {
        var owners = neko.getOwners();
        if (owners == null || owners.isEmpty()) {
            return "";
        }
        return LanguageUtil.translatable("misc.toneko.owner");
    }

    public static boolean isAreaChat(@NotNull ServerPlayer player) {
        return ToNekoNetworkEvents.isPlayerAreaChat(player.getUUID());
    }

    public static boolean isAreaChat(@NotNull UUID uuid) {
        return ToNekoNetworkEvents.isPlayerAreaChat(uuid);
    }

    /**
     * 把一条原始聊天内容变成「toNeko 处理过」的内容：
     * 先处理口球（JustARod），再走 toNeko 的 {@code Messaging.prepareMessage}
     * （屏蔽词 → 口癖 → 主人别名替换）。
     */
    public static @NotNull String prepareContent(@NotNull ServerPlayer player, @NotNull String rawMessage) {
        String content = rawMessage;
        if (JustARodCompat.isGagged(player)) {
            content = JustARodCompat.randomMuffled();
        }
        return Messaging.prepareMessage(content, neko(player));
    }

    /** 与 toNeko 一致的「最终单行文本」，仅用于喵叫统计/等级计算，不用于显示。 */
    public static @NotNull String displayLineForStats(@NotNull ServerPlayer player, @NotNull String preparedContent) {
        INeko neko = neko(player);
        String line = Messaging.formatMessage(preparedContent, neko);
        if (isAreaChat(player)) {
            line = AREA_TAG + line;
        }
        return line;
    }

    /** 供占位符使用的「人类可读名称」。 */
    public static @NotNull String playerName(@NotNull ServerPlayer player) {
        return player.getGameProfile().getName();
    }

    /** 便捷：把 § 颜色码转成不带样式的纯文本（调试/统计用）。 */
    public static @NotNull String stripLegacyCodes(@NotNull String input) {
        return input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }

    /** 便捷：把 § 颜色码文本包成 Component（客户端会解释 § 码）。 */
    public static @NotNull Component literal(@NotNull String legacyText) {
        return Component.literal(legacyText);
    }
}
