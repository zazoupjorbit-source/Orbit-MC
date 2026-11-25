// Structure complète du serveur Discord ORBIT MC.
// Modifie ce fichier puis relance `npm run setup` : le script met à jour sans rien dupliquer.
const { PermissionFlagsBits: P } = require('discord.js');

// ─── Groupes de rôles ────────────────────────────────────────────────
const STAFF = ['admin', 'dev', 'modo', 'support'];
const TEAM = [...STAFF, 'builder', 'scenariste'];

// ─── Rôles (du plus haut au plus bas) ───────────────────────────────
const ROLES = [
  // Staff
  { key: 'fondateur', name: '👑 Fondateur', color: 0x3a7fff, hoist: true, permissions: [P.Administrator] },
  { key: 'admin', name: '🛠️ Administrateur', color: 0xe74c3c, hoist: true, permissions: [P.Administrator] },
  { key: 'dev', name: '⚙️ Développeur', color: 0x5dade2, hoist: true,
    permissions: [P.ManageMessages, P.ManageThreads, P.MentionEveryone] },
  { key: 'modo', name: '🛡️ Modérateur', color: 0x2ecc71, hoist: true,
    permissions: [P.KickMembers, P.BanMembers, P.ModerateMembers, P.ManageMessages, P.ManageThreads,
      P.ManageNicknames, P.MuteMembers, P.DeafenMembers, P.MoveMembers, P.ViewAuditLog, P.MentionEveryone] },
  { key: 'support', name: '🎧 Support', color: 0x1abc9c, hoist: true,
    permissions: [P.ManageMessages, P.ManageThreads, P.ModerateMembers] },
  // Équipe
  { key: 'builder', name: '🏗️ Builder', color: 0xe67e22, hoist: true },
  { key: 'scenariste', name: '📜 Scénariste', color: 0xc39bd3, hoist: true },
  // Communauté
  { key: 'createur', name: '🎥 Créateur de contenu', color: 0xff4d8d, hoist: true },
  { key: 'partenaire', name: '🤝 Partenaire', color: 0x95a5a6 },
  { key: 'beta', name: '🧪 Bêta-testeur', color: 0x7f8c8d },
  // Factions (choisies dans #rôles, exclusives)
  { key: 'lumiere', name: '☀️ Lumière', color: 0xffd66e, hoist: true },
  { key: 'ombre', name: '🌑 Ombre', color: 0xb36bff, hoist: true },
  // Membre vérifié (donné en acceptant le règlement)
  { key: 'marque', name: '✦ Marqué', color: 0x8fa3bf },
  // Notifications (cochées dans #rôles)
  { key: 'notif_annonces', name: '🔔 Annonces' },
  { key: 'notif_devblog', name: '🔔 Devblog' },
  { key: 'notif_events', name: '🔔 Événements' },
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
const membersOnly = () => [ow('everyone', [], [P.ViewChannel]), ow('marque', VIEW)];
/** Visible uniquement par certains rôles. */
const restrictedTo = (keys) => [ow('everyone', [], [P.ViewChannel]), ...keys.map((k) => ow(k, [...VIEW, ...WRITE, P.Connect, P.Speak]))];
/** Lecture seule pour `viewer`, écriture pour `posters`. */
const readOnly = (viewer, posters = [], { reactions = true } = {}) => [
  ow(viewer, reactions ? [...VIEW, P.AddReactions] : VIEW, reactions ? WRITE : [...WRITE, P.AddReactions]),
  ...posters.map((k) => ow(k, [...VIEW, P.SendMessages, P.AttachFiles, P.EmbedLinks])),
];

// ─── Catégories & salons ────────────────────────────────────────────
// type : text | voice | forum | announcement  (forum/announcement → texte si le serveur n'est pas "Communauté")
// Les `overwrites` d'un salon remplacent ceux de sa catégorie pour les mêmes cibles.
const CATEGORIES = [
  {
    key: 'cat_bienvenue', name: '📌 BIENVENUE',
    overwrites: readOnly('everyone', [], { reactions: false }),
    channels: [
      { key: 'bienvenue', name: '👋・bienvenue', topic: 'Bienvenue sur ORBIT MC. Commence ici.', message: 'bienvenue' },
      { key: 'reglement', name: '📜・règlement', topic: 'Lis et accepte le règlement pour accéder au serveur.', message: 'reglement' },
      { key: 'annonces', name: '📢・annonces', type: 'announcement', topic: 'Annonces officielles ORBIT MC.',
        overwrites: readOnly('everyone', ['admin', 'dev']) },
      { key: 'roles', name: '🎭・rôles', topic: 'Choisis ta faction et tes notifications.', message: 'roles',
        overwrites: [ow('everyone', [], [P.ViewChannel]), ...readOnly('marque', [], { reactions: false })] },
      { key: 'liens', name: '🔗・liens', topic: 'Adresse du serveur, site et réseaux.', message: 'liens' },
    ],
  },
  {
    key: 'cat_orbit', name: '🌌 ORBIT MC',
    overwrites: [ow('everyone', [], [P.ViewChannel]), ...readOnly('marque')],
    channels: [
      { key: 'jeu', name: '🌌・le-jeu', topic: "L'univers d'ORBIT MC : l'Astre, les Éclats, les Marqués.", message: 'jeu' },
      { key: 'devblog', name: '📰・devblog', type: 'announcement', topic: 'Les coulisses du développement.',
        overwrites: readOnly('marque', ['admin', 'dev', 'scenariste']) },
      { key: 'changelog', name: '🛠️・changelog', topic: 'Toutes les mises à jour, version par version.',
        overwrites: readOnly('marque', ['admin', 'dev']) },
      { key: 'statut', name: '📡・statut-serveurs', topic: 'État des serveurs, mis à jour automatiquement.', message: 'statut' },
      { key: 'chroniques', name: '📖・chroniques', topic: 'Les récits du monde, écrits par les scénaristes.',
        overwrites: readOnly('marque', ['admin', 'scenariste']) },
      { key: 'evenements', name: '🎉・événements', topic: 'Événements en jeu et sur le Discord.',
        overwrites: readOnly('marque', ['admin', 'modo', 'dev']) },
    ],
  },
  {
    key: 'cat_communaute', name: '💬 COMMUNAUTÉ',
    overwrites: membersOnly(),
    channels: [
      { key: 'general', name: '💬・général', topic: 'Discussion générale autour d’ORBIT MC.' },
      { key: 'medias', name: '🖼️・médias', topic: 'Captures, vidéos, builds. Partage tes plus beaux moments.', slowmode: 30 },
      { key: 'groupe', name: '🤝・recherche-de-groupe', topic: 'Trouve des coéquipiers pour jouer.', slowmode: 60 },
      { key: 'suggestions', name: '💡・suggestions', type: 'forum', topic: 'Une idée pour le serveur ? Un post par suggestion.' },
      { key: 'commandes', name: '🤖・commandes', topic: 'Les commandes de bots, c’est ici.' },
      { key: 'hors_sujet', name: '🌀・hors-sujet', topic: 'Tout ce qui ne concerne pas ORBIT MC.' },
    ],
  },
  {
    key: 'cat_modes', name: '⚔️ MODES DE JEU',
    overwrites: membersOnly(),
    channels: [
      { key: 'mode_survie', name: '🌍・survie', topic: 'Discussions autour du mode Survie.' },
      { key: 'mode_extraction', name: '⛏️・extraction', topic: 'Discussions autour du mode Extraction.' },
      { key: 'mode_practice', name: '🗡️・practice', topic: 'Discussions autour du mode Practice.' },
      { key: 'mode_reborn', name: '🔁・reborn', topic: 'Discussions autour du mode Reborn.' },
    ],
  },
  {
    key: 'cat_lumiere', name: '☀️ LUMIÈRE',
    overwrites: restrictedTo(['lumiere', 'modo']),
    channels: [
      { key: 'lumiere_txt', name: '☀️・bastion-de-l-aube', topic: 'Le quartier général des gardiens de l’Astre.' },
      { key: 'lumiere_voc', name: '🔊 Bastion de l’Aube', type: 'voice' },
    ],
  },
  {
    key: 'cat_ombre', name: '🌑 OMBRE',
    overwrites: restrictedTo(['ombre', 'modo']),
    channels: [
      { key: 'ombre_txt', name: '🌑・citadelle-d-obsidienne', topic: 'Le repaire de ceux qui ont choisi le Néant.' },
      { key: 'ombre_voc', name: '🔊 Citadelle d’Obsidienne', type: 'voice' },
    ],
  },
  {
    key: 'cat_support', name: '🎫 SUPPORT',
    overwrites: membersOnly(),
    channels: [
      { key: 'aide', name: '❓・aide', type: 'forum', topic: 'Pose ta question, la communauté et le staff te répondent.' },
      { key: 'bugs', name: '🐞・bugs', type: 'forum', topic: 'Un bug ? Décris-le : ce que tu faisais, ce qui s’est passé, captures.' },
      { key: 'tickets', name: '🎫・tickets', topic: 'Ouvre un ticket privé avec le staff.', message: 'tickets',
        overwrites: readOnly('marque', [], { reactions: false }) },
    ],
  },
  {
    key: 'cat_vocaux', name: '🔊 VOCAUX',
    overwrites: membersOnly(),
    channels: [
      { key: 'voc_taverne', name: '🍺 Taverne', type: 'voice' },
      { key: 'voc_g1', name: '⚔️ Groupe I', type: 'voice', userLimit: 5 },
      { key: 'voc_g2', name: '⚔️ Groupe II', type: 'voice', userLimit: 5 },
      { key: 'voc_g3', name: '⚔️ Groupe III', type: 'voice', userLimit: 5 },
      { key: 'voc_duel', name: '🗡️ Duel', type: 'voice', userLimit: 2 },
      { key: 'voc_afk', name: '💤 AFK', type: 'voice' },
    ],
  },
  {
    key: 'cat_beta', name: '🧪 BÊTA',
    overwrites: restrictedTo(['beta', ...TEAM]),
    channels: [
      { key: 'beta_infos', name: '🧪・bêta-infos', topic: 'Infos des sessions de test.',
        overwrites: readOnly('beta', ['admin', 'dev']) },
      { key: 'beta_retours', name: '📝・bêta-retours', topic: 'Vos retours sur les sessions de test.' },
      { key: 'beta_voc', name: '🔊 Session de test', type: 'voice' },
    ],
  },
  {
    key: 'cat_staff', name: '🔒 STAFF',
    overwrites: restrictedTo(TEAM),
    channels: [
      { key: 'staff_general', name: '🛡️・staff', topic: 'Discussion de l’équipe.' },
      // Panneau du guide du staff, posté et tenu à jour par le bot (src/guide.js, dossier guide/).
      { key: 'staff_guide', name: '📖・guide', topic: 'Guide du staff ORBIT MC : plugins, commandes admin, procédures. Réponses privées.',
        overwrites: [ow('builder', [], [P.ViewChannel]), ow('scenariste', [], [P.ViewChannel]),
          ...readOnly('admin', [], { reactions: false }), ...readOnly('dev', [], { reactions: false }),
          ...readOnly('modo', [], { reactions: false }), ...readOnly('support', [], { reactions: false })] },
      { key: 'staff_logs', name: '📋・logs', topic: 'Journal automatique : tickets, actions du bot.',
        overwrites: [ow('builder', [], [P.ViewChannel]), ow('scenariste', [], [P.ViewChannel]),
          ...readOnly('modo'), ...readOnly('support')] },
      { key: 'staff_dev', name: '⚙️・dev', topic: 'Plugins, OrbitCore, infra.' },
      { key: 'staff_build', name: '🏗️・build', topic: 'Avancement des maps et constructions.' },
      { key: 'staff_lore', name: '📜・lore', topic: 'Écriture du lore. Le canon fait foi.' },
      { key: 'staff_voc', name: '🔊 Réunion', type: 'voice' },
    ],
  },
  {
    // Les tickets ouverts par le bot arrivent ici (catégorie vide au départ).
    key: 'cat_tickets', name: '📨 TICKETS OUVERTS',
    overwrites: restrictedTo(['support', 'modo']),
    channels: [],
  },
];

module.exports = { ROLES, EVERYONE_PERMISSIONS, CATEGORIES, STAFF, TEAM };
