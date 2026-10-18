package ar.hxi.tfbridge.mixin;

import ar.hxi.tfbridge.BridgeConfig;
import ar.hxi.tfbridge.BridgeTonekoFuji;
import ar.hxi.tfbridge.chat.FujiText;
import ar.hxi.tfbridge.chat.TonekoChat;
import mod.fuji.module.initializer.chat.style.ChatStyleInitializer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.cneko.toneko.common.mod.entities.INeko;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 把 toNeko 的聊天修饰注入 Fuji 的 {@code chat.style} 渲染。
 *
 * <p>Fuji 的 {@code handleOnPlayerChatEvent} 顺序是：
 * <ol>
 *   <li>{@code parseSenderText(player)} —— 解析全局 sender 格式（原生支持占位符）；</li>
 *   <li>{@code parseContentText(player, 原始消息)} —— 解析内容格式（原本<b>不</b>支持占位符）。</li>
 * </ol>
 *
 * <p>这里：
 * <ul>
 *   <li>{@code parseContentText} 的 HEAD 处拦截，先把「原始消息」交给 toNeko 的
 *       {@code Messaging.prepareMessage}（屏蔽词 → 口癖 → 主人别名）与 JustARod 的口球替换，
 *       再用 {@link FujiText#parseContent} 以 Fuji 原顺序渲染，并补上 {@code %toneko:*%} 解析；</li>
 *   <li>{@code parseSenderText} 的 RETURN 处补上 toNeko 区域聊天的 {@code [区域]} 行首标记
 *       （若管理员已经在 sender 格式里写了 {@code %toneko:area%} 则不再重复）。</li>
 * </ul>
 */
@Mixin(ChatStyleInitializer.class)
public class ChatStyleInitializerMixin {

    @Inject(method = "parseContentText", at = @At("HEAD"), cancellable = true, require = 1)
    private static void bridge$parseContentText(ServerPlayer player, String message,
                                                CallbackInfoReturnable<Component> cir) {
        if (!BridgeConfig.enableChatTakeover()) {
            return;
        }
        if (!(player instanceof INeko)) {
            return;
        }
        String prepared = TonekoChat.prepareContent(player, message);
        Component out = FujiText.parseContent(player, prepared);
        BridgeTonekoFuji.LOGGER.debug("[bridge_toneko_fuji] parseContentText in='{}' prepared='{}' out='{}'",
                message, prepared, out.getString());
        cir.setReturnValue(out);
    }

    @Inject(method = "parseSenderText", at = @At("RETURN"), cancellable = true, require = 1)
    private static void bridge$prependAreaTag(ServerPlayer player, CallbackInfoReturnable<Component> cir) {
        if (!BridgeConfig.enableChatTakeover() || !BridgeConfig.keepAreaTag()) {
            return;
        }
        if (!TonekoChat.isAreaChat(player)) {
            return;
        }
        // 管理员显式写了 %toneko:area% 就以他的排版为准，不重复插入。
        if (FujiText.senderFormat().contains("%toneko:area%")) {
            return;
        }
        Component original = cir.getReturnValue();
        cir.setReturnValue(Component.literal(TonekoChat.AREA_TAG)
                .append(original == null ? Component.empty() : original));
    }
}
