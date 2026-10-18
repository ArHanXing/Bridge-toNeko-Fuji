package ar.hxi.tfbridge.chat;

import ar.hxi.tfbridge.BridgeConfig;
import ar.hxi.tfbridge.BridgeTonekoFuji;
import mod.fuji.core.config.Configs;
import mod.fuji.core.config.model.MainControlConfigModel;
import mod.fuji.core.module.ModuleLoadDeterminer;

/**
 * 让 Fuji 的 <code>chat.style</code> 模块真正运行起来。
 *
 * <p>Fuji 的模块开关体系：
 * <ul>
 *   <li>配置文件 {@code config/fuji/config.json} 里的 {@code modules.<path>.enable}；</li>
 *   <li>判定函数 {@code ModuleLoadDeterminer.shouldLoadThis(className)} —— 模块的类是否加载、
 *       它的 mixin 是否应用、它的事件消费者是否注册，全看这一个返回值；</li>
 *   <li>判定结果缓存在 {@code MODULE_ENABLE_STATUS}（Map，public static）；</li>
 *   <li>判定读的是 {@code Configs.MAIN_CONTROL_CONFIG.getModelAsJsonTree()}，即
 *       <b>内存里的 model</b>，不是磁盘文件。</li>
 * </ul>
 *
 * <p>坑在于：Fuji 的 mixin 插件（{@code GlobalMixinConfigPlugin}）在 <b>mixin 准备阶段</b>
 * 就会调用 {@code shouldLoadThis}，那比 {@code preLaunch} 入口还早；第一次开服时
 * 它读到的是 Fuji 刚生成的默认配置（{@code modules.chat.enable=false}），于是
 * 「chat.style 未启用」这个结论被写进了 model 和缓存。之后 {@code preLaunch} 再改磁盘文件
 * 已经晚了（下一次开服才生效）。
 *
 * <p>所以这里在本模组的 {@code main} 入口（此时仍在 Fuji 的模块扫描之前）直接改
 * <b>内存里的 model</b> 并清空判定缓存，让后续扫描/注册正确地把 chat.style 拉起来。
 * 配合 {@code BridgePreLaunch} 写盘，第一次开服和之后的每一次都一致。
 */
public final class FujiModuleGate {

    private FujiModuleGate() {
    }

    public static void enableChatStyle() {
        if (!BridgeConfig.forceFujiChatStyle()) {
            BridgeTonekoFuji.LOGGER.info(
                    "[bridge_toneko_fuji] forceFujiChatStyle=false: expecting Fuji's `chat.style` module to be enabled manually.");
            return;
        }
        try {
            MainControlConfigModel model = Configs.MAIN_CONTROL_CONFIG.model();

            if (model.core.debug.disable_all_modules) {
                BridgeTonekoFuji.LOGGER.warn(
                        "[bridge_toneko_fuji] Fuji's `core.debug.disable_all_modules` is true; "
                                + "cannot activate `chat.style`. Set it to false to use this bridge.");
                return;
            }

            boolean changed = false;
            if (!model.modules.chat.enable) {
                model.modules.chat.enable = true;
                changed = true;
            }
            if (!model.modules.chat.style.enable) {
                model.modules.chat.style.enable = true;
                changed = true;
            }

            /* Drop the early decisions made during mixin preparation. */
            ModuleLoadDeterminer.MODULE_ENABLE_STATUS.clear();

            if (changed) {
                BridgeTonekoFuji.LOGGER.info(
                        "[bridge_toneko_fuji] Activated Fuji's `chat.style` module in memory (chat.enable/style.enable = true).");
            }
        } catch (Throwable e) {
            BridgeTonekoFuji.LOGGER.warn(
                    "[bridge_toneko_fuji] Could not activate Fuji's `chat.style` module. "
                            + "Set `modules.chat.enable=true` in config/fuji/config.json and restart.", e);
        }
    }

    /** 供状态日志使用：Fuji 的 chat.style 当前是否判定为启用。 */
    public static boolean isChatStyleEnabled() {
        try {
            return ModuleLoadDeterminer.shouldLoadThis(
                    "mod.fuji.module.initializer.chat.style.ChatStyleInitializer");
        } catch (Throwable e) {
            return false;
        }
    }
}
