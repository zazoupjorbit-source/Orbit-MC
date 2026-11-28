// npm start : bot ORBIT STUDIO — règlement, notifications et demandes de projet.
const fs = require('fs');
const path = require('path');
const {
  Client, Events, GatewayIntentBits, ChannelType, PermissionFlagsBits: P, MessageFlags,
  EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle, ActivityType,
  ModalBuilder, LabelBuilder, TextInputBuilder, TextInputStyle, StringSelectMenuBuilder,
} = require('discord.js');
const { loadConfig, loadState, requireEnv } = require('./common');
const { STATUTS, TYPES_PROJET, FOOTER } = require('./messages');

const { token, guildId } = requireEnv();
const cfg = loadConfig();
const state = loadState();
if (!state.roles?.membre) {
  console.error('✖ data/state.json introuvable ou incomplet : lance d’abord « npm run setup ».');
  process.exit(1);
}

const NOTIFS = ['notif_annonces', 'notif_devblog', 'notif_sorties', 'notif_tests'];
const STUDIO_ROLES = ['fondateur', 'dev'];
const MAX_OUVERTES = Math.max(1, cfg.demandes?.maxOuvertesParMembre ?? 2);
const SUIVI_PUBLIC = cfg.demandes?.suiviPublic !== false;
const EPH = { flags: MessageFlags.Ephemeral };

// ─── Registre des demandes (data/demandes.json) ──────────────────────
const DEMANDES_FILE = path.join(__dirname, '..', 'data', 'demandes.json');
const registre = (() => {
  try { return { next: 1, items: {}, ...JSON.parse(fs.readFileSync(DEMANDES_FILE, 'utf8')) }; }
  catch { return { next: 1, items: {} }; }
})();
function saveRegistre() {
  fs.mkdirSync(path.dirname(DEMANDES_FILE), { recursive: true });
  fs.writeFileSync(DEMANDES_FILE, JSON.stringify(registre, null, 2));
}
const isOuverte = (d) => !d.fermee && d.statut !== 'livree' && d.statut !== 'refusee';
const typeInfo = (v) => TYPES_PROJET.find((t) => t.value === v) || TYPES_PROJET[TYPES_PROJET.length - 1];

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

const isStudio = (member) =>
  member.permissions.has(P.ManageChannels) || STUDIO_ROLES.some((k) => member.roles.cache.has(state.roles[k]));

async function logStudio(guild, text) {
  const channel = guild.channels.cache.get(state.channels.studio_logs);
  if (channel?.isTextBased()) await channel.send({ content: text, allowedMentions: { parse: [] } }).catch(() => {});
}

// ─── Règlement & notifications ──────────────────────────────────────
async function onRulesAccept(i) {
  const roleId = state.roles.membre;
  if (i.member.roles.cache.has(roleId)) return i.reply({ content: 'Tu as déjà accepté le règlement. ✦', ...EPH });
  await i.member.roles.add(roleId, 'Règlement accepté');
  return i.reply({
    content: `✦ Bienvenue chez ORBIT STUDIO ! Choisis tes notifications dans <#${state.channels.roles}>, puis passe dire bonjour dans <#${state.channels.general}>.`,
    ...EPH,
  });
}

async function onRole(i, key) {
  const roleId = state.roles[key];
  if (!roleId || !NOTIFS.includes(key)) return i.reply({ content: 'Bouton inconnu.', ...EPH });
  if (!i.member.roles.cache.has(state.roles.membre)) {
    return i.reply({ content: `Accepte d’abord le règlement dans <#${state.channels.reglement}>.`, ...EPH });
  }
  if (i.member.roles.cache.has(roleId)) {
    await i.member.roles.remove(roleId, 'Notifications');
    return i.reply({ content: `🔕 Notifications <@&${roleId}> désactivées.`, ...EPH });
  }
  await i.member.roles.add(roleId, 'Notifications');
  return i.reply({ content: `🔔 Notifications <@&${roleId}> activées.`, ...EPH });
}

// ─── Demandes de projet ─────────────────────────────────────────────
function formulaire() {
  const input = (id, style, opts = {}) => {
    const t = new TextInputBuilder().setCustomId(id).setStyle(style).setRequired(opts.required !== false);
    if (opts.placeholder) t.setPlaceholder(opts.placeholder);
    if (opts.min) t.setMinLength(opts.min);
    t.setMaxLength(opts.max ?? 100);
    return t;
  };
  return new ModalBuilder()
    .setCustomId('projet:form')
    .setTitle('Demander un projet')
    .addLabelComponents(
      new LabelBuilder().setLabel('Nom du projet')
        .setTextInputComponent(input('titre', TextInputStyle.Short, { placeholder: 'Ex. : Mode Sorter, Trieur automatique…', min: 3, max: 80 })),
      new LabelBuilder().setLabel('Type de projet')
        .setStringSelectMenuComponent(new StringSelectMenuBuilder().setCustomId('type').setPlaceholder('Choisis un type')
          .addOptions(TYPES_PROJET.map((t) => ({ label: t.label, value: t.value, emoji: t.emoji })))),
      new LabelBuilder().setLabel('Plateforme et version').setDescription('Serveur, loader, version du jeu')
        .setTextInputComponent(input('plateforme', TextInputStyle.Short, { placeholder: 'Ex. : Paper 1.21.4, Fabric 26.3, Garry’s Mod', max: 100 })),
      new LabelBuilder().setLabel('Description').setDescription('Le but, et ce que le joueur doit pouvoir faire')
        .setTextInputComponent(input('description', TextInputStyle.Paragraph, { min: 20, max: 2000 })),
      new LabelBuilder().setLabel('Infos utiles (facultatif)').setDescription('Délai souhaité, exemples, liens, nom du serveur')
        .setTextInputComponent(input('infos', TextInputStyle.Paragraph, { required: false, max: 1000 })),
    );
}

function embedDemande(d) {
  const s = STATUTS[d.statut];
  const t = typeInfo(d.type);
  const e = new EmbedBuilder()
    .setColor(s.color)
    .setTitle(`📨 Demande n°${d.n} · ${d.titre}`)
    .addFields(
      { name: 'Statut', value: `${s.emoji} **${s.label}** · ${s.texte}` },
      { name: 'Type', value: `${t.emoji} ${t.label}`, inline: true },
      { name: 'Plateforme', value: d.plateforme || '—', inline: true },
      { name: 'Demandé par', value: `<@${d.userId}>`, inline: true },
      { name: 'Description', value: d.description },
    )
    .setFooter(FOOTER)
    .setTimestamp(d.majLe);
  if (d.infos) e.addFields({ name: 'Infos utiles', value: d.infos });
  if (d.raison) e.addFields({ name: 'Raison du refus', value: d.raison });
  return e;
}

function boutonsStudio(d) {
  const b = (id, label, emoji, style) =>
    new ButtonBuilder().setCustomId(`demande:${id}:${d.n}`).setLabel(label).setEmoji(emoji).setStyle(style).setDisabled(d.statut === id);
  return [
    new ActionRowBuilder().addComponents(
      b('acceptee', 'Accepter', '✅', ButtonStyle.Success),
      b('en_cours', 'En cours', '🛠️', ButtonStyle.Primary),
      b('test', 'En test', '🧪', ButtonStyle.Primary),
      b('livree', 'Livrée', '🏁', ButtonStyle.Success),
    ),
    new ActionRowBuilder().addComponents(
      b('refusee', 'Refuser', '❌', ButtonStyle.Danger),
      new ButtonBuilder().setCustomId(`demande:fermer:${d.n}`).setLabel('Fermer le salon').setEmoji('🔒').setStyle(ButtonStyle.Secondary),
    ),
  ];
}

function embedSuivi(d) {
  const s = STATUTS[d.statut];
  const t = typeInfo(d.type);
  return new EmbedBuilder()
    .setColor(s.color)
    .setTitle(`${t.emoji} ${d.titre}`)
    .setDescription(`${s.emoji} **${s.label}** · ${t.label}${d.plateforme ? ` · ${d.plateforme}` : ''}\nProposé par <@${d.userId}>`)
    .setFooter({ text: `Demande n°${d.n} · ORBIT STUDIO` })
    .setTimestamp(d.majLe);
}

async function onDemander(i) {
  if (!i.member.roles.cache.has(state.roles.membre)) {
    return i.reply({ content: `Accepte d’abord le règlement dans <#${state.channels.reglement}>.`, ...EPH });
  }
  const ouvertes = Object.values(registre.items).filter((d) => d.userId === i.user.id && isOuverte(d));
  if (ouvertes.length >= MAX_OUVERTES) {
    return i.reply({
      content: `Tu as déjà ${ouvertes.length} demande(s) en attente : ${ouvertes.map((d) => `<#${d.channelId}>`).join(', ')}. Attends qu’une soit traitée avant d’en ouvrir une autre.`,
      ...EPH,
    });
  }
  return i.showModal(formulaire());
}

async function onFormulaire(i) {
  await i.deferReply(EPH);
  const guild = i.guild;
  const n = registre.next++;
  const d = {
    n,
    userId: i.user.id,
    titre: i.fields.getTextInputValue('titre').trim(),
    type: i.fields.getStringSelectValues('type')[0] || 'autre',
    plateforme: i.fields.getTextInputValue('plateforme').trim(),
    description: i.fields.getTextInputValue('description').trim(),
    infos: (i.fields.getTextInputValue('infos') || '').trim(),
    statut: 'recue',
    creeLe: Date.now(),
    majLe: Date.now(),
  };

  const studioIds = STUDIO_ROLES.map((k) => state.roles[k]).filter(Boolean);
  const slug = d.titre.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '')
    .replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '').slice(0, 30) || 'projet';
  const channel = await guild.channels.create({
    name: `📨・${n}-${slug}`,
    type: ChannelType.GuildText,
    parent: state.channels.cat_demandes_ouvertes,
    topic: `demande:${n}:${i.user.id}`,
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [P.ViewChannel] },
      { id: i.user.id, allow: [P.ViewChannel, P.SendMessages, P.ReadMessageHistory, P.AttachFiles, P.EmbedLinks] },
      ...studioIds.map((id) => ({ id, allow: [P.ViewChannel, P.SendMessages, P.ReadMessageHistory, P.AttachFiles, P.EmbedLinks, P.ManageMessages] })),
      { id: client.user.id, allow: [P.ViewChannel, P.SendMessages, P.ManageChannels, P.ReadMessageHistory] },
    ],
    reason: `Demande de projet n°${n} de ${i.user.tag}`,
  });
  d.channelId = channel.id;

  const msg = await channel.send({
    content: `${i.user} ta demande est bien arrivée. Le studio te répond ici. Tu peux ajouter des images ou des précisions dans ce salon.`,
    embeds: [embedDemande(d)],
    components: boutonsStudio(d),
    allowedMentions: { users: [i.user.id] },
  });
  d.messageId = msg.id;
  registre.items[n] = d;
  saveRegistre();

  await logStudio(guild, `📨 Nouvelle demande n°${n} « ${d.titre} » (${typeInfo(d.type).label}) par ${i.user} (${i.user.tag}) → ${channel}`);
  return i.editReply({ content: `📨 Demande envoyée ! On en parle ici : ${channel}` });
}

async function onMesDemandes(i) {
  const miennes = Object.values(registre.items).filter((d) => d.userId === i.user.id).sort((a, b) => b.n - a.n).slice(0, 15);
  if (!miennes.length) return i.reply({ content: 'Tu n’as encore fait aucune demande.', ...EPH });
  const lignes = miennes.map((d) => {
    const s = STATUTS[d.statut];
    const ou = !d.fermee && d.channelId ? ` · <#${d.channelId}>` : '';
    return `${s.emoji} **n°${d.n} · ${d.titre}** · ${s.label}${ou}`;
  });
  return i.reply({ embeds: [new EmbedBuilder().setColor(0x3a7fff).setTitle('📂 Mes demandes').setDescription(lignes.join('\n'))], ...EPH });
}

/** Met à jour le panneau public #suivi-des-projets pour une demande. */
async function majSuivi(guild, d) {
  if (!SUIVI_PUBLIC) return;
  const channel = guild.channels.cache.get(state.channels.suivi);
  if (!channel?.isTextBased()) return;
  const prev = d.suiviId ? await channel.messages.fetch(d.suiviId).catch(() => null) : null;
  const visible = !['recue', 'refusee'].includes(d.statut);
  if (!visible) {
    if (prev) await prev.delete().catch(() => {});
    delete d.suiviId;
    return;
  }
  const payload = { embeds: [embedSuivi(d)], allowedMentions: { parse: [] } };
  if (prev) await prev.edit(payload);
  else d.suiviId = (await channel.send(payload)).id;
}

async function changerStatut(i, d, statut, raison) {
  const guild = i.guild;
  d.statut = statut;
  d.majLe = Date.now();
  if (raison !== undefined) d.raison = raison;
  else if (statut !== 'refusee') delete d.raison;

  const channel = guild.channels.cache.get(d.channelId);
  if (channel?.isTextBased()) {
    const msg = await channel.messages.fetch(d.messageId).catch(() => null);
    if (msg) await msg.edit({ embeds: [embedDemande(d)], components: boutonsStudio(d) });
    const s = STATUTS[statut];
    await channel.send({
      content: `<@${d.userId}> ${s.emoji} Ta demande passe à **${s.label}** : ${s.texte}${raison ? `\n> ${raison}` : ''}`,
      allowedMentions: { users: [d.userId] },
    });
  }

  if (statut === 'acceptee' && state.roles.porteur) {
    const member = await guild.members.fetch(d.userId).catch(() => null);
    if (member) await member.roles.add(state.roles.porteur, `Demande n°${d.n} acceptée`).catch(() => {});
  }
  await majSuivi(guild, d).catch((e) => console.error('✖ suivi :', e.message));
  saveRegistre();
  await logStudio(guild, `${STATUTS[statut].emoji} Demande n°${d.n} « ${d.titre} » → ${STATUTS[statut].label} (par ${i.user.tag})`);
}

async function onBoutonDemande(i, action, n) {
  const d = registre.items[n];
  if (!d) return i.reply({ content: 'Demande introuvable.', ...EPH });

  if (action === 'fermer') {
    if (i.user.id !== d.userId && !isStudio(i.member)) {
      return i.reply({ content: 'Seul l’auteur de la demande ou le studio peut fermer ce salon.', ...EPH });
    }
    d.fermee = true;
    d.majLe = Date.now();
    saveRegistre();
    await i.reply({ content: '🔒 Fermeture du salon dans 5 secondes. La demande reste enregistrée (bouton « Mes demandes »).' });
    await logStudio(i.guild, `🔒 Salon de la demande n°${d.n} « ${d.titre} » fermé par ${i.user.tag} (statut : ${STATUTS[d.statut].label})`);
    setTimeout(() => i.channel.delete(`Demande fermée par ${i.user.tag}`).catch(() => {}), 5000);
    return;
  }

  if (!STATUTS[action]) return i.reply({ content: 'Bouton inconnu.', ...EPH });
  if (!isStudio(i.member)) return i.reply({ content: 'Seul le studio peut changer le statut d’une demande.', ...EPH });

  if (action === 'refusee') {
    return i.showModal(new ModalBuilder().setCustomId(`demande:refus:${n}`).setTitle(`Refuser la demande n°${n}`).addLabelComponents(
      new LabelBuilder().setLabel('Raison (visible par le demandeur)').setTextInputComponent(
        new TextInputBuilder().setCustomId('raison').setStyle(TextInputStyle.Paragraph).setRequired(false).setMaxLength(500)
          .setPlaceholder('Ex. : trop gros pour le moment, déjà existant, hors de nos compétences…'),
      ),
    ));
  }
  await i.deferUpdate();
  return changerStatut(i, d, action);
}

async function onRefus(i, n) {
  const d = registre.items[n];
  if (!d) return i.reply({ content: 'Demande introuvable.', ...EPH });
  if (!isStudio(i.member)) return i.reply({ content: 'Seul le studio peut refuser une demande.', ...EPH });
  await i.deferUpdate();
  return changerStatut(i, d, 'refusee', i.fields.getTextInputValue('raison').trim() || 'Aucune raison donnée.');
}

// ─── Routage ────────────────────────────────────────────────────────
client.on(Events.InteractionCreate, async (i) => {
  if (!i.inGuild() || !(i.isButton() || i.isModalSubmit())) return;
  const [kind, arg, n] = i.customId.split(':');
  try {
    if (i.isModalSubmit()) {
      if (kind === 'projet' && arg === 'form') return await onFormulaire(i);
      if (kind === 'demande' && arg === 'refus') return await onRefus(i, n);
      return;
    }
    if (kind === 'rules' && arg === 'accept') return await onRulesAccept(i);
    if (kind === 'role') return await onRole(i, arg);
    if (kind === 'projet' && arg === 'demander') return await onDemander(i);
    if (kind === 'projet' && arg === 'mes') return await onMesDemandes(i);
    if (kind === 'demande') return await onBoutonDemande(i, arg, n);
  } catch (e) {
    console.error(`✖ ${i.customId} :`, e.message);
    const msg = { content: 'Une erreur est survenue. Préviens le studio si ça persiste.', ...EPH };
    if (i.deferred || i.replied) await i.followUp(msg).catch(() => {});
    else await i.reply(msg).catch(() => {});
  }
});

function majPresence() {
  const enCours = Object.values(registre.items).filter((d) => ['acceptee', 'en_cours', 'test'].includes(d.statut)).length;
  client.user.setPresence({
    activities: [{ type: ActivityType.Watching, name: enCours ? `${enCours} projet${enCours > 1 ? 's' : ''} en cours` : 'les demandes de projet' }],
    status: 'online',
  });
}

client.once(Events.ClientReady, async () => {
  const guild = await client.guilds.fetch(guildId);
  await guild.channels.fetch();
  console.log(`✅ Bot ORBIT STUDIO connecté (${client.user.tag}) sur ${guild.name}`);
  majPresence();
  setInterval(majPresence, 5 * 60 * 1000);
});

client.login(token);
