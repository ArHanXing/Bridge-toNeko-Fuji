package ar.hxi.tfbridge;

import ar.hxi.tfbridge.chat.BridgeChatEvents;
import ar.hxi.tfbridge.chat.BridgePlaceholders;
import ar.hxi.tfbridge.chat.FujiModuleGate;
import ar.hxi.tfbridge.chat.JustARodCompat;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bridge: toNeko × Fuji 的主入口（双端通用）。
 *
 * <p>启动顺序：
 * <ol>
 *   <li>{@code preLaunch}（{@link BridgePreLaunch}）：把 Fuji 的 {@code chat.style}
 *       写进 {@code config/fuji/config.json}，让下一次开服天然就是启用状态；</li>
 *   <li>{@code main}（本类）：读本模组配置 → {@link FujiModuleGate} 在内存里确保
 *       {@code chat.style} 已启用（解决首次开服时 Fuji 已在 mixin 阶段提前判定为关闭的问题）
 *       → 注册占位符 → 注册聊天监听；</li>
 *   <li>运行期由两处 mixin 接线：{@code ToNekoEventsMixin} 跳过 toNeko 自己的聊天注册，
 *       {@code ChatStyleInitializerMixin} 把 toNeko 的预处理结果喂给 Fuji 的内容渲染。</li>
 * </ol>
 */
public class BridgeTonekoFuji implements ModInitializer {

    public static final String MOD_ID = "bridge_toneko_fuji";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        BridgeConfig.load();

        FujiModuleGate.enableChatStyle();

        BridgePlaceholders.register();
        BridgeChatEvents.register();

        LOGGER.info("[bridge_toneko_fuji] Fuji={} toNeko={} JustARod={} fujiChatStyle={}.",
                versionOf("fuji"), versionOf("toneko"), JustARodCompat.isAvailable(),
                FujiModuleGate.isChatStyleEnabled());
        LOGGER.info("[bridge_toneko_fuji] Fuji chat.style is now the renderer for toNeko chat (takeover={}).",
                BridgeConfig.enableChatTakeover());
    }

    private static String versionOf(String modId) {
        return FabricLoader.getInstance().getModContainer(modId)
                .map(container -> container.getMetadata().getVersion().getFriendlyString())
                .orElse("<absent>");
    }
}
