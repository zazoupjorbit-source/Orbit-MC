package fr.orbit.extractionmod.raid;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import fr.orbit.extractionmod.carte.Carte;
import fr.orbit.extractionmod.carte.Generateur;
import fr.orbit.extractionmod.gun.Ammo;
import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * /raid : partir depuis le camp.
 * /orbit … (op) : carte generer [forcer] | carte etat | kit | infectes N | infectes nettoyer | caisses | raid fin | camp.
 */
public final class Commandes {
	private Commandes() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> enregistrer(d));
	}

	private static Component txt(String s) {
		return Component.literal(s).withColor(0xF2C14E);
	}

	private static void enregistrer(CommandDispatcher<CommandSourceStack> d) {
		d.register(Commands.literal("raid").executes(c -> {
			ServerPlayer p = c.getSource().getPlayerOrException();
			if (!RaidService.actif()) {
				c.getSource().sendFailure(Component.literal("Pas de carte d'extraction sur ce serveur."));
				return 0;
			}
			if (RaidService.enRaid(p)) {
				c.getSource().sendFailure(Component.literal("Tu es déjà en raid."));
				return 0;
			}
			if (!Carte.dansCamp(p.getX(), p.getZ())) {
				c.getSource().sendFailure(Component.literal("Il faut être au camp pour partir."));
				return 0;
			}
			return RaidService.partir(p) ? 1 : 0;
		}));

		d.register(Commands.literal("orbit").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("carte")
						.then(Commands.literal("generer")
								.executes(c -> generer(c.getSource(), false))
								.then(Commands.literal("forcer").executes(c -> generer(c.getSource(), true))))
						.then(Commands.literal("image").executes(c -> {
							java.nio.file.Path p = Generateur.image(c.getSource().getServer());
							c.getSource().sendSuccess(() -> txt("Plan dessiné : " + p.getFileName()), false);
							return 1;
						}))
						.then(Commands.literal("etat").executes(c -> {
							c.getSource().sendSuccess(() -> txt(Generateur.etat() + " Plan : " + Generateur.plan().elements.size()
									+ " éléments, " + Generateur.plan().caisses.size() + " caisses."), false);
							return 1;
						})))
				.then(Commands.literal("kit").executes(c -> {
					ServerPlayer p = c.getSource().getPlayerOrException();
					for (GunItem g : ModItems.ARMES.values()) {
						p.getInventory().add(new ItemStack(g));
					}
					for (Ammo a : Ammo.values()) {
						p.getInventory().add(new ItemStack(ModItems.MUNITIONS.get(a), 64));
					}
					p.getInventory().add(new ItemStack(ModItems.BANDAGE, 8));
					p.getInventory().add(new ItemStack(ModItems.TROUSSE, 2));
					c.getSource().sendSuccess(() -> txt("Arsenal complet donné."), false);
					return 1;
				}))
				.then(Commands.literal("infectes")
						.then(Commands.literal("nettoyer").executes(c -> {
							int n = InfecteService.nettoyer(c.getSource().getLevel());
							c.getSource().sendSuccess(() -> txt(n + " infectés retirés."), false);
							return n;
						}))
						.then(Commands.argument("nombre", IntegerArgumentType.integer(1, 40)).executes(c -> {
							int n = InfecteService.invoquer(c.getSource().getPlayerOrException(), IntegerArgumentType.getInteger(c, "nombre"));
							c.getSource().sendSuccess(() -> txt(n + " infectés autour de toi."), false);
							return n;
						})))
				.then(Commands.literal("caisses").executes(c -> {
					int n = LootService.remplirAutour(c.getSource().getPlayerOrException(), 32);
					c.getSource().sendSuccess(() -> txt(n + " caisses remplies dans un rayon de 32 blocs."), false);
					return n;
				}))
				.then(Commands.literal("raid").then(Commands.literal("fin").executes(c -> {
					boolean ok = RaidService.forcerExtraction(c.getSource().getPlayerOrException());
					if (!ok) {
						c.getSource().sendFailure(Component.literal("Tu n'es pas en raid."));
					}
					return ok ? 1 : 0;
				})))
				.then(Commands.literal("camp").executes(c -> {
					RaidService.auCamp(c.getSource().getPlayerOrException());
					return 1;
				})));
	}

	private static int generer(CommandSourceStack src, boolean forcer) {
		if (!Generateur.mondePlat(src.getServer())) {
			src.sendFailure(Component.literal("Ce monde n'est pas plat : génération refusée (elle écraserait le terrain)."));
			return 0;
		}
		Generateur.demarrer(src.getServer(), forcer);
		src.sendSuccess(() -> txt("Génération de " + Carte.NOM + " lancée" + (forcer ? " (remise à zéro des tronçons)" : "") + "."), true);
		return 1;
	}
}
