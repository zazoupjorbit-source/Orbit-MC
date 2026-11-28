// npm run setup : crée / met à jour tout le serveur Discord (rôles, salons, permissions, messages).
// Relançable sans risque : rien n'est dupliqué, ce qui existe est mis à jour.
const {
  Client, Events, GatewayIntentBits, ChannelType, PermissionFlagsBits: P,
  GuildVerificationLevel, GuildDefaultMessageNotifications, GuildExplicitContentFilter,
} = require('discord.js');
const { ROLES, EVERYONE_PERMISSIONS, CATEGORIES } = require('./structure');
const messages = require('./messages');
const { loadConfig, loadState, saveState, requireEnv } = require('./common');

const REASON = 'Installation ORBIT STUDIO';
const TYPES = {
  text: ChannelType.GuildText,
  voice: ChannelType.GuildVoice,
  forum: ChannelType.GuildForum,
  announcement: ChannelType.GuildAnnouncement,
  category: ChannelType.GuildCategory,
};

const log = (icon, msg) => console.log(`${icon} ${msg}`);

async function main() {
  const { token, guildId } = requireEnv();
  const cfg = loadConfig();
  const state = loadState();
  const client = new Client({ intents: [GatewayIntentBits.Guilds] });
  const ready = new Promise((r) => client.once(Events.ClientReady, r));
  await client.login(token);
  await ready;

  const guild = await client.guilds.fetch(guildId);
  await guild.roles.fetch();
  await guild.channels.fetch();
  const me = await guild.members.fetchMe();
  if (!me.permissions.has(P.Administrator)) {
    throw new Error('Le bot doit avoir la permission Administrateur pour l’installation (voir README).');
  }
  const community = guild.features.includes('COMMUNITY');
  log('🛰️', `Serveur : ${guild.name}${community ? ' (Communauté activée)' : ' (Communauté non activée : forums → salons texte)'}`);

  // ── 1. Réglages du serveur ──────────────────────────────────────────
  await guild.edit({
    verificationLevel: GuildVerificationLevel.Medium,
    defaultMessageNotifications: GuildDefaultMessageNotifications.OnlyMentions,
    explicitContentFilter: GuildExplicitContentFilter.AllMembers,
    reason: REASON,
  });
  await guild.roles.everyone.setPermissions(EVERYONE_PERMISSIONS, REASON);
  log('⚙️ ', 'Réglages de sécurité et permissions @everyone appliqués');

  // ── 2. Rôles ────────────────────────────────────────────────────────
  const roleIds = { everyone: guild.roles.everyone.id };
  for (const spec of ROLES) {
    let role = guild.roles.cache.get(state.roles[spec.key]) || guild.roles.cache.find((r) => r.name === spec.name);
    const data = {
      name: spec.name,
      colors: { primaryColor: spec.color ?? 0 },
      hoist: !!spec.hoist,
      mentionable: false,
      permissions: spec.permissions ?? [],
      reason: REASON,
    };
    if (!role) {
      role = await guild.roles.create(data);
      log('➕', `Rôle ${spec.name}`);
    } else if (role.position < me.roles.highest.position && !role.managed) {
      await role.edit(data);
      log('♻️ ', `Rôle ${spec.name}`);
    } else {
      log('⚠️ ', `Rôle ${spec.name} au-dessus du bot : non modifié`);
    }
    roleIds[spec.key] = role.id;
  }
  state.roles = Object.fromEntries(ROLES.map((r) => [r.key, roleIds[r.key]]));

  // Ordre des rôles (du plus haut au plus bas, sous le rôle du bot)
  const positions = ROLES.map((r) => guild.roles.cache.get(roleIds[r.key])?.position ?? 0);
  const ordered = positions.every((p, i) => i === 0 || positions[i - 1] > p);
  if (ordered) {
    log('↕️ ', 'Ordre des rôles correct');
  } else try {
    const n = ROLES.length;
    await guild.roles.setPositions(ROLES.map((r, i) => ({ role: roleIds[r.key], position: n - i })));
    log('↕️ ', 'Ordre des rôles appliqué');
  } catch (e) {
    log('⚠️ ', `Ordre des rôles non appliqué (${e.message}). Range-les à la main si besoin.`);
  }

  // ── 3. Catégories & salons ──────────────────────────────────────────
  const resolveOverwrites = (list) => list
    .filter((o) => roleIds[o.target])
    .map((o) => ({ id: roleIds[o.target], allow: o.allow, deny: o.deny }));
  const merge = (catList, chList = []) => {
    const targets = new Set(chList.map((o) => o.target));
    return [...catList.filter((o) => !targets.has(o.target)), ...chList];
  };

  async function ensureChannel(spec, typeName, parent, overwrites) {
    let type = TYPES[typeName];
    if (!community && (type === ChannelType.GuildForum || type === ChannelType.GuildAnnouncement)) type = ChannelType.GuildText;

    let channel = guild.channels.cache.get(state.channels[spec.key])
      || guild.channels.cache.find((c) => c.name === spec.name && (c.parentId ?? null) === (parent?.id ?? null));

    const base = { name: spec.name, permissionOverwrites: resolveOverwrites(overwrites), reason: REASON };
    if (parent) base.parent = parent.id;
    if (spec.topic && type !== ChannelType.GuildVoice && type !== ChannelType.GuildCategory) base.topic = spec.topic;
    if (spec.slowmode) base.rateLimitPerUser = spec.slowmode;
    if (spec.userLimit) base.userLimit = spec.userLimit;

    if (channel) {
      await channel.edit(base);
      log('♻️ ', `${typeName === 'category' ? 'Catégorie' : 'Salon'} ${spec.name}`);
    } else {
      try {
        channel = await guild.channels.create({ ...base, type });
      } catch (e) {
        if (type === ChannelType.GuildText) throw e;
        log('⚠️ ', `${spec.name} : type ${typeName} refusé (${e.message}), création en salon texte`);
        channel = await guild.channels.create({ ...base, type: ChannelType.GuildText });
      }
      log('➕', `${typeName === 'category' ? 'Catégorie' : 'Salon'} ${spec.name}`);
    }
    state.channels[spec.key] = channel.id;
    return channel;
  }

  const categoryOrder = [];
  for (const cat of CATEGORIES) {
    const category = await ensureChannel(cat, 'category', null, cat.overwrites);
    categoryOrder.push(category.id);
    const childOrder = [];
    for (const spec of cat.channels) {
      const channel = await ensureChannel(spec, spec.type || 'text', category, merge(cat.overwrites, spec.overwrites));
      childOrder.push(channel.id);
    }
    try {
      await guild.channels.setPositions(childOrder.map((id, i) => ({ channel: id, position: i })));
    } catch { /* ordre facultatif */ }
  }
  try {
    await guild.channels.setPositions(categoryOrder.map((id, i) => ({ channel: id, position: i })));
  } catch { /* ordre facultatif */ }

  // Salon système, AFK, règlement
  const ids = state.channels;
  const guildEdit = { systemChannel: ids.general, afkChannel: ids.voc_afk, afkTimeout: 900, reason: REASON };
  if (community) Object.assign(guildEdit, { rulesChannel: ids.reglement, publicUpdatesChannel: ids.studio_logs });
  await guild.edit(guildEdit);
  log('⚙️ ', 'Salon système, AFK et règlement configurés');

  // ── 4. Messages fixes ───────────────────────────────────────────────
  for (const cat of CATEGORIES) {
    for (const spec of cat.channels) {
      if (!spec.message) continue;
      const channel = guild.channels.cache.get(ids[spec.key]);
      if (!channel?.isTextBased()) continue;
      const payload = messages.build(spec.message, cfg, ids);
      payload.components ??= [];
      const prev = state.messages[spec.message];
      let msg = null;
      if (prev?.channelId === channel.id) msg = await channel.messages.fetch(prev.messageId).catch(() => null);
      if (msg) {
        await msg.edit(payload);
        log('♻️ ', `Message ${spec.message}`);
      } else {
        msg = await channel.send(payload);
        log('➕', `Message ${spec.message}`);
      }
      state.messages[spec.message] = { channelId: channel.id, messageId: msg.id };
    }
  }

  saveState(state);
  log('✅', `Terminé. Lance maintenant le bot avec « npm start » pour activer les boutons et les demandes de projet.`);
  await client.destroy();
}

main().catch((e) => {
  console.error('✖ Échec :', e.message);
  if (e.rawError) console.error(JSON.stringify(e.rawError, null, 2));
  process.exit(1);
});
