// Post #devblog : sortie du mod Trieur ORBIT (fait pour une demande sur le Discord Minecraft FR).
const path = require('path');
const { EmbedBuilder, ActionRowBuilder, ButtonBuilder, ButtonStyle } = require('discord.js');

const MOD = path.join(__dirname, '..', '..', 'mods-src', 'OrbitTrieur');
const GOLD = 0xffd66e;
const BLUE = 0x3a7fff;

module.exports = {
  key: 'trieur',
  channel: 'devblog',
  build: (ids) => ({
    embeds: [
      new EmbedBuilder()
        .setColor(GOLD)
        .setTitle('🧩 Nouveau mod : Trieur ORBIT')
        .setDescription([
          'Premier projet fait pour la communauté : un **bloc trieur** qui range tout seul tes objets dans les coffres autour de lui.',
          'Il nous a été demandé sur le Discord **Minecraft FR**, et on l’a fait **gratuitement**.',
          '',
          '**Ce qu’il fait**',
          '• Un tampon de **54 cases** (comme un grand coffre) : tu y jettes tout, il répartit.',
          '• Il trouve les coffres, tonneaux et autres stockages dans un **rayon réglable** (16 par défaut, jusqu’à 64).',
          '• Un **panel en jeu** pour régler chaque coffre : bouton **Réglages**, ou accroupi + clic droit sur le trieur.',
          '• Un coffre **surplus** reçoit tout ce que les autres refusent.',
          '• Le mode **Regrouper** envoie un objet sans règle là où il y en a déjà.',
          '• Compatible avec les stockages des autres mods qui utilisent l’**API de transfert Fabric**.',
          '',
          '**Les filtres** (séparés par des virgules)',
          '`diamond` un objet · `#c:ingots` un tag · `@create` tout un mod',
          '`*_log` un joker · `~épée` un nom · `!cobblestone` exclure',
        ].join('\n'))
        .setImage('attachment://trieur-panel.png'),
      new EmbedBuilder()
        .setColor(BLUE)
        .setTitle('📥 Installation')
        .setDescription([
          '**1.** Installe **Fabric** pour Minecraft **26.3** (loader 0.20.1 ou plus) et **Java 25**.',
          '**2.** Ajoute **Fabric API** dans ton dossier `mods`.',
          '**3.** Télécharge **OrbitTrieur-1.0.0.jar** (fichier joint à ce message) et mets-le aussi dans `mods`.',
          '**4.** Sur un serveur, le mod doit être installé **côté serveur et côté joueurs**.',
          '',
          '**Recette** (établi)',
          '```',
          'Fer      Entonnoir   Fer',
          'Or       Coffre      Or',
          'Fer      Comparateur Fer',
          '```',
          'Côté serveur, les limites (rayon max…) se règlent dans `config/orbittrieur.json`, puis `/orbittrieur recharger`.',
          '',
          `🐞 Un bug, une idée d’amélioration ? Dis-le dans ${ids.p_autres ? `<#${ids.p_autres}>` : '#autres-projets'}.`,
          `📨 Toi aussi tu as un projet ? ${ids.demander ? `<#${ids.demander}>` : '#demander-un-projet'}.`,
        ].join('\n'))
        .setImage('attachment://trieur-tampon.png')
        .setFooter({ text: 'Trieur ORBIT 1.0.0 · Fabric 26.3 · ORBIT STUDIO · Non affilié à Mojang ou Microsoft' }),
    ],
    components: [
      new ActionRowBuilder().addComponents(
        new ButtonBuilder().setStyle(ButtonStyle.Link).setURL('https://fabricmc.net/use/installer/').setLabel('Installer Fabric'),
        new ButtonBuilder().setStyle(ButtonStyle.Link).setURL('https://modrinth.com/mod/fabric-api').setLabel('Fabric API'),
      ),
    ],
    files: [
      { attachment: path.join(MOD, 'build', 'libs', 'OrbitTrieur.jar'), name: 'OrbitTrieur-1.0.0.jar' },
      { attachment: path.join(MOD, 'apercu-panel.png'), name: 'trieur-panel.png' },
      { attachment: path.join(MOD, 'apercu-tampon.png'), name: 'trieur-tampon.png' },
    ],
  }),
};
