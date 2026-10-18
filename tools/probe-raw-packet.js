const mineflayer = require('mineflayer');
const fs = require('fs');
const FIFO = process.env.TB_FIFO || './console.fifo';
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
const cmd = (c) => fs.appendFileSync(FIFO, c + '\n');

// Unwrap prismarine's {type:'compound', value:{...}} wrappers and flatten to plain text.
function unwrap(c) {
  if (c == null) return c;
  if (typeof c === 'string') return c;
  if (typeof c === 'object' && Object.keys(c).length === 2 && 'type' in c && 'value' in c) return unwrap(c.value);
  return c;
}
function plain(c) {
  c = unwrap(c);
  if (c == null) return null;
  if (typeof c === 'string') return c;
  if (Array.isArray(c)) return c.map(plain).join('');
  let out = '';
  if (typeof c.text === 'string') out += c.text;
  if (c.extra !== undefined) out += plain(unwrap(c.extra));
  return out;
}

(async () => {
  const bot = mineflayer.createBot({ host: '127.0.0.1', port: 25565, username: 'Probe4', auth: 'offline', version: '1.21.1' });
  const got = [];
  await new Promise((res, rej) => { bot.once('spawn', res); bot.once('error', rej); });
  bot._client.on('player_chat', (p) => got.push(p));
  await sleep(1200);
  cmd('tonekoadmin neko Probe4 setNeko true');
  await sleep(1200);
  console.log('[probe4] normal neko chat');
  bot.chat('probe message!');
  await sleep(2000);
  console.log('[probe4] gag + chat');
  bot.chat('/bdsm ballmouth set 400');
  await sleep(1000);
  bot.chat('secret words!');
  await sleep(2000);

  console.log('\n===== RESULTS =====');
  for (const p of got) {
    console.log('\nplainMessage        :', JSON.stringify(p.plainMessage));
    console.log('unsignedChatContent :', JSON.stringify(plain(p.unsignedChatContent)));
    console.log('networkName         :', JSON.stringify(plain(p.networkName)));
  }
  const normal = got.find(p => (p.plainMessage || '').includes('probe message'));
  const gagged = got.find(p => (p.plainMessage || '').includes('secret words'));
  const u1 = normal ? plain(normal.unsignedChatContent) : null;
  const u2 = gagged ? plain(gagged.unsignedChatContent) : null;
  const n1 = normal ? plain(normal.networkName) : null;
  console.log('\n--- assertions ---');
  console.log((u1 && /喵/.test(u1)) ? 'PASS 口癖 in unsignedChatContent: ' + JSON.stringify(u1) : 'FAIL 口癖: ' + JSON.stringify(u1));
  console.log((u2 && !/secret words/.test(u2)) ? 'PASS gag replaced: ' + JSON.stringify(u2) : 'FAIL gag: ' + JSON.stringify(u2));
  console.log((n1 && n1.includes('[猫娘]')) ? 'PASS sender carries [猫娘]: ' + JSON.stringify(n1) : 'FAIL sender: ' + JSON.stringify(n1));
  bot.quit();
  await sleep(300);
  process.exit(0);
})().catch(e => { console.error(e); process.exit(2); });
