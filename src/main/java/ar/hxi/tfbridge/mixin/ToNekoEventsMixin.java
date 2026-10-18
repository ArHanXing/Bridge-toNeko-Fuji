package ar.hxi.tfbridge.mixin;

import ar.hxi.tfbridge.BridgeConfig;
import ar.hxi.tfbridge.BridgeTonekoFuji;
import net.fabricmc.fabric.api.event.Event;
import org.cneko.toneko.common.mod.events.ToNekoEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 跳过 toNeko 在 {@code ToNekoEvents.init()} 里对
 * {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE} 的注册。
 *
 * <p>原因见 {@link ar.hxi.tfbridge.chat.BridgeChatEvents}：toNeko 的那个监听器会
 * {@code return false} 取消原版消息，从而彻底绕开 Fuji 的 <code>chat.style</code>。
 * 把注册跳过后，改由本模组注册一个「永不取消」的等价监听器。
 *
 * <p>目标点是 {@code init()} 里第一次出现的 {@code Event.register(Object)V} 调用
 * （即 {@code ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(...)}，字节码见
 * <code>invokevirtual net/fabricmc/fabric/api/event/Event.register:(Ljava/lang/Object;)V</code>）。
 */
@Mixin(ToNekoEvents.class)
public class ToNekoEventsMixin {

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/fabricmc/fabric/api/event/Event;register(Ljava/lang/Object;)V",
                    ordinal = 0
            ),
            require = 1
    )
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void bridge$suppressToNekoChatHandler(Event<?> event, Object listener) {
        if (BridgeConfig.enableChatTakeover()) {
            BridgeTonekoFuji.LOGGER.info(
                    "[bridge_toneko_fuji] Suppressed toNeko's ALLOW_CHAT_MESSAGE handler; Fuji chat.style is authoritative.");
            return;
        }
        // 总开关关闭：保留 toNeko 的原始行为（它自己格式化并取消原版消息）。
        ((Event) event).register(listener);
    }
}
