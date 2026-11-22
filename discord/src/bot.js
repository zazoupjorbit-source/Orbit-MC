// npm start : bot ORBIT MC — boutons (règlement, rôles), tickets et statut des serveurs.
const {
  Client, Events, GatewayIntentBits, ChannelType, PermissionFlagsBits: P, MessageFlags,
  EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle, ActivityType,
} = require('discord.js');
const { loadConfig, loadState, requireEnv } = require('./common');
const { ping } = require('./mcping');
const { BLUE } = require('./messages');

const { token, guildId } = requireEnv();
const cfg = loadConfig();
const state = loadState();
if (!state.roles?.marque) {
  console.error('✖ data/state.json introuvable ou incomplet : lance d’abord « npm run setup ».');
  process.exit(1);
}

const FACTIONS = ['lumiere', 'ombre'];
const NOTIFS = ['notif_annonces', 'notif_devblog', 'notif_events', 'notif_tests'];
const STAFF_ROLES = ['fondateur', 'admin', 'modo', 'support'];
const EPH = { flags: MessageFlags.Ephemeral };

const client = new Client({ intents: [GatewayIntentBits.Guilds] });

const isStaff = (member) =>
  member.permissions.has(P.ManageChannels) || STAFF_ROLES.some((k) => member.roles.cache.has(state.roles[k]));

async function logToStaff(guild, text) {
  const channel = guild.channels.cache.get(state.channels.staff_logs);
  if (channel?.isTextBased()) await channel.send({ content: text, allowedMentions: { parse: [] } }).catch(() => {});
}

// ─── Boutons ──────────────────────────────────────────────────────────
async function onRulesAccept(i) {
  const roleId = state.roles.marque;
  if (i.member.roles.cache.has(roleId)) return i.reply({ content: 'Tu as déjà accepté le règlement. ✦', ...EPH });
  await i.member.roles.add(roleId, 'Règlement accepté');
  return i.reply({
    content: `✦ Bienvenue parmi les Marqués ! Choisis ta faction dans <#${state.channels.roles}>, puis passe dire bonjour dans <#${state.channels.general}>.`,
    ...EPH,
  });
}

async function onRole(i, key) {
  const roleId = state.roles[key];
  if (!roleId) return i.reply({ content: 'Rôle introuvable, relance `npm run setup`.', ...EPH });
  if (!i.member.roles.cache.has(state.roles.marque)) {
    return i.reply({ content: `Accepte d’abord le règlement dans <#${state.channels.reglement}>.`, ...EPH });
  }

  if (FACTIONS.includes(key)) {
    if (i.member.roles.cache.has(roleId)) {
      return i.reply({ content: `Tu fais déjà partie de la faction <@&${roleId}>.`, ...EPH });
    }
    const other = FACTIONS.filter((k) => k !== key).map((k) => state.roles[k]).filter((id) => i.member.roles.cache.has(id));
    if (other.length) await i.member.roles.remove(other, 'Changement de faction');
    await i.member.roles.add(roleId, 'Choix de faction');
    const home = key === 'lumiere' ? state.channels.lumiere_txt : state.channels.ombre_txt;
    return i.reply({ content: `Tu as rejoint <@&${roleId}>. Ton salon : <#${home}>.`, ...EPH });
  }

  if (NOTIFS.includes(key)) {
    if (i.member.roles.cache.has(roleId)) {
      await i.member.roles.remove(roleId, 'Notifications');
      return i.reply({ content: `🔕 Notifications <@&${roleId}> désactivées.`, ...EPH });
    }
    await i.member.roles.add(roleId, 'Notifications');
    return i.reply({ content: `🔔 Notifications <@&${roleId}> activées.`, ...EPH });
  }

  return i.reply({ content: 'Bouton inconnu.', ...EPH });
}

async function onTicketOpen(i) {
  const guild = i.guild;
  const parentId = state.channels.cat_tickets;
  const existing = guild.channels.cache.find((c) => c.parentId === parentId && c.topic === `ticket:${i.user.id}`);
  if (existing) return i.reply({ content: `Tu as déjà un ticket ouvert : <#${existing.id}>.`, ...EPH });

  await i.deferReply(EPH);
  const staffIds = ['support', 'modo', 'admin'].map((k) => state.roles[k]).filter(Boolean);
  const safeName = i.user.username.toLowerCase().replace(/[^a-z0-9_-]/g, '').slice(0, 20) || 'joueur';
  const channel = await guild.channels.create({
    name: `🎫・${safeName}`,
    type: ChannelType.GuildText,
    parent: parentId,
    topic: `ticket:${i.user.id}`,
    permissionOverwrites: [
      { id: guild.roles.everyone.id, deny: [P.ViewChannel] },
      { id: i.user.id, allow: [P.ViewChannel, P.SendMessages, P.ReadMessageHistory, P.AttachFiles, P.EmbedLinks] },
      ...staffIds.map((id) => ({ id, allow: [P.ViewChannel, P.SendMessages, P.ReadMessageHistory, P.AttachFiles, P.EmbedLinks, P.ManageMessages] })),
      { id: client.user.id, allow: [P.ViewChannel, P.SendMessages, P.ManageChannels] },
    ],
    reason: `Ticket de ${i.user.tag}`,
  });

  await channel.send({
    content: `${i.user} ${staffIds[0] ? `<@&${staffIds[0]}>` : ''}`,
    embeds: [
      new EmbedBuilder()
        .setColor(BLUE)
        .setTitle('🎫 Ticket ouvert')
        .setDescription('Explique ta demande en détail (pseudo en jeu, ce qui s’est passé, captures si besoin). Un membre du staff te répond dès que possible.'),
    ],
    components: [
      new ActionRowBuilder().addComponents(
        new ButtonBuilder().setCustomId('ticket:close').setLabel('Fermer le ticket').setEmoji('🔒').setStyle(ButtonStyle.Danger),
      ),
    ],
    allowedMentions: { users: [i.user.id], roles: staffIds.slice(0, 1) },
  });
  await logToStaff(guild, `🎫 Ticket ouvert par ${i.user} (${i.user.tag}) → ${channel}`);
  return i.editReply({ content: `Ton ticket est ouvert : ${channel}` });
}

async function onTicketClose(i) {
  const ownerId = i.channel.topic?.startsWith('ticket:') ? i.channel.topic.slice(7) : null;
  if (!ownerId) return i.reply({ content: 'Ce salon n’est pas un ticket.', ...EPH });
  if (i.user.id !== ownerId && !isStaff(i.member)) {
    return i.reply({ content: 'Seul l’auteur du ticket ou le staff peut le fermer.', ...EPH });
  }
  await i.reply({ content: '🔒 Fermeture du ticket dans 5 secondes…' });
  await logToStaff(i.guild, `🔒 Ticket \`${i.channel.name}\` (auteur <@${ownerId}>) fermé par ${i.user} (${i.user.tag})`);
  setTimeout(() => i.channel.delete(`Ticket fermé par ${i.user.tag}`).catch(() => {}), 5000);
}

client.on(Events.InteractionCreate, async (i) => {
  if (!i.isButton() || !i.inGuild()) return;
  const [kind, arg] = i.customId.split(':');
  try {
    if (kind === 'rules' && arg === 'accept') return await onRulesAccept(i);
    if (kind === 'role') return await onRole(i, arg);
    if (kind === 'ticket' && arg === 'open') return await onTicketOpen(i);
    if (kind === 'ticket' && arg === 'close') return await onTicketClose(i);
  } catch (e) {
    console.error(`✖ ${i.customId} :`, e.message);
    const msg = { content: 'Une erreur est survenue. Préviens le staff si ça persiste.', ...EPH };
    if (i.deferred || i.replied) await i.followUp(msg).catch(() => {});
    else await i.reply(msg).catch(() => {});
  }
});

// ─── Statut des serveurs ─────────────────────────────────────────────
async function updateStatus(guild) {
  const servers = cfg.status?.servers || [];
  const ref = state.messages.statut;
  if (!servers.length || !ref) return;
  const results = await Promise.all(servers.map((s) => ping(s.host, s.port)));

  const lines = servers.map((s, n) => {
    const r = results[n];
    return r.online
      ? `🟢 **${s.name}** · ${r.players}/${r.max} joueur${r.players > 1 ? 's' : ''}`
      : `🔴 **${s.name}** · hors ligne`;
  });
  const main = results[0];
  const address = cfg.minecraft?.address?.trim();
  const embed = new EmbedBuilder()
    .setColor(main?.online ? 0x2ecc71 : 0xe74c3c)
    .setTitle('📡 Statut des serveurs')
    .setDescription([address ? `🎮 \`${address}\`\n` : '', ...lines].join('\n'))
    .setFooter({ text: 'Mis à jour automatiquement' })
    .setTimestamp();

  const channel = guild.channels.cache.get(ref.channelId);
  const msg = await channel?.messages.fetch(ref.messageId).catch(() => null);
  if (msg) await msg.edit({ embeds: [embed] }).catch((e) => console.error('✖ statut :', e.message));

  client.user.setPresence({
    activities: [{
      type: ActivityType.Watching,
      name: main?.online ? `${main.players} Marqué${main.players > 1 ? 's' : ''} en jeu` : 'ORBIT MC · hors ligne',
    }],
    status: main?.online ? 'online' : 'idle',
  });
}

client.once(Events.ClientReady, async () => {
  const guild = await client.guilds.fetch(guildId);
  await guild.channels.fetch();
  console.log(`✅ Bot ORBIT MC connecté (${client.user.tag}) sur ${guild.name}`);
  if (cfg.status?.enabled !== false) {
    const every = Math.max(30, cfg.status?.intervalSeconds || 60) * 1000;
    const tick = () => updateStatus(guild).catch((e) => console.error('✖ statut :', e.message));
    tick();
    setInterval(tick, every);
  }
});

client.login(token);
