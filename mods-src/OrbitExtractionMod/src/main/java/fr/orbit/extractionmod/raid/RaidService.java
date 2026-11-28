package fr.orbit.extractionmod.raid;

import fr.orbit.extractionmod.carte.Carte;
import fr.orbit.extractionmod.carte.Generateur;
import fr.orbit.extractionmod.gun.GunItem;
import fr.orbit.extractionmod.net.Payloads;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Boucle de raid : camp (zone sûre) → porte dorée ou /raid → largage en périphérie de Cendreville →
 * 25 minutes pour rejoindre une balise de sortie et y tenir 8 secondes. Mort ou temps écoulé : le sac reste sur place.
 */
public final class RaidService {
	private RaidService() {
	}

	public static final int DUREE = 25 * 60 * 20;
	private static final Random RNG = new Random();

	private static final class Raid {
		int fin;
		int sortie;
		boolean parachute;
		String largage;
	}

	private static final Map<UUID, Raid> raids = new HashMap<>();
	private static final Map<UUID, Integer> extractions = new HashMap<>();
	private static boolean actif;

	public static boolean actif() {
		return actif;
	}

	public static boolean enRaid(ServerPlayer p) {
		return raids.containsKey(p.getUUID());
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(s -> actif = Generateur.mondePlat(s));
		ServerTickEvents.END_SERVER_TICK.register(RaidService::tick);
		ServerPlayConnectionEvents.JOIN.register((h, sender, server) -> server.execute(() -> rejoindre(h.player)));
		ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
				!(actif && entity instanceof ServerPlayer p && Carte.dansCamp(p.getX(), p.getZ())));
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (actif && entity instanceof ServerPlayer p && raids.remove(p.getUUID()) != null) {
				p.level().getServer().getPlayerList().broadcastSystemMessage(
						Component.literal("☠ " + p.getName().getString() + " est tombé à " + Carte.NOM + ".").withColor(0xE74C3C), false);
			}
		});
		ServerPlayerEvents.AFTER_RESPAWN.register((oldP, p, alive) -> {
			if (actif) {
				p.level().getServer().execute(() -> {
					auCamp(p);
					kit(p, false);
				});
			}
		});
	}

	// ------------------------------------------------------------------ arrivée, camp

	private static void rejoindre(ServerPlayer p) {
		if (!actif) {
			// Monde de test (pas Cendreville) : simple kit d'essai.
			kit(p, true);
			return;
		}
		if (!p.isCreative() && !p.isSpectator()) {
			p.setGameMode(GameType.ADVENTURE);
		}
		if (raids.containsKey(p.getUUID())) {
			p.sendSystemMessage(Component.literal("[Orbit] Raid repris : il te reste " + temps(raids.get(p.getUUID()).fin
					- p.level().getServer().getTickCount()) + ".").withColor(0xF2C14E));
		} else {
			auCamp(p);
			kit(p, false);
			p.sendSystemMessage(Component.literal("Bienvenue au camp ORBIT. Franchis la porte dorée (ou /raid) pour être largué sur "
					+ Carte.NOM + ". Rejoins une balise jaune pour t'extraire.").withColor(0xF2C14E));
		}
	}

	public static void auCamp(ServerPlayer p) {
		ServerLevel l = p.level().getServer().overworld();
		p.teleportTo(l, Carte.CAMP_X + 0.5, Carte.SOL + 1, Carte.CAMP_Z - 6 + 0.5, Set.<Relative>of(), 0f, 0f, true);
		p.removeEffect(MobEffects.SLOW_FALLING);
		p.setHealth(p.getMaxHealth());
		p.getFoodData().setFoodLevel(20);
	}

	/** Kit de base (si le joueur n'a aucune arme) : de quoi repartir, jamais plus. */
	public static void kit(ServerPlayer p, boolean essai) {
		for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
			if (p.getInventory().getItem(i).getItem() instanceof GunItem) {
				return;
			}
		}
		p.getInventory().add(new ItemStack(ModItems.G18));
		p.getInventory().add(new ItemStack(ModItems.AMMO_9MM, essai ? 64 : 34));
		p.getInventory().add(new ItemStack(ModItems.BANDAGE, 2));
		if (essai) {
			p.sendSystemMessage(Component.literal("[Orbit] Prototype moddé : clic droit pour tirer, R pour recharger.").withColor(0xF2C14E));
		}
	}

	// ------------------------------------------------------------------ départ, fin

	public static boolean partir(ServerPlayer p) {
		if (!actif || raids.containsKey(p.getUUID())) {
			return false;
		}
		if (Generateur.enCours()) {
			p.sendOverlayMessage(Component.literal(Generateur.etat()).withColor(0xE74C3C));
			return false;
		}
		MinecraftServer server = p.level().getServer();
		List<Carte.Point> pts = Carte.LARGAGES;
		Carte.Point pt = pts.get(RNG.nextInt(pts.size()));
		Raid r = new Raid();
		r.fin = server.getTickCount() + DUREE;
		r.parachute = true;
		r.largage = pt.nom();
		raids.put(p.getUUID(), r);
		float yaw = (float) Math.toDegrees(Math.atan2(-(0 - pt.x()), (0 - pt.z()))); // regard vers le centre
		p.teleportTo(server.overworld(), pt.x() + 0.5, Carte.SOL + 30, pt.z() + 0.5, Set.<Relative>of(), yaw, 20f, true);
		p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30 * 20, 0, false, false));
		titre(p, "§6§lCENDREVILLE", "§fLargage " + pt.nom() + " §7· rejoins une §ebalise jaune", 10, 60, 20);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.5f, 1.2f);
		envoyer(p, server.getTickCount());
		return true;
	}

	private static void extraire(ServerPlayer p) {
		raids.remove(p.getUUID());
		int n = extractions.merge(p.getUUID(), 1, Integer::sum);
		auCamp(p);
		titre(p, "§6§lEXTRAIT !", "§fTon butin est sauf §7· raid réussi n°" + n, 5, 60, 20);
		p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8f, 1.0f);
		p.level().getServer().getPlayerList().broadcastSystemMessage(
				Component.literal("✔ " + p.getName().getString() + " s'est extrait de " + Carte.NOM + ".").withColor(0x2ECC71), false);
	}

	/** Pour les tests (op) : extraction immédiate. */
	public static boolean forcerExtraction(ServerPlayer p) {
		if (!raids.containsKey(p.getUUID())) {
			return false;
		}
		extraire(p);
		return true;
	}

	// ------------------------------------------------------------------ boucle

	private static void tick(MinecraftServer server) {
		if (!actif) {
			return;
		}
		int now = server.getTickCount();
		for (ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (p.isSpectator() || p.isDeadOrDying()) {
				continue;
			}
			Raid r = raids.get(p.getUUID());
			if (r == null) {
				if (dansPorte(p)) {
					partir(p);
				} else if (!p.isCreative() && !Carte.dansCamp(p.getX(), p.getZ()) && !Carte.dansCarte(p.getX(), p.getZ())) {
					auCamp(p);
				}
				if (now % 10 == 0) {
					ServerPlayNetworking.send(p, new Payloads.RaidSync(1, 0, 0));
				}
				continue;
			}
			int reste = r.fin - now;
			if (r.parachute && p.onGround()) {
				r.parachute = false;
				p.removeEffect(MobEffects.SLOW_FALLING);
			}
			if (reste == 5 * 60 * 20 || reste == 60 * 20) {
				p.sendSystemMessage(Component.literal("⚠ Plus que " + temps(reste) + " avant la fermeture des sorties !").withColor(0xE74C3C));
				p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_BELL, SoundSource.PLAYERS, 1f, 0.6f);
			}
			if (reste <= 0) {
				raids.remove(p.getUUID());
				p.sendSystemMessage(Component.literal("Les sorties sont fermées. " + Carte.NOM + " t'a englouti.").withColor(0xE74C3C));
				p.kill((ServerLevel) p.level());
				continue;
			}
			// Sortie : rester 8 s dans le cercle jaune d'une balise.
			Carte.Point s = sortieProche(p.getX(), p.getZ(), 4.5);
			if (s != null) {
				if (r.sortie == 0) {
					InfecteService.alerter((ServerLevel) p.level(), p, 40); // la balise attire les infectés
				}
				r.sortie++;
				if (r.sortie % 20 == 0) {
					p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.NOTE_BLOCK_PLING, SoundSource.PLAYERS, 0.6f,
							0.8f + r.sortie / (float) Payloads.RaidSync.SORTIE_TICKS);
				}
				if (r.sortie >= Payloads.RaidSync.SORTIE_TICKS) {
					extraire(p);
					ServerPlayNetworking.send(p, new Payloads.RaidSync(1, 0, 0));
					continue;
				}
			} else {
				r.sortie = 0;
			}
			if (now % 5 == 0) {
				envoyer(p, now);
			}
		}
	}

	private static void envoyer(ServerPlayer p, int now) {
		Raid r = raids.get(p.getUUID());
		if (r != null) {
			ServerPlayNetworking.send(p, new Payloads.RaidSync(2, Math.max(0, r.fin - now), r.sortie));
		}
	}

	private static boolean dansPorte(ServerPlayer p) {
		return p.getX() >= Carte.PORTE_X0 && p.getX() < Carte.PORTE_X1 + 1 && p.getZ() >= Carte.PORTE_Z0
				&& p.getZ() < Carte.PORTE_Z1 + 1 && p.getY() < Carte.SOL + 6;
	}

	public static Carte.Point sortieProche(double x, double z, double rayon) {
		for (Carte.Point s : Carte.SORTIES) {
			double dx = x - (s.x() + 0.5), dz = z - (s.z() + 0.5);
			if (dx * dx + dz * dz <= rayon * rayon) {
				return s;
			}
		}
		return null;
	}

	private static void titre(ServerPlayer p, String titre, String sous, int in, int stay, int out) {
		p.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
		p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal(sous)));
		p.connection.send(new ClientboundSetTitleTextPacket(Component.literal(titre)));
	}

	public static String temps(int ticks) {
		int s = Math.max(0, ticks) / 20;
		return String.format("%d:%02d", s / 60, s % 60);
	}
}
