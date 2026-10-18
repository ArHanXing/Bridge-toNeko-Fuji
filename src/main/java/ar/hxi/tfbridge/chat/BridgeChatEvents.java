package ar.hxi.tfbridge.chat;

import ar.hxi.tfbridge.BridgeConfig;
import ar.hxi.tfbridge.BridgeTonekoFuji;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import org.cneko.toneko.common.mod.events.CommonChatEvent;
import org.cneko.toneko.common.util.ConfigUtil;
import org.jetbrains.annotations.NotNull;

/**
 * 接管玩家聊天的入口。
 *
 * <p>toNeko 原本在 {@code ToNekoEvents.init()} 里注册
 * {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE}，一旦 {@code ConfigUtil.isChatEnable()} 为真就直接
 * 「自己格式化 + 自己广播，并 return false 取消原版消息」。这会让挂在这一步之后的 Fuji
 * {@code chat.style} 完全收不到消息。
 *
 * <p>本模组用 {@code ToNekoEventsMixin} 把那次注册跳过，再在这里注册一个<b>永不取消</b>的监听器：
 * <ul>
 *   <li>仍然调用 {@link CommonChatEvent#onChatMessage}，从而保留 toNeko 的全部副作用
 *       （喵叫统计、等级经验、区域频道判定、猫娘 AI 触发、以及 JustARod 在它 HEAD 上的口球注入）；</li>
 *   <li>但它在 {@code sendMessage} / {@code sendMessageInRange} 里的广播被本模组的
 *       {@code CommonChatEventMixin} 静音，所以不会出现「两份消息」；</li>
 *   <li>最后 {@code return true}，让原版继续广播，于是 Fuji 的 {@code PlayerChatMessagePreEvent}
 *       → {@code ChatStyleInitializer} 正常渲染，内容由 {@code ChatStyleInitializerMixin} 注入
 *       toNeko 的预处理结果与占位符。</li>
 * </ul>
 */
public final class BridgeChatEvents {

    private BridgeChatEvents() {
    }

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(BridgeChatEvents::onAllowChatMessage);
    }

    /**
     * @return 恒为 {@code true}：让原版消息继续走到 Fuji 的渲染管线。
     */
    private static boolean onAllowChatMessage(@NotNull net.minecraft.network.chat.PlayerChatMessage message,
                                              @NotNull net.minecraft.server.level.ServerPlayer sender,
                                              @NotNull net.minecraft.network.chat.ChatType.Bound parameters) {
        // 总开关关闭时交还给 toNeko 自己的注册（ToNekoEventsMixin 会放行）。
        if (!BridgeConfig.enableChatTakeover()) {
            return true;
        }
        // toNeko 自己关闭了聊天接管时，本模组也不应该插手。
        if (!ConfigUtil.isChatEnable()) {
            return true;
        }
        try {
            CommonChatEvent.onChatMessage(message, sender, parameters);
        } catch (Throwable e) {
            // 聊天路径上的异常绝不能让玩家发不出话。
            BridgeTonekoFuji.LOGGER.error("[bridge_toneko_fuji] toNeko chat hook failed; falling back to Fuji-only rendering.", e);
        }
        return true;
    }
}
