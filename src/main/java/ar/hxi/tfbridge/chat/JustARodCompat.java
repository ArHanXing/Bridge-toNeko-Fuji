package ar.hxi.tfbridge.chat;

import ar.hxi.tfbridge.BridgeConfig;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JustARod 的可选兼容层。
 *
 * <p>JustARod 0.2.2 通过 <code>@Mixin(Player.class) ... implements BDSMable</code> 把
 * <code>org.cneko.justarod.entity.BDSMable</code> 接口注入到 {@link Player} 上，
 * 所以运行期可以直接用 <code>instanceof</code> 判断，而编译期完全不需要依赖 JustARod 的 jar。
 *
 * <p>它原本在 {@code CommonChatEvent.onChatMessage} 的 HEAD 注入：戴着口球时取消原消息，
 * 换成一串「呜呜」再走 toNeko 的格式化。桥接接管聊天后那条注入依然会执行（只是输出被本模组
 * 静音），因此这里独立复刻一次「口球 → 呜呜文本」的替换，让 Fuji 的格式能渲染出同样的效果。
 */
public final class JustARodCompat {

    private static final String BDSMABLE_CLASS = "org.cneko.justarod.entity.BDSMable";

    private static boolean resolved;
    private static Class<?> bdsmableClass;
    private static Method getBallMouth;

    private JustARodCompat() {
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            bdsmableClass = Class.forName(BDSMABLE_CLASS);
            getBallMouth = bdsmableClass.getMethod("getBallMouth");
        } catch (Throwable ignored) {
            bdsmableClass = null;
            getBallMouth = null;
        }
    }

    /** JustARod 是否在场（仅用于日志/提示）。 */
    public static boolean isAvailable() {
        resolve();
        return bdsmableClass != null && getBallMouth != null;
    }

    /** 口球剩余时间（tick）；未安装 JustARod 时恒为 0。 */
    public static int ballMouth(@NotNull Player player) {
        resolve();
        if (bdsmableClass == null || getBallMouth == null || !bdsmableClass.isInstance(player)) {
            return 0;
        }
        try {
            Object value = getBallMouth.invoke(player);
            return value instanceof Integer integer ? integer : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    public static boolean isGagged(@NotNull Player player) {
        return ballMouth(player) > 0;
    }

    public static @NotNull String randomMuffled() {
        List<String> phrases = BridgeConfig.muffledPhrases();
        if (phrases.isEmpty()) {
            return "唔…";
        }
        return phrases.get(ThreadLocalRandom.current().nextInt(phrases.size()));
    }
}
