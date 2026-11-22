/* Minecraft Dev Panel - interface */
'use strict';

// ---------------------------------------------------------------- Token & API
const params = new URLSearchParams(location.search);
if (params.get('token')) { sessionStorage.setItem('token', params.get('token')); history.replaceState(null, '', location.pathname + location.hash); }
const TOKEN = sessionStorage.getItem('token') || '';

async function api(method, path, body, raw) {
  const opt = { method, headers: { 'X-Token': TOKEN } };
  if (body !== undefined) {
    if (raw) opt.body = body;
    else { opt.body = JSON.stringify(body); opt.headers['Content-Type'] = 'application/json'; }
  }
  const r = await fetch('/api/' + path, opt);
  const txt = await r.text();
  let data = null;
  try { data = txt ? JSON.parse(txt) : null; } catch { data = txt; }
  if (!r.ok) throw new Error((data && data.error) || ('Erreur ' + r.status));
  return data;
}
const enc = encodeURIComponent;

// ---------------------------------------------------------------- Icones
const ICONS = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  term: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M7 9l3 3-3 3M13 15h4"/>',
  users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20c.6-3.6 3.3-5.5 6.5-5.5s5.9 1.9 6.5 5.5"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.8c2 .7 3.2 2.5 3.5 5.2"/>',
  plug: '<path d="M9 2v5M15 2v5M6 7h12v4a6 6 0 0 1-12 0zM12 17v5"/>',
  folder: '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z"/>',
  file: '<path d="M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8z"/><path d="M14 3v5h5"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z"/>',
  sliders: '<path d="M4 6h10M18 6h2M4 12h4M12 12h8M4 18h12M20 18h0"/><circle cx="16" cy="6" r="2"/><circle cx="10" cy="12" r="2"/><circle cx="18" cy="18" r="2"/>',
  code: '<path d="M8 8l-4 4 4 4M16 8l4 4-4 4M13.5 5l-3 14"/>',
  cog: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  play: '<path d="M7 4.5v15l12-7.5z"/>',
  stop: '<rect x="6" y="6" width="12" height="12" rx="1.5"/>',
  restart: '<path d="M20 11a8 8 0 1 0-2.3 5.7M20 4v7h-7"/>',
  skull: '<path d="M12 3a8 8 0 0 0-8 8c0 2.6 1.3 4.4 3 5.5V20h10v-3.5c1.7-1.1 3-2.9 3-5.5a8 8 0 0 0-8-8z"/><circle cx="9" cy="11.5" r="1.6"/><circle cx="15" cy="11.5" r="1.6"/>',
  cpu: '<rect x="6" y="6" width="12" height="12" rx="2"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>',
  mem: '<rect x="3" y="7" width="18" height="10" rx="1.5"/><path d="M7 7v10M11 7v10M15 7v10M6 17v3M18 17v3"/>',
  bolt: '<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  upload: '<path d="M12 16V4M7 9l5-5 5 5M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/>',
  download: '<path d="M12 4v12M7 11l5 5 5-5M4 16v3a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-3"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 13h10l1-13M9 7V4h6v3"/>',
  edit: '<path d="M4 20h4L19 9l-4-4L4 16z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  back: '<path d="M15 6l-6 6 6 6"/>',
  save: '<path d="M5 3h11l3 3v13a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z"/><path d="M7 3v5h8M7 21v-7h10v7"/>',
  box: '<path d="M21 8l-9-5-9 5 9 5zM3 8v8l9 5 9-5V8M12 13v8"/>',
  ext: '<path d="M14 4h6v6M20 4l-9 9M18 14v5a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h5"/>',
  bug: '<rect x="7" y="7" width="10" height="13" rx="5"/><path d="M12 11v9M3 13h4M17 13h4M4 7l3 2M20 7l-3 2M4 20l3-2M20 20l-3-2M9 4l1.5 3M15 4l-1.5 3"/>',
  rocket: '<path d="M5 15c-1.5 1.5-2 5-2 5s3.5-.5 5-2M9 15l-3-3c1-4 4-8.5 13-9-.5 9-5 12-9 13z"/><circle cx="15" cy="9" r="1.5"/>',
  search: '<circle cx="11" cy="11" r="7"/><path d="M20 20l-4-4"/>',
  net: '<rect x="9" y="2.5" width="6" height="5" rx="1"/><rect x="2.5" y="16.5" width="6" height="5" rx="1"/><rect x="15.5" y="16.5" width="6" height="5" rx="1"/><path d="M12 7.5V12M5.5 16.5V12h13v4.5"/>',
};
function icon(n) { return `<i data-i="${n}"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${ICONS[n] || ''}</svg></i>`; }
function hydrateIcons(root) { (root || document).querySelectorAll('i[data-i]:empty').forEach(el => { el.outerHTML = icon(el.dataset.i); }); }

// ---------------------------------------------------------------- Utilitaires
const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => [...(r || document).querySelectorAll(s)];
const esc = s => String(s == null ? '' : s).replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtSize = b => b < 1024 ? b + ' o' : b < 1048576 ? (b / 1024).toFixed(1) + ' Ko' : b < 1073741824 ? (b / 1048576).toFixed(1) + ' Mo' : (b / 1073741824).toFixed(2) + ' Go';
function fmtDur(s) { if (!s) return '—'; const h = Math.floor(s / 3600), m = Math.floor(s % 3600 / 60), x = s % 60; return h ? `${h}h ${m}min` : m ? `${m}min ${x}s` : `${x}s`; }

function toast(msg, err) {
  const t = document.createElement('div');
  t.className = 'toast' + (err ? ' err' : '');
  t.textContent = msg;
  $('#toasts').appendChild(t);
  setTimeout(() => t.remove(), err ? 6000 : 3200);
}
async function act(fn, okMsg) {
  try { const r = await fn(); if (okMsg) toast(okMsg); return r; } catch (e) { toast(e.message, true); throw e; }
}

function modal(html) {
  $('#modalBox').innerHTML = html; hydrateIcons($('#modalBox'));
  $('#modal').classList.remove('hidden');
  const f = $('#modalBox input'); if (f) setTimeout(() => f.focus(), 30);
}
function closeModal() { $('#modal').classList.add('hidden'); }
$('#modal').addEventListener('mousedown', e => { if (e.target.id === 'modal') closeModal(); });
document.addEventListener('keydown', e => { if (e.key === 'Escape') closeModal(); });

function confirmBox(title, text, okLabel, danger) {
  return new Promise(res => {
    modal(`<h3>${esc(title)}</h3><p>${text}</p><div class="row"><button class="btn" id="mNo">Annuler</button><button class="btn ${danger ? 'red' : 'green'}" id="mYes">${esc(okLabel || 'Confirmer')}</button></div>`);
    $('#mNo').onclick = () => { closeModal(); res(false); };
    $('#mYes').onclick = () => { closeModal(); res(true); };
  });
}
function promptBox(title, label, value) {
  return new Promise(res => {
    modal(`<h3>${esc(title)}</h3><div class="field"><label>${esc(label)}</label><input class="input" id="mIn" value="${esc(value || '')}"></div><div class="row"><button class="btn" id="mNo">Annuler</button><button class="btn green" id="mYes">OK</button></div>`);
    const ok = () => { const v = $('#mIn').value.trim(); closeModal(); res(v || null); };
    $('#mNo').onclick = () => { closeModal(); res(null); };
    $('#mYes').onclick = ok;
    $('#mIn').onkeydown = e => { if (e.key === 'Enter') ok(); };
  });
}

// ---------------------------------------------------------------- Etat global
const S = {
  status: { state: 'offline' }, stats: null, sys: null,
  hist: { tps: [], mspt: [], ram: [], cpu: [], players: [] },
  logs: [], lastLog: 0, needRestart: false,
};
const STATE_LABEL = { offline: 'Hors ligne', starting: 'Démarrage…', online: 'En ligne', stopping: 'Arrêt…' };

async function pollStatus() {
  try {
    S.status = await api('GET', 'status');
    S.stats = S.status.state === 'offline' ? null : await api('GET', 'stats');
    const h = S.hist, st = S.stats, push = (a, v) => { a.push(v); if (a.length > 120) a.shift(); };
    push(h.ram, S.status.ramMb); push(h.cpu, S.status.cpu);
    push(h.tps, st ? Math.min(20, st.tps[0]) : null); push(h.mspt, st ? st.mspt : null);
    push(h.players, st ? st.players.length : 0);
    try { S.inst = await api('GET', 'instances'); } catch { S.inst = []; }
    renderTop();
    if (pages[route] && pages[route].tick) pages[route].tick();
  } catch (e) { /* panel ferme */ }
}

function renderTop() {
  const s = S.status.state;
  $('#statePill').className = 'state ' + s;
  $('#stateText').textContent = STATE_LABEL[s] || s;
  const off = s === 'offline';
  $('[data-power=start]').disabled = !off;
  $('[data-power=stop]').disabled = off || s === 'stopping';
  $('[data-power=restart]').disabled = off;
  $('[data-power=kill]').disabled = off;
  if (S.stats) $('#brandVer').textContent = 'Paper ' + S.stats.mcVersion;
  $('#navPlayers').textContent =S.stats && S.stats.players.length ? S.stats.players.length : '';
  const up = (S.inst || []).filter(i => i.state !== 'offline').length;
  $('#navInst').textContent = up ? up + '/' + S.inst.length : '';
}

$$('[data-power]').forEach(b => b.onclick = async () => {
  const a = b.dataset.power;
  if (a === 'kill' && !await confirmBox('Tuer le processus ?', 'Le serveur sera coupé net, <b>sans sauvegarde</b> du monde.', 'Tuer', true)) return;
  act(() => api('POST', 'power', { action: a }), { start: 'Démarrage…', stop: 'Arrêt en cours…', restart: 'Redémarrage…', kill: 'Processus tué' }[a]);
  if (a === 'restart' || a === 'start') S.needRestart = false;
  setTimeout(pollStatus, 300);
});

// ---------------------------------------------------------------- Console (flux)
const ANSI = { 30: '#5d6b7a', 31: '#ff6b70', 32: '#4ade80', 33: '#facc15', 34: '#60a5fa', 35: '#e879f9', 36: '#22d3ee', 37: '#e6edf3',
  90: '#7d8a99', 91: '#ff8a8f', 92: '#86efac', 93: '#fde68a', 94: '#93c5fd', 95: '#f0abfc', 96: '#67e8f9', 97: '#ffffff' };
function ansiHtml(s) {
  let out = '', open = 0;
  const parts = s.split(/\x1b\[([0-9;]*)m/);
  for (let i = 0; i < parts.length; i++) {
    if (i % 2 === 0) { out += esc(parts[i]); continue; }
    const codes = parts[i].split(';').map(Number);
    for (const c of codes) {
      if (c === 0 || isNaN(c)) { out += '</span>'.repeat(open); open = 0; }
      else if (ANSI[c]) { out += `<span style="color:${ANSI[c]}">`; open++; }
      else if (c === 1) { out += '<span style="font-weight:700">'; open++; }
    }
  }
  return out + '</span>'.repeat(open);
}
function lineClass(t) {
  if (t.startsWith('[Panel]')) return 'panel';
  if (t.startsWith('> ')) return 'cmd';
  if (/\b(ERROR|SEVERE|FATAL)\]|Exception|^\s+at /.test(t)) return 'err';
  if (/\bWARN\]/.test(t)) return 'warn';
  return '';
}
function lineHtml(l, hl) {
  const time = new Date(l.ts).toLocaleTimeString('fr-FR');
  let body = ansiHtml(l.t);
  if (hl) { const re = new RegExp('(' + hl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') + ')', 'gi'); body = body.replace(/(^|>)([^<]*)/g, (m, a, b) => a + b.replace(re, '<mark>$1</mark>')); }
  return `<div class="l ${lineClass(l.t.replace(/\x1b\[[0-9;]*m/g, ''))}">${l.t.startsWith('[Panel]') ? `<span class="ts">${time}</span> ` : ''}${body}</div>`;
}

const consoleSubs = new Set();
function addLogs(batch) {
  const fresh = batch.filter(l => l.id > S.lastLog);
  if (!fresh.length) return;
  S.lastLog = fresh[fresh.length - 1].id;
  S.logs.push(...fresh);
  if (S.logs.length > 4000) S.logs.splice(0, S.logs.length - 4000);
  consoleSubs.forEach(fn => fn(fresh));
}
async function startStream() {
  try { addLogs(await api('GET', 'console')); } catch { }
  const connect = () => {
    const es = new EventSource(`/api/console/stream?token=${enc(TOKEN)}&since=${S.lastLog}`);
    es.onmessage = e => addLogs(JSON.parse(e.data));
    es.onerror = () => { es.close(); setTimeout(connect, 1500); };
  };
  connect();
}

// Composant console reutilisable
function mountConsole(box, opts) {
  opts = opts || {};
  let filter = 'all', search = '', follow = true;
  const keep = l => {
    const c = lineClass(l.t);
    if (filter === 'warn' && c !== 'warn' && c !== 'err') return false;
    if (filter === 'err' && c !== 'err') return false;
    if (filter === 'panel' && c !== 'panel' && c !== 'cmd') return false;
    if (search && !l.t.toLowerCase().includes(search.toLowerCase())) return false;
    return true;
  };
  const full = () => {
    const src = opts.tail ? S.logs.slice(-opts.tail) : S.logs;
    box.innerHTML = src.filter(keep).map(l => lineHtml(l, search)).join('');
    box.scrollTop = box.scrollHeight;
  };
  box.addEventListener('scroll', () => { follow = box.scrollTop + box.clientHeight >= box.scrollHeight - 30; });
  const sub = fresh => {
    if (!document.body.contains(box)) { consoleSubs.delete(sub); return; }
    box.insertAdjacentHTML('beforeend', fresh.filter(keep).map(l => lineHtml(l, search)).join(''));
    const max = opts.tail || 4000;
    while (box.childElementCount > max) box.firstChild.remove();
    if (follow) box.scrollTop = box.scrollHeight;
  };
  consoleSubs.add(sub);
  full();
  return {
    setFilter(f) { filter = f; full(); },
    setSearch(s) { search = s; full(); },
    clear() { box.innerHTML = ''; },
  };
}

const cmdHistory = JSON.parse(localStorage.getItem('cmdHistory') || '[]');
function mountCmdLine(input, send) {
  let idx = cmdHistory.length;
  input.addEventListener('keydown', async e => {
    if (e.key === 'ArrowUp') { if (idx > 0) { idx--; input.value = cmdHistory[idx]; } e.preventDefault(); }
    else if (e.key === 'ArrowDown') { if (idx < cmdHistory.length) { idx++; input.value = cmdHistory[idx] || ''; } e.preventDefault(); }
    else if (e.key === 'Enter') {
      const c = input.value.trim(); if (!c) return;
      if (cmdHistory[cmdHistory.length - 1] !== c) { cmdHistory.push(c); if (cmdHistory.length > 100) cmdHistory.shift(); localStorage.setItem('cmdHistory', JSON.stringify(cmdHistory)); }
      idx = cmdHistory.length;
      input.value = '';
      (send || sendCmd)(c);
    }
  });
}
function sendCmd(c) { return act(() => api('POST', 'command', { cmd: c })); }

// ---------------------------------------------------------------- Graphiques
function spark(canvas, data, color, max) {
  if (!canvas) return;
  const dpr = devicePixelRatio || 1, w = canvas.clientWidth, h = canvas.clientHeight;
  canvas.width = w * dpr; canvas.height = h * dpr;
  const g = canvas.getContext('2d'); g.scale(dpr, dpr);
  const vals = data.map(v => v == null ? null : v);
  const mx = max || Math.max(1, ...vals.filter(v => v != null)) * 1.15;
  const n = 120, step = w / (n - 1), off = n - vals.length;
  const pts = vals.map((v, i) => v == null ? null : [(i + off) * step, h - 4 - (v / mx) * (h - 8)]);
  const grad = g.createLinearGradient(0, 0, 0, h); grad.addColorStop(0, color + '55'); grad.addColorStop(1, color + '00');
  let seg = [];
  const flush = () => {
    if (seg.length > 1) {
      g.beginPath(); seg.forEach((p, i) => i ? g.lineTo(p[0], p[1]) : g.moveTo(p[0], p[1]));
      g.strokeStyle = color; g.lineWidth = 1.8; g.stroke();
      g.lineTo(seg[seg.length - 1][0], h); g.lineTo(seg[0][0], h); g.closePath(); g.fillStyle = grad; g.fill();
    }
    seg = [];
  };
  pts.forEach(p => p ? seg.push(p) : flush()); flush();
}

// ---------------------------------------------------------------- Pages
const pages = {};
let route = 'dashboard';

pages.dashboard = {
  title: 'Tableau de bord',
  render(v) {
    v.innerHTML = `
      <div id="restartBanner"></div>
      <div class="grid g4">
        <div class="card stat"><div class="label">${icon('bolt')}TPS</div><div class="value" id="dTps">—</div><div class="hint" id="dMspt">MSPT —</div><canvas id="cTps"></canvas></div>
        <div class="card stat"><div class="label">${icon('mem')}Mémoire</div><div class="value" id="dRam">—</div><div class="hint" id="dRamH">—</div><canvas id="cRam"></canvas></div>
        <div class="card stat"><div class="label">${icon('cpu')}Processeur</div><div class="value" id="dCpu">—</div><div class="hint" id="dCpuH">—</div><canvas id="cCpu"></canvas></div>
        <div class="card stat"><div class="label">${icon('users')}Joueurs</div><div class="value" id="dPl">—</div><div class="hint" id="dUp">—</div><canvas id="cPl"></canvas></div>
      </div>
      <div class="grid g21 mt">
        <div class="card">
          <h2>${icon('term')}Console <span class="right"><a class="btn sm ghost" href="#console">Ouvrir en grand ${icon('ext')}</a></span></h2>
          <div class="console" id="miniCon" style="height:340px"></div>
          <div class="cmdline"><div class="prompt"><span>&gt;</span><input id="miniCmd" placeholder="Commande… (↑ ↓ historique)" autocomplete="off" spellcheck="false"></div></div>
        </div>
        <div>
          <div class="card">
            <h2>${icon('globe')}Mondes</h2>
            <div id="dWorlds" class="muted">Serveur hors ligne</div>
          </div>
          <div class="card mt">
            <h2>${icon('rocket')}Actions rapides</h2>
            <div class="chips" id="quick"></div>
          </div>
          <div class="card mt">
            <h2>${icon('bug')}Infos</h2>
            <div class="muted" id="dInfo" style="font-size:13px"></div>
          </div>
        </div>
      </div>`;
    hydrateIcons(v);
    mountConsole($('#miniCon'), { tail: 300 });
    mountCmdLine($('#miniCmd'));
    const q = [['save-all', 'Sauvegarder'], ['time set day', 'Jour'], ['weather clear', 'Beau temps'], ['gamerule doDaylightCycle false', 'Figer le temps'],
      ['gamemode creative @a', 'Créatif @a'], ['kill @e[type=item]', 'Nettoyer items'], ['tps', 'TPS'], ['plugins', 'Plugins']];
    $('#quick').innerHTML = q.map(([c, l]) => `<span class="chip" data-c="${esc(c)}" title="${esc(c)}">${esc(l)}</span>`).join('');
    $$('#quick .chip').forEach(c => c.onclick = () => sendCmd(c.dataset.c));
    this.tick();
  },
  tick() {
    const st = S.status, s = S.stats, h = S.hist;
    $('#restartBanner').innerHTML = S.needRestart && st.state !== 'offline' ? `<div class="banner">${icon('restart')}<div class="grow">Des plugins ont changé : redémarre le serveur pour les charger.</div><button class="btn sm" onclick="document.querySelector('[data-power=restart]').click()">Redémarrer</button></div>` : '';
    if (s) {
      const tps = Math.min(20, s.tps[0]);
      $('#dTps').innerHTML = `${tps.toFixed(1)} <small>/ 20</small>`;
      $('#dTps').style.color = tps >= 19 ? 'var(--green)' : tps >= 15 ? 'var(--amber)' : 'var(--red)';
      $('#dMspt').textContent = `MSPT ${s.mspt.toFixed(1)} ms · 5m ${Math.min(20, s.tps[1]).toFixed(1)} · 15m ${Math.min(20, s.tps[2]).toFixed(1)}`;
      $('#dPl').innerHTML = `${s.players.length} <small>/ ${s.maxPlayers}</small>`;
      $('#dWorlds').innerHTML = s.worlds.map(w => `<div class="row between" style="padding:6px 0;border-bottom:1px solid var(--line)"><b>${esc(w.name)}</b><span class="dim mono">${w.chunks} chunks · ${w.entities} entités · ${w.players} j.</span></div>`).join('');
    } else {
      $('#dTps').textContent = '—'; $('#dTps').style.color = ''; $('#dMspt').textContent = st.state === 'starting' ? 'En attente du serveur…' : 'Serveur hors ligne';
      $('#dPl').textContent = '—'; $('#dWorlds').textContent = st.state === 'offline' ? 'Serveur hors ligne' : 'Chargement…';
    }
    $('#dRam').innerHTML = st.state === 'offline' ? '—' : `${(st.ramMb / 1024).toFixed(2)} <small>Go</small>`;
    $('#dRamH').textContent = `Alloué : ${(st.ramMaxMb / 1024).toFixed(1)} Go (Xmx)`;
    $('#dCpu').innerHTML = st.state === 'offline' ? '—' : `${st.cpu.toFixed(0)} <small>%</small>`;
    $('#dCpuH').textContent = `${st.cores || '?'} cœurs logiques`;
    $('#dUp').textContent = st.state === 'offline' ? 'Hors ligne' : 'En ligne depuis ' + fmtDur(st.uptime);
    $('#dInfo').innerHTML = `
      <div>Version : <span class="mono">${esc(s ? s.mcVersion : '26.3')}</span>${s ? ` <span class="dim">(${esc(s.version.replace(/^git-/, ''))})</span>` : ''}</div>
      <div>PID : <span class="mono">${st.pid || '—'}</span></div>
      <div>Débogage Java : ${st.debug ? `<span class="tag green">port ${st.debugPort}</span>` : '<span class="tag">désactivé</span>'}</div>
      <div>HotSwap avancé : ${st.hotswap ? '<span class="tag green">JetBrains Runtime</span>' : '<span class="tag">non</span>'}</div>`;
    spark($('#cTps'), h.tps, '#41c77a', 20);
    spark($('#cRam'), h.ram, '#3a7fff',Math.max(st.ramMaxMb || 1, 1) * 1.1);
    spark($('#cCpu'), h.cpu, '#a78bfa', 100);
    spark($('#cPl'), h.players, '#f5c518',Math.max(4, ...h.players) + 1);
  },
};

pages.console = {
  title: 'Console',
  render(v) {
    v.innerHTML = `
      <div class="toolbar">
        <div class="chips" id="cFilter">
          <span class="chip on" data-f="all">Tout</span><span class="chip" data-f="warn">Warn + erreurs</span>
          <span class="chip" data-f="err">Erreurs</span><span class="chip" data-f="panel">Panel &amp; commandes</span>
        </div>
        <div class="grow"></div>
        <div class="prompt" style="display:flex;align-items:center;gap:6px;background:var(--bg);border:1px solid var(--line2);border-radius:9px;padding:0 10px;width:260px">${icon('search')}<input id="cSearch" class="input" style="border:0;background:none;padding:7px 0;box-shadow:none" placeholder="Rechercher…"></div>
        <button class="btn sm" id="cClear">Vider l'affichage</button>
        <button class="btn sm" id="cLog">${icon('file')}latest.log</button>
      </div>
      <div class="console" id="bigCon" style="height:calc(100vh - 290px)"></div>
      <div class="cmdline"><div class="prompt"><span>&gt;</span><input id="bigCmd" placeholder="Tape une commande serveur (sans /)…  ↑ ↓ pour l'historique" autocomplete="off" spellcheck="false"></div></div>
      <div class="chips mt" id="cQuick"></div>`;
    hydrateIcons(v);
    const con = mountConsole($('#bigCon'));
    mountCmdLine($('#bigCmd'));
    $('#bigCmd').focus();
    $$('#cFilter .chip').forEach(c => c.onclick = () => { $$('#cFilter .chip').forEach(x => x.classList.remove('on')); c.classList.add('on'); con.setFilter(c.dataset.f); });
    let t; $('#cSearch').oninput = e => { clearTimeout(t); t = setTimeout(() => con.setSearch(e.target.value), 200); };
    $('#cClear').onclick = () => con.clear();
    $('#cLog').onclick = () => openEditor('logs/latest.log');
    const q = ['list', 'save-all', 'tps', 'mspt', 'plugins', 'version', 'time set day', 'weather clear', 'difficulty peaceful', 'gamerule keepInventory true', 'kill @e[type=!player]', 'whitelist list'];
    $('#cQuick').innerHTML = q.map(c => `<span class="chip" data-c="${esc(c)}">${esc(c)}</span>`).join('');
    $$('#cQuick .chip').forEach(c => c.onclick = () => sendCmd(c.dataset.c));
  },
};

// ---- Reseau & instances (proxy Velocity + mapdev, le lobby = serveur principal)
const INST_LABEL = Object.assign({}, STATE_LABEL, { external: 'Hors panel' });
const INST_QUICK = {
  velocity: [['glist', 'Joueurs (glist)'], ['server', 'Serveurs'], ['velocity info', 'Infos'], ['velocity plugins', 'Plugins'], ['velocity reload', 'Reload config']],
  paper: [['list', 'Joueurs'], ['save-all', 'Sauvegarder'], ['tps', 'TPS'], ['time set day', 'Jour'], ['weather clear', 'Beau temps'], ['gamemode creative @a', 'Créatif @a'], ['plugins', 'Plugins']],
};
const IC = { sel: localStorage.getItem('instSel') || 'velocity', last: 0, busy: false };

function instPower(id, a) {
  return act(() => id === 'lobby' ? api('POST', 'power', { action: a }) : api('POST', `instances/power?id=${enc(id)}`, { action: a }),
    { start: 'Démarrage…', stop: 'Arrêt en cours…', restart: 'Redémarrage…', kill: 'Processus tué' }[a]).finally(() => setTimeout(pollStatus, 300));
}

function nodes() {
  const st = S.status;
  const lobby = { id: 'lobby', name: 'Lobby', role: 'Serveur principal', port: 30065, state: st.state, ramMb: st.ramMb, cpu: st.cpu, uptime: st.uptime, main: true };
  const inst = S.inst || [];
  return [inst.find(i => i.id === 'velocity'), lobby, ...inst.filter(i => i.id !== 'velocity')].filter(Boolean);
}

pages.instances = {
  title: 'Réseau & instances',
  render(v) {
    v.innerHTML = `
      <div class="toolbar">
        <div class="muted">En jeu : <b class="mono" style="color:var(--text)">localhost</b> → Velocity (25565) → lobby / instances</div>
        <div class="grow"></div>
        <button class="btn green" id="iAllStart">${icon('play')}Tout démarrer</button>
        <button class="btn red" id="iAllStop">${icon('stop')}Tout arrêter</button>
      </div>
      <div id="iExt"></div>
      <div class="grid g3" id="iNodes"></div>
      <div class="card mt">
        <h2>${icon('term')}Console <span class="right"><span class="chips" id="iTabs"></span></span></h2>
        <div class="console" id="iCon" style="height:calc(100vh - 470px);min-height:260px"></div>
        <div class="cmdline"><div class="prompt"><span>&gt;</span><input id="iCmd" placeholder="Commande… (↑ ↓ historique)" autocomplete="off" spellcheck="false"></div></div>
        <div class="chips mt" id="iQuick"></div>
      </div>`;
    hydrateIcons(v);

    $('#iNodes').innerHTML = nodes().map(n => `
      <div class="card node" data-id="${n.id}">
        <div class="node-head">
          <div><div class="node-role">${esc(n.role)}</div><h3>${esc(n.name)}</h3></div>
          <div class="state" data-f="pill"><span class="dot"></span><span data-f="state"></span></div>
        </div>
        <div class="node-meta">
          <div>Port<b class="mono">${n.port}</b></div><div>RAM<b data-f="ram">—</b></div>
          <div>CPU<b data-f="cpu">—</b></div><div>En ligne<b data-f="up">—</b></div>
        </div>
        <div class="node-acts">
          <button class="btn sm green" data-a="start">${icon('play')}Démarrer</button>
          <button class="btn sm" data-a="restart">${icon('restart')}Redémarrer</button>
          <button class="btn sm red" data-a="stop">${icon('stop')}Arrêter</button>
          <button class="btn sm ghost danger icon" data-a="kill" title="Tuer le processus">${icon('skull')}</button>
          <div style="flex:1"></div>
          ${n.main ? `<a class="btn sm ghost" href="#dashboard" title="Tableau de bord">${icon('ext')}</a>`
                   : `<button class="btn sm ghost icon" data-a="open" title="Ouvrir le dossier">${icon('folder')}</button>`}
        </div>
      </div>`).join('');
    $$('#iNodes .node').forEach(card => card.querySelectorAll('[data-a]').forEach(b => b.onclick = async () => {
      const id = card.dataset.id, a = b.dataset.a;
      if (a === 'open') return act(() => api('POST', 'open', { what: id }));
      if (a === 'kill' && !await confirmBox('Tuer le processus ?', 'Il sera coupé net, <b>sans sauvegarde</b>.', 'Tuer', true)) return;
      instPower(id, a);
      if (id !== 'lobby' && id !== IC.sel && (a === 'start' || a === 'restart')) this.select(id);
    }));

    $('#iAllStart').onclick = async () => {
      // Le lobby d'abord, puis les instances : Velocity redirige vers le lobby.
      if (S.status.state === 'offline') await instPower('lobby', 'start').catch(() => { });
      for (const i of S.inst || []) if (i.state === 'offline') await instPower(i.id, 'start').catch(() => { });
    };
    $('#iAllStop').onclick = async () => {
      if (!await confirmBox('Tout arrêter ?', 'Velocity, le lobby et toutes les instances lancées depuis le panel seront arrêtés proprement.', 'Tout arrêter', true)) return;
      for (const i of S.inst || []) if (i.state !== 'offline' && i.state !== 'external') await instPower(i.id, 'stop').catch(() => { });
      if (S.status.state !== 'offline') instPower('lobby', 'stop');
    };

    mountCmdLine($('#iCmd'), c => act(() => api('POST', `instances/command?id=${enc(IC.sel)}`, { cmd: c })).then(() => this.pull()));
    $('#iCon').addEventListener('scroll', e => { IC.follow = e.target.scrollTop + e.target.clientHeight >= e.target.scrollHeight - 30; });
    this.select(IC.sel);
    this.tick();
  },
  select(id) {
    if (!(S.inst || []).some(i => i.id === id)) id = (S.inst && S.inst[0] && S.inst[0].id) || id;
    IC.sel = id; IC.last = 0; IC.follow = true;
    try { localStorage.setItem('instSel', id); } catch { }
    $('#iTabs').innerHTML = (S.inst || []).map(i => `<span class="chip ${i.id === id ? 'on' : ''}" data-id="${i.id}">${esc(i.name)}</span>`).join('');
    $$('#iTabs .chip').forEach(c => c.onclick = () => this.select(c.dataset.id));
    const meI = (S.inst || []).find(i => i.id === id);
    $('#iQuick').innerHTML = (INST_QUICK[id] || INST_QUICK[meI ? meI.kind : 'paper'] || []).map(([c, l]) => `<span class="chip" data-c="${esc(c)}" title="${esc(c)}">${esc(l)}</span>`).join('');
    $$('#iQuick .chip').forEach(c => c.onclick = () => act(() => api('POST', `instances/command?id=${enc(IC.sel)}`, { cmd: c.dataset.c })).then(() => this.pull()));
    $('#iCon').innerHTML = '';
    this.pull();
  },
  async pull() {
    if (IC.busy) return;
    IC.busy = true;
    const id = IC.sel, box = $('#iCon');
    try {
      const lines = await api('GET', `instances/console?id=${enc(id)}&since=${IC.last}`);
      if (id !== IC.sel || !box || !document.body.contains(box)) return;
      if (lines.length) {
        const ph = box.querySelector('.ph'); if (ph) ph.remove();
        IC.last = lines[lines.length - 1].id;
        box.insertAdjacentHTML('beforeend', lines.map(l => lineHtml(l)).join(''));
        while (box.childElementCount > 3000) box.firstChild.remove();
        if (IC.follow) box.scrollTop = box.scrollHeight;
      }
      const me = (S.inst || []).find(i => i.id === id);
      if ((!box.childElementCount || box.querySelector('.ph')) && me) box.innerHTML = `<div class="dim ph">${me.state === 'external'
        ? `${esc(me.name)} a été lancé hors du panel (fenêtre start-network) : sa console est dans cette fenêtre.`
        : `${esc(me.name)} est arrêté. Démarre-le pour voir sa console ici.`}</div>`;
    } catch { } finally { IC.busy = false; }
  },
  tick() {
    const ext = [];
    nodes().forEach(n => {
      const card = $(`#iNodes .node[data-id="${n.id}"]`); if (!card) return;
      const s = n.state, off = s === 'offline', running = !off && s !== 'external';
      card.classList.toggle('on', s === 'online');
      card.querySelector('[data-f=pill]').className = 'state ' + s;
      card.querySelector('[data-f=state]').textContent = INST_LABEL[s] || s;
      card.querySelector('[data-f=ram]').textContent = running ? (n.ramMb / 1024).toFixed(2) + ' Go' : '—';
      card.querySelector('[data-f=cpu]').textContent = running ? Math.round(n.cpu) + ' %' : '—';
      card.querySelector('[data-f=up]').textContent = running ? fmtDur(n.uptime) : '—';
      card.querySelector('[data-a=start]').disabled = !off;
      card.querySelector('[data-a=stop]').disabled = !running || s === 'stopping';
      card.querySelector('[data-a=restart]').disabled = !running;
      card.querySelector('[data-a=kill]').disabled = !running;
      if (s === 'external') ext.push(n.name);
    });
    $('#iExt').innerHTML = ext.length ? `<div class="banner info">${icon('net')}<div class="grow"><b>${ext.length > 1 ? ext.slice(0, -1).map(esc).join(', ') + ' et ' + esc(ext[ext.length - 1]) : esc(ext[0])}</b> ${ext.length > 1 ? 'tournent' : 'tourne'} déjà hors du panel (lancé par start-network.ps1). Arrête-les avec <span class="mono">stop-network.ps1</span> (ou <span class="mono">end</span> / <span class="mono">stop</span> dans leur fenêtre), puis relance-les ici pour avoir leur console dans le panel.</div></div>` : '';
    this.pull();
  },
};

const AV_COLORS = ['#e8573f', '#2f9e8f', '#7b61ff', '#e2a03f', '#3b82f6', '#d9467f', '#3fae5c', '#8b5cf6'];
function avatar(name) {
  let h = 0; for (const c of name) h = (h * 31 + c.charCodeAt(0)) >>> 0;
  return `<div class="avatar" style="background:${AV_COLORS[h % AV_COLORS.length]}">${esc(name[0].toUpperCase())}</div>`;
}
const GM = { SURVIVAL: ['Survie', 'green'], CREATIVE: ['Créatif', 'blue'], ADVENTURE: ['Aventure', 'amber'], SPECTATOR: ['Spectateur', ''] };

pages.players = {
  title: 'Joueurs',
  render(v) {
    v.innerHTML = `<div class="toolbar"><div class="grow muted" id="pCount"></div>
      <button class="btn sm" id="pWl">Whitelist…</button><button class="btn sm" id="pOps">Opérateurs (ops.json)</button></div>
      <div id="pList"></div>`;
    $('#pWl').onclick = async () => { const n = await promptBox('Ajouter à la whitelist', 'Pseudo'); if (n) sendCmd('whitelist add ' + n); };
    $('#pOps').onclick = () => openEditor('ops.json');
    this.sig = null; this.tick();
  },
  tick() {
    const s = S.stats, list = s ? s.players : [];
    $('#pCount').textContent = s ? `${list.length} joueur(s) en ligne sur ${s.maxPlayers}` : 'Serveur hors ligne';
    const sig = JSON.stringify(list);
    if (sig === this.sig) return; this.sig = sig;
    if (!list.length) { $('#pList').innerHTML = `<div class="card empty">${icon('users')}<div>Aucun joueur connecté.</div><div class="dim">Connecte-toi en jeu sur <b class="mono">localhost</b>.</div></div>`; return; }
    $('#pList').innerHTML = `<div class="players">${list.map(p => {
      const gm = GM[p.gamemode] || [p.gamemode, ''];
      return `<div class="card player" data-n="${esc(p.name)}">
        <div class="head">${avatar(p.name)}<div style="flex:1;min-width:0"><b style="font-size:15px">${esc(p.name)}</b> ${p.op ? '<span class="tag amber">OP</span>' : ''}
          <div class="dim mono" style="font-size:11px;overflow:hidden;text-overflow:ellipsis">${esc(p.uuid)}</div></div><span class="tag ${gm[1]}">${esc(gm[0])}</span></div>
        <div class="meta">
          <div>Santé<b>❤ ${p.health}</b></div><div>Faim<b>🍗 ${p.food}</b></div><div>Ping<b>${p.ping} ms</b></div>
          <div style="grid-column:span 3">Position<b class="mono">${esc(p.world)} · ${p.x} / ${p.y} / ${p.z}</b></div>
        </div>
        <div class="acts">
          <button class="btn sm" data-a="gamemode creative">Créatif</button><button class="btn sm" data-a="gamemode survival">Survie</button>
          <button class="btn sm" data-a="gamemode spectator">Spectateur</button>
          <button class="btn sm" data-a="${p.op ? 'deop' : 'op'}">${p.op ? 'Retirer OP' : 'Donner OP'}</button>
          <button class="btn sm" data-a="heal">Soigner</button><button class="btn sm" data-a="clear">Vider inv.</button>
          <button class="btn sm" data-a="msg">Message</button><button class="btn sm red" data-a="kick">Kick</button>
        </div></div>`;
    }).join('')}</div>`;
    $$('#pList .player').forEach(card => {
      const n = card.dataset.n;
      $$('[data-a]', card).forEach(b => b.onclick = async () => {
        const a = b.dataset.a;
        if (a === 'heal') { sendCmd(`effect give ${n} minecraft:instant_health 1 10 true`); sendCmd(`effect give ${n} minecraft:saturation 1 10 true`); }
        else if (a === 'clear') { if (await confirmBox('Vider l\'inventaire', `Vider l'inventaire de <b>${esc(n)}</b> ?`, 'Vider', true)) sendCmd('clear ' + n); }
        else if (a === 'kick') { const r = await promptBox('Expulser ' + n, 'Raison', 'Expulsé depuis le panel'); if (r) sendCmd(`kick ${n} ${r}`); }
        else if (a === 'msg') { const m = await promptBox('Message à ' + n, 'Message'); if (m) sendCmd(`tell ${n} ${m}`); }
        else if (a.startsWith('gamemode')) sendCmd(`${a} ${n}`);
        else sendCmd(`${a} ${n}`);
        toast('Commande envoyée');
      });
    });
  },
};

pages.plugins = {
  title: 'Plugins',
  async render(v) {
    v.innerHTML = `<div id="plBanner"></div>
      <div class="grid g21">
        <div class="card"><h2>${icon('box')}Fichiers du dossier plugins <span class="right"><button class="btn sm" id="plOpen">${icon('ext')}Ouvrir le dossier</button></span></h2>
          <div id="plFiles" class="muted">Chargement…</div></div>
        <div>
          <div class="card"><h2>${icon('upload')}Ajouter un plugin</h2>
            <div class="drop" id="plDrop">Glisse un ou plusieurs <b>.jar</b> ici<br><span class="dim">ou</span> <a href="#" id="plPick">choisis un fichier</a><input type="file" id="plInput" accept=".jar" multiple hidden></div>
            <p class="dim" style="font-size:12px;margin:10px 0 0">Astuce : configure le <a href="#dev">dossier de build</a> pour que tes plugins se déploient tout seuls à chaque compilation.</p>
          </div>
          <div class="card mt"><h2>${icon('plug')}Chargés par le serveur</h2><div id="plLoaded" class="muted">—</div></div>
        </div>
      </div>`;
    hydrateIcons(v);
    $('#plOpen').onclick = () => act(() => api('POST', 'open', { what: 'plugins' }));
    $('#plPick').onclick = e => { e.preventDefault(); $('#plInput').click(); };
    $('#plInput').onchange = e => this.upload([...e.target.files]);
    const d = $('#plDrop');
    d.ondragover = e => { e.preventDefault(); d.classList.add('over'); };
    d.ondragleave = () => d.classList.remove('over');
    d.ondrop = e => { e.preventDefault(); d.classList.remove('over'); this.upload([...e.dataTransfer.files].filter(f => f.name.endsWith('.jar'))); };
    await this.load(); this.tick();
  },
  async upload(files) {
    for (const f of files) await act(() => api('POST', `upload?path=plugins&name=${enc(f.name)}`, f, true), f.name + ' ajouté');
    if (files.length) { S.needRestart = true; this.load(); }
  },
  async load() {
    const list = await api('GET', 'plugins');
    $('#plBanner').innerHTML = S.needRestart && S.status.state !== 'offline' ? `<div class="banner">${icon('restart')}<div class="grow">Redémarre le serveur pour appliquer les changements de plugins.</div><button class="btn sm" onclick="document.querySelector('[data-power=restart]').click()">Redémarrer</button></div>` : '';
    hydrateIcons($('#plBanner'));
    if (!list.length) { $('#plFiles').innerHTML = '<div class="empty">Aucun plugin.</div>'; return; }
    $('#plFiles').innerHTML = `<table><thead><tr><th>Fichier</th><th>Taille</th><th>Modifié</th><th>État</th><th></th></tr></thead><tbody>${list.map(p => `
      <tr><td class="mono">${esc(p.file.replace(/\.disabled$/, ''))}</td><td class="dim">${fmtSize(p.size)}</td><td class="dim">${esc(p.mtime)}</td>
      <td>${p.disabled ? '<span class="tag">Désactivé</span>' : '<span class="tag green">Actif</span>'}</td>
      <td class="actions">
        ${p.file === 'PanelBridge.jar' ? '<span class="tag blue" title="Nécessaire aux stats du panel">panel</span>' : `
        <button class="btn sm" data-t="${esc(p.file)}">${p.disabled ? 'Activer' : 'Désactiver'}</button>
        <button class="btn sm icon danger" data-d="${esc(p.file)}" title="Supprimer">${icon('trash')}</button>`}
      </td></tr>`).join('')}</tbody></table>`;
    $$('[data-t]', $('#plFiles')).forEach(b => b.onclick = async () => { await act(() => api('POST', 'plugins/toggle?file=' + enc(b.dataset.t))); S.needRestart = true; this.load(); });
    $$('[data-d]', $('#plFiles')).forEach(b => b.onclick = async () => {
      if (!await confirmBox('Supprimer le plugin', `Supprimer <b>${esc(b.dataset.d)}</b> ? Son dossier de config est conservé.`, 'Supprimer', true)) return;
      await act(() => api('POST', 'plugins/delete?file=' + enc(b.dataset.d)), 'Plugin supprimé'); S.needRestart = true; this.load();
    });
  },
  tick() {
    const s = S.stats, el = $('#plLoaded'); if (!el) return;
    if (!s) { el.innerHTML = '<span class="dim">Serveur hors ligne.</span>'; return; }
    const sig = JSON.stringify(s.plugins); if (sig === this.sig) return; this.sig = sig;
    el.innerHTML = s.plugins.map(p => `<div class="row between" style="padding:7px 0;border-bottom:1px solid var(--line)">
      <div><b>${esc(p.name)}</b> <span class="dim mono">v${esc(p.version)}</span>${p.description ? `<div class="dim" style="font-size:12px">${esc(p.description)}</div>` : ''}</div>
      ${p.enabled ? '<span class="tag green">OK</span>' : '<span class="tag red">Erreur</span>'}</div>`).join('');
  },
};

// ---- Fichiers
const TEXT_EXT = /\.(ya?ml|json|properties|txt|log|md|toml|conf|cfg|ini|mcmeta|mcfunction|csv|xml|js|sk|lang|secret)$/i;
let filesPath = '';
pages.files = {
  title: 'Fichiers',
  render(v) { this.v = v; this.load(filesPath); },
  async load(path) {
    filesPath = path;
    let data;
    try { data = await api('GET', 'files?path=' + enc(path)); } catch (e) { toast(e.message, true); if (path) return this.load(''); return; }
    const v = this.v, parts = data.path ? data.path.split('/') : [];
    v.innerHTML = `
      <div class="toolbar">
        <div class="crumbs grow"><a data-p="">server</a>${parts.map((p, i) => `<span class="sep">/</span><a data-p="${esc(parts.slice(0, i + 1).join('/'))}">${esc(p)}</a>`).join('')}</div>
        <button class="btn sm" id="fNewFile">${icon('file')}Nouveau fichier</button>
        <button class="btn sm" id="fNewDir">${icon('plus')}Dossier</button>
        <button class="btn sm" id="fUp">${icon('upload')}Envoyer</button><input type="file" id="fIn" multiple hidden>
        <button class="btn sm" id="fExp">${icon('ext')}Explorateur</button>
      </div>
      <div class="card" id="fDrop" style="padding:6px 8px">
        <table><thead><tr><th>Nom</th><th style="width:110px">Taille</th><th style="width:160px">Modifié</th><th style="width:150px"></th></tr></thead><tbody>
        ${path ? `<tr class="file-row dir" data-dir="${esc(parts.slice(0, -1).join('/'))}"><td class="fname">${icon('back')}<span class="muted">..</span></td><td></td><td></td><td></td></tr>` : ''}
        ${data.items.map(f => `<tr class="file-row ${f.dir ? 'dir' : ''}" ${f.dir ? `data-dir="${esc(f.path)}"` : `data-file="${esc(f.path)}"`}>
          <td class="fname">${icon(f.dir ? 'folder' : 'file')}<span>${esc(f.name)}</span></td>
          <td class="dim">${f.dir ? '' : fmtSize(f.size)}</td><td class="dim">${esc(f.mtime)}</td>
          <td class="actions">
            ${f.dir ? '' : `<button class="btn sm icon" data-dl="${esc(f.path)}" title="Télécharger">${icon('download')}</button>`}
            <button class="btn sm icon" data-rn="${esc(f.path)}" title="Renommer">${icon('edit')}</button>
            <button class="btn sm icon danger" data-rm="${esc(f.path)}" title="Supprimer">${icon('trash')}</button>
          </td></tr>`).join('')}
        </tbody></table>
        ${data.items.length ? '' : '<div class="empty">Dossier vide</div>'}
      </div>`;
    hydrateIcons(v);
    $$('.crumbs a', v).forEach(a => a.onclick = () => this.load(a.dataset.p));
    $$('[data-dir]', v).forEach(r => r.onclick = () => this.load(r.dataset.dir));
    $$('[data-file]', v).forEach(r => r.onclick = e => { if (e.target.closest('button')) return; openEditor(r.dataset.file); });
    $$('[data-dl]', v).forEach(b => b.onclick = e => { e.stopPropagation(); location.href = `/api/download?token=${enc(TOKEN)}&path=${enc(b.dataset.dl)}`; });
    $$('[data-rn]', v).forEach(b => b.onclick = async e => {
      e.stopPropagation();
      const from = b.dataset.rn, base = from.split('/').pop();
      const n = await promptBox('Renommer', 'Nouveau nom', base); if (!n || n === base) return;
      await act(() => api('POST', 'file/rename', { from, to: from.slice(0, from.length - base.length) + n }), 'Renommé'); this.load(path);
    });
    $$('[data-rm]', v).forEach(b => b.onclick = async e => {
      e.stopPropagation();
      if (!await confirmBox('Supprimer', `Supprimer définitivement <b>${esc(b.dataset.rm)}</b> ?`, 'Supprimer', true)) return;
      await act(() => api('POST', 'file/delete?path=' + enc(b.dataset.rm)), 'Supprimé'); this.load(path);
    });
    $('#fNewDir').onclick = async () => { const n = await promptBox('Nouveau dossier', 'Nom'); if (n) { await act(() => api('POST', 'file/mkdir?path=' + enc((path ? path + '/' : '') + n))); this.load(path); } };
    $('#fNewFile').onclick = async () => { const n = await promptBox('Nouveau fichier', 'Nom (ex : config.yml)'); if (n) { const p = (path ? path + '/' : '') + n; await act(() => api('PUT', 'file?path=' + enc(p), '', true)); openEditor(p); } };
    $('#fUp').onclick = () => $('#fIn').click();
    $('#fExp').onclick = () => act(() => api('POST', 'open', { what: 'server' }));
    const up = async files => { for (const f of files) await act(() => api('POST', `upload?path=${enc(path)}&name=${enc(f.name)}`, f, true), f.name + ' envoyé'); this.load(path); };
    $('#fIn').onchange = e => up([...e.target.files]);
    const d = $('#fDrop');
    d.ondragover = e => { e.preventDefault(); d.style.borderColor = 'var(--green)'; };
    d.ondragleave = () => d.style.borderColor = '';
    d.ondrop = e => { e.preventDefault(); d.style.borderColor = ''; up([...e.dataTransfer.files]); };
  },
};

async function openEditor(path) {
  const name = path.split('/').pop();
  if (!TEXT_EXT.test(name) && !await confirmBox('Ouvrir comme texte ?', `<b>${esc(name)}</b> ne ressemble pas à un fichier texte.`, 'Ouvrir quand même')) return;
  let data;
  try { data = await api('GET', 'file?path=' + enc(path)); } catch (e) { return toast(e.message, true); }
  if (location.hash !== '#files') { skipHash = true; location.hash = 'files'; }
  route = 'files';
  setActive('files');
  const v = $('#view'), dir = path.split('/').slice(0, -1).join('/');
  $('#pageTitle').textContent = 'Éditeur';
  v.innerHTML = `
    <div class="toolbar">
      <button class="btn sm" id="eBack">${icon('back')}Retour</button>
      <div class="grow mono">${esc(path)} <span class="dim" id="eState"></span></div>
      <button class="btn sm" id="eReload">${icon('restart')}Recharger</button>
      <button class="btn sm green" id="eSave">${icon('save')}Enregistrer <span class="dim" style="color:#cfe">Ctrl+S</span></button>
    </div>
    <textarea class="editor" id="eText" spellcheck="false"></textarea>
    <p class="dim" style="font-size:12px">Les changements de config d'un plugin s'appliquent après sa commande de reload ou un redémarrage du serveur.</p>`;
  hydrateIcons(v);
  const ta = $('#eText'); ta.value = data.content;
  let saved = ta.value;
  const dirty = () => $('#eState').textContent = ta.value !== saved ? '● modifié' : '';
  ta.oninput = dirty;
  const save = async () => { await act(() => api('PUT', 'file?path=' + enc(path), ta.value, true), 'Enregistré'); saved = ta.value; dirty(); };
  ta.onkeydown = e => {
    if (e.key === 's' && e.ctrlKey) { e.preventDefault(); save(); }
    if (e.key === 'Tab') { e.preventDefault(); const s = ta.selectionStart; ta.setRangeText('  ', s, ta.selectionEnd, 'end'); dirty(); }
  };
  $('#eSave').onclick = save;
  $('#eReload').onclick = async () => { const d = await api('GET', 'file?path=' + enc(path)); ta.value = saved = d.content; dirty(); };
  $('#eBack').onclick = async () => {
    if (ta.value !== saved && !await confirmBox('Modifications non enregistrées', 'Quitter sans enregistrer ?', 'Quitter', true)) return;
    filesPath = dir; pages.files.render(v); $('#pageTitle').textContent = 'Fichiers';
  };
  ta.focus();
}

// ---- Monde & sauvegardes
pages.worlds = {
  title: 'Monde & sauvegardes',
  async render(v) {
    v.innerHTML = `
      <div class="grid g3" id="wCards"></div>
      <div class="grid g21 mt">
        <div class="card"><h2>${icon('save')}Sauvegardes du monde <span class="right"><button class="btn sm" id="bOpen">${icon('ext')}Dossier</button><button class="btn sm green" id="bNew">${icon('plus')}Créer une sauvegarde</button></span></h2>
          <div class="sub">Une sauvegarde peut être créée serveur allumé. La restauration demande un serveur arrêté.</div>
          <div id="bList" class="muted">Chargement…</div></div>
        <div>
          <div class="card"><h2>${icon('restart')}Map d'origine</h2>
            <p class="muted" style="margin:0 0 12px">Remet la map <b>GameLand spawn</b> telle qu'importée. Tout ce qui a été modifié dans le monde est perdu.</p>
            <button class="btn red" id="wReset">${icon('restart')}Remettre la map d'origine</button></div>
          <div class="card mt"><h2>${icon('globe')}Règles du monde</h2><div class="chips" id="wRules"></div></div>
        </div>
      </div>`;
    hydrateIcons(v);
    $('#bOpen').onclick = () => act(() => api('POST', 'open', { what: 'backups' }));
    $('#bNew').onclick = async () => {
      const label = await promptBox('Nouvelle sauvegarde', 'Étiquette (optionnelle)', 'avant-test');
      if (label === null) return;
      toast('Sauvegarde en cours…');
      await act(() => api('POST', 'backups/create', { label }), 'Sauvegarde créée'); this.load();
    };
    $('#wReset').onclick = async () => {
      if (S.status.state !== 'offline') return toast('Arrête d\'abord le serveur.', true);
      if (!await confirmBox('Remettre la map d\'origine ?', 'Le monde actuel sera <b>remplacé</b>. Pense à créer une sauvegarde avant si tu veux garder tes modifs.', 'Remettre', true)) return;
      act(() => api('POST', 'world/reset'), 'Map d\'origine restaurée');
    };
    const rules = [['gamerule doDaylightCycle false', 'Figer le jour'], ['gamerule doDaylightCycle true', 'Cycle jour/nuit'], ['gamerule doWeatherCycle false', 'Figer la météo'],
      ['gamerule doMobSpawning false', 'Pas de mobs'], ['gamerule doMobSpawning true', 'Mobs activés'], ['gamerule keepInventory true', 'Keep inventory'],
      ['gamerule mobGriefing false', 'Pas de griefing mobs'], ['gamerule doFireTick false', 'Feu ne se propage pas'], ['gamerule announceAdvancements false', 'Masquer progrès'],
      ['setworldspawn', 'Spawn ici (console = 0,0)'], ['time set day', 'Jour'], ['time set night', 'Nuit']];
    $('#wRules').innerHTML = rules.map(([c, l]) => `<span class="chip" data-c="${esc(c)}" title="${esc(c)}">${esc(l)}</span>`).join('');
    $$('#wRules .chip').forEach(c => c.onclick = () => sendCmd(c.dataset.c).then(() => toast(c.dataset.c)));
    this.sig = null; this.tick(); this.load();
  },
  async load() {
    const list = await api('GET', 'backups');
    $('#bList').innerHTML = !list.length ? '<div class="empty">Aucune sauvegarde pour l\'instant.</div>' :
      `<table><thead><tr><th>Sauvegarde</th><th>Taille</th><th>Date</th><th></th></tr></thead><tbody>${list.map(b => `
        <tr><td class="mono">${esc(b.name)}</td><td class="dim">${fmtSize(b.size)}</td><td class="dim">${esc(b.mtime)}</td>
        <td class="actions"><button class="btn sm" data-r="${esc(b.name)}">Restaurer</button>
        <button class="btn sm icon" data-g="${esc(b.name)}" title="Télécharger">${icon('download')}</button>
        <button class="btn sm icon danger" data-x="${esc(b.name)}" title="Supprimer">${icon('trash')}</button></td></tr>`).join('')}</tbody></table>`;
    $$('[data-r]', $('#bList')).forEach(b => b.onclick = async () => {
      if (S.status.state !== 'offline') return toast('Arrête d\'abord le serveur.', true);
      if (!await confirmBox('Restaurer ?', `Le monde actuel sera remplacé par <b>${esc(b.dataset.r)}</b>.`, 'Restaurer', true)) return;
      act(() => api('POST', 'backups/restore?name=' + enc(b.dataset.r)), 'Sauvegarde restaurée');
    });
    $$('[data-g]', $('#bList')).forEach(b => b.onclick = () => location.href = `/api/backups/download?token=${enc(TOKEN)}&name=${enc(b.dataset.g)}`);
    $$('[data-x]', $('#bList')).forEach(b => b.onclick = async () => {
      if (!await confirmBox('Supprimer la sauvegarde', `Supprimer définitivement <b>${esc(b.dataset.x)}</b> ?`, 'Supprimer', true)) return;
      await act(() => api('POST', 'backups/delete?name=' + enc(b.dataset.x)), 'Supprimée'); this.load();
    });
  },
  tick() {
    const s = S.stats, el = $('#wCards'); if (!el) return;
    const sig = s ? JSON.stringify(s.worlds) : 'off'; if (sig === this.sig) return; this.sig = sig;
    el.innerHTML = !s ? `<div class="card muted" style="grid-column:1/-1">Démarre le serveur pour voir les statistiques du monde en direct.</div>` :
      s.worlds.map(w => `<div class="card"><h2>${icon('globe')}${esc(w.name)} <span class="right tag">${esc(w.env)}</span></h2>
        <div class="grid g3" style="gap:8px">
          <div><div class="dim" style="font-size:12px">Chunks</div><b style="font-size:18px">${w.chunks}</b></div>
          <div><div class="dim" style="font-size:12px">Entités</div><b style="font-size:18px">${w.entities}</b></div>
          <div><div class="dim" style="font-size:12px">Blocs-entités</div><b style="font-size:18px">${w.tileEntities}</b></div>
        </div>
        <div class="dim mt" style="font-size:12px">Heure : ${w.time} ticks · ${w.storm ? 'Pluie' : 'Beau temps'} · ${w.players} joueur(s)</div></div>`).join('');
    hydrateIcons(el);
  },
};

// ---- server.properties
const PROPS = {
  'Général': {
    motd: ['Message du serveur (MOTD)', 'text'], 'max-players': ['Joueurs max', 'number'], 'gamemode': ['Mode de jeu par défaut', ['survival', 'creative', 'adventure', 'spectator']],
    'force-gamemode': ['Forcer le mode de jeu', 'bool'], difficulty: ['Difficulté', ['peaceful', 'easy', 'normal', 'hard']], hardcore: ['Hardcore', 'bool'], pvp: ['PvP', 'bool'],
  },
  'Monde': {
    'level-name': ['Dossier du monde', 'text'], 'spawn-protection': ['Protection du spawn (blocs)', 'number'], 'allow-nether': ['Nether', 'bool'],
    'spawn-monsters': ['Monstres', 'bool'], 'spawn-animals': ['Animaux', 'bool'], 'spawn-npcs': ['Villageois', 'bool'], 'allow-flight': ['Autoriser le vol', 'bool'],
    'view-distance': ['Distance de vue', 'number'], 'simulation-distance': ['Distance de simulation', 'number'], 'enable-command-block': ['Blocs de commande', 'bool'],
    'generate-structures': ['Générer des structures', 'bool'], 'level-type': ['Type de monde (nouveau monde)', 'text'], 'level-seed': ['Graine (nouveau monde)', 'text'],
  },
  'Joueurs & sécurité': {
    'online-mode': ['Mode en ligne (comptes Mojang)', 'bool'], 'white-list': ['Whitelist', 'bool'], 'enforce-whitelist': ['Appliquer la whitelist', 'bool'],
    'op-permission-level': ['Niveau de permission OP', 'number'], 'player-idle-timeout': ['Expulsion AFK (min, 0 = jamais)', 'number'], 'enforce-secure-profile': ['Profils sécurisés (chat signé)', 'bool'],
  },
  'Réseau': {
    'server-ip': ['IP d\'écoute', 'text'], 'server-port': ['Port', 'number'], 'network-compression-threshold': ['Seuil de compression', 'number'],
    'enable-rcon': ['RCON', 'bool'], 'enable-query': ['Query', 'bool'], 'max-tick-time': ['Watchdog (ms)', 'number'],
  },
};
pages.config = {
  title: 'Configuration',
  async render(v) {
    const props = await api('GET', 'properties');
    const map = {}; props.forEach(p => map[p.key] = p.value);
    const known = new Set(Object.values(PROPS).flatMap(g => Object.keys(g)));
    const field = (k, [label, type]) => {
      const val = map[k] == null ? '' : map[k];
      if (type === 'bool') return `<div class="toggle-row"><div><b>${esc(label)}</b><small class="mono">${esc(k)}</small></div><label class="switch"><input type="checkbox" data-k="${esc(k)}" ${val === 'true' ? 'checked' : ''}><span></span></label></div>`;
      if (Array.isArray(type)) return `<div class="field"><label>${esc(label)}</label><select class="input" data-k="${esc(k)}">${type.map(o => `<option ${o === val ? 'selected' : ''}>${o}</option>`).join('')}</select><span class="desc mono">${esc(k)}</span></div>`;
      return `<div class="field"><label>${esc(label)}</label><input class="input" type="${type === 'number' ? 'number' : 'text'}" data-k="${esc(k)}" value="${esc(val)}"><span class="desc mono">${esc(k)}</span></div>`;
    };
    v.innerHTML = `<div class="banner info">${icon('sliders')}<div class="grow">Modifie <b>server.properties</b>. Les changements s'appliquent au prochain démarrage du serveur.</div>
        <button class="btn sm" id="cfRaw">${icon('file')}Éditer le fichier brut</button><button class="btn sm green" id="cfSave">${icon('save')}Enregistrer</button></div>
      ${Object.entries(PROPS).map(([g, keys]) => `<div class="card mt"><h2>${esc(g)}</h2><div class="form-grid">${Object.entries(keys).map(([k, d]) => field(k, d)).join('')}</div></div>`).join('')}
      <div class="card mt"><h2>Autres propriétés</h2><div class="form-grid">${props.filter(p => !known.has(p.key)).map(p => field(p.key, [p.key, 'text'])).join('')}</div></div>
      <div class="card mt"><h2>Autres fichiers de configuration</h2><div class="chips">${['bukkit.yml', 'spigot.yml', 'config/paper-global.yml', 'config/paper-world-defaults.yml', 'ops.json', 'whitelist.json', 'banned-players.json', 'commands.yml', 'permissions.yml']
        .map(f => `<span class="chip" data-f="${f}">${f}</span>`).join('')}</div></div>`;
    hydrateIcons(v);
    $$('.chip[data-f]', v).forEach(c => c.onclick = () => openEditor(c.dataset.f));
    $('#cfRaw').onclick = () => openEditor('server.properties');
    $('#cfSave').onclick = async () => {
      const out = {};
      $$('[data-k]', v).forEach(i => out[i.dataset.k] = i.type === 'checkbox' ? String(i.checked) : i.value);
      await act(() => api('PUT', 'properties', out), 'server.properties enregistré');
      if (S.status.state !== 'offline' && await confirmBox('Redémarrer ?', 'Redémarrer le serveur maintenant pour appliquer la configuration ?', 'Redémarrer')) api('POST', 'power', { action: 'restart' });
    };
  },
};

// ---- Outils de dev
pages.dev = {
  title: 'Outils de dev',
  async render(v) {
    const c = await api('GET', 'settings');
    v.innerHTML = `
      <div class="grid g2">
        <div class="card"><h2>${icon('rocket')}Déploiement automatique des plugins</h2>
          <div class="sub">Le panel surveille le dossier où ton IDE compile tes plugins. Dès qu'un <b>.jar</b> change, il est copié dans <span class="mono">plugins/</span> et le serveur redémarre.</div>
          <div class="field"><label>Dossier de build</label>
            <input class="input mono" id="dvPath" placeholder="ex : C:\\Users\\Zazou\\IdeaProjects\\MonPlugin\\target" value="${esc(c.watchPath)}">
            <span class="desc">Maven : <span class="mono">target</span> · Gradle : <span class="mono">build\\libs</span>. Les jars <span class="mono">original-*</span>, <span class="mono">-sources</span> et <span class="mono">-javadoc</span> sont ignorés.</span></div>
          <div class="toggle-row mt"><div><b>Redémarrer automatiquement après déploiement</b><small>Sinon, le jar est juste copié.</small></div><label class="switch"><input type="checkbox" id="dvAuto" ${c.autoDeploy ? 'checked' : ''}><span></span></label></div>
          <div class="row mt"><button class="btn green" id="dvSave">${icon('save')}Enregistrer</button><button class="btn" id="dvNow">${icon('upload')}Déployer maintenant</button><button class="btn ghost" id="dvOpen">${icon('ext')}Ouvrir le dossier</button></div>
        </div>
        <div class="card"><h2>${icon('bug')}Débogage avec IntelliJ IDEA</h2>
          <div class="sub">Le serveur démarre avec un agent de débogage : tu peux mettre des points d'arrêt dans ton plugin pendant que tu joues.</div>
          <div class="toggle-row"><div><b>Port de débogage : ${c.debugPort}</b><small>${c.debug ? 'Activé' : 'Désactivé'} · modifiable dans Réglages du panel</small></div>${c.debug ? '<span class="tag green">actif</span>' : '<span class="tag">off</span>'}</div>
          <div class="toggle-row mt"><div><b>HotSwap avancé</b><small>${c.hotswap && c.debug ? 'Serveur lancé sur JetBrains Runtime' : 'Désactivé · activable dans Réglages du panel'}</small></div>${c.hotswap && c.debug ? '<span class="tag green">actif</span>' : '<span class="tag">off</span>'}</div>
          <ol class="steps mt">
            <li>Une seule fois : <b>Run → Edit Configurations… → + → Remote JVM Debug</b>, host <code>localhost</code>, port <code>${c.debugPort}</code></li>
            <li>Lance le serveur ici, puis clique sur <b>Debug</b> dans IntelliJ</li>
            <li>Modifie ton code puis <b>Build → Build Project (Ctrl+F9)</b> : IntelliJ propose de recharger les classes → <b>Reload</b></li>
          </ol>
          <div class="grid g2 mt" style="gap:10px;font-size:12.5px">
            <div class="toggle-row" style="display:block"><b style="color:var(--green)">Rechargé à chaud</b><small>Code des méthodes, nouvelles méthodes, nouveaux champs, nouvelles classes, signatures, lambdas, valeurs de config lues à l'exécution</small></div>
            <div class="toggle-row" style="display:block"><b style="color:var(--amber)">Demande un redémarrage</b><small>Nouveau listener ou nouvelle commande à enregistrer, code de <span class="mono">onEnable()</span>, <span class="mono">plugin.yml</span>, nouvelle dépendance shadée</small></div>
          </div>
          <p class="dim" style="font-size:12px;margin:10px 0 0">Les champs ajoutés valent null/0 sur les objets déjà créés. Si quelque chose part en vrille après un HotSwap, redémarre : c'est l'état en mémoire, pas ton code.</p>
        </div>
      </div>
      <div class="grid g2 mt">
        <div class="card"><h2>${icon('code')}Dépendance Paper API (Maven)</h2>
          <textarea class="editor" style="height:250px" readonly>&lt;repositories&gt;
  &lt;repository&gt;
    &lt;id&gt;papermc&lt;/id&gt;
    &lt;url&gt;https://repo.papermc.io/repository/maven-public/&lt;/url&gt;
  &lt;/repository&gt;
&lt;/repositories&gt;

&lt;dependencies&gt;
  &lt;dependency&gt;
    &lt;groupId&gt;io.papermc.paper&lt;/groupId&gt;
    &lt;artifactId&gt;paper-api&lt;/artifactId&gt;
    &lt;version&gt;26.3.build.257-stable&lt;/version&gt;
    &lt;scope&gt;provided&lt;/scope&gt;
  &lt;/dependency&gt;
&lt;/dependencies&gt;</textarea></div>
        <div class="card"><h2>${icon('cpu')}Machine & JVM</h2><div id="dvSys" class="muted">…</div></div>
      </div>`;
    hydrateIcons(v);
    $('#dvSave').onclick = async () => {
      const s = await api('GET', 'settings'); s.watchPath = $('#dvPath').value.trim(); s.autoDeploy = $('#dvAuto').checked;
      act(() => api('PUT', 'settings', s), s.watchPath ? 'Surveillance activée' : 'Surveillance désactivée');
    };
    $('#dvNow').onclick = async () => { const r = await act(() => api('POST', 'dev/deploy')); if (r) toast(r.files.length ? 'Déployé : ' + r.files.join(', ') : 'Aucun .jar trouvé', !r.files.length); };
    $('#dvOpen').onclick = () => act(() => api('POST', 'open', { what: 'watch' }));
    const sys = await api('GET', 'system');
    $('#dvSys').innerHTML = `<div>RAM du PC : <b>${(sys.totalRamMb / 1024).toFixed(1)} Go</b> (libre ${(sys.freeRamMb / 1024).toFixed(1)} Go) · ${sys.cores} cœurs</div>
      <div class="dim">${esc(sys.os)}</div><div class="mt">Dossier : <span class="mono">${esc(sys.serverDir)}</span></div>
      <div class="mt">Arguments Java :</div><div class="console mt" style="max-height:130px;font-size:11.5px">${esc(sys.javaArgs)}</div>`;
  },
};

// ---- Reglages
pages.settings = {
  title: 'Réglages du panel',
  async render(v) {
    const [c, sys] = await Promise.all([api('GET', 'settings'), api('GET', 'system')]);
    const maxRam = Math.min(14336, Math.floor(sys.totalRamMb / 512) * 512 - 2048);
    v.innerHTML = `
      <div class="grid g2">
        <div class="card"><h2>${icon('mem')}Mémoire du serveur</h2>
          <div class="row between"><span class="muted">RAM allouée (Xms/Xmx)</span><b id="stRamV" style="font-size:18px"></b></div>
          <input type="range" id="stRam" min="1024" max="${maxRam}" step="512" value="${c.ramMb}">
          <div class="dim" style="font-size:12px">Ton PC a ${(sys.totalRamMb / 1024).toFixed(1)} Go. Pour du dev, 3–4 Go suffisent largement.</div>
        </div>
        <div class="card"><h2>${icon('cog')}Comportement</h2>
          <div class="toggle-row"><div><b>Redémarrage auto en cas de crash</b><small>3 tentatives max en 5 minutes</small></div><label class="switch"><input type="checkbox" id="stAuto" ${c.autoRestart ? 'checked' : ''}><span></span></label></div>
          <div class="toggle-row mt"><div><b>Démarrer le serveur à l'ouverture du panel</b><small>Lancé dès que tu ouvres l'exe</small></div><label class="switch"><input type="checkbox" id="stStart" ${c.autoStart ? 'checked' : ''}><span></span></label></div>
        </div>
        <div class="card"><h2>${icon('bug')}Débogage Java</h2>
          <div class="toggle-row"><div><b>Agent de débogage (JDWP)</b><small>Pour IntelliJ / Eclipse</small></div><label class="switch"><input type="checkbox" id="stDbg" ${c.debug ? 'checked' : ''}><span></span></label></div>
          <div class="toggle-row mt"><div><b>HotSwap avancé (JetBrains Runtime)</b><small>${sys.jbrInstalled ? 'Ajout de méthodes, champs et classes à chaud. Nécessite le débogage.' : 'JetBrains Runtime non installé'}</small></div><label class="switch"><input type="checkbox" id="stHs" ${c.hotswap ? 'checked' : ''} ${sys.jbrInstalled ? '' : 'disabled'}><span></span></label></div>
          <div class="field mt"><label>Port</label><input class="input" type="number" id="stDbgPort" value="${c.debugPort}"></div>
        </div>
        <div class="card"><h2>${icon('code')}Arguments Java supplémentaires</h2>
          <div class="field"><input class="input mono" id="stExtra" placeholder="ex : -Dmonplugin.debug=true" value="${esc(c.extraArgs)}"><span class="desc">Ajoutés avant <span class="mono">-jar paper.jar</span>.</span></div>
        </div>
      </div>
      <div class="row mt"><button class="btn green" id="stSave">${icon('save')}Enregistrer</button><span class="dim">S'applique au prochain démarrage du serveur.</span>
        <div style="flex:1"></div><button class="btn red" id="stQuit">Quitter le panel (arrête le serveur)</button></div>`;
    hydrateIcons(v);
    const upd = () => $('#stRamV').textContent = ($('#stRam').value / 1024).toFixed(1) + ' Go';
    $('#stRam').oninput = upd; upd();
    $('#stSave').onclick = () => {
      c.ramMb = +$('#stRam').value; c.autoRestart = $('#stAuto').checked; c.autoStart = $('#stStart').checked;
      c.debug = $('#stDbg').checked; c.hotswap = $('#stHs').checked; c.debugPort = +$('#stDbgPort').value || 5005; c.extraArgs = $('#stExtra').value;
      act(() => api('PUT', 'settings', c), 'Réglages enregistrés');
    };
    $('#stQuit').onclick = async () => {
      if (!await confirmBox('Quitter le panel ?', 'Le serveur sera arrêté proprement puis le panel se fermera.', 'Quitter', true)) return;
      await api('POST', 'quit'); document.body.innerHTML = '<div class="empty" style="padding-top:30vh">Panel fermé. Tu peux fermer cette fenêtre.</div>';
    };
  },
};

// ---------------------------------------------------------------- Routeur
function setActive(p) { $$('#nav a').forEach(a => a.classList.toggle('active', a.dataset.page === p)); }
let skipHash = false;
async function go() {
  if (skipHash) { skipHash = false; return; }
  route = (location.hash || '#dashboard').slice(1);
  if (!pages[route]) route = 'dashboard';
  setActive(route);
  $('#pageTitle').textContent = pages[route].title;
  const v = $('#view'); v.innerHTML = ''; v.scrollTop = 0;
  try { await pages[route].render(v); } catch (e) { v.innerHTML = `<div class="banner">${esc(e.message)}</div>`; }
}
window.addEventListener('hashchange', go);

// ---------------------------------------------------------------- Demarrage
hydrateIcons();
if (!TOKEN) {
  $('#view').innerHTML = '<div class="banner">Ouvre le panel depuis l\'exe « Minecraft Dev Server » (ou l\'icône près de l\'horloge).</div>';
} else {
  pollStatus().then(go);
  startStream();
  setInterval(pollStatus, 1500);
}
