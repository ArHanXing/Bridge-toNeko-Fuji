package ar.hxi.tfbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 在 Fuji 读取配置之前，把 Fuji 的 <code>chat.style</code> 模块打开。
 *
 * <p><b>为什么需要这一步？</b> Fuji 把所有模块默认关掉了：生成的
 * {@code config/fuji/config.json} 里是 {@code modules.chat.enable = false}。
 * 而「某个模块的类是否加载 / 它的 mixin 是否应用 / 它的事件消费者是否注册」
 * 全部由 {@code ModuleLoadDeterminer.shouldLoadThis(...)} 依据这份配置决定。
 * 也就是说：<b>不打开这个开关，Fuji 的聊天格式模块根本不会加载</b>，
 * 本模组也就没有可依托的渲染器。
 *
 * <p>必须放在 {@code preLaunch} 而不是 {@code main}：Fuji 在它自己的
 * {@code main} 入口里就会读取配置，等到本模组的 {@code main} 执行时已经太晚。
 *
 * <p>这里只改两个布尔值（{@code modules.chat.enable} 与
 * {@code modules.chat.style.enable}），其余配置原样保留；文件不存在时会写一份最小的
 * 初始配置，Fuji 之后会自己补全缺省字段。如果管理员想自己管理 Fuji 配置，把本模组
 * 配置里的 {@code forceFujiChatStyle} 设为 {@code false} 即可跳过。
 */
public class BridgePreLaunch implements PreLaunchEntrypoint {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @Override
    public void onPreLaunch() {
        BridgeConfig.load();
        if (!BridgeConfig.forceFujiChatStyle()) {
            return;
        }
        try {
            enableFujiChatStyle();
        } catch (Exception e) {
            BridgeTonekoFuji.LOGGER.warn(
                    "[bridge_toneko_fuji] Failed to pre-enable Fuji's chat.style module; "
                            + "set `modules.chat.enable=true` in config/fuji/config.json manually.", e);
        }
    }

    private static void enableFujiChatStyle() throws Exception {
        Path path = FabricLoader.getInstance().getConfigDir().resolve("fuji").resolve("config.json");

        JsonObject root;
        if (Files.exists(path)) {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            root = GSON.fromJson(json, JsonObject.class);
            if (root == null) {
                root = new JsonObject();
            }
        } else {
            root = new JsonObject();
        }

        JsonObject modules = childObject(root, "modules");
        JsonObject chat = childObject(modules, "chat");
        JsonObject style = childObject(chat, "style");

        boolean chatWasEnabled = isEnabled(chat);
        boolean styleWasEnabled = isEnabled(style);

        chat.addProperty("enable", true);
        style.addProperty("enable", true);

        if (chatWasEnabled && styleWasEnabled) {
            return;
        }

        Files.createDirectories(path.getParent());
        Files.writeString(path, GSON.toJson(root), StandardCharsets.UTF_8);
        BridgeTonekoFuji.LOGGER.info(
                "[bridge_toneko_fuji] Enabled Fuji's `chat.style` module in {} (chat.enable={} -> true, style.enable={} -> true).",
                path, chatWasEnabled, styleWasEnabled);
    }

    private static boolean isEnabled(@NotNull JsonObject object) {
        return object.has("enable") && object.get("enable").getAsJsonPrimitive().getAsBoolean();
    }

    private static @NotNull JsonObject childObject(@NotNull JsonObject parent, @NotNull String key) {
        if (parent.has(key) && parent.get(key).isJsonObject()) {
            return parent.getAsJsonObject(key);
        }
        JsonObject created = new JsonObject();
        parent.add(key, created);
        return created;
    }
}
