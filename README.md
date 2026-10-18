# Bridge: toNeko × Fuji

修好了 toneko 和 fuji 互相抢聊天位的问题。

让 **Fuji** 的 `chat.style` 成为 **toNeko** 聊天的渲染器，并把 toNeko / JustARod 的聊天数据
暴露成 Fuji 格式串里可用的占位符。

目标平台：**Fabric 1.21.1**

使用 DS v4.1 生成，使用 GPLv3。

以下内容为 AI 生成。

---

## 1. 解决什么问题

这两个模组都在改聊天，而且**互相会打架**：

| 模组 | 它做了什么 |
|---|---|
| **Fuji** | `chat.style` 模块挂在 `ServerGamePacketListenerImpl.broadcastChatMessage` 上（自己的 `PlayerChatMessagePreEvent`），用 `style.sender` + `style.content` / 玩家个人 `chat.json` 格式渲染整行聊天。原生支持 MiniMessage 与 Text Placeholder API。 |
| **toNeko** | 在 `ServerMessageEvents.ALLOW_CHAT_MESSAGE` 里，只要 `ConfigUtil.isChatEnable()` 为真就**自己格式化并广播，然后 `return false` 取消原版消息**。这会让 Fuji 完全收不到这条聊天。它自己还带前缀（`[猫娘]`）、昵称、口癖（语气词插入）、屏蔽词、主人别名替换、区域聊天、以及猫娘 AI 触发。 |
| **JustARod** | `@Mixin(CommonChatEvent.onChatMessage)`：戴着「口球」时取消原消息、换成一串呜呜语；同时通过 `CREATE_CHAT_PREFIXES` 给前缀列表追加效果标签（高潮/发情/强壮/淫趴…）。 |

一句话：**toNeko 的 `return false` 把 Fuji 挤掉了**。本模组把这条链路重新接上，并且让
toNeko 的那些「修饰」以 Fuji 的格式呈现，而不是两套格式二选一。

---

## 2. 工作方式

```
玩家说话
  │
  ├─ BridgeChatEvents（本模组注册的 ALLOW_CHAT_MESSAGE）
  │     └─ 仍然调用 toNeko 的 CommonChatEvent.onChatMessage(...)
  │           ├─ 保留全部副作用：喵叫统计 / 等级经验 / 区域频道 / 猫娘 AI 触发
  │           └─ 它在 sendMessage / sendMessageInRange 里的「自己广播」被本模组静音
  │        → 返回 true（永不取消），让原版消息继续往下走
  │
  ├─ Fuji 的 PlayerChatMessagePreEvent → ChatStyleInitializer
  │     ├─ parseSenderText(player)        ← 本模组在这里补 [区域] 标记
  │     └─ parseContentText(player, msg)  ← 本模组在这里注入
  │            ├─ Messaging.prepareMessage(...)  ← toNeko 的屏蔽词 / 口癖 / 主人别名
  │            ├─ JustARod 口球 → 呜呜语替换
  │            └─ 按 Fuji 的原顺序渲染（全局 content 格式 → 个人格式 → 样式剥离 → 解析）
  │               并在最后额外解析 %toneko:*% / %justarod:*% 占位符
  │
  └─ 客户端拿到 unsignedChatContent = Fuji 渲染结果
```

### 具体接线

| 文件 | 作用 |
|---|---|
| `mixin/ToNekoEventsMixin` | `@Redirect` 掉 `ToNekoEvents.init()` 里对 `ALLOW_CHAT_MESSAGE` 的**第 1 次** `Event.register`，使 toNeko 不再抢聊天；`enableChatTakeover=false` 时原样放行。 |
| `chat/BridgeChatEvents` | 注册一个永不取消的 `ALLOW_CHAT_MESSAGE`，调用 toNeko 的 `onChatMessage` 以保留 AI/统计等副作用。 |
| `mixin/CommonChatEventMixin` | 静音 toNeko 的 `sendMessage` / `sendMessageInRange`，避免「一条消息出现两遍」。 |
| `mixin/ChatStyleInitializerMixin` | 在 Fuji 的 `parseContentText` 上注入 toNeko 预处理 + 占位符解析；在 `parseSenderText` 上补 `[区域]` 标记。 |
| `chat/FujiText` | 按 Fuji 的原顺序复刻内容渲染，并额外支持 `%toneko:*%` / `%justarod:*%`。 |
| `chat/BridgePlaceholders` | 用 Text Placeholder API 注册 `%toneko:*%` / `%justarod:*%`。 |
| `chat/FujiModuleGate` + `BridgePreLaunch` | 打开 Fuji 的 `chat.style` 模块（Fuji 默认把所有模块关掉了，见下）。 |
| `chat/TonekoChat` / `chat/JustARodCompat` | 读取 toNeko 数据；对 JustARod 只做反射，**编译期不依赖它**。 |

---

## 3. 依赖与安装

### 运行时必需

| 模组 | 版本 | 说明 |
|---|---|---|
| Fabric Loader | ≥ 0.15（实测 0.20.0） | |
| Fabric API | 1.21.1 线（实测 `0.116.17+1.21.1`） | |
| **Fuji** | `14.12.0`（`mc1.21.1` 构建） | 必须有 `chat.style` 模块（本模组会自动打开） |
| **toNeko** | `1.9.6`（Fabric 1.21.1） | |
| JustARod | `0.2.2`（可选） | 只在场时才启用口球相关功能 |

toNeko 自己还需要 GeckoLib 与 Player Animator；JustARod 还需要 Fabric Language Kotlin、ctLib 等。
把它们一起放进 `mods/` 即可。

> **重要**：本模组 `depends` 里写了 `fuji` 与 `toneko`，缺少任一个都不会加载。

### 关于 Fuji 的模块开关

Fuji 默认**关掉所有模块**，生成的 `config/fuji/config.json` 里是：

```json
"modules": { "chat": { "enable": false, "style": { "enable": true } } }
```

而 Fuji 判断「模块是否加载 / 它的 mixin 是否应用 / 它的事件消费者是否注册」只看一个函数
`ModuleLoadDeterminer.shouldLoadThis(className)`，它读的是**内存里的配置 model**。
更麻烦的是：Fuji 的 mixin 插件在 **mixin 准备阶段**（比 `preLaunch` 入口还早）就会问一次这个问题，
所以第一次开服时它读到的是 Fuji 刚写出的默认值 `chat.enable=false`，并把这个结论缓存下来。

因此本模组两级处理：

1. `preLaunch`（`BridgePreLaunch`）把磁盘上的 `config/fuji/config.json` 改成
   `modules.chat.enable=true`、`modules.chat.style.enable=true`
   （下一次开服天然就是启用状态；文件不存在时会写一份最小配置，Fuji 之后自行补全）；
2. `main`（`FujiModuleGate`）在 **Fuji 扫描模块之前**，直接改内存里的 model 并清空判定缓存，
   解决「首次开服时已被提前判定为关闭」的问题。

想自己管理 Fuji 配置就设 `forceFujiChatStyle=false`；此时若 Fuji 的 `chat.style` 没开，
本模组**不会**接管聊天（避免出现「既没有 Fuji 格式、也没有 toNeko 格式」的裸原版聊天）。

---

## 4. 配置

`config/bridge_toneko_fuji.json`（首次启动自动生成）：

```json
{
  "enableChatTakeover": true,
  "forceFujiChatStyle": true,
  "suffix": "",
  "keepAreaTag": true,
  "muffledPhrases": ["嗯…呜…♡", "呜…咿…", "唔…嗯嗯…", "嗯嗯~♡", "咿呀…唔…",
                     "呜嗯…哈…", "唔…咕…", "嗯唔…", "呜…唔唔…", "唔…啾…"]
}
```

| 键 | 默认 | 说明 |
|---|---|---|
| `enableChatTakeover` | `true` | 总开关。`false` 时本模组完全不介入：toNeko 恢复它自己的 `chat.format`（Fuji 不再渲染玩家聊天），也不会强制打开 Fuji 模块。适合排障与 A/B 对比。 |
| `forceFujiChatStyle` | `true` | 是否自动打开 Fuji 的 `chat.style` 模块（见上一节）。 |
| `suffix` | `""` | `%toneko:suffix%` 的取值。toNeko 本身没有后缀概念，这个是留给管理员排版用的。 |
| `keepAreaTag` | `true` | 区域聊天时是否自动在 sender 最前面加 toNeko 的 `[区域]` 标记。 |
| `muffledPhrases` | 同 JustARod 0.2.2 | 口球时替换用的呜呜语池。 |

---

## 5. 占位符

在 **Fuji 的格式串**里使用：

* `chat.style` 的 `style.sender`
* `chat.style` 的 `style.content`
* 玩家个人格式 `config/fuji/modules/chat/style/chat.json` 的 `player2format`

### `%toneko:*%`

| 占位符 | 含义 | 示例值 |
|---|---|---|
| `%toneko:prefix%` | toNeko + JustARod 的完整前缀列表，保留 § 颜色码 | `[§a§a猫娘§f§r]`（非猫娘为空） |
| `%toneko:prefix_raw%` | 同上，去掉 § 颜色码 | `[猫娘]` |
| `%toneko:suffix%` | 配置项 `suffix` | 默认空 |
| `%toneko:nickname%` | 猫娘昵称（带 `§6~§f` 高亮）；没设昵称时回落真实名 | `§6~§f小喵` |
| `%toneko:name%` | 玩家真实名 | `Steve` |
| `%toneko:is_neko%` | 是否猫娘 | `true` / `false` |
| `%toneko:level%` | toNeko 等级总值（保留 1 位小数） | `0.6` |
| `%toneko:energy%` | 当前猫娘能量 | `42` |
| `%toneko:max_energy%` | 能量上限 | `100` |
| `%toneko:phrase%` | 口癖 / 语气词 | `喵` |
| `%toneko:owner%` | 主人称谓；没有主人时为空 | `主人` |
| `%toneko:area%` | 区域聊天标记；非区域聊天为空 | `§a[§f区域§a]§r ` |

### `%justarod:*%`（JustARod 不在场时返回 `false` / `0`）

| 占位符 | 含义 |
|---|---|
| `%justarod:gagged%` | 是否戴着口球 |
| `%justarod:ball_mouth%` | 口球剩余秒数 |

### 示例格式

`config/fuji/modules/chat/style/config.json`：

```json
{
  "style": {
    "sender": "%toneko:prefix%%toneko:nickname% <dark_gray>»</dark_gray> ",
    "content": "%toneko:area%%message%"
  }
}
```

`config/fuji/modules/chat/style/chat.json`（个人格式，同样能用这些占位符）：

```json
{ "format": { "player2format": {
    "Steve": "<#FFC7EA>[%toneko:level%]</#FFC7EA> %message%"
} } }
```

> **注意**：`style.sender` 由 Fuji 用「MiniMessage + 全局占位符」解析，所以那里
> **任何** Placeholder API 占位符都能用（如 `%fuji:player_prefix%`、`%player:name%`）。
> 而 `style.content` 与个人格式原本只解析 MiniMessage，本模组**只额外解析
> `%toneko:*%` / `%justarod:*%`**，刻意不启用全局占位符 —— 否则玩家聊天正文里的
> `%xxx%` 也会被当成占位符解析（占位符注入）。

---

## 6. 行为细节

* **toNeko 的 `chat.format` 被忽略**。接管期间一行聊天由 Fuji 决定长什么样；
  toNeko 的数据通过上面的占位符参与排版。
* **保留的 toNeko 行为**：屏蔽词替换、口癖插入、主人别名替换、昵称、
  `CREATE_CHAT_PREFIXES`（含 JustARod 的效果标签）、区域频道判定、
  喵叫统计与等级经验、猫娘 AI 触发（`processProximityChat` / `processAreaChatAI`）。
* **保留的 JustARod 行为**：口球时消息被替换成呜呜语（随机取自 `muffledPhrases`），
  并且与 JustARod 原实现一致地**跳过**该条的 AI 触发与统计。
* **不受影响**：toNeko 走网络包发送的猫娘 AI 回复 / 气泡（`Messaging.sendNekoChat*`）、
  Fuji 的其它 `chat.*` 模块（history / mention / replace / trigger / display）。
* **控制台仍会打印渲染后的整行**（原版对广播消息的日志），方便服务端排查。
* **前缀里的双重颜色码**：`[§a§a猫娘§f§r]` 是 toNeko 自己的写法
  （`PREFIX_FORMAT = "[§a%s§f§r]"` 与 `LanguageUtil.prefix` 里自带的 `§a` 叠加），本模组保持一致。

---

## 7. 构建

```bash
# 需要 JDK 25 跑 Gradle（Loom 1.18.4 要求），产物仍是 Java 21 字节码
export JAVA_HOME=/path/to/jdk-25
./gradlew build        # 产物 build/libs/bridge_toneko_fuji-0.1.0.jar
```

`libs/` 里的两个 jar 是**编译期**目标（loom 会把它们从 intermediary 重映射到本工程的
mojmap 命名空间）：

| 文件 | 来源 |
|---|---|
| `libs/fuji-14.12.0-mc1.21.1.jar` | Modrinth `fuji` 14.12.0 / mc1.21.1（对应 commit `38824a5371`） |
| `libs/toneko-1.9.6.jar` | Modrinth `tonekomod` 1.9.6 / Fabric 1.21.1 |

> 本工程钉死 **Loom 1.18.4 + Gradle 9.7.1 + JDK 25**：Fuji 14.12.0 的 jar 由 Loom 1.18.2 构建，
> Loom 会拒绝加载「比自己新」的 mod；而 Loom 1.18.4 自身要求 Gradle ≥ 9.7 且运行在 JDK 25 上。

---

## 8. 复现验证（三模组并存）

`tools/setup-testserver.sh` 会下载 Fabric 服务端、Fabric API、Fuji、toNeko、GeckoLib、
Player Animator、Fabric Language Kotlin、ctLib、JustARod 0.2.2，并把本模组的 jar 放进 `mods/`。

```bash
bash tools/setup-testserver.sh /tmp/tfbridge-testserver
cd /tmp/tfbridge-testserver
echo "eula=true" > eula.txt
java -Xmx2G -jar fabric-server-launch.jar nogui
```

然后用 `tools/verify-chat.js`（需要 `npm i mineflayer`）跑端到端断言：

```bash
cd tools && npm i mineflayer && TB_FIFO=/tmp/tfbridge-testserver/console.fifo node verify-chat.js
```

它在真实服务端上开一个离线机器人，覆盖三种场景并断言**最终发给客户端的
`ClientboundPlayerChatPacket`**：

```
PASS  A1 exactly one broadcast per send
PASS  A2 Fuji sender format applied
PASS  A3 placeholder %toneko:is_neko% == false
PASS  A4 content preserved end-to-end
PASS  B1 [猫娘] prefix appears
PASS  B2 toNeko nickname placeholder
PASS  B3 %toneko:is_neko% == true
PASS  B4 口癖 inserted into content              ["hello world喵!"]
PASS  C1 %justarod:gagged% == true
PASS  C2 gag replaces content with muffled text  ["唔…咕…喵"]
===== 10/10 checks passed =====
```

服务端控制台里对应的渲染结果（`§` 码被日志剥离）：

```
[TONEKO-PREFIX:]              [NICK:Tester]     [ISNEKO:false] [LV:0.0] [GAG:false] Tester > plain line
[TONEKO-PREFIX:[§a§a猫娘§f§r]] [NICK:§6~§f小喵] [ISNEKO:true]  [LV:0.6] [GAG:false] Tester > hello world喵!
[TONEKO-PREFIX:[§a§a猫娘§f§r]] [NICK:§6~§f小喵] [ISNEKO:true]  [LV:0.6] [GAG:true]  Tester > 唔…咕…喵
```

> 上面这个 `[TONEKO-PREFIX:...]` 之类的 sender 只是**验证用**的临时格式，
> 用来把每个占位符的取值显式印出来；默认请用第 5 节的示例格式。

### 手动测试清单（真实客户端）

1. 普通玩家说话：只出现**一行**，样式来自 Fuji，toNeko 的格式不再出现。
2. `/tonekoadmin neko <自己> setNeko true` + `setNickName 小喵`：
   前缀 `[猫娘]` 出现、昵称变成 `~小喵`、带标点的句子末尾被插入口癖。
3. 说一句带屏蔽词的话（先用 toNeko 的指令加屏蔽词）：内容被替换。
4. 点击 toNeko 聊天框右上角的「区域/全服」按钮切到区域聊天：
   sender 前面出现 `[区域]`，且只有附近玩家收到。
5. 用 JustARod 给自己戴上口球（`/bdsm ballmouth set 400`）：说话变成呜呜语。
6. `/fuji module` 里把 `chat.style` 关掉并重启：本模组不接管，toNeko 恢复自己的格式（不会出现裸原版聊天）。

---

## 9. 已知限制

* 只针对 **MC 1.21.1 + Fuji 14.12.0 + toNeko 1.9.6** 这条组合做过验证。
  三个模组任意一方内部结构变动都可能让 mixin 目标失配；本模组的 mixin 配置是
  `"required": true` + `defaultRequire: 1`，失配会**直接报错**而不是静默错乱。
* JustARod 在 `13757efd` 之前的源码处于「yarn→mojmap 迁移中」的状态、**无法编译**
  （`Item.Settings` / `PacketCodecs` 等 yarn 名仍在），所以联调用的是它最后一个
  Kotlin/Fabric 1.21.1 发行包 `JustARod-0.2.2.jar`（release V0.1.22，2026-05-16）。
  口球相关的类名/接口名与源码一致（`org.cneko.justarod.entity.BDSMable#getBallMouth`）。
* toNeko 的口癖依赖它的语言文件；若语言里 `phrase` 为空，口癖自然不会插入
  （不是本模组的问题）。
* `style.content` 里的占位符是**字符串替换**实现的，所以取值中的 `<` 会被转义，
  不支持在 content 里用 `<click>` 之类的标签包裹占位符结果（sender 里可以，因为那里走
  Fuji 原生的 Component 解析）。
