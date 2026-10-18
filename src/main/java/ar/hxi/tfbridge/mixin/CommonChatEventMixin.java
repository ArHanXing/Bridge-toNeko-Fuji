package ar.hxi.tfbridge.mixin;

import ar.hxi.tfbridge.BridgeConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.cneko.toneko.common.mod.events.CommonChatEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 静音 toNeko 自己的聊天广播。
 *
 * <p>桥接仍然调用 {@code CommonChatEvent.onChatMessage(...)} 以保留它的全部副作用
 * （喵叫统计、等级经验、区域频道、猫娘 AI 触发、JustARod 的口球注入），
 * 但最终的上屏交给 Fuji。因此这里把 toNeko 的两个「广播出口」取消掉，
 * 避免同一句话出现两遍：
 * <ul>
 *   <li>{@code sendMessage(Component)} —— 全服模式；</li>
 *   <li>{@code sendMessageInRange(Component, ServerPlayer, double)} —— 区域模式。</li>
 * </ul>
 */
@Mixin(CommonChatEvent.class)
public class CommonChatEventMixin {

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bridge$muteBroadcast(Component message, CallbackInfo ci) {
        if (BridgeConfig.enableChatTakeover()) {
            ci.cancel();
        }
    }

    @Inject(method = "sendMessageInRange", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bridge$muteRangeBroadcast(Component message, ServerPlayer sender, double range, CallbackInfo ci) {
        if (BridgeConfig.enableChatTakeover()) {
            ci.cancel();
        }
    }
}
