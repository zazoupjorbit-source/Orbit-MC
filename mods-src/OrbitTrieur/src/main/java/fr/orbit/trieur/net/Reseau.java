package fr.orbit.trieur.net;

import fr.orbit.trieur.TrieurConfig;
import fr.orbit.trieur.bloc.TrieurBlockEntity;
import fr.orbit.trieur.tri.Moteur;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Côté serveur du panel : envoi de l'état, réception et validation des réglages, commande de rechargement. */
public final class Reseau {
	private Reseau() {
	}

	/** Distance maximale (au carré) entre le joueur et le trieur pour le régler. */
	private static final double PORTEE2 = 10 * 10;

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Demander.TYPE, (p, ctx) -> {
			TrieurBlockEntity be = trieurAPortee(ctx.player(), p.pos());
			if (be != null) {
				envoyerReglages(ctx.player(), be);
			}
		});
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Enregistrer.TYPE, (p, ctx) -> enregistrer(ctx.player(), p));

		CommandRegistrationCallback.EVENT.register((d, reg, env) -> d.register(Commands.literal("orbittrieur")
				.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
				.then(Commands.literal("recharger").executes(c -> {
					TrieurConfig.charger();
					c.getSource().sendSuccess(() -> Component.translatable("orbittrieur.commande.recharge"), true);
					return 1;
				}))));
	}

	private static TrieurBlockEntity trieurAPortee(ServerPlayer player, BlockPos pos) {
		Level level = player.level();
		if (!level.isLoaded(pos) || player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) > PORTEE2) {
			return null;
		}
		return level.getBlockEntity(pos) instanceof TrieurBlockEntity be ? be : null;
	}

	/** Rescanne le rayon et envoie au joueur la liste des inventaires avec leurs filtres. */
	public static void envoyerReglages(ServerPlayer player, TrieurBlockEntity be) {
		Level level = be.getLevel();
		if (level == null) {
			return;
		}
		BlockPos origine = be.getBlockPos();
		Moteur.scanner(level, origine, be);
		List<Payloads.Cible> cibles = new ArrayList<>();
		for (BlockPos p : be.etat.cibles) {
			BlockState st = level.getBlockState(p);
			ItemStack icone = new ItemStack(st.getBlock().asItem());
			String nom = st.getBlock().getName().getString();
			if (nom.length() > 120) {
				nom = nom.substring(0, 120);
			}
			cibles.add(new Payloads.Cible(p, icone, nom, be.filtreA(p), be.estSurplus(p),
					Moteur.apercu(Moteur.stockage(level, p, be.etat), 16)));
		}
		int attente = 0;
		for (int i = 0; i < be.getContainerSize(); i++) {
			attente += be.getItem(i).getCount();
		}
		ServerPlayNetworking.send(player, new Payloads.Reglages(origine, be.actif, be.regrouper, be.rayon,
				TrieurConfig.get().rayonMax, attente, cibles));
	}

	private static void enregistrer(ServerPlayer player, Payloads.Enregistrer p) {
		TrieurBlockEntity be = trieurAPortee(player, p.pos());
		if (be == null) {
			return;
		}
		int rayonMax = TrieurConfig.get().rayonMax;
		int rayon = Math.clamp(p.rayon(), 1, rayonMax);
		BlockPos origine = be.getBlockPos();
		// On n'accepte que des positions dans le rayon maximal, sans doublon.
		Set<BlockPos> vus = new HashSet<>();
		List<Map.Entry<BlockPos, String>> regles = new ArrayList<>();
		for (Payloads.Regle r : p.regles()) {
			BlockPos d = r.pos().subtract(origine);
			if (Math.abs(d.getX()) > rayonMax || Math.abs(d.getY()) > rayonMax || Math.abs(d.getZ()) > rayonMax
					|| !vus.add(r.pos()) || regles.size() >= TrieurConfig.get().maxCibles) {
				continue;
			}
			String f = r.filtre();
			if (f.length() > Payloads.MAX_FILTRE) {
				f = f.substring(0, Payloads.MAX_FILTRE);
			}
			regles.add(new AbstractMap.SimpleEntry<>(r.pos(), f));
		}
		BlockPos surplus = p.surplus().filter(s -> {
			BlockPos d = s.subtract(origine);
			return Math.abs(d.getX()) <= rayonMax && Math.abs(d.getY()) <= rayonMax && Math.abs(d.getZ()) <= rayonMax;
		}).orElse(null);
		be.appliquer(p.actif(), p.regrouper(), rayon, regles, surplus);
		if (p.rescan()) {
			envoyerReglages(player, be);
		} else {
			player.sendSystemMessage(Component.translatable("orbittrieur.panel.enregistre"), true);
		}
	}
}
