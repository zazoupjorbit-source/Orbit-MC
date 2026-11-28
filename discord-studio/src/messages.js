// Messages fixes postés par `npm run setup` (édités en place si on relance).
const { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } = require('discord.js');

const BLUE = 0x3a7fff;
const GOLD = 0xffd66e;
const SEP = '━━━━━━━━━━━━━━━━━━━━━━';
const FOOTER = { text: 'ORBIT STUDIO' };
const DISCLAIMER = 'Les projets Minecraft d’ORBIT STUDIO ne sont pas des produits officiels Minecraft. Non approuvés par Mojang ou Microsoft, ni associés à eux.';

// Types proposés dans le formulaire « Demander un projet ».
const TYPES_PROJET = [
  { value: 'mode', label: 'Mode de jeu Minecraft', emoji: '🎮' },
  { value: 'plugin', label: 'Plugin (Paper / Spigot)', emoji: '🧩' },
  { value: 'mod', label: 'Mod (Fabric / Forge / NeoForge)', emoji: '🧱' },
  { value: 'map', label: 'Map ou génération de monde', emoji: '🗺️' },
  { value: 'gmod', label: 'Addon Garry’s Mod', emoji: '🔧' },
  { value: 'discord', label: 'Bot ou serveur Discord', emoji: '🤖' },
  { value: 'web', label: 'Site ou panel web', emoji: '🌐' },
  { value: 'autre', label: 'Autre', emoji: '✨' },
];

// Étapes d'une demande de projet.
const STATUTS = {
  recue: { label: 'Reçue', emoji: '📥', color: 0x8fa3bf, texte: 'Le studio va lire ta demande.' },
  acceptee: { label: 'Acceptée', emoji: '✅', color: 0x2ecc71, texte: 'Le studio prend ton projet.' },
  en_cours: { label: 'En cours', emoji: '🛠️', color: BLUE, texte: 'Le développement a commencé.' },
  test: { label: 'En test', emoji: '🧪', color: 0xb36bff, texte: 'Une version est prête à tester.' },
  livree: { label: 'Livrée', emoji: '🏁', color: GOLD, texte: 'Le projet est terminé et livré.' },
  refusee: { label: 'Refusée', emoji: '❌', color: 0xe74c3c, texte: 'Le studio ne peut pas prendre ce projet.' },
};

const ch = (ids, key) => (ids[key] ? `<#${ids[key]}>` : `#${key}`);

function build(key, cfg, ids) {
  switch (key) {
    case 'bienvenue':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('✦ Bienvenue chez ORBIT STUDIO')
            .setDescription([
              'Le studio de développement derrière **ORBIT MC** et ses projets : modes Minecraft, plugins, mods, addons, outils.',
              '',
              SEP,
              '**Pour commencer**',
              `**1.** Lis le ${ch(ids, 'reglement')} et clique sur **J’accepte**.`,
              `**2.** Choisis tes notifications dans ${ch(ids, 'roles')}.`,
              `**3.** Suis l’actu dans ${ch(ids, 'devblog')} et ${ch(ids, 'vitrine')}.`,
              SEP,
              `📨 **Une idée de projet ?** Fais ta demande dans ${ch(ids, 'demander')}.`,
              `💬 Viens discuter dans ${ch(ids, 'general')}.`,
            ].join('\n'))
            .setFooter(FOOTER),
        ],
      };

    case 'reglement':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(BLUE)
            .setTitle('📜 Règlement d’ORBIT STUDIO')
            .setDescription([
              '**1. Respect.** Pas d’insultes, de harcèlement, de menaces ni de discrimination.',
              '**2. Contenu.** Rien de NSFW, choquant ou illégal, y compris pseudo, avatar et statut.',
              '**3. Spam & pub.** Pas de flood, de mentions abusives ni de publicité non autorisée (MP compris).',
              '**4. Bons salons.** Chaque salon a un sujet, respecte-le.',
              '**5. Demandes de projet.** Passe par le formulaire, une demande par projet. Relancer en boucle ne la fait pas avancer plus vite.',
              '**6. Code et fichiers.** Ne partage pas de code, de mods ou de fichiers qui ne t’appartiennent pas (leaks, crack, revente).',
              '**7. Vie privée.** Ne partage jamais les informations personnelles de quelqu’un d’autre, ni de token ou de mot de passe.',
              '**8. Discord.** Les [Conditions d’utilisation](https://discord.com/terms) et les [Règles de la communauté](https://discord.com/guidelines) de Discord s’appliquent ici.',
              '',
              SEP,
              '**Sanctions :** avertissement → exclusion temporaire → bannissement, selon la gravité.',
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
            .setTitle('🔔 Notifications')
            .setDescription([
              'Clique pour activer ou désactiver. Tu ne seras mentionné que pour ce que tu choisis.',
              '',
              '📢 **Annonces** · les nouvelles importantes du studio',
              '📰 **Devblog** · les coulisses du développement',
              '🏁 **Sorties** · un projet est terminé ou une mise à jour sort',
              '🧪 **Sessions de test** · on cherche des testeurs',
            ].join('\n')),
        ],
        components: [
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('role:notif_annonces').setLabel('Annonces').setEmoji('📢').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_devblog').setLabel('Devblog').setEmoji('📰').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_sorties').setLabel('Sorties').setEmoji('🏁').setStyle(ButtonStyle.Primary),
            new ButtonBuilder().setCustomId('role:notif_tests').setLabel('Sessions de test').setEmoji('🧪').setStyle(ButtonStyle.Primary),
          ),
        ],
      };

    case 'liens': {
      const labels = { site: '🌐 Site', github: '💻 GitHub', youtube: '▶️ YouTube', tiktok: '🎵 TikTok', x: '𝕏 X', orbitmc: '🌌 Discord ORBIT MC' };
      const links = Object.entries(cfg.links || {}).filter(([, url]) => url && /^https?:\/\//.test(url));
      const embed = new EmbedBuilder()
        .setColor(BLUE)
        .setTitle('🔗 Liens du studio')
        .setDescription(links.length ? 'Retrouve-nous ici :' : 'Les liens arrivent bientôt.')
        .setFooter({ text: DISCLAIMER });
      const components = links.length
        ? [new ActionRowBuilder().addComponents(
            links.slice(0, 5).map(([k, url]) => new ButtonBuilder().setStyle(ButtonStyle.Link).setURL(url).setLabel(labels[k] || k)),
          )]
        : [];
      return { embeds: [embed], components };
    }

    case 'demander':
      return {
        embeds: [
          new EmbedBuilder()
            .setColor(GOLD)
            .setTitle('📨 Demander un projet')
            .setDescription([
              'Tu as besoin d’un mode de jeu, d’un plugin, d’un mod, d’une map ou d’un outil ? Décris-le au studio.',
              '',
              '**Comment ça marche**',
              '**1.** Clique sur **Demander un projet** et remplis le formulaire.',
              '**2.** Un salon privé s’ouvre entre toi et le studio pour en parler.',
              '**3.** Le studio accepte ou non le projet, puis le fait avancer : en cours, en test, livré.',
              `**4.** Les projets acceptés apparaissent dans ${ch(ids, 'suivi')}.`,
              '',
              SEP,
              '**Pour une demande claire**',
              '• Le but du projet en une phrase',
              '• La plateforme et la version (Paper 1.21.4, Fabric 26.3…)',
              '• Ce que le joueur doit pouvoir faire, étape par étape',
              '• Des exemples ou des images si tu en as',
              '',
              `*Pas encore de projet précis ? Lance plutôt l’idée dans ${ch(ids, 'idees')}.*`,
            ].join('\n'))
            .setFooter({ text: 'Le studio choisit les projets qu’il prend selon son temps libre. Rien n’est garanti.' }),
        ],
        components: [
          new ActionRowBuilder().addComponents(
            new ButtonBuilder().setCustomId('projet:demander').setLabel('Demander un projet').setEmoji('📨').setStyle(ButtonStyle.Success),
            new ButtonBuilder().setCustomId('projet:mes').setLabel('Mes demandes').setEmoji('📂').setStyle(ButtonStyle.Secondary),
          ),
        ],
      };

    default:
      throw new Error(`Message inconnu : ${key}`);
  }
}

module.exports = { build, BLUE, GOLD, STATUTS, TYPES_PROJET, FOOTER };
