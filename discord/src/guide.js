// Guide du staff ORBIT MC : plugins, commandes admin et joueurs, procédures.
// Même format que le guide staff d'ORBIT ARK (OrbitBot, modules_js/guide.js).
//
// Le contenu n'est pas dans ce fichier : une fiche Markdown par sujet dans
// discord/guide/ (format en tête de src/guideLib.js). Les fiches sont relues à
// chaque ouverture du guide : une fiche ajoutée ou corrigée est en ligne sans
// redémarrer le bot. Le panneau permanent est réédité tout seul quand le
// contenu change (vérifié toutes les `refreshMinutes`).
// Navigation : panneau dans le salon du guide (menu des rubriques) → réponses
// privées où l'on passe de rubrique en fiche par des menus déroulants.
const fs = require('fs');
const path = require('path');
const {
  Events, ChannelType, PermissionFlagsBits: P, MessageFlags, EmbedBuilder, ActionRowBuilder,
  ButtonBuilder, ButtonStyle, StringSelectMenuBuilder, ModalBuilder, TextInputBuilder, TextInputStyle,
  SlashCommandBuilder,
} = require('discord.js');
const lib = require('./guideLib');
const { saveState: saveBotState } = require('./common');

const PREFIX = 'guide:';
const DEFAULT_COLOR = 0x3a7fff; // bleu ORBIT MC
const STATE_FILE = path.join(__dirname, '..', 'data', 'guide-state.json');
// Rôles qui ouvrent le guide (en plus de la permission Administrateur).
const GUIDE_ROLES = ['fondateur', 'admin', 'dev', 'modo', 'support'];
const CHANNEL_NAME = '📖・guide';

const cut = (str, max) => {
  const s = String(str || '');
  return s.length <= max ? s : `${s.slice(0, max - 1)}…`;
};

function loadState() {
  try {
    return JSON.parse(fs.readFileSync(STATE_FILE, 'utf8'));
  } catch {
    return {};
  }
}

function saveState(s) {
  fs.mkdirSync(path.dirname(STATE_FILE), { recursive: true });
  fs.writeFileSync(STATE_FILE, JSON.stringify(s, null, 2));
}

/**
 * Branche le guide sur le bot. À appeler une fois, dans ClientReady.
 * @param {import('discord.js').Client} client
 * @param {{ guild: import('discord.js').Guild, botState: object, cfg: object }} opts
 */
async function setupGuide(client, { guild, botState, cfg }) {
  const gcfg = cfg.guide || {};
  if (gcfg.enabled === false) {
    console.log('ℹ️  Guide du staff désactivé (config.json → guide.enabled).');
    return;
  }
  const refreshMs = Math.max(1, Number(gcfg.refreshMinutes) || 5) * 60_000;
  const updatesKey = gcfg.updatesChannel === undefined ? 'staff_general' : gcfg.updatesChannel;

  let guide = lib.loadGuide();
  let lastErrors = '';
  function reload() {
    guide = lib.loadGuide();
    const errText = guide.errors.join('\n');
    if (errText && errText !== lastErrors) {
      console.warn(`⚠️  Guide : ${guide.errors.length} erreur(s) dans les fiches :\n${errText}`);
    }
    lastErrors = errText;
    return guide;
  }
  reload();

  const staffRoleIds = () => GUIDE_ROLES.map((k) => botState.roles[k]).filter(Boolean);
  function isAllowed(member) {
    if (!member?.permissions) return false;
    if (member.permissions.has(P.Administrator)) return true;
    return staffRoleIds().some((id) => member.roles.cache.has(id));
  }

  // ─── Vues ───────────────────────────────────────────────────────────
  const colorOf = (category) => category?.couleur || DEFAULT_COLOR;

  function categorySelect(currentId) {
    return new ActionRowBuilder().addComponents(
      new StringSelectMenuBuilder()
        .setCustomId(`${PREFIX}cat`)
        .setPlaceholder('📚 Choisir une rubrique…')
        .addOptions(guide.categories.map((c) => ({
          label: cut(c.titre, 100),
          value: c.id,
          description: cut(c.resume, 100) || undefined,
          emoji: c.emoji,
          default: c.id === currentId,
        }))),
    );
  }

  function pageSelect(pages, currentKey, placeholder) {
    return new ActionRowBuilder().addComponents(
      new StringSelectMenuBuilder()
        .setCustomId(`${PREFIX}page`)
        .setPlaceholder(placeholder || '📄 Ouvrir une fiche…')
        .addOptions(pages.slice(0, 25).map((p) => ({
          label: cut(p.titre, 100),
          value: p.key,
          description: cut(p.resume, 100) || undefined,
          emoji: p.emoji,
          default: p.key === currentKey,
        }))),
    );
  }

  function toolButtons(extra = []) {
    return new ActionRowBuilder().addComponents(
      ...extra,
      new ButtonBuilder().setCustomId(`${PREFIX}home`).setLabel('Accueil').setEmoji('🏠').setStyle(ButtonStyle.Secondary),
      new ButtonBuilder().setCustomId(`${PREFIX}search`).setLabel('Rechercher').setEmoji('🔎').setStyle(ButtonStyle.Primary),
      new ButtonBuilder().setCustomId(`${PREFIX}news`).setLabel('Nouveautés').setEmoji('🆕').setStyle(ButtonStyle.Secondary),
    );
  }

  const recentLines = (limit) => lib.recentPages(guide, limit)
    .map((p) => `${p.emoji} **${p.titre}** · ${lib.formatDate(p.maj)}`)
    .join('\n');

  function homeEmbed(forPanel) {
    const embed = new EmbedBuilder()
      .setColor(DEFAULT_COLOR)
      .setTitle('📖 Guide ORBIT MC — staff')
      .setDescription(
        'Tout pour gérer le réseau Minecraft : **plugins ORBIT**, **commandes admin et joueurs**, **outils du staff**, **procédures**.\n\n'
        + '**Mode d’emploi** : choisis une **rubrique** dans le menu, puis une **fiche**. '
        + 'Les réponses ne sont visibles **que par toi**. 🔎 cherche un mot (ex. `ban`, `continent`, `monnaie`), '
        + 'la commande `/guide` fait pareil depuis n’importe quel salon.',
      );
    const fields = guide.categories.map((c) => ({
      name: cut(`${c.emoji} ${c.titre}`, 256),
      value: cut(`${c.resume}\n*${c.pages.length} fiche${c.pages.length > 1 ? 's' : ''}*`, 1024),
      inline: true,
    }));
    const recent = recentLines(5);
    if (recent) fields.push({ name: '🆕 Mis à jour récemment', value: cut(recent, 1024) });
    if (!forPanel && guide.errors.length) {
      fields.push({
        name: '⚠️ Fiches à corriger',
        value: cut(`${guide.errors.length} erreur(s) dans le dossier guide/ (détail : console du bot, ou \`npm run check-guide\`).`, 1024),
      });
    }
    embed.addFields(fields.slice(0, 25));
    embed.setFooter({ text: `${guide.pages.length} fiches · ${guide.categories.length} rubriques · réservé au staff ORBIT MC` });
    return embed;
  }

  function emptyView() {
    return {
      embeds: [new EmbedBuilder().setColor(0xe5484d).setTitle('📖 Guide ORBIT MC')
        .setDescription('Le guide est vide ou illisible (dossier `discord/guide/`). Voir la console du bot.')],
      components: [],
    };
  }

  function homeView(forPanel = false) {
    if (!guide.categories.length) return emptyView();
    return {
      embeds: [homeEmbed(forPanel)],
      components: [
        categorySelect(null),
        forPanel
          ? new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId(`${PREFIX}search`).setLabel('Rechercher').setEmoji('🔎').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId(`${PREFIX}news`).setLabel('Nouveautés').setEmoji('🆕').setStyle(ButtonStyle.Secondary),
          )
          : toolButtons(),
      ],
    };
  }

  function categoryView(category) {
    const lines = category.pages.map((p) => `${p.emoji} **${p.titre}** — ${p.resume}`);
    const intro = category.intro ? `${category.intro}\n\n` : '';
    const embed = new EmbedBuilder()
      .setColor(colorOf(category))
      .setTitle(cut(`${category.emoji} ${category.titre}`, 256))
      .setDescription(cut(`${intro}${lines.join('\n') || '*Aucune fiche pour l’instant.*'}`, 4096))
      .setFooter({ text: `${category.pages.length} fiche(s) · choisis-en une dans le menu` });
    const components = [];
    if (category.pages.length) components.push(pageSelect(category.pages, null));
    components.push(categorySelect(category.id), toolButtons());
    return { embeds: [embed], components };
  }

  function pageView(page, part) {
    const category = lib.findCategory(guide, page.categoryId);
    const total = page.chunks.length;
    const index = Math.min(Math.max(0, part || 0), total - 1);
    const footer = [
      category ? `${category.emoji} ${category.titre}` : null,
      total > 1 ? `partie ${index + 1}/${total}` : null,
      page.maj ? `mise à jour le ${lib.formatDate(page.maj)}` : null,
    ].filter(Boolean).join(' · ');
    const embed = new EmbedBuilder()
      .setColor(colorOf(category))
      .setTitle(cut(`${page.emoji} ${page.titre}`, 256))
      .setDescription(page.chunks[index])
      .setFooter({ text: cut(footer, 2048) });
    const nav = [];
    if (total > 1) {
      nav.push(
        new ButtonBuilder().setCustomId(`${PREFIX}nav:${page.key}:${index - 1}`).setEmoji('◀️')
          .setStyle(ButtonStyle.Secondary).setDisabled(index === 0),
        new ButtonBuilder().setCustomId(`${PREFIX}nav:${page.key}:${index + 1}`).setEmoji('▶️')
          .setStyle(ButtonStyle.Secondary).setDisabled(index >= total - 1),
      );
    }
    const components = [];
    if (category) components.push(pageSelect(category.pages, page.key));
    // Flèches + 3 boutons = 5, le maximum d'une rangée.
    components.push(categorySelect(page.categoryId), toolButtons(nav));
    return { embeds: [embed], components };
  }

  function listView(title, description, pages, emptyText) {
    const lines = pages.map((p) => {
      const category = lib.findCategory(guide, p.categoryId);
      return `${p.emoji} **${p.titre}** · *${category ? category.titre : ''}*\n${p.resume}`;
    });
    const embed = new EmbedBuilder()
      .setColor(DEFAULT_COLOR)
      .setTitle(cut(title, 256))
      .setDescription(cut(`${description ? `${description}\n\n` : ''}${lines.join('\n\n') || emptyText}`, 4096));
    const components = [];
    if (pages.length) components.push(pageSelect(pages, null, '📄 Ouvrir un résultat…'));
    components.push(categorySelect(null), toolButtons());
    return { embeds: [embed], components };
  }

  function searchView(query) {
    const results = lib.search(guide, query, 25);
    return listView(
      `🔎 Recherche : « ${cut(query, 80)} »`,
      results.length ? `${results.length} fiche(s) trouvée(s), la plus pertinente en premier.` : '',
      results.map((r) => r.page).slice(0, 10),
      'Aucune fiche ne contient ce mot. Essaie un mot plus court (`ban`, `kit`, `monnaie`…) ou parcours les rubriques.',
    );
  }

  function newsView() {
    return listView(
      '🆕 Dernières mises à jour du guide',
      'Fiches classées de la plus récente à la plus ancienne (date de mise à jour).',
      lib.recentPages(guide, 15).slice(0, 10),
      '*Aucune date de mise à jour renseignée.*',
    );
  }

  function searchModal() {
    return new ModalBuilder()
      .setCustomId(`${PREFIX}searchmodal`)
      .setTitle('Rechercher dans le guide')
      .addComponents(new ActionRowBuilder().addComponents(
        new TextInputBuilder()
          .setCustomId('q')
          .setLabel('Mot-clé ou commande')
          .setPlaceholder('ex : ban, continent, tycoon monnaie, station')
          .setStyle(TextInputStyle.Short)
          .setRequired(true)
          .setMaxLength(80),
      ));
  }

  // ─── Salon et panneau permanent ─────────────────────────────────────
  // Le salon 📖・guide est dans la catégorie STAFF (aussi déclaré dans structure.js).
  // S'il n'existe pas encore, le bot le crée : lecture seule, visible par le staff.
  async function ensureChannel() {
    const key = gcfg.channel || 'staff_guide';
    let channel = guild.channels.cache.get(botState.channels[key]);
    if (!channel) channel = guild.channels.cache.find((c) => c.name === CHANNEL_NAME && c.type === ChannelType.GuildText);
    if (!channel) {
      const parent = botState.channels.cat_staff;
      const view = [P.ViewChannel, P.ReadMessageHistory];
      channel = await guild.channels.create({
        name: CHANNEL_NAME,
        type: ChannelType.GuildText,
        parent: parent || undefined,
        topic: 'Guide du staff ORBIT MC : plugins, commandes admin, procédures. Réponses privées.',
        permissionOverwrites: [
          { id: guild.roles.everyone.id, deny: [P.ViewChannel, P.SendMessages] },
          ...staffRoleIds().map((id) => ({ id, allow: view, deny: [P.SendMessages, P.AddReactions] })),
          { id: client.user.id, allow: [...view, P.SendMessages, P.EmbedLinks] },
        ],
        reason: 'Guide du staff ORBIT MC',
      });
      console.log(`📖 Salon ${CHANNEL_NAME} créé dans la catégorie STAFF.`);
    }
    if (botState.channels[key] !== channel.id) {
      botState.channels[key] = channel.id;
      saveBotState(botState);
    }
    return channel;
  }

  async function ensurePanel() {
    const channel = await ensureChannel();
    const payload = homeView(true);
    const s = loadState();
    if (s.channelId === channel.id && s.messageId) {
      const msg = await channel.messages.fetch(s.messageId).catch(() => null);
      if (msg) {
        await msg.edit(payload);
        saveState({ ...loadState(), panelSignature: guide.signature });
        return;
      }
      console.warn('⚠️  Ancien panneau du guide introuvable : nouveau message.');
    }
    const msg = await channel.send(payload);
    saveState({ ...loadState(), channelId: channel.id, messageId: msg.id, panelSignature: guide.signature });
    console.log(`📖 Panneau du guide posté dans #${channel.name}.`);
  }

  // Fiches nouvelles ou dont la date « maj » a changé depuis le dernier passage.
  // Premier passage (aucun état) : enregistrement silencieux.
  async function announceUpdates() {
    const s = loadState();
    const known = s.pages || null;
    const current = {};
    for (const p of guide.pages) current[p.key] = p.maj || '';
    saveState({ ...s, pages: current });
    const updatesId = updatesKey ? botState.channels[updatesKey] || updatesKey : '';
    if (!known || !updatesId) return;
    const added = guide.pages.filter((p) => !(p.key in known));
    const changed = guide.pages.filter((p) => p.key in known && known[p.key] !== (p.maj || ''));
    if (!added.length && !changed.length) return;
    const lines = [
      ...added.map((p) => `🆕 ${p.emoji} **${p.titre}** — ${p.resume}`),
      ...changed.map((p) => `✏️ ${p.emoji} **${p.titre}** — ${p.resume}`),
    ];
    const channel = guild.channels.cache.get(updatesId);
    if (!channel?.isTextBased()) return;
    await channel.send({
      embeds: [new EmbedBuilder().setColor(DEFAULT_COLOR).setTitle('📖 Guide mis à jour')
        .setDescription(cut(`${lines.join('\n')}\n\nÀ lire avec \`/guide\` ou depuis le panneau du guide.`, 4096))
        .setTimestamp()],
      allowedMentions: { parse: [] },
    }).catch((e) => console.error('✖ Nouveautés du guide :', e.message));
  }

  async function refresh() {
    reload();
    if (loadState().panelSignature !== guide.signature) await ensurePanel();
    await announceUpdates();
  }

  // ─── Interactions ───────────────────────────────────────────────────
  // Depuis le panneau : réponse privée (puis remise à zéro du menu du panneau).
  // Depuis une réponse privée : on la remplace.
  async function show(i, payload) {
    if (i.message?.flags.has(MessageFlags.Ephemeral)) return i.update(payload);
    await i.reply({ ...payload, flags: MessageFlags.Ephemeral });
    if (i.isStringSelectMenu() && i.message) await i.message.edit(homeView(true)).catch(() => {});
  }

  function openKey(key, part) {
    const page = lib.findPage(guide, key);
    if (page) return pageView(page, part);
    return listView('📖 Fiche introuvable', 'Cette fiche a été renommée ou retirée.', [], '*Choisis une rubrique.*');
  }

  async function onSlash(i) {
    reload();
    const query = (i.options.getString('fiche') || '').trim();
    let payload;
    if (!query) payload = homeView(false);
    else if (lib.findPage(guide, query)) payload = openKey(query, 0);
    else payload = searchView(query);
    return i.reply({ ...payload, flags: MessageFlags.Ephemeral });
  }

  async function onAutocomplete(i) {
    const focused = String(i.options.getFocused() || '').trim();
    const pages = focused ? lib.search(guide, focused, 25).map((r) => r.page) : lib.recentPages(guide, 25);
    return i.respond(pages.slice(0, 25).map((p) => {
      const category = lib.findCategory(guide, p.categoryId);
      return { name: cut(`${p.emoji} ${p.titre} · ${category ? category.titre : ''}`, 100), value: p.key };
    }));
  }

  client.on(Events.InteractionCreate, async (i) => {
    if (!i.inGuild()) return;
    try {
      if (i.isAutocomplete() && i.commandName === 'guide') {
        if (!isAllowed(i.member)) return await i.respond([]);
        return await onAutocomplete(i);
      }
      const isSlash = i.isChatInputCommand() && i.commandName === 'guide';
      const isOwn = typeof i.customId === 'string' && i.customId.startsWith(PREFIX);
      if (!isSlash && !isOwn) return;
      if (!isAllowed(i.member)) {
        return await i.reply({ content: '⛔ Le guide est réservé au staff ORBIT MC.', flags: MessageFlags.Ephemeral });
      }
      if (isSlash) return await onSlash(i);

      reload();
      const [kind, a, b] = i.customId.slice(PREFIX.length).split(':');
      if (i.isStringSelectMenu() && kind === 'cat') {
        const category = lib.findCategory(guide, i.values[0]);
        return await show(i, category ? categoryView(category) : homeView(false));
      }
      if (i.isStringSelectMenu() && kind === 'page') return await show(i, openKey(i.values[0], 0));
      // Clé de fiche = « rubrique/fiche » : pas de « : » dedans, le découpage reste sûr.
      if (i.isButton() && kind === 'nav') return await show(i, openKey(a, Number(b) || 0));
      if (i.isButton() && kind === 'home') return await show(i, homeView(false));
      if (i.isButton() && kind === 'news') return await show(i, newsView());
      if (i.isButton() && kind === 'search') return await i.showModal(searchModal());
      if (i.isModalSubmit() && kind === 'searchmodal') {
        const payload = searchView(i.fields.getTextInputValue('q').trim());
        // Modal ouverte depuis une réponse privée : on la remplace ; depuis le panneau : nouvelle réponse privée.
        if (i.message?.flags.has(MessageFlags.Ephemeral)) return await i.update(payload);
        return await i.reply({ ...payload, flags: MessageFlags.Ephemeral });
      }
    } catch (e) {
      console.error('✖ Guide :', e.message);
      if (i.isAutocomplete()) return;
      const msg = { content: '❌ Une erreur interne est survenue.', flags: MessageFlags.Ephemeral };
      if (i.deferred || i.replied) await i.followUp(msg).catch(() => {});
      else await i.reply(msg).catch(() => {});
    }
  });

  // Commande /guide, déclarée sur le serveur seulement (disponible tout de suite).
  await guild.commands.create(
    new SlashCommandBuilder()
      .setName('guide')
      .setDescription('[Staff] Guide ORBIT MC : plugins, commandes admin et joueurs, procédures')
      .setDefaultMemberPermissions(P.ManageMessages)
      .addStringOption((o) => o.setName('fiche')
        .setDescription('Fiche ou mot-clé à chercher (vide = accueil du guide)')
        .setRequired(false)
        .setAutocomplete(true)
        .setMaxLength(100))
      .toJSON(),
  ).catch((e) => console.error('✖ Commande /guide :', e.message));

  try {
    await ensurePanel();
  } catch (e) {
    console.error('✖ Panneau du guide (permissions du bot ?) :', e.message);
  }
  await announceUpdates().catch((e) => console.error('✖ Nouveautés du guide :', e.message));
  setInterval(() => refresh().catch((e) => console.error('✖ Relecture du guide :', e.message)), refreshMs);

  console.log(`📖 Guide du staff : ${guide.pages.length} fiche(s), ${guide.categories.length} rubrique(s)`
    + `${guide.errors.length ? `, ${guide.errors.length} erreur(s)` : ''}.`);
}

module.exports = { setupGuide };
