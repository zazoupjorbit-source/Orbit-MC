// node src/publier.js : met l'icône du serveur et publie (ou met à jour) les posts de posts/*.js.
// Relançable : un post déjà publié est modifié sur place, pas reposté (sauf si ses fichiers ont changé).
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const { Client, Events, GatewayIntentBits, ChannelType } = require('discord.js');
const { loadState, saveState, requireEnv } = require('./common');

const ROOT = path.join(__dirname, '..');
const ICONE = path.join(ROOT, 'logo', 'orbit-studio-icone-rond.png');

async function main() {
  const { token, guildId } = requireEnv();
  const state = loadState();
  state.posts ??= {};
  const client = new Client({ intents: [GatewayIntentBits.Guilds] });
  const ready = new Promise((r) => client.once(Events.ClientReady, r));
  await client.login(token);
  await ready;
  const guild = await client.guilds.fetch(guildId);
  await guild.channels.fetch();

  // Icône du serveur (seulement si elle a changé)
  if (fs.existsSync(ICONE)) {
    const hash = crypto.createHash('sha1').update(fs.readFileSync(ICONE)).digest('hex');
    if (state.iconeHash !== hash) {
      await guild.setIcon(ICONE, 'Logo ORBIT STUDIO');
      state.iconeHash = hash;
      console.log('🖼️  Icône du serveur mise à jour');
    } else console.log('🖼️  Icône déjà à jour');
  }

  const dir = path.join(ROOT, 'posts');
  for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.js'))) {
    const post = require(path.join(dir, file));
    const channel = guild.channels.cache.get(state.channels[post.channel] || post.channel);
    if (!channel || !(channel.isTextBased() || channel.type === ChannelType.GuildForum)) { console.log(`⚠️  ${file} : salon ${post.channel} introuvable`); continue; }
    const payload = post.build(state.channels);
    const filesHash = crypto.createHash('sha1')
      .update((payload.files || []).map((f) => fs.readFileSync(f.attachment)).reduce((a, b) => Buffer.concat([a, b]), Buffer.alloc(0)))
      .digest('hex');
    const prev = state.posts[post.key];
    // Salon forum : le post est un fil dont le premier message porte le contenu.
    if (channel.type === ChannelType.GuildForum) {
      const thread = prev?.threadId ? await channel.threads.fetch(prev.threadId).catch(() => null) : null;
      const starter = thread ? await thread.fetchStarterMessage().catch(() => null) : null;
      if (starter && prev.filesHash === filesHash) {
        if (thread.name !== post.threadName) await thread.setName(post.threadName);
        await starter.edit({ content: payload.content ?? null, embeds: payload.embeds, components: payload.components ?? [] });
        console.log(`♻️  Post ${post.key} mis à jour (forum)`);
      } else {
        if (thread) await thread.delete('Nouvelle version du post').catch(() => {});
        const t = await channel.threads.create({ name: post.threadName, message: { ...payload, allowedMentions: { parse: [] } } });
        state.posts[post.key] = { channelId: channel.id, threadId: t.id, filesHash };
        console.log(`➕ Post ${post.key} publié dans le forum #${channel.name}`);
        continue;
      }
      state.posts[post.key] = { channelId: channel.id, threadId: thread.id, filesHash };
      continue;
    }
    let msg = prev?.channelId === channel.id ? await channel.messages.fetch(prev.messageId).catch(() => null) : null;
    if (msg && prev.filesHash === filesHash) {
      await msg.edit({ content: payload.content ?? null, embeds: payload.embeds, components: payload.components ?? [] });
      console.log(`♻️  Post ${post.key} mis à jour`);
    } else {
      if (msg) await msg.delete().catch(() => {});
      msg = await channel.send({ ...payload, allowedMentions: { parse: [] } });
      console.log(`➕ Post ${post.key} publié dans #${channel.name}`);
    }
    state.posts[post.key] = { channelId: channel.id, messageId: msg.id, filesHash };
  }

  saveState(state);
  await client.destroy();
}

main().catch((e) => {
  console.error('✖ Échec :', e.message);
  if (e.rawError) console.error(JSON.stringify(e.rawError, null, 2));
  process.exit(1);
});
