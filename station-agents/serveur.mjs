// La Station des agents : node serveur.mjs  →  http://127.0.0.1:8777
// Lit en local ce que font les sessions Claude Code de ce PC (dossier ~/.claude) et le sert à la page.
// Rien ne sort de la machine : écoute sur 127.0.0.1 seulement, lecture seule.
//   /            → index.html (la station)
//   /api/etat    → l'état des agents en JSON (+ univers, + résumé du QG ORBIT)
//   /qg          → ouvre le QG ORBIT (le lance s'il est éteint), puis y redirige
import { createServer, get as httpGet } from "node:http";
import { readFile, readdir, stat, open } from "node:fs/promises";
import { existsSync } from "node:fs";
import { spawn } from "node:child_process";
import { homedir } from "node:os";
import { join, dirname, resolve } from "node:path";
import { StringDecoder } from "node:string_decoder";
import { fileURLToPath } from "node:url";

const PORT = Number(process.env.STATION_PORT) || 8777;
const CLAUDE = process.env.CLAUDE_CONFIG_DIR || join(homedir(), ".claude");
const ICI = dirname(fileURLToPath(import.meta.url));

const PREMIERE_LECTURE = 64_000_000; // au plus, octets relus à la fin d'un journal quand on le découvre
const MORCEAU = 4_000_000;

// ---------- Réglages (config.json, facultatif) ----------
let config = { modules: {}, univers: [], qg: null, reposApresMin: 20, attenteMaxMin: 180 };
async function chargerConfig() {
  try {
    const c = JSON.parse(await readFile(join(ICI, "config.json"), "utf8"));
    config = { ...config, ...c, modules: { ...(c.modules || {}) }, univers: Array.isArray(c.univers) ? c.univers : [] };
  } catch { /* pas de config : valeurs par défaut */ }
}

// L'univers d'une session : le premier dont un nom de dossier apparaît dans son chemin.
function universDe(cwd) {
  const morceaux = String(cwd || "").toLowerCase().split(/[\\/]/);
  return config.univers.find((u) => (u.dossiers || []).some((d) => morceaux.includes(String(d).toLowerCase()))) || null;
}

// Les rôles : une compétence orbit-… utilisée récemment fait un spécialiste ; sinon l'activité dominante.
const COMPETENCES = { "orbit-sav-ark": "sav", "orbit-go-prod-site": "prod", "orbit-annonce-discord": "annonce",
  "orbit-couple-boutique": "boutique", "orbit-plugin-mc": "plugin" };
const METIER = { code: "ingenierie", lit: "recherche", cherche: "recherche", commande: "operations", web: "web", design: "design", drone: "commandement" };
function roleDe(e) {
  if (e.competence && e.total - e.competenceA < 150) return { cle: e.competence.role, via: "compétence " + e.competence.nom };
  let mieux = null, max = 0;
  for (const [g, v] of Object.entries(e.compte)) if (v > max) { max = v; mieux = g; }
  if (!mieux || e.total < 3) return { cle: "ingenierie", via: "nouvelle recrue" };
  return { cle: METIER[mieux] || "ingenierie", via: "activité" };
}

// ---------- Suivi d'un journal de session ----------
const suivis = new Map(); // sessionId → suivi

function nouvelEtat() {
  return {
    tourDebut: 0, outils: 0, activite: { genre: "veille", texte: "" },
    questionId: null, question: null, demande: null, recap: null, plan: null,
    compte: {}, total: 0, competence: null, competenceA: 0, // pour le rôle
  };
}

function noterCompetence(e, nom) {
  const court = String(nom || "").split(":").pop();
  if (COMPETENCES[court]) { e.competence = { nom: court, role: COMPETENCES[court] }; e.competenceA = e.total; }
}

// Chaque outil compte pour le rôle ; les anciens comptent de moins en moins (l'activité récente l'emporte).
function compter(e, nom, inp, genre) {
  const ext = String(inp.file_path || "").toLowerCase().split(".").pop();
  let g = genre;
  if (nom === "Artifact" || /show_widget|meshy|canvas/i.test(nom) || ((nom === "Edit" || nom === "Write") && ["html", "css", "svg", "png"].includes(ext))) g = "design";
  if (!METIER[g]) return;
  for (const k in e.compte) e.compte[k] *= 0.97;
  e.compte[g] = (e.compte[g] || 0) + 1; e.total++;
}

const court = (s, n = 64) => { s = String(s ?? "").replace(/\s+/g, " ").trim(); return s.length > n ? s.slice(0, n - 1) + "…" : s; };
const fichier = (p) => String(p ?? "").split(/[\\/]/).pop() || "";

function decrire(nom, e = {}) {
  if (nom === "Read") return { genre: "lit", texte: "lit " + fichier(e.file_path) };
  if (nom === "Edit" || nom === "Write" || nom === "NotebookEdit")
    return { genre: "code", texte: "écrit " + fichier(e.file_path || e.notebook_path) };
  if (nom === "Grep" || nom === "Glob") return { genre: "cherche", texte: "cherche « " + court(e.pattern, 30) + " »" };
  if (nom === "ToolSearch") return { genre: "cherche", texte: "prépare ses outils" };
  if (nom === "Bash" || nom === "PowerShell") return { genre: "commande", texte: court(e.description || "lance une commande") };
  if (nom === "Agent" || nom === "Task") return { genre: "drone", texte: "envoie un drone : " + court(e.description, 40) };
  if (nom === "WebSearch" || nom === "WebFetch") return { genre: "web", texte: "cherche sur le web" };
  if (nom === "AskUserQuestion") return { genre: "question", texte: "te pose une question" };
  if (nom === "Artifact") return { genre: "code", texte: "publie une page" };
  if (nom === "Skill") return { genre: "lit", texte: "ouvre un manuel : " + court(e.skill, 30) };
  if (nom === "TodoWrite" || nom.endsWith("update_plan")) return { genre: "plan", texte: "met son plan à jour" };
  if (nom.endsWith("wrap_up")) return { genre: "redige", texte: "fait son compte rendu" };
  if (nom.includes("Browser") || nom.includes("chrome")) return { genre: "web", texte: "navigue dans le navigateur" };
  if (nom.includes("computer-use")) return { genre: "web", texte: "pilote l'écran" };
  if (nom.includes("meshy")) return { genre: "commande", texte: "modélise en 3D" };
  if (nom.includes("terminal")) return { genre: "commande", texte: "regarde le terminal" };
  const propre = nom.replace(/^mcp__[^_]+(?:_[^_]+)*?__/, "").replace(/_/g, " ");
  return { genre: "commande", texte: court("utilise " + propre, 48) };
}

function majPlan(e, inp) {
  const liste = Array.isArray(inp.items) ? inp.items : Array.isArray(inp.todos) ? inp.todos : null;
  if (liste) e.plan = liste.map((i) => ({ t: court(i.content, 80), s: i.status || "pending" }));
  if (!e.plan) return;
  if (inp.advance) {
    const i = e.plan.findIndex((p) => p.s === "in_progress"); if (i >= 0) e.plan[i].s = "completed";
    const j = e.plan.findIndex((p) => p.s === "pending"); if (j >= 0) e.plan[j].s = "in_progress";
  }
  if (Array.isArray(inp.done)) for (const n of inp.done) if (e.plan[n - 1]) e.plan[n - 1].s = "completed";
  if (inp.start && e.plan[inp.start - 1]) e.plan[inp.start - 1].s = "in_progress";
}

function traiterLigne(e, ligne) {
  if (ligne.length < 2) return;
  const tete = ligne.slice(0, 48);
  if (tete.includes('"type":"api-request') || tete.includes('"type":"data-flow')) return; // gros blocs sans intérêt
  let o; try { o = JSON.parse(ligne); } catch { return; }
  if (!o || o.isSidechain) return;
  const ts = o.timestamp ? Date.parse(o.timestamp) : 0;

  if (o.type === "assistant") {
    for (const b of o.message?.content || []) {
      if (b.type === "thinking") e.activite = { genre: "reflechit", texte: "réfléchit" };
      else if (b.type === "text") e.activite = { genre: "redige", texte: "rédige sa réponse" };
      else if (b.type === "tool_use") {
        e.outils++;
        e.activite = decrire(b.name || "", b.input || {});
        compter(e, b.name || "", b.input || {}, e.activite.genre);
        if (b.name === "Skill") noterCompetence(e, b.input?.skill);
        if (b.name === "AskUserQuestion") { e.questionId = b.id; e.question = court(b.input?.questions?.[0]?.question, 140); }
        else if (b.name === "TodoWrite" || (b.name || "").endsWith("update_plan")) majPlan(e, b.input || {});
        else if ((b.name || "").endsWith("wrap_up")) {
          e.demande = b.input?.ask ? court(b.input.ask, 140) : null;
          e.recap = b.input?.recap ? court(b.input.recap, 320) : null;
          if (e.plan) for (const p of e.plan) p.s = "completed";
        }
      }
    }
  } else if (o.type === "user") {
    const c = o.message?.content;
    const resultats = Array.isArray(c) ? c.filter((b) => b.type === "tool_result") : [];
    if (resultats.length) {
      if (e.questionId && resultats.some((b) => b.tool_use_id === e.questionId)) { e.questionId = null; e.question = null; }
    } else if (!o.isMeta) {
      const cmd = typeof c === "string" && c.match(/<command-name>\/?([\w:.-]+)<\/command-name>/); // /orbit-… tapé par Zazou
      if (cmd) noterCompetence(e, cmd[1]);
      // un nouveau message : le tour commence
      e.tourDebut = ts; e.outils = 0; e.demande = null; e.recap = null; e.questionId = null; e.question = null;
      e.activite = { genre: "reflechit", texte: "réfléchit" };
    }
  }
}

async function lireSuite(s) {
  let st; try { st = await stat(s.chemin); } catch { return; }
  if (st.size < s.offset) { s.offset = 0; s.reste = ""; s.etat = nouvelEtat(); s.premier = true; s.dec = new StringDecoder("utf8"); }
  if (st.size === s.offset) return;
  let pos = s.offset, tronque = false;
  if (s.premier && st.size > PREMIERE_LECTURE) { pos = st.size - PREMIERE_LECTURE; tronque = true; }
  const buf = Buffer.alloc(Math.min(MORCEAU, st.size - pos));
  const fh = await open(s.chemin, "r");
  try {
    while (pos < st.size) { // par morceaux : un journal peut peser des dizaines de Mo
      const { bytesRead } = await fh.read(buf, 0, Math.min(buf.length, st.size - pos), pos);
      if (!bytesRead) break;
      pos += bytesRead;
      const lignes = (s.reste + s.dec.write(buf.subarray(0, bytesRead))).split("\n");
      s.reste = lignes.pop();
      if (tronque) { if (lignes.length) { lignes.shift(); tronque = false; } else s.reste = ""; } // première ligne coupée en deux
      for (const l of lignes) traiterLigne(s.etat, l);
    }
  } finally { await fh.close(); }
  s.offset = pos; s.premier = false;
}

// ---------- Où sont les journaux ----------
const index = new Map(); // sessionId → chemin du .jsonl
let indexLe = 0;
async function reindexer() {
  if (Date.now() - indexLe < 5000) return;
  indexLe = Date.now();
  const racine = join(CLAUDE, "projects");
  let dossiers = []; try { dossiers = await readdir(racine, { withFileTypes: true }); } catch { return; }
  for (const d of dossiers) {
    if (!d.isDirectory()) continue;
    let noms = []; try { noms = await readdir(join(racine, d.name)); } catch { continue; }
    for (const n of noms) if (n.endsWith(".jsonl")) index.set(n.slice(0, -6), join(racine, d.name, n));
  }
}

function vivant(pid) {
  try { process.kill(pid, 0); return true; } catch (e) { return e.code === "EPERM"; }
}

async function dronesDe(chemin, id) {
  const dir = join(dirname(chemin), id, "subagents");
  let noms = []; try { noms = await readdir(dir); } catch { return []; }
  const out = [], limite = Date.now() - 10 * 60_000;
  for (const n of noms) {
    if (!n.endsWith(".meta.json")) continue;
    try {
      const m = JSON.parse(await readFile(join(dir, n), "utf8"));
      if (m.finished) continue;
      const st = await stat(join(dir, n.replace(".meta.json", ".jsonl")));
      if (st.mtimeMs < limite) continue;
      out.push({ id: n.slice(0, -10), texte: court(m.description || m.agentType || "sous-agent", 60), type: String(m.agentType || "") });
    } catch { /* fichier en cours d'écriture */ }
  }
  return out;
}

// ---------- L'état complet ----------
async function calculer() {
  await chargerConfig();
  const maintenant = Date.now();
  const dir = join(CLAUDE, "sessions");
  let noms = []; try { noms = await readdir(dir); } catch { /* aucun registre */ }
  const sessions = new Map();
  for (const n of noms) {
    if (!/^\d+\.json$/.test(n)) continue; // jamais les fichiers .key
    let o; try { o = JSON.parse(await readFile(join(dir, n), "utf8")); } catch { continue; }
    if (!o?.sessionId || !vivant(o.pid)) continue;
    const deja = sessions.get(o.sessionId);
    if (!deja || (o.updatedAt || 0) > (deja.updatedAt || 0)) sessions.set(o.sessionId, o);
  }

  const agents = [];
  for (const [id, o] of sessions) {
    if (!index.has(id)) await reindexer();
    const chemin = index.get(id);
    let s = suivis.get(id);
    if (chemin && !s) { s = { chemin, offset: 0, reste: "", premier: true, etat: nouvelEtat(), dec: new StringDecoder("utf8") }; suivis.set(id, s); }
    if (s) await lireSuite(s);
    const e = s?.etat || nouvelEtat();

    const occupe = o.status === "busy";
    const depuis = o.statusUpdatedAt || o.updatedAt || o.startedAt || maintenant;
    const inactif = maintenant - depuis;
    let phase;
    const bloque = /wait|block|permission|approv/i.test(String(o.status || "")); // au cas où le registre signale une attente
    if (bloque) { phase = "attend"; if (!e.demande) e.demande = "Attend ton accord pour continuer."; }
    else if (occupe) phase = e.questionId ? "question" : "travaille";
    else if (e.demande || e.questionId) phase = inactif < config.attenteMaxMin * 60_000 ? "attend" : "repos";
    else phase = inactif < config.reposApresMin * 60_000 && e.tourDebut ? "fini" : "repos";

    const dossier = fichier(o.cwd), u = universDe(o.cwd);
    const enCours = e.plan?.find((p) => p.s === "in_progress");
    agents.push({
      id, nom: court(o.name || "Session sans nom", 60), dossier,
      univers: u?.cle || null, role: roleDe(e),
      projet: u?.nom || config.modules[dossier] || (/^scratch-/i.test(dossier) ? "ATELIER" : dossier.toUpperCase()), // brouillons de l'appli Claude
      phase, depuis, debut: o.startedAt || 0,
      activite: occupe ? e.activite : { genre: "veille", texte: "" },
      tourDebut: e.tourDebut, outils: e.outils,
      plan: e.plan ? { fait: e.plan.filter((p) => p.s === "completed").length, total: e.plan.length, etape: enCours?.t || null } : null,
      demande: e.demande || e.question || null, recap: e.recap,
      drones: occupe && chemin ? await dronesDe(chemin, id) : [],
    });
  }
  for (const id of suivis.keys()) if (!sessions.has(id)) suivis.delete(id);
  agents.sort((a, b) => a.debut - b.debut);
  const univers = config.univers.map(({ cle, nom, court: c, couleur, salle, planete }) => ({ cle, nom, court: c || nom, couleur, salle: !!salle, planete: planete !== false }));
  return { maintenant, agents, univers, qg: await resumeQg() };
}

// ---------- QG ORBIT (panel perso, lu en lecture seule) ----------
const QG_ADRESSE = "http://127.0.0.1:4410";
function dossierQg() {
  if (!config.qg) return null;
  const d = String(config.qg).replace(/^~(?=$|[\\/])/, homedir());
  return existsSync(join(d, "server.mjs")) ? resolve(d) : null;
}
function qgRepond() {
  return new Promise((ok) => {
    const r = httpGet(QG_ADRESSE + "/api/data", { timeout: 600 }, (res) => { res.resume(); ok(res.statusCode === 200); });
    r.on("timeout", () => { r.destroy(); ok(false); }); r.on("error", () => ok(false));
  });
}
let qgCache = null, qgLe = 0;
async function resumeQg() {
  if (qgCache && Date.now() - qgLe < 10_000) return qgCache;
  const d = dossierQg();
  if (!d) return (qgCache = null);
  const r = { enLigne: await qgRepond(), projets: {}, objectifs: { ouverts: 0, haute: 0, prochains: [] }, agenda: null };
  try {
    const q = JSON.parse(await readFile(join(d, "data", "qg.json"), "utf8"));
    for (const p of q.projets || []) {
      const k = String(p.projet || "autre"), s = String(p.statut || "autre");
      (r.projets[k] ||= {})[s] = (r.projets[k][s] || 0) + 1;
    }
    const ouvertes = (q.objectifs?.taches || []).filter((t) => !t.fait);
    const rang = { haute: 0, normale: 1, basse: 2 };
    r.objectifs.ouverts = ouvertes.length;
    r.objectifs.haute = ouvertes.filter((t) => t.prio === "haute").length;
    r.objectifs.prochains = ouvertes.sort((a, b) => (rang[a.prio] ?? 1) - (rang[b.prio] ?? 1) || (a.cree || 0) - (b.cree || 0))
      .slice(0, 3).map((t) => ({ txt: court(t.txt, 90), cat: t.cat || "", prio: t.prio || "normale" }));
    const auj = new Date().toISOString().slice(0, 10);
    const a = (q.agenda || []).filter((x) => (x.date || "") >= auj).sort((x, y) => (x.date + x.heure).localeCompare(y.date + y.heure))[0];
    if (a) r.agenda = { date: a.date, heure: a.heure || "", titre: court(a.titre, 80) };
  } catch { /* fichier absent ou en cours d'écriture */ }
  qgCache = r; qgLe = Date.now();
  return r;
}
async function ouvrirQg() {
  if (await qgRepond()) return true;
  const d = dossierQg(); if (!d) return false;
  spawn(process.execPath, ["server.mjs"], { cwd: d, detached: true, stdio: "ignore", windowsHide: true }).unref();
  for (let i = 0; i < 20; i++) { await new Promise((ok) => setTimeout(ok, 250)); if (await qgRepond()) { qgLe = 0; return true; } }
  return false;
}

let cache = null, cacheLe = 0, enCours = null;
async function etat() {
  if (cache && Date.now() - cacheLe < 800) return cache;
  if (!enCours) enCours = calculer().then((r) => { cache = r; cacheLe = Date.now(); return r; }).finally(() => { enCours = null; });
  return enCours;
}

// ---------- Serveur ----------
createServer(async (req, res) => {
  // seulement depuis ce PC (protège aussi d'une page web qui viserait le port)
  const hote = (req.headers.host || "").split(":")[0];
  if (hote !== "127.0.0.1" && hote !== "localhost") { res.writeHead(403); return res.end(); }
  const { pathname } = new URL(req.url, "http://x");
  try {
    if (pathname === "/api/etat") {
      const corps = JSON.stringify(await etat());
      res.writeHead(200, { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store" });
      return res.end(corps);
    }
    if (pathname === "/qg") {
      // lance un programme : jamais depuis un autre site (le navigateur signale l'origine dans Sec-Fetch-Site)
      const site = req.headers["sec-fetch-site"];
      if (site && site !== "none" && site !== "same-origin") { res.writeHead(403); return res.end(); }
      if (await ouvrirQg()) { res.writeHead(302, { Location: QG_ADRESSE }); return res.end(); }
      res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" });
      return res.end("QG ORBIT introuvable ou ne démarre pas (réglage « qg » dans station-agents/config.json).");
    }
    if (pathname === "/" || pathname === "/index.html") {
      const buf = await readFile(join(ICI, "index.html"));
      res.writeHead(200, { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-store" });
      return res.end(buf);
    }
    res.writeHead(404, { "Content-Type": "text/plain; charset=utf-8" }); res.end("Introuvable");
  } catch (err) {
    res.writeHead(500, { "Content-Type": "text/plain; charset=utf-8" }); res.end("Erreur : " + err.message);
  }
}).listen(PORT, "127.0.0.1", () => console.log("La Station des agents : http://127.0.0.1:" + PORT));
