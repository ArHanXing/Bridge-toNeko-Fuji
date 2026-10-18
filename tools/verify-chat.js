// Definitive end-to-end verification for the bridge mod.
//
// Why raw packets: mineflayer's `bot.on('message')` renders the *signed* body content
// (`plainMessage`), whereas vanilla clients render `unsignedChatContent` whenever the
// server supplies it — which is exactly how Fuji's chat.style replaces the chat text.
// So we assert on the raw ClientboundPlayerChatPacket fields.
//
// Scenarios:
//   A. plain player      -> Fuji sender format + %toneko:*% resolve; exactly one broadcast
//   B. neko player       -> [猫娘] prefix, nickname, is_neko, level, 口癖 inserted
//   C. gagged player     -> %justarod:gagged% true + content replaced by muffled text
const mineflayer = require('mineflayer');
const fs = require('fs');

const FIFO = process.env.TB_FIFO || './console.fifo';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const cmd = (c) => fs.appendFileSync(FIFO, c + '\n');

function unwrap(c) {
  if (c == null || typeof c !== 'object') return c;
  // prismarine wraps NBT-typed values as {type, value}
  if ('type' in c && 'value' in c && typeof c.type === 'string') return unwrap(c.value);
  return c;
}
function plain(c) {
  c = unwrap(c);
  if (c == null) return null;
  if (typeof c === 'string') return c;
  if (Array.isArray(c)) return c.map(plain).join('');
  if (typeof c === 'object') {
    if ('type' in c && 'value' in c) return plain(c.value);
    let out = '';
    if (c.text !== undefined) out += plain(c.text);
    if (c.extra !== undefined) out += plain(c.extra);
    return out;
  }
  return String(c);
}

const results = [];
function check(name, ok, detail) {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail !== undefined ? '   [' + detail + ']' : ''}`);
}

(async () => {
  const bot = mineflayer.createBot({
    host: '127.0.0.1', port: 25565, username: 'Tester', auth: 'offline', version: '1.21.1',
  });
  const packets = [];
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('error', rej); });
  bot._client.on('player_chat', (p) => packets.push(p));
  await sleep(1200);
  console.log('[test] joined as Tester');

  // Reset persisted toNeko/JustARod state so phase A really tests a plain, ungagged player.
  cmd('tonekoadmin neko Tester setNeko false');
  await sleep(900);
  bot.chat('/bdsm ballmouth set 0');
  await sleep(1200);

  /* ---- A. plain player ---- */
  packets.length = 0;
  bot.chat('plain line');
  await sleep(2000);
  const plainPkt = packets.filter((p) => (p.plainMessage || '') === 'plain line');
  check('A1 exactly one broadcast per send', plainPkt.length === 1, plainPkt.length + ' packet(s)');
  const a = plainPkt[0] || {};
  const aSender = plain(a.networkName) || '';
  // Vanilla omits unsignedContent when the rendered content equals the signed one,
  // so "absent" and "equal to plainMessage" are both correct.
  const aContent = a.unsignedChatContent === undefined ? a.plainMessage : (plain(a.unsignedChatContent) || '');
  check('A2 Fuji sender format applied', aSender.includes('[TONEKO-PREFIX:'), aSender);
  check('A3 placeholder %toneko:is_neko% == false', aSender.includes('[ISNEKO:false]'), aSender);
  check('A4 content preserved end-to-end', aContent === 'plain line', JSON.stringify(aContent));

  /* ---- B. neko player ---- */
  cmd('tonekoadmin neko Tester setNeko true');
  await sleep(1200);
  cmd('tonekoadmin neko Tester setNickName 小喵');
  await sleep(1400);

  packets.length = 0;
  bot.chat('hello world!');
  await sleep(2200);
  const nekoPkt = (packets.filter((p) => (p.plainMessage || '') === 'hello world!'))[0] || {};
  const bSender = plain(nekoPkt.networkName) || '';
  const bContent = nekoPkt.unsignedChatContent === undefined ? (nekoPkt.plainMessage || '') : (plain(nekoPkt.unsignedChatContent) || '');
  check('B1 [猫娘] prefix appears', bSender.includes('猫娘'), bSender);
  check('B2 toNeko nickname placeholder', bSender.includes('小喵'));
  check('B3 %toneko:is_neko% == true', bSender.includes('[ISNEKO:true]'), bSender);
  check('B4 口癖 inserted into content', /喵/.test(bContent) && !/^hello world!$/.test(bContent), JSON.stringify(bContent));

  /* ---- C. gagged ---- */
  bot.chat('/bdsm ballmouth set 400');
  await sleep(1300);
  packets.length = 0;
  bot.chat('secret words!');
  await sleep(2200);
  const gagPkt = (packets.filter((p) => (p.plainMessage || '') === 'secret words!'))[0] || {};
  const cSender = plain(gagPkt.networkName) || '';
  const cContent = gagPkt.unsignedChatContent === undefined ? (gagPkt.plainMessage || '') : (plain(gagPkt.unsignedChatContent) || '');
  check('C1 %justarod:gagged% == true', cSender.includes('[GAG:true]'), cSender);
  check('C2 gag replaces content with muffled text', /呜|唔|嗯|咿/.test(cContent) && !cContent.includes('secret'), JSON.stringify(cContent));

  const failed = results.filter((r) => !r.ok);
  console.log(`\n===== ${results.length - failed.length}/${results.length} checks passed =====`);
  if (failed.length) console.log('failed: ' + failed.map((f) => f.name).join(' | '));
  console.log(failed.length ? 'RESULT: FAIL' : 'RESULT: PASS');
  bot.quit();
  await sleep(400);
  process.exit(failed.length ? 1 : 0);
})().catch((e) => { console.error('[test] fatal', e); process.exit(2); });
