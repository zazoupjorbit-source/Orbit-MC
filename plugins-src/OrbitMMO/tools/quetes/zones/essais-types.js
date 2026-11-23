// Zone d'essai des types de quêtes du 23/11 (escorte, défense, choix…) — RÉSERVÉE AUX ADMINS (orbitmmo.admin).
// Une quête par type, toutes proposées par l'Essayeur, sans prérequis, niveau 1, les deux camps.
// Installation : node tools\quetes\gen-quetes.js --installer, /quetes admin recharger, puis sur un terrain plat
// d'environ 90 × 90 blocs : /quetes admin poser essais-types (les points sont posés autour de vous), /quetes admin recharger.
// Explication de chaque type : docs\quetes-types.md.
module.exports = {
  fichier: 'essais-types',
  admin: true,
  entete: ["Zone d'essai des types de quêtes — réservée à l'administration (admin: true).",
    "Points relatifs : /quetes admin poser essais-types sur un terrain plat d'environ 90 × 90 blocs."],

  pnj: {
    essayeur: { n: ["L'Essayeur", 'The Tester'], t: ['Quêtes d\'essai', 'Test quests'], c: '#FFD66E', v: 'steve',
      r: [["Une quête par type, proposées l'une après l'autre. Pour en choisir une : /quetes admin demarrer.", 'One quest per type, offered one after another. To pick one: /quetes admin demarrer.']] },
    messager_essai: { n: ['Messagère', 'Messenger'], t: ['Livraisons et enquêtes', 'Deliveries and investigations'], c: '#9FD8FF', v: 'alex',
      r: [["J'attends des lettres. Et des conclusions.", 'I am waiting for letters. And conclusions.']] },
    pelerin_essai: { n: ['Pèlerin', 'Pilgrim'], t: ['À escorter', 'To escort'], c: '#C9D6A3', v: 'sunny',
      r: [["Je n'irai pas seul jusqu'au col.", 'I will not go to the pass alone.']] },
  },

  monstres: {
    essai_rodeur: { nom: "Rôdeur d'essai", type: 'HUSK', pv: 0.6, degats: 0.6, vitesse: 1.0 },
    essai_garde: { nom: "Garde d'essai", type: 'VINDICATOR', pv: 1.0, degats: 0.8, vitesse: 0.9 },
    essai_sentinelle: { nom: "Sentinelle d'essai", type: 'HUSK', taille: 1.15, pv: 1.0, degats: 0.8, vitesse: 0.9 },
  },

  apparitions: {
    essai_gardes_camp: ['garde_camp', 'essai_garde', 3, 3, 4],
    essai_sentinelles: ['deguise_camp', 'essai_sentinelle', 3, 4, 5],
  },

  // [dx, dy, dz] autour de l'admin qui lance /quetes admin poser essais-types (x vers l'est, z vers le sud).
  pointsRelatifs: {
    essayeur: [0, 0, 0], messager_essai: [4, 0, 6], pelerin_essai: [6, 0, -4],
    escorte_1: [20, 0, -4], escorte_2: [20, 0, -20], escorte_fin: [36, 0, -20],
    defense_porte: [-20, 0, 0], defense_entree: [-36, 0, 0],
    indice_1: [-8, 0, 14], indice_2: [-14, 0, 20], indice_3: [-4, 0, 24],
    infiltre_depart: [10, 0, 26], garde_camp: [20, 0, 36], infiltre_but: [30, 0, 44],
    trace_1: [-18, 0, -12], trace_2: [-28, 0, -20], trace_3: [-24, 0, -32], trace_fin: [-40, 0, -40],
    chrono_but: [0, 0, -34],
    deguise_camp: [28, 0, 14], deguise_but: [34, 0, 12],
    survie_centre: [-30, 0, 40],
    transport_depart: [8, 0, -10], transport_but: [44, 0, 0],
  },

  quetes: [
    { titre: 'ESSAIS — UNE QUÊTE PAR TYPE' },

    { id: 't_escorte', n: ['Essai : escorte', 'Test: escort'], lv: 1, g: 'essayeur',
      res: [["Escorter le Pèlerin jusqu'au col.", 'Escort the Pilgrim to the pass.']],
      off: [['essayeur', "Le Pèlerin part vers le col. Deux embuscades l'attendent. S'il tombe, il repart du début.", 'The Pilgrim is heading for the pass. Two ambushes await. If he falls, he starts over.']],
      s: [['E', 'pelerin_essai', ['escorte_1', 'escorte_2', 'escorte_fin'], ['Escorter le Pèlerin', 'Escort the Pilgrim'],
        { monstres: ['essai_rodeur'], nombre: 2, vagues: 2, pv: 80, degats: 4 },
        [['pelerin_essai', "Merci. Je n'y serais jamais arrivé seul.", 'Thank you. I would never have made it alone.']]]],
      xp: 50, or: 10 },

    { id: 't_defense', n: ['Essai : défense', 'Test: defence'], lv: 1, g: 'essayeur',
      res: [['Tenir la porte pendant trois vagues.', 'Hold the gate for three waves.']],
      off: [['essayeur', "La porte, à l'ouest. Trois vagues. Si elle tombe, on recommence.", 'The gate, to the west. Three waves. If it falls, we start again.']],
      s: [['D', 'defense_porte', ['Défendre la porte', 'Defend the gate'],
        { cible: ['La porte', 'The gate'], monstres: ['essai_rodeur'], nombre: 3, vagues: 3, apparition: 'defense_entree', pv: 150, degats: 5 },
        [['-', 'La dernière vague se disperse. La porte tient.', 'The last wave scatters. The gate holds.']]]],
      xp: 50, or: 10 },

    { id: 't_choix', n: ['Essai : dialogue à choix', 'Test: choice dialogue'], lv: 1, g: 'essayeur',
      res: [["Répondre à l'Essayeur. La suite dépend de la réponse.", "Answer the Tester. What follows depends on the answer."]],
      off: [['essayeur', "Une question, et votre réponse comptera.", 'One question, and your answer will matter.']],
      s: [['Q', 'essayeur', 'essai-pacte', ["Répondre à l'Essayeur", 'Answer the Tester'],
        [['essayeur', "Un inconnu demande de l'aide. Il porte la marque de l'autre camp.", 'A stranger asks for help. He bears the mark of the other side.']],
        ['Que faites-vous ?', 'What do you do?'],
        [['aider', ["Je l'aide.", 'I help him.'], [['essayeur', "Généreux. On s'en souviendra.", 'Generous. It will be remembered.']], { xp: 20 }],
          ['refuser', ["Je le laisse.", 'I leave him.'], [['essayeur', "Prudent. On s'en souviendra aussi.", 'Careful. That will be remembered too.']], { or: 15 }]]],
        // Branche : une seule de ces deux étapes est jouée, selon la réponse.
        ['P', 'messager_essai', ['Annoncer votre aide à la Messagère', 'Tell the Messenger you helped'],
          [['messager_essai', "Vous l'avez aidé ? Alors une suite vous attend chez l'Essayeur.", 'You helped him? Then a follow-up awaits you with the Tester.']], { si: 'essai-pacte=aider' }],
        ['P', 'messager_essai', ['Rendre compte à la Messagère', 'Report to the Messenger'],
          [['messager_essai', "Vous l'avez laissé. Pas de suite, cette fois.", 'You left him. No follow-up this time.']], { si: 'essai-pacte=refuser' }]],
      xp: 50, or: 10 },

    { id: 't_choix_suite', n: ['Essai : suite du choix', 'Test: choice follow-up'], lv: 1, g: 'essayeur', req: ['t_choix', 'choix:essai-pacte=aider'],
      res: [["Seulement si vous avez aidé l'inconnu.", 'Only if you helped the stranger.']],
      off: [['essayeur', "L'inconnu vous remercie. Cette quête n'existe que parce que vous l'avez aidé.", 'The stranger thanks you. This quest only exists because you helped him.']],
      s: [['P', 'essayeur', ["Recevoir le remerciement", 'Receive the thanks'], [['essayeur', 'Branche vérifiée.', 'Branch checked.']]]],
      xp: 20, or: 5 },

    { id: 't_livraison', n: ['Essai : livraison', 'Test: delivery'], lv: 1, g: 'essayeur',
      res: [['Porter une lettre à la Messagère.', 'Carry a letter to the Messenger.']],
      off: [['essayeur', "Cette lettre, pour la Messagère. Elle ne se jette pas, et si vous la perdez, on vous en refait une.", 'This letter, for the Messenger. It cannot be thrown away, and if you lose it, you get another.']],
      s: [['L', 'messager_essai', ["Lettre de l'Essayeur", "The Tester's letter"], 'PAPER', ['Remettre la lettre à la Messagère', 'Deliver the letter to the Messenger'],
        [['messager_essai', 'Une lettre ! Merci.', 'A letter! Thank you.']]]],
      xp: 50, or: 10 },

    { id: 't_enquete', n: ['Essai : enquête', 'Test: investigation'], lv: 1, g: 'essayeur',
      res: [['Trois indices, dans le désordre, puis conclure.', 'Three clues, in any order, then conclude.']],
      off: [['essayeur', "Trois indices au sud. L'ordre n'a pas d'importance. Puis allez conclure chez la Messagère.", 'Three clues to the south. Order does not matter. Then go and conclude with the Messenger.']],
      s: [['X', [
          ['indice_1', ['Une empreinte de botte', 'A boot print'], [['-', 'Une botte ferrée. Lourde.', 'An iron-shod boot. Heavy.']]],
          ['indice_2', ['Un bout de tissu', 'A scrap of cloth'], [['-', "Du tissu teint en rouge. L'uniforme d'une garde.", "Cloth dyed red. A guard's uniform."]]],
          ['indice_3', ['Une lame brisée', 'A broken blade'], [['-', 'La lame porte un poinçon.', 'The blade bears a hallmark.']]]],
        'messager_essai', ['Examiner les indices', 'Examine the clues'],
        [['j', 'Une garde, en bottes ferrées, lame brisée.', 'A guard, in iron-shod boots, broken blade.'], ['messager_essai', 'Bien vu. Enquête close.', 'Well spotted. Case closed.']]]],
      xp: 50, or: 10 },

    { id: 't_infiltration', n: ['Essai : infiltration', 'Test: infiltration'], lv: 1, g: 'essayeur',
      res: [['Traverser le camp sans être vu.', 'Cross the camp unseen.']],
      off: [['essayeur', "Des gardes au sud-est. Passez derrière eux, accroupi. S'ils vous voient, retour au départ.", 'Guards to the south-east. Go behind them, sneaking. If they see you, back to the start.']],
      s: [['A', 'infiltre_depart', 3, ["Rejoindre l'entrée du camp", 'Reach the camp entrance']],
        ['F', 'infiltre_but', 'infiltre_depart', ['essai_garde'], ['Traverser le camp sans être vu', 'Cross the camp unseen'], { vue: 10, rayon: 3 },
          [['-', 'Personne ne vous a vu.', 'Nobody saw you.']]]],
      xp: 50, or: 10 },

    { id: 't_pistage', n: ['Essai : pistage', 'Test: tracking'], lv: 1, g: 'essayeur',
      res: [['Suivre les traces au sol.', 'Follow the tracks on the ground.']],
      off: [['essayeur', 'Des traces partent au nord-ouest. Suivez-les, une à une.', 'Tracks lead north-west. Follow them, one by one.']],
      s: [['T', ['trace_1', 'trace_2', 'trace_3', 'trace_fin'], ['Suivre les traces', 'Follow the tracks'],
        [['-', "Les traces s'arrêtent ici. Quelque chose a été enterré.", 'The tracks stop here. Something was buried.']]]],
      xp: 50, or: 10 },

    { id: 't_chrono', n: ['Essai : contre la montre', 'Test: against the clock'], lv: 1, g: 'essayeur',
      res: [['Courir au nord en 30 secondes.', 'Run north within 30 seconds.']],
      off: [['essayeur', 'Au nord, une balise. Trente secondes. Partez !', 'To the north, a beacon. Thirty seconds. Go!']],
      s: [['C', 30, ['A', 'chrono_but', 3, ['Atteindre la balise à temps', 'Reach the beacon in time'], [['-', 'Juste à temps.', 'Just in time.']]],
        [['essayeur', 'Trop lent. On recommence.', 'Too slow. Again.']]]],
      xp: 50, or: 10 },

    { id: 't_deguisement', n: ['Essai : déguisement', 'Test: disguise'], lv: 1, g: 'essayeur',
      res: [['Entrer dans le camp des sentinelles, déguisé.', 'Enter the sentries\' camp in disguise.']],
      off: [['essayeur', "Portez cette tenue : les sentinelles vous laisseront passer. Frappez-en une, et le déguisement tombe.", 'Wear this outfit: the sentries will let you through. Strike one, and the disguise is blown.']],
      s: [['G', 'deguise_but', ['essai_sentinelle'], ['Tenue de sentinelle', 'Sentry outfit'], 'LEATHER_CHESTPLATE', '#6B4A2B',
        ['Entrer dans le camp déguisé', 'Enter the camp in disguise'], [['-', "Personne ne vous a reconnu.", 'Nobody recognised you.']]]],
      xp: 50, or: 10 },

    { id: 't_survie', n: ['Essai : survie', 'Test: survival'], lv: 1, g: 'essayeur',
      res: [['Tenir dans une zone qui se resserre, puis qui brûle.', 'Hold out in a zone that shrinks, then burns.']],
      off: [['essayeur', "Au sud-ouest, un cercle. Il va se resserrer. Puis le sol va brûler. Ne mourez pas.", 'To the south-west, a circle. It will shrink. Then the ground will burn. Do not die.']],
      s: [['V', 'survie_centre', 14, 30, ['Tenir dans le cercle', 'Hold out in the circle'], { mode: 'resserre', rayonFin: 4, degats: 5 }],
        ['V', 'survie_centre', 12, 30, ['Esquiver les foyers', 'Dodge the fires'], { mode: 'brule', degats: 5, monstres: ['essai_rodeur'], nombre: 1, intervalle: 15 },
          [['-', 'Le feu retombe.', 'The fire dies down.']]]],
      xp: 50, or: 10 },

    { id: 't_transport', n: ['Essai : transport', 'Test: transport'], lv: 1, g: 'essayeur',
      res: [['Ramener une caisse à dos de monture.', 'Bring back a crate on a mount.']],
      off: [['essayeur', "Une caisse au nord-est, à charger sur votre monture. Un coup, ou pied à terre, et elle tombe.", 'A crate to the north-east, to load on your mount. One hit, or getting off, and it falls.']],
      s: [['M', 'transport_but', ['Caisse de vivres', 'Supply crate'], 'transport_depart', ['Ramener la caisse à dos de monture', 'Bring the crate back on a mount'], { materiau: 'BARREL', rayon: 4 },
        [['-', 'La caisse arrive intacte.', 'The crate arrives intact.']]]],
      xp: 50, or: 10 },
  ],
};
