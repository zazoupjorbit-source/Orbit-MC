'use strict';

// Lecture du guide (dossier guide/ à la racine du bot), sans Discord : utilisé
// par src/guide.js et scripts/check-guide.js. Repris du guide staff d'ORBIT ARK.
//
// Arborescence :
//   guide/<NN>-<rubrique>/_rubrique.md      en-tête de la rubrique (+ texte d'introduction)
//   guide/<NN>-<rubrique>/<NN>-<fiche>.md   une fiche
// <NN> donne l'ordre d'affichage ; l'identifiant est le reste du nom (minuscules,
// chiffres, tirets). Chaque fichier commence par un en-tête :
//   ---
//   titre: Commandes RCON du jeu
//   emoji: ⌨️
//   resume: ListPlayers, Broadcast, SaveWorld, kick et ban
//   mots: rcon, saveworld, doexit        (mots-clés de recherche, facultatif)
//   maj: 2026-10-15                       (date de mise à jour, fiches seulement)
//   couleur: #5865F2                      (rubrique seulement, facultatif)
//   ---
// Le texte est du Markdown Discord. Une fiche trop longue pour un message est
// coupée en parties (boutons ◀ ▶) entre deux paragraphes, jamais dans un bloc
// de code ; une ligne « <!-- suite --> » force une coupure.

const fs = require('fs');
const path = require('path');
const crypto = require('crypto');

const DEFAULT_ROOT = path.join(__dirname, '..', 'guide');

// Limites de Discord (menus déroulants : 25 options, libellés de 100 caractères).
const LIMITS = {
    categories: 25,
    pages: 25,
    title: 100,
    resume: 100,
    chunk: 3800,
    intro: 1500
};

const ID_PATTERN = /^[a-z0-9][a-z0-9-]{0,29}$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;
const EMOJI_PATTERN = /^(\p{Extended_Pictographic}|\p{Regional_Indicator}|[#*0-9]️?⃣)/u;
const BREAK_LINE = '<!-- suite -->';

function parseFrontMatter(text) {
    const src = String(text).replace(/^﻿/, '').replace(/\r\n/g, '\n');
    const m = src.match(/^---\n([\s\S]*?)\n---\n?/);
    if (!m) {
        return { meta: {}, body: src.trim(), hasHeader: false };
    }
    const meta = {};
    for (const line of m[1].split('\n')) {
        const kv = line.match(/^\s*([a-z_]+)\s*:\s*(.*)$/i);
        if (kv) {
            meta[kv[1].toLowerCase()] = kv[2].trim();
        }
    }
    return { meta, body: src.slice(m[0].length).trim(), hasHeader: true };
}

// Blocs = paragraphes séparés par une ligne vide, un bloc de code ``` restant entier.
function splitBlocks(body) {
    const blocks = [];
    let current = [];
    let inFence = false;
    const flush = () => {
        if (current.length) {
            blocks.push(current.join('\n'));
            current = [];
        }
    };
    for (const line of body.split('\n')) {
        if (/^\s*```/.test(line)) {
            inFence = !inFence;
        }
        if (!inFence && line.trim() === BREAK_LINE) {
            flush();
            blocks.push(BREAK_LINE);
            continue;
        }
        if (!inFence && line.trim() === '') {
            flush();
            continue;
        }
        current.push(line);
    }
    flush();
    return blocks;
}

/** Découpe un texte en parties de `max` caractères au plus. Renvoie { chunks, tooLong }. */
function splitChunks(body, max = LIMITS.chunk) {
    const chunks = [];
    let current = '';
    let tooLong = false;
    for (const block of splitBlocks(body)) {
        if (block === BREAK_LINE) {
            if (current) chunks.push(current);
            current = '';
            continue;
        }
        if (block.length > max) {
            tooLong = true;
        }
        const next = current ? `${current}\n\n${block}` : block;
        if (next.length > max && current) {
            chunks.push(current);
            current = block;
        } else {
            current = next;
        }
    }
    if (current) chunks.push(current);
    return { chunks: chunks.length ? chunks : ['*(fiche vide)*'], tooLong };
}

function splitName(name) {
    const m = name.match(/^(\d+)-(.+)$/);
    return m ? { order: Number(m[1]), id: m[2] } : { order: 9999, id: name };
}

function hash(text) {
    return crypto.createHash('sha1').update(text).digest('hex').slice(0, 12);
}

function parseWords(value) {
    return String(value || '')
        .split(/[,;]/)
        .map((w) => w.trim())
        .filter(Boolean);
}

function checkMeta(meta, where, errors, isPage) {
    if (!meta.titre) errors.push(`${where} : « titre » manquant`);
    else if (meta.titre.length > LIMITS.title) errors.push(`${where} : titre de plus de ${LIMITS.title} caractères`);
    if (!meta.emoji) errors.push(`${where} : « emoji » manquant`);
    else if (!EMOJI_PATTERN.test(meta.emoji) || meta.emoji.length > 16) {
        errors.push(`${where} : emoji « ${meta.emoji} » refusé (un seul emoji Unicode, sinon Discord rejette le menu)`);
    }
    if (!meta.resume) errors.push(`${where} : « resume » manquant`);
    else if (meta.resume.length > LIMITS.resume) errors.push(`${where} : resume de plus de ${LIMITS.resume} caractères`);
    if (isPage) {
        if (!meta.maj) errors.push(`${where} : « maj » manquant (date AAAA-MM-JJ)`);
        else if (!DATE_PATTERN.test(meta.maj)) errors.push(`${where} : maj « ${meta.maj} » au mauvais format (AAAA-MM-JJ)`);
    }
    if (meta.couleur && !/^#?[0-9a-f]{6}$/i.test(meta.couleur)) errors.push(`${where} : couleur « ${meta.couleur} » invalide (#RRGGBB)`);
}

/**
 * Charge tout le guide. Les fichiers en erreur sont décrits dans `errors` ;
 * les fiches lisibles restent utilisables (une faute ne coupe pas tout le guide).
 */
function loadGuide(root = DEFAULT_ROOT) {
    const errors = [];
    const categories = [];
    if (!fs.existsSync(root)) {
        return { categories, pages: [], errors: [`dossier ${root} introuvable`], signature: '' };
    }
    const allHashes = [];
    const dirs = fs
        .readdirSync(root, { withFileTypes: true })
        .filter((d) => d.isDirectory() && !d.name.startsWith('.'))
        .map((d) => d.name)
        .sort();
    const seenCats = new Set();
    for (const dir of dirs) {
        const { order, id } = splitName(dir);
        const where = `guide/${dir}`;
        if (!ID_PATTERN.test(id)) {
            errors.push(`${where} : identifiant « ${id} » invalide (minuscules, chiffres, tirets, 30 caractères)`);
            continue;
        }
        if (seenCats.has(id)) {
            errors.push(`${where} : rubrique « ${id} » en double`);
            continue;
        }
        seenCats.add(id);
        const headFile = path.join(root, dir, '_rubrique.md');
        if (!fs.existsSync(headFile)) {
            errors.push(`${where} : _rubrique.md manquant`);
            continue;
        }
        const headText = fs.readFileSync(headFile, 'utf8');
        allHashes.push(hash(headText));
        const head = parseFrontMatter(headText);
        checkMeta(head.meta, `${where}/_rubrique.md`, errors, false);
        if (head.body.length > LIMITS.intro) errors.push(`${where}/_rubrique.md : introduction de plus de ${LIMITS.intro} caractères`);

        const category = {
            id,
            order,
            titre: head.meta.titre || id,
            emoji: head.meta.emoji || '📁',
            resume: head.meta.resume || '',
            couleur: head.meta.couleur ? parseInt(head.meta.couleur.replace('#', ''), 16) : null,
            intro: head.body.slice(0, LIMITS.intro),
            pages: []
        };
        const files = fs
            .readdirSync(path.join(root, dir))
            .filter((f) => f.endsWith('.md') && f !== '_rubrique.md')
            .sort();
        const seenPages = new Set();
        for (const file of files) {
            const p = splitName(file.replace(/\.md$/, ''));
            const pWhere = `${where}/${file}`;
            if (!ID_PATTERN.test(p.id)) {
                errors.push(`${pWhere} : identifiant « ${p.id} » invalide`);
                continue;
            }
            if (seenPages.has(p.id)) {
                errors.push(`${pWhere} : fiche « ${p.id} » en double dans la rubrique`);
                continue;
            }
            seenPages.add(p.id);
            const text = fs.readFileSync(path.join(root, dir, file), 'utf8');
            const parsed = parseFrontMatter(text);
            if (!parsed.hasHeader) errors.push(`${pWhere} : en-tête --- manquant`);
            checkMeta(parsed.meta, pWhere, errors, true);
            const { chunks, tooLong } = splitChunks(parsed.body);
            if (tooLong) errors.push(`${pWhere} : un paragraphe ou bloc de code dépasse ${LIMITS.chunk} caractères (le couper)`);
            const pageHash = hash(text);
            allHashes.push(pageHash);
            category.pages.push({
                id: p.id,
                order: p.order,
                key: `${id}/${p.id}`,
                categoryId: id,
                titre: parsed.meta.titre || p.id,
                emoji: parsed.meta.emoji || '📄',
                resume: parsed.meta.resume || '',
                mots: parseWords(parsed.meta.mots),
                maj: DATE_PATTERN.test(parsed.meta.maj || '') ? parsed.meta.maj : null,
                body: parsed.body,
                chunks: chunks.map((c) => (c.length > 4000 ? c.slice(0, 3990) + '…' : c)),
                hash: pageHash,
                file: pWhere
            });
        }
        if (category.pages.length > LIMITS.pages) {
            errors.push(`${where} : ${category.pages.length} fiches, ${LIMITS.pages} au plus (menu Discord) : créer une autre rubrique`);
            category.pages = category.pages.slice(0, LIMITS.pages);
        }
        categories.push(category);
    }
    if (categories.length > LIMITS.categories) {
        errors.push(`${categories.length} rubriques, ${LIMITS.categories} au plus (menu Discord)`);
        categories.length = LIMITS.categories;
    }
    const pages = categories.flatMap((c) => c.pages);
    return { categories, pages, errors, signature: hash(allHashes.join('|')) };
}

function normalize(text) {
    return String(text || '')
        .normalize('NFD')
        .replace(/[̀-ͯ]/g, '')
        .toLowerCase();
}

/** Recherche plein texte : renvoie [{ page, score }] triés, au plus `limit`. */
function search(guide, query, limit = 25) {
    const tokens = normalize(query)
        .split(/[^a-z0-9._/-]+/)
        .filter((t) => t.length >= 2);
    if (!tokens.length) return [];
    const results = [];
    for (const page of guide.pages) {
        const title = normalize(page.titre);
        const words = normalize(page.mots.join(' '));
        const resume = normalize(page.resume);
        const body = normalize(page.body);
        let score = 0;
        let matched = 0;
        for (const t of tokens) {
            let s = 0;
            if (title.includes(t)) s += 10;
            if (words.includes(t)) s += 6;
            if (resume.includes(t)) s += 3;
            const count = body.split(t).length - 1;
            s += Math.min(count, 5);
            if (s > 0) matched++;
            score += s;
        }
        if (matched) results.push({ page, score: score + matched * 20 });
    }
    return results.sort((a, b) => b.score - a.score || a.page.titre.localeCompare(b.page.titre)).slice(0, limit);
}

function findCategory(guide, id) {
    return guide.categories.find((c) => c.id === id) || null;
}

function findPage(guide, key) {
    return guide.pages.find((p) => p.key === key) || null;
}

/** Fiches les plus récemment mises à jour (date `maj`). */
function recentPages(guide, limit = 10) {
    return guide.pages
        .filter((p) => p.maj)
        .sort((a, b) => b.maj.localeCompare(a.maj) || a.titre.localeCompare(b.titre))
        .slice(0, limit);
}

function formatDate(iso) {
    if (!iso) return '?';
    const [y, m, d] = iso.split('-');
    return `${d}/${m}/${y}`;
}

module.exports = {
    DEFAULT_ROOT,
    LIMITS,
    parseFrontMatter,
    splitChunks,
    loadGuide,
    search,
    findCategory,
    findPage,
    recentPages,
    formatDate
};
