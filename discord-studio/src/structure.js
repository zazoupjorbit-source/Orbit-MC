// Structure complète du serveur Discord ORBIT STUDIO.
// Modifie ce fichier puis relance `npm run setup` : le script met à jour sans rien dupliquer.
const { PermissionFlagsBits: P } = require('discord.js');

// ─── Groupes de rôles ────────────────────────────────────────────────
const STUDIO = ['fondateur', 'dev'];

// ─── Rôles (du plus haut au plus bas) ───────────────────────────────
const ROLES = [
  // Studio
  { key: 'fondateur', name: '👑 Fondateur', color: 0x3a7fff, hoist: true, permissions: [P.Administrator] },
  { key: 'dev', name: '⚙️ Développeur', color: 0x5dade2, hoist: true,
    permissions: [P.ManageMessages, P.ManageThreads, P.MentionEveryone, P.ModerateMembers, P.KickMembers, P.ViewAuditLog] },
  // Communauté
  { key: 'porteur', name: '🚀 Porteur de projet', color: 0xffd66e, hoist: true },
  { key: 'partenaire', name: '🤝 Partenaire', color: 0x95a5a6, hoist: true },
  { key: 'beta', name: '🧪 Bêta-testeur', color: 0x7f8c8d },
  // Membre vérifié (donné en acceptant le règlement)
  { key: 'membre', name: '✦ Membre', color: 0x8fa3bf },
  // Notifications (cochées dans #rôles)
  { key: 'notif_annonces', name: '🔔 Annonces' },
  { key: 'notif_devblog', name: '🔔 Devblog' },
  { key: 'notif_sorties', name: '🔔 Sorties' },
  { key: 'notif_tests', name: '🔔 Sessions de test' },
];

// Permissions de base de @everyone (appliquées au niveau du serveur)
const EVERYONE_PERMISSIONS = [
  P.ViewChannel, P.SendMessages, P.SendMessagesInThreads, P.CreatePublicThreads, P.ReadMessageHistory,
  P.AddReactions, P.EmbedLinks, P.AttachFiles, P.UseExternalEmojis, P.UseExternalStickers,
  P.UseApplicationCommands, P.ChangeNickname, P.Connect, P.Speak, P.Stream, P.UseVAD,
  P.UseEmbeddedActivities, P.UseSoundboard,
];

// ─── Helpers de permissions ─────────────────────────────────────────
const VIEW = [P.ViewChannel, P.ReadMessageHistory];
const WRITE = [P.SendMessages, P.SendMessagesInThreads, P.CreatePublicThreads, P.CreatePrivateThreads];
const ow = (target, allow = [], deny = []) => ({ target, allow, deny });

/** Visible uniquement par les membres ayant accepté le règlement. */
const membersOnly = () => [ow('everyone', [], [P.ViewChannel]), ow('membre', VIEW)];
/** Visible uniquement par certains rôles. */
const restrictedTo = (keys) => [ow('everyone', [], [P.ViewChannel]), ...keys.map((k) => ow(k, [...VIEW, ...WRITE, P.Connect, P.Speak]))];
/** Lecture seule pour `viewer`, écriture pour `posters`. */
const readOnly = (viewer, posters = [], { reactions = true } = {}) => [
  ow(viewer, reactions ? [...VIEW, P.AddReactions] : VIEW, reactions ? WRITE : [...WRITE, P.AddReactions]),
  ...posters.map((k) => ow(k, [...VIEW, P.SendMessages, P.AttachFiles, P.EmbedLinks])),
];
/** Membres seulement, en lecture seule, l'équipe écrit. */
const membersReadOnly = (opts) => [ow('everyone', [], [P.ViewChannel]), ...readOnly('membre', STUDIO, opts)];

// ─── Catégories & salons ────────────────────────────────────────────
// type : text | voice | forum | announcement  (forum/announcement → texte si le serveur n'est pas "Communauté")
// Les `overwrites` d'un salon remplacent ceux de sa catégorie pour les mêmes cibles.
const CATEGORIES = [
  {
    key: 'cat_accueil', name: '📌 ACCUEIL',
    overwrites: readOnly('everyone', [], { reactions: false }),
    channels: [
      { key: 'bienvenue', name: '👋・bienvenue', topic: 'Bienvenue chez ORBIT STUDIO. Commence ici.', message: 'bienvenue' },
      { key: 'reglement', name: '📜・règlement', topic: 'Lis et accepte le règlement pour accéder au serveur.', message: 'reglement' },
      { key: 'annonces', name: '📢・annonces', type: 'announcement', topic: 'Annonces officielles du studio.',
        overwrites: readOnly('everyone', STUDIO) },
      { key: 'roles', name: '🎭・rôles', topic: 'Choisis tes notifications.', message: 'roles',
        overwrites: [ow('everyone', [], [P.ViewChannel]), ...readOnly('membre', [], { reactions: false })] },
      { key: 'liens', name: '🔗・liens', topic: 'Site, GitHub et réseaux du studio.', message: 'liens' },
    ],
  },
  {
    key: 'cat_actu', name: '🛰️ ACTUALITÉ',
    overwrites: membersReadOnly(),
    channels: [
      { key: 'devblog', name: '📰・devblog', type: 'announcement', topic: 'Les coulisses du développement, projet par projet.' },
      { key: 'changelog', name: '🛠️・changelog', topic: 'Toutes les mises à jour, version par version.' },
      { key: 'vitrine', name: '🏆・vitrine', topic: 'Nos réalisations terminées : captures, vidéos, liens.' },
      { key: 'suivi', name: '📊・suivi-des-projets', topic: 'Les projets demandés par la communauté et leur avancement. Mis à jour par le bot.' },
    ],
  },
  {
    key: 'cat_projets', name: '🧩 NOS PROJETS',
    overwrites: membersOnly(),
    channels: [
      { key: 'p_orbitmc', type: 'forum', name: '⛏️・minecraft', topic: 'Portfolio : toutes nos créations Minecraft disponibles pour la communauté.' },
      { key: 'p_gmod', type: 'forum', name: '🔧・garrys-mod', topic: 'Addons et modes Garry’s Mod.' },
      { key: 'p_ark', type: 'forum', name: '🦖・ark', topic: 'Serveurs et outils ARK: Survival Ascended.' },
      { key: 'p_autres', type: 'forum', name: '🧪・autres-projets', topic: 'Sites, bots, prototypes et tout le reste.' },
    ],
  },
  {
    key: 'cat_demandes', name: '📨 DEMANDES DE PROJET',
    overwrites: membersOnly(),
    channels: [
      { key: 'demander', name: '📨・demander-un-projet', topic: 'Tu veux un mode, un plugin, un mod ? Clique sur le bouton et décris ton projet.',
        message: 'demander', overwrites: membersReadOnly({ reactions: false }) },
      { key: 'idees', name: '💡・idées-de-projets', type: 'forum', topic: 'Une idée sans demande précise ? Lance la discussion, la communauté vote.' },
    ],
  },
  {
    key: 'cat_communaute', name: '💬 COMMUNAUTÉ',
    overwrites: membersOnly(),
    channels: [
      { key: 'general', name: '💬・général', topic: 'Discussion générale autour du studio.' },
      { key: 'medias', name: '🖼️・médias', topic: 'Captures, vidéos, tes créations.', slowmode: 30 },
      { key: 'entraide', name: '❓・entraide-dev', type: 'forum', topic: 'Une question de dev (Java, Lua, configs, serveurs) ? Un post par question.' },
      { key: 'suggestions', name: '🗳️・suggestions', type: 'forum', topic: 'Une idée pour améliorer le Discord ou le studio ?' },
      { key: 'hors_sujet', name: '🌀・hors-sujet', topic: 'Tout le reste.' },
    ],
  },
  {
    key: 'cat_vocaux', name: '🔊 VOCAUX',
    overwrites: membersOnly(),
    channels: [
      { key: 'voc_salon', name: '🛋️ Salon', type: 'voice' },
      { key: 'voc_live', name: '🎥 Dev en direct', type: 'voice' },
      { key: 'voc_test', name: '🎮 Session de jeu', type: 'voice' },
      { key: 'voc_afk', name: '💤 AFK', type: 'voice' },
    ],
  },
  {
    key: 'cat_beta', name: '🧪 BÊTA',
    overwrites: restrictedTo(['beta', 'porteur', ...STUDIO]),
    channels: [
      { key: 'beta_infos', name: '🧪・bêta-infos', topic: 'Infos des sessions de test.', overwrites: readOnly('beta', STUDIO) },
      { key: 'beta_retours', name: '📝・bêta-retours', topic: 'Vos retours sur les versions de test.' },
    ],
  },
  {
    key: 'cat_studio', name: '🔒 STUDIO',
    overwrites: restrictedTo(STUDIO),
    channels: [
      { key: 'studio_general', name: '🛰️・studio', topic: 'Discussion entre fondateurs et devs.' },
      { key: 'studio_idees', name: '🧠・idées', topic: 'Idées en vrac, à trier.' },
      { key: 'studio_logs', name: '📋・logs', topic: 'Journal automatique : demandes de projet, actions du bot.' },
      { key: 'studio_voc', name: '🔊 Réunion', type: 'voice' },
    ],
  },
  {
    // Chaque demande de projet ouvre ici un salon privé (demandeur + studio).
    key: 'cat_demandes_ouvertes', name: '📂 DEMANDES OUVERTES',
    overwrites: restrictedTo(STUDIO),
    channels: [],
  },
];

module.exports = { ROLES, EVERYONE_PERMISSIONS, CATEGORIES, STUDIO };
