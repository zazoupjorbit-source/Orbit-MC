// Messages fixes postés par `npm run setup` (édités en place si on relance).
const { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } = require('discord.js');

const BLUE = 0x3a7fff;
const GOLD = 0xffd66e;
const VIOLET = 0xb36bff;
const SEP = '━━━━━━━━━━━━━━━━━━━━━━';
const DISCLAIMER = 'ORBIT MC n’est pas un produit officiel Minecraft. Non approuvé par Mojang ou Microsoft, ni associé à eux.';

const ch = (ids, key) => (ids[key] ? `<#${ids[key]}>` : `#${key}`);

function build(key, cfg, ids) {
  const address = cfg.minecraft?.address?.trim();
  const footer = { text: 'ORBIT MC · ORBIT GAMES' };

  switch (key) {
    case 'bienvenue':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('✦ Bienvenue sur ORBIT MC')
            .setDescription([
              'Il y a mille ans, l’Astre s’est brisé. Cette nuit, ses Éclats se sont remis à tomber.',
              'Toi, tu t’es réveillé dans un cratère, une Marque qui luit sous la peau.',
              '',
              SEP,
              '**Pour commencer**',
              `**1.** Lis le ${ch(ids, 'reglement')} et clique sur **J’accepte**.`,
              `**2.** Choisis ta faction et tes notifications dans ${ch(ids, 'roles')}.`,
              `**3.** Viens dire bonjour dans ${ch(ids, 'general')}.`,
              SEP,
              `🎮 **Serveur :** ${address ? `\`${address}\`` : 'adresse bientôt disponible'}`,
              `❓ Besoin d’aide ? ${ch(ids, 'aide')} ou ${ch(ids, 'tickets')}.`,
            ].join('\n'))
            .setFooter(footer),
        ],
      };

    case 'reglement':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('📜 Règlement d’ORBIT MC')
            .setDescription([
              '**1. Respect.** Pas d’insultes, de harcèlement, de menaces ni de discrimination.',
              '**2. Contenu.** Rien de NSFW, choquant ou illégal, y compris pseudo, avatar et statut.',
              '**3. Spam & pub.** Pas de flood, de mentions abusives ni de publicité non autorisée (MP compris).',
              '**4. Bons salons.** Chaque salon a un sujet, respecte-le.',
              '**5. Triche.** Pas de triche, de client modifié interdit ni d’exploitation de bugs. Un bug se signale, il ne s’exploite pas.',
              '**6. Vie privée.** Ne partage jamais les informations personnelles de quelqu’un d’autre.',
              '**7. Staff.** Les décisions du staff s’appliquent. Pour contester, ouvre un ticket plutôt que d’en débattre en public.',
              '**8. Discord.** Les [Conditions d’utilisation](https://discord.com/terms) et les [Règles de la communauté](https://discord.com/guidelines) de Discord s’appliquent ici.',
              '',
              SEP,
              '**Sanctions :** avertissement → exclusion temporaire → bannissement, selon la gravité. Une faute grave peut mener directement au bannissement.',
            ].join('\n'))
            .setFooter({ text: 'En cliquant sur « J’accepte », tu confirmes avoir lu ce règlement.' }),
        ],
        components: [
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('rules:accept').setLabel('J’accepte le règlement').setEmoji('✅').setStyle(ButtonStyle.Success),
          ),
        ],
      };

    case 'roles':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('🎭 Choisis ta faction')
            .setDescription([
              'Deux camps se disputent les Marqués. Ton choix t’ouvre le salon privé de ta faction.',
              '',
              '☀️ **Lumière** · Bastion de l’Aube',
              'Gardiens de l’ordre. Ils veulent rassembler les Éclats pour faire renaître l’Astre.',
              '',
              '🌑 **Ombre** · Citadelle d’Obsidienne',
              'Ils ont choisi le Néant. Pour eux, la chute de l’Astre est une libération.',
              '',
              '*Un seul camp à la fois. Tu peux en changer en cliquant sur l’autre.*',
            ].join('\n')),
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('🔔 Notifications')
            .setDescription('Clique pour activer ou désactiver. Tu ne seras mentionné que pour ce que tu choisis.'),
        ],
        components: [
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('role:lumiere').setLabel('Lumière').setEmoji('☀️').setStyle(ButtonStyle.Secondary),
            new ButtonBuilder().setCustomId('role:ombre').setLabel('Ombre').setEmoji('🌑').setStyle(ButtonStyle.Secondary),
          ),
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('role:notif_annonces').setLabel('Annonces').setEmoji('📢').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_devblog').setLabel('Devblog').setEmoji('📰').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_events').setLabel('Événements').setEmoji('🎉').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_tests').setLabel('Sessions de test').setEmoji('🧪').setStyle(ButtonStyle.Primary),
          ),
        ],
      };

    case 'liens': {
      const labels = { site: '🌐 Site', boutique: '🛒 Boutique', youtube: '▶️ YouTube', tiktok: '🎵 TikTok', x: '𝕏 X', twitch: '🟣 Twitch' };
      const links = Object.entries(cfg.links || {}).filter(([, url]) => url && /^https?:\/\//.test(url));
      const embed = new EmbedBuilder()
        .setColor(BLUE)
        .setTitle('🔗 Liens utiles')
        .addFields(
          { name: '🎮 Adresse du serveur', value: address ? `\`${address}\`` : 'Bientôt disponible' },
          { name: '🧩 Version', value: cfg.minecraft?.version || 'À venir', inline: true },
        )
        .setFooter({ text: DISCLAIMER });
      if (!links.length) embed.addFields({ name: '🌐 Réseaux', value: 'Les liens arrivent bientôt.' });
      const components = links.length
        ? [new ActionRowBuilder().addComponents(
            links.slice(0, 5).map(([k, url]) => new ButtonBuilder().setStyle(ButtonStyle.Link).setURL(url).setLabel(labels[k] || k)),
          )]
        : [];
      return { embeds: [embed], components };
    }

    case 'jeu':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('🌌 L’univers d’ORBIT MC')
            .setDescription([
              '**L’Astre** était une étoile vivante, source de toute magie. Il y a mille ans, il s’est brisé.',
              'Ses fragments, les **Éclats**, sont tombés sur le continent. À sa place, une déchirure dans le ciel : **le Néant**.',
              '',
              'Mille ans plus tard, lors de la **Nuit des Étoiles Filantes**, les Éclats se remettent à tomber.',
              'Certains ont été touchés et y ont survécu. On les appelle les **Marqués**. Tu en fais partie.',
              '',
              SEP,
              '**Deux factions, sept classes.**',
              'Guerrier · Chasseur · Mage · Prêtre · Paladin · Druide · Astromancien',
              SEP,
              `🛠️ **État du projet :** en développement. Suis l’avancement dans ${ch(ids, 'devblog')} et ${ch(ids, 'changelog')}.`,
            ].join('\n'))
            .setFooter(footer),
          new EmbedBuilder().setColor(GOLD).setTitle('☀️ Lumière').setDescription('Capitale : **le Bastion de l’Aube**\nFeu, foudre et magie sacrée. Ils veulent faire renaître l’Astre.'),
          new EmbedBuilder().setColor(VIOLET).setTitle('🌑 Ombre').setDescription('Capitale : **la Citadelle d’Obsidienne**\nMagie noire, nécromancie et poisons. Le Néant est l’avenir.'),
        ],
      };

    case 'tickets':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('🎫 Support privé')
            .setDescription([
              'Un souci qui ne peut pas se régler en public ? Ouvre un ticket : un salon privé entre toi et le staff.',
              '',
              '• Signaler un joueur',
              '• Contester une sanction',
              '• Problème de compte ou d’achat',
              '• Autre demande privée',
              '',
              `*Pour une question générale, utilise plutôt ${ch(ids, 'aide')}. Pour un bug, ${ch(ids, 'bugs')}.*`,
            ].join('\n')),
        ],
        components: [
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('ticket:open').setLabel('Ouvrir un ticket').setEmoji('🎫').setStyle(ButtonStyle.Primary),
          ),
        ],
      };

    case 'statut':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('📡 Statut des serveurs')
            .setDescription('En attente du bot… Le statut s’affichera ici dès que `npm start` tourne.'),
        ],
      };

    default:
      throw new Error(`Message inconnu : ${key}`);
  }
}

module.exports = { build, BLUE };
