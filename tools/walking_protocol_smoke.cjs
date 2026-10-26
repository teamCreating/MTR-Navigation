// Real localhost protocol clients, NOT GUI or rendering acceptance tests.
// npm install --prefix .cache/protocol-bots --ignore-scripts minecraft-protocol
// Handshake fields verified against the target NeoForge 21.1.249 sources.
const mc = require('../.cache/protocol-bots/node_modules/minecraft-protocol');
const data = require('../.cache/protocol-bots/node_modules/minecraft-data')('1.21.1');
const { randomUUID } = require('node:crypto');
const zlib = require('node:zlib');
const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');
const port = Number(process.env.MTRNAV_TEST_PORT || 25586);
const loader = process.env.MTRNAV_TEST_LOADER || 'neoforge';
const legacy = process.env.MTRNAV_TEST_LEGACY === '1';
if (![25586, 25588].includes(port) || !['neoforge', 'fabric'].includes(loader)) throw new Error('Use only the dedicated local test ports');
const dimension = 'minecraft/overworld', steps = [], clients = [];
function vi(n) { const out = []; do { let b = n & 127; n >>>= 7; if (n) b |= 128; out.push(b); } while (n); return Buffer.from(out); }
function str(s) { const b = Buffer.from(s); return Buffer.concat([vi(b.length), b]); }
function variable(b, cursor) { let n = 0, bits = 0, v; do { if (cursor.at >= b.length || bits > 28) throw new Error('Invalid varint'); v = b[cursor.at++]; n |= (v & 127) << bits; bits += 7; } while (v & 128); return n; }
const playChannels = [
  ['mtr:packet_c2s', null, false], ['mtr:packet_s2c', null, false],
  ['mtrnav:request_v1', 0, true], ['mtrnav:request_v2', 0, true], ['mtrnav:response_v1', 1, true],
  ['mtrnav:walking_request_v1', 0, true], ['mtrnav:walking_response_v1', 1, true]
].filter(([name]) => !legacy || name.startsWith('mtr:') || ['mtrnav:request_v1', 'mtrnav:response_v1'].includes(name));
const component = ([name, flow, optional]) => Buffer.concat([str(name), str('1'), Buffer.from(flow === null ? [0, +optional] : [1, flow, +optional])]);
const negotiation = Buffer.concat([vi(2), vi(4), vi(2), component(['neoforge:extensible_enum_data', 1, true]), component(['neoforge:extensible_enum_ack', 0, true]), vi(1), vi(playChannels.length), ...playChannels.map(component)]);
function sleep(ms) { return new Promise(resolve => setTimeout(resolve, ms)); }
function passed(name) { steps.push(name); console.log('PASS ' + name); }
async function connect(username) {
  // No rendering or inventory simulation: modded item/component registries differ from vanilla.
  // Only decode packets required for connection management and the actual tested mod payloads.
  const keep = new Set(['packet_login', 'packet_custom_payload', 'packet_keep_alive', 'packet_ping', 'packet_kick_disconnect', 'packet_position', 'packet_start_configuration', 'packet_server_data', 'packet_player_info', 'packet_player_remove', 'packet_player_chat', 'packet_profileless_chat', 'packet_system_chat']);
  const types = {};
  for (const key of Object.keys(data.protocol.play.toClient.types)) if (key.startsWith('packet_') && !keep.has(key)) types[key] = 'restBuffer';
  const customPackets = { [data.version.majorVersion]: { play: { toClient: { types } } } };
  const client = mc.createClient({ host: '127.0.0.1', port, username, version: '1.21.1', auth: 'offline', profilesFolder: path.resolve('.cache/protocol-bots/profiles'), customPackets, clientSettings: { viewDistance: 2 } });
  const bot = { client, name: username, id: 0, generation: 0, revision: 0, waiters: new Map(), transfers: new Map(), notifications: [] };
  clients.push(client);
  let fatal;
  client.on('error', error => { fatal = error; console.error(username + ' protocol error: ' + error.message); });
  client.on('disconnect', packet => { fatal = new Error(username + ' disconnected: ' + JSON.stringify(packet)); });
  client.on('ping', packet => client.write('pong', { id: packet.id }));
  client.on('position', packet => client.write('teleport_confirm', { teleportId: packet.teleportId }));
  client.on('custom_payload', packet => {
    if (loader === 'neoforge' && packet.channel === 'neoforge:register') client.write('custom_payload', { channel: packet.channel, data: negotiation });
    if (packet.channel === 'neoforge:extensible_enum_data') client.write('custom_payload', { channel: 'neoforge:extensible_enum_ack', data: Buffer.alloc(0) });
    if (!['mtrnav:response_v1', 'mtrnav:walking_response_v1'].includes(packet.channel)) return;
    try {
      const cursor = { at: 8 }, transfer = packet.data.readBigInt64BE(0).toString();
      const index = variable(packet.data, cursor), total = variable(packet.data, cursor), length = variable(packet.data, cursor);
      const key = packet.channel + ':' + transfer;
      if (!bot.transfers.has(key)) bot.transfers.set(key, { total, parts: new Map() });
      const assembly = bot.transfers.get(key); assert.equal(assembly.total, total);
      assembly.parts.set(index, packet.data.subarray(cursor.at, cursor.at + length));
      if (assembly.parts.size !== total) return;
      const raw = Buffer.concat(Array.from({ length: total }, (_, i) => assembly.parts.get(i)));
      const reply = JSON.parse(zlib.gunzipSync(raw, { maxOutputLength: packet.channel.includes('walking') ? 1024 * 1024 : 32 * 1024 * 1024 }).toString());
      bot.transfers.delete(key);
      if (packet.channel.includes('walking')) { bot.generation = Math.max(bot.generation, reply.generation); bot.revision = Math.max(bot.revision, reply.revision); }
      if (reply.id === 0) { bot.notifications.push(reply); return; }
      const waiter = bot.waiters.get(reply.id); if (waiter) { bot.waiters.delete(reply.id); waiter.resolve(reply); }
    } catch (error) { fatal = error; console.error('Reply decode failed: ' + error.message); }
  });
  await new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(fatal || new Error('Login timeout for ' + username)), 20000);
    client.once('login', () => { clearTimeout(timer); client.removeAllListeners('declare_commands'); resolve(); });
  });
  await sleep(300); if (fatal) throw fatal;
  if (loader === 'fabric') client.write('custom_payload', { channel: 'minecraft:register', data: Buffer.from((legacy ? ['mtrnav:response_v1', 'mtr:packet_s2c'] : ['mtrnav:response_v1', 'mtrnav:walking_response_v1', 'mtr:packet_s2c']).join('\0')) });
  bot.send = (channel, payload) => {
    const id = ++bot.id; payload.id = id;
    if (payload.request) { payload.request.id = id; delete payload.id; }
    const encoded = Buffer.from(JSON.stringify(payload)); assert(encoded.length <= 16384);
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => { bot.waiters.delete(id); reject(fatal || new Error(username + ' timed out waiting for ' + channel + ', id=' + id)); }, 12000);
      bot.waiters.set(id, { resolve: value => { clearTimeout(timer); resolve(value); } });
      client.write('custom_payload', { channel, data: Buffer.concat([vi(encoded.length), encoded]) });
    });
  };
  bot.read = (review = false) => bot.send('mtrnav:walking_request_v1', { action: 'read', dimension, generation: bot.generation, command: null, backup: 0, page: 0, review });
  bot.change = command => bot.send('mtrnav:walking_request_v1', { action: 'change', dimension, generation: bot.generation, command, backup: 0, page: 0 });
  return bot;
}
async function run() {
  const alice = await connect('NavApplicant'), moderator = await connect('NavModerator'), observer = await connect('NavObserver');
  passed('three real localhost connections completed game login');
  let initial = await moderator.read(); assert.equal(initial.admin, true); assert.equal((await alice.read()).admin, false); await observer.read();
  passed('moderator permission comes from the server');
  // Keep reruns isolated to entries created by this test, never delete unrelated fixtures.
  for (const entry of initial.entries.filter(e => e.connection.name === 'Protocol smoke')) { await moderator.change({ action: 'remove', recordId: entry.connection.id, expectedRevision: entry.revision, value: null, reason: '' }); await sleep(350); }
  const hello = { action: 'hello', dimension, revision: 0, origin: '', destination: '', sort: 'FASTEST', journeyId: '', stationId: '', correction: '' };
  let nav;
  for (let i = 0; i < 5; i++) { nav = await alice.send('mtrnav:request_v1', { ...hello }); if (nav.catalog?.stations?.length >= 2) break; await sleep(2000); }
  assert(nav.catalog?.stations?.length >= 2, 'Fixture must have at least two MTR stations');
  const stations = nav.catalog.stations;
  const value = { id: randomUUID(), groupId: randomUUID(), dimension, fromStation: stations[0].id, fromPlatform: '', toStation: stations.at(-1).id, toPlatform: '', seconds: 60, name: 'Protocol smoke', description: 'Local protocol acceptance fixture', enabled: true };
  await alice.read();
  let reply = await alice.change({ action: 'submit', recordId: '', expectedRevision: 0, value, reason: '' }); assert.equal(reply.message, 'saved'); assert(!reply.entries.some(e => e.connection.id === value.id));
  const proposal = reply.proposals.find(p => p.value.id === value.id); assert(proposal); assert.equal(proposal.status, 'PENDING');
  passed('submission persists pending and is not published');
  reply = await observer.read(); assert(!reply.proposals.some(p => p.id === proposal.id)); assert(!reply.entries.some(e => e.connection.id === value.id));
  reply = await observer.read(true); assert.equal(reply.message, 'permission_denied');
  passed('other ordinary client cannot read private pending submissions');
  reply = await alice.change({ action: 'approve', recordId: proposal.id, expectedRevision: proposal.revision, value: null, reason: '' }); assert.equal(reply.message, 'permission_denied');
  reply = await observer.change({ action: 'withdraw', recordId: proposal.id, expectedRevision: proposal.revision, value: null, reason: '' }); assert.equal(reply.message, 'permission_denied');
  passed('unauthorized approval and another player withdrawal are rejected');
  reply = await moderator.read(true); assert(reply.proposals.some(p => p.id === proposal.id));
  reply = await moderator.change({ action: 'approve', recordId: proposal.id, expectedRevision: proposal.revision, value: null, reason: '' }); assert.equal(reply.message, 'saved');
  const published = reply.entries.find(e => e.connection.id === value.id); assert(published);
  await sleep(400); reply = await observer.read(); assert(reply.entries.some(e => e.connection.id === value.id)); assert(observer.notifications.some(n => n.kind === 'revision'));
  passed('approved revision reaches the second client over actual game payloads');
  await sleep(400);
  reply = await moderator.change({ action: 'approve', recordId: proposal.id, expectedRevision: proposal.revision, value: null, reason: '' }); assert.equal(reply.message, 'revision_conflict');
  passed('repeated approval with stale proposal revision is rejected');
  const privateValue = { ...value, id: randomUUID(), groupId: randomUUID(), fromStation: value.toStation, toStation: value.fromStation, name: '', description: '' };
  const query = await alice.send('mtrnav:request_v2', { request: { ...hello, action: 'query', origin: value.fromStation, destination: value.toStation, revision: nav.catalog.revision }, options: { limit: 5, extraMinutes: 30, distinct: true }, walkingRevision: alice.revision, personal: [privateValue] });
  assert(query.report, 'V2 report is present'); assert(query.journeys.length <= 5);
  reply = await observer.read(); assert(!JSON.stringify(reply).includes(privateValue.id));
  const storeRoot = path.resolve('run-server', loader === 'fabric' ? 'fabric' : '', 'navwalk-smoke/data/mtrnav');
  for (const file of fs.readdirSync(storeRoot).filter(n => n === 'library.json' || n.startsWith('audit'))) assert(!fs.readFileSync(path.join(storeRoot, file), 'utf8').includes(privateValue.id), 'Private query rules must not be persisted or audited');
  passed('v2 query accepts private rules without sharing them with another client');
  assert(query.journeys.length > 0, 'Fixture query must offer a usable rail journey');
  reply = await alice.send('mtrnav:request_v1', { ...hello, action: 'cancel_query', revision: nav.catalog.revision });
  assert.equal(reply.kind, 'cancel_query');
  reply = await alice.send('mtrnav:request_v1', { ...hello, action: 'select', journeyId: query.journeys[0].id, revision: nav.catalog.revision });
  assert.equal(reply.message, 'network_changed', 'Cancelled offers must be released from the player session');
  passed('cancelled query releases its offered journeys from the player session');
  await moderator.read(); reply = await moderator.change({ action: 'remove', recordId: value.id, expectedRevision: published.revision, value: null, reason: '' }); assert.equal(reply.message, 'saved');
  await sleep(300); reply = await observer.read(); assert(!reply.entries.some(e => e.connection.id === value.id));
  passed('removal publishes a new revision and clears the second client snapshot');
  const report = { loader, port, date: new Date().toISOString(), steps, fixtureStations: stations.length, queryResults: query.journeys.length, guiTested: false, fullMinecraftClients: false, protocolClients: clients.length };
  fs.mkdirSync(path.resolve('build/validation'), { recursive: true }); fs.writeFileSync(path.resolve('build/validation/walking-protocol-' + loader + '.json'), JSON.stringify(report, null, 2) + '\n');
}
async function runLegacy() {
  const bot = await connect('NavLegacy');
  const base = { action: 'hello', dimension, revision: 0, origin: '', destination: '', sort: 'FASTEST', journeyId: '', stationId: '', correction: '' };
  const hello = await bot.send('mtrnav:request_v1', base); assert(hello.catalog?.stations.length >= 2);
  const query = await bot.send('mtrnav:request_v1', { ...base, action: 'query', origin: hello.catalog.stations[0].id, destination: hello.catalog.stations.at(-1).id });
  assert(query.journeys.length > 0 && query.journeys.length <= 3); assert(query.journeys.every(j => !j.walks?.length)); assert(query.report == null);
  passed('v1-only channel negotiation still permits old rail-only queries');
  fs.mkdirSync(path.resolve('build/validation'), { recursive: true });
  fs.writeFileSync(path.resolve('build/validation/walking-legacy-' + loader + '.json'), JSON.stringify({ loader, port, steps, results: query.journeys.length, guiTested: false }, null, 2) + '\n');
}
(legacy ? runLegacy() : run()).then(() => { clients.forEach(c => c.end()); setTimeout(() => process.exit(0), 200); }).catch(error => { console.error(error.stack); clients.forEach(c => c.end()); setTimeout(() => process.exit(1), 200); });
