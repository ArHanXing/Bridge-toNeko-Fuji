package ar.hxi.tfbridge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 桥接模组自己的配置（<code>config/bridge_toneko_fuji.json</code>）。
 */
public final class BridgeConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "bridge_toneko_fuji.json";

    /** JustARod 0.2.2 (CommonChatEventMixin) 的默认呜呜语，保留同款文案。 */
    private static final List<String> DEFAULT_MUFFLED = List.of(
            "嗯…呜…♡",
            "呜…咿…",
            "唔…嗯嗯…",
            "嗯嗯~♡",
            "咿呀…唔…",
            "呜嗯…哈…",
            "唔…咕…",
            "嗯唔…",
            "呜…唔唔…",
            "唔…啾…"
    );

    /** 总开关：是否由本模组接管玩家聊天，把 toNeko 的修饰注入 Fuji 的 chat.style 管线。 */
    private static boolean enableChatTakeover = true;
    /** 是否在 Fuji 的 chat.style 模块被关闭时强制启用它（Fuji 默认 `modules.chat.enable=false`）。 */
    private static boolean forceFujiChatStyle = true;
    /** {@code %toneko:suffix%} 的取值。 */
    private static String suffix = "";
    /** 是否在区域聊天时自动在 sender 前面加上 toNeko 的 {@code [区域]} 标记。 */
    private static boolean keepAreaTag = true;
    /** 口球（JustARod）替换语。 */
    private static List<String> muffledPhrases = new ArrayList<>(DEFAULT_MUFFLED);

    private BridgeConfig() {
    }

    public static boolean enableChatTakeover() {
        return enableChatTakeover;
    }

    public static boolean forceFujiChatStyle() {
        return forceFujiChatStyle;
    }

    public static @NotNull String suffix() {
        return suffix;
    }

    public static boolean keepAreaTag() {
        return keepAreaTag;
    }

    public static @NotNull List<String> muffledPhrases() {
        return muffledPhrases;
    }

    /** 由主入口调用一次：文件不存在就写默认值，存在就读取。 */
    public static void load() {
        Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
        if (!Files.exists(path)) {
            write(path);
        } else {
            read(path);
        }
        BridgeTonekoFuji.LOGGER.info(
                "[bridge_toneko_fuji] Config: takeover={} forceFujiChatStyle={} keepAreaTag={} suffix='{}'.",
                enableChatTakeover, forceFujiChatStyle, keepAreaTag, suffix);
    }

    private static void read(@NotNull Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            JsonObject object = GSON.fromJson(json, JsonObject.class);
            if (object == null) {
                return;
            }
            if (object.has("enableChatTakeover")) {
                enableChatTakeover = object.get("enableChatTakeover").getAsBoolean();
            }
            if (object.has("forceFujiChatStyle")) {
                forceFujiChatStyle = object.get("forceFujiChatStyle").getAsBoolean();
            }
            if (object.has("suffix")) {
                suffix = object.get("suffix").getAsString();
            }
            if (object.has("keepAreaTag")) {
                keepAreaTag = object.get("keepAreaTag").getAsBoolean();
            }
            if (object.has("muffledPhrases") && object.get("muffledPhrases").isJsonArray()) {
                List<String> loaded = new ArrayList<>();
                object.getAsJsonArray("muffledPhrases").forEach(element -> loaded.add(element.getAsString()));
                if (!loaded.isEmpty()) {
                    muffledPhrases = loaded;
                }
            }
        } catch (Exception e) {
            BridgeTonekoFuji.LOGGER.warn("[bridge_toneko_fuji] Failed to read {}; using defaults.", path, e);
        }
    }

    private static void write(@NotNull Path path) {
        JsonObject object = new JsonObject();
        object.addProperty("enableChatTakeover", enableChatTakeover);
        object.addProperty("forceFujiChatStyle", forceFujiChatStyle);
        object.addProperty("suffix", suffix);
        object.addProperty("keepAreaTag", keepAreaTag);
        JsonArray array = new JsonArray();
        DEFAULT_MUFFLED.forEach(array::add);
        object.add("muffledPhrases", array);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(object), StandardCharsets.UTF_8);
        } catch (IOException e) {
            BridgeTonekoFuji.LOGGER.warn("[bridge_toneko_fuji] Failed to write {}.", path, e);
        }
    }
}
