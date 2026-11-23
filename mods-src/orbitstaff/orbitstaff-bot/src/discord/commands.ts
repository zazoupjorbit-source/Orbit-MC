import { SlashCommandBuilder } from "discord.js";

/**
 * Definitions des commandes slash. Uniquement des commandes slash, boutons et
 * formulaires : jamais de commande console arbitraire, jamais d'execution de
 * commande Minecraft libre.
 */

const uuidOption = (name: string, description: string, required = true) =>
  (o: import("discord.js").SlashCommandStringOption) =>
    o.setName(name).setDescription(description).setRequired(required);

const link = new SlashCommandBuilder()
  .setName("link")
  .setDescription("Lier votre compte Discord a votre compte Minecraft")
  .addStringOption((o) => o.setName("code").setDescription("Code affiche en jeu par /discord link").setRequired(true));

const player = new SlashCommandBuilder()
  .setName("player")
  .setDescription("Consultation d'un joueur")
  .addSubcommand((s) =>
    s.setName("consulter").setDescription("Dossier d'un joueur").addStringOption(uuidOption("uuid", "UUID du joueur")))
  .addSubcommand((s) =>
    s.setName("sanctions").setDescription("Historique de moderation").addStringOption(uuidOption("uuid", "UUID du joueur")))
  .addSubcommand((s) =>
    s.setName("notes").setDescription("Notes internes (a venir)").addStringOption(uuidOption("uuid", "UUID du joueur")))
  .addSubcommand((s) =>
    s.setName("rechercher").setDescription("Rechercher un joueur (a venir)").addStringOption(uuidOption("pseudo", "Pseudo")));

function modSanction(name: string, description: string, withDuration: boolean) {
  return (s: import("discord.js").SlashCommandSubcommandBuilder) => {
    s.setName(name).setDescription(description).addStringOption(uuidOption("uuid", "UUID de la cible"));
    if (withDuration) {
      s.addStringOption((o) => o.setName("duree").setDescription("Ex: 30m, 2h, 7d, permanent").setRequired(true));
    }
    s.addStringOption((o) => o.setName("motif").setDescription("Motif").setRequired(true));
    s.addStringOption((o) => o.setName("acteur").setDescription("UUID du staff auteur (requis pour ban)").setRequired(false));
    return s;
  };
}

const mod = new SlashCommandBuilder()
  .setName("mod")
  .setDescription("Actions de moderation")
  .addSubcommand(modSanction("warn", "Avertir", false))
  .addSubcommand(modSanction("mute", "Reduire au silence", true))
  .addSubcommand(modSanction("kick", "Expulser", false))
  .addSubcommand(modSanction("tempban", "Bannir temporairement", true))
  .addSubcommand(modSanction("ban", "Bannir", false))
  .addSubcommand((s) =>
    s.setName("note").setDescription("Ajouter une note interne")
      .addStringOption(uuidOption("uuid", "UUID de la cible"))
      .addStringOption((o) => o.setName("contenu").setDescription("Contenu de la note").setRequired(true))
      .addStringOption((o) => o.setName("acteur").setDescription("UUID du staff auteur").setRequired(true)))
  .addSubcommand((s) =>
    s.setName("dossier").setDescription("Consulter le dossier").addStringOption(uuidOption("uuid", "UUID de la cible")))
  .addSubcommand((s) =>
    s.setName("unban").setDescription("Annuler un bannissement (a venir)").addStringOption(uuidOption("uuid", "UUID de la cible")));

const server = new SlashCommandBuilder()
  .setName("server")
  .setDescription("Etat du serveur")
  .addSubcommand((s) => s.setName("statut").setDescription("Disponibilite du service OrbitStaff"))
  .addSubcommand((s) => s.setName("joueurs").setDescription("Joueurs en ligne (a venir)"))
  .addSubcommand((s) => s.setName("tps").setDescription("TPS du serveur (a venir)"));

const staff = new SlashCommandBuilder()
  .setName("staff")
  .setDescription("Informations staff")
  .addSubcommand((s) => s.setName("en-ligne").setDescription("Staff en ligne (a venir)"))
  .addSubcommand((s) => s.setName("activite").setDescription("Activite staff (a venir)"))
  .addSubcommand((s) => s.setName("statistiques").setDescription("Statistiques staff (a venir)"));

/** Corps JSON des commandes, prets pour l'enregistrement REST. */
export const commandDefinitions = [link, player, mod, server, staff].map((builder) => builder.toJSON());
