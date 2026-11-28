package fr.orbit.extractionmod.carte;

import fr.orbit.extractionmod.OrbitExtractionMod;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Génère Cendreville tronçon par tronçon, sans geler le serveur (~30 ms par tick).
 * Démarre tout seul au premier lancement (pas de fichier repère dans le monde), ou via /orbit carte generer.
 */
public final class Generateur {
	private Generateur() {
	}

	private static final String REPERE = "orbit-cendreville.txt";
	private static final long BUDGET_NS = 30_000_000L;

	private static Plan plan;
	private static final Deque<Long> file = new ArrayDeque<>();
	private static boolean forcer;
	private static int total, faits, dernierPalier;
	private static long debut;

	public static Plan plan() {
		if (plan == null) {
			long t = System.currentTimeMillis();
			plan = new Plan();
			OrbitExtractionMod.LOG.info("[Cendreville] Plan : {} éléments, {} tronçons, {} caisses ({} ms)",
					plan.elements.size(), plan.troncons.size(), plan.caisses.size(), System.currentTimeMillis() - t);
		}
		return plan;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			if (!mondePlat(server)) {
				OrbitExtractionMod.LOG.info("[Cendreville] Monde non plat : carte non générée (créer un monde plat ou passer level-type=minecraft:flat).");
				return;
			}
			reglages(server);
			if (!Files.exists(repere(server))) {
				OrbitExtractionMod.LOG.info("[Cendreville] Première ouverture : génération de la carte.");
				demarrer(server, false);
			} else if (!Files.exists(server.getWorldPath(LevelResource.ROOT).resolve("cendreville-plan.png"))) {
				image(server);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(Generateur::tick);
	}

	private static Path repere(MinecraftServer s) {
		return s.getWorldPath(LevelResource.ROOT).resolve(REPERE);
	}

	/** La carte ne se dessine que dans un monde plat (sécurité : jamais par-dessus un monde existant). */
	public static boolean mondePlat(MinecraftServer server) {
		return server.overworld().getChunkSource().getGenerator() instanceof FlatLevelSource;
	}

	/** Vrai quand ce serveur fait tourner Cendreville (monde plat + carte générée ou en cours). */
	public static boolean actif(MinecraftServer server) {
		return mondePlat(server);
	}

	public static boolean enCours() {
		return !file.isEmpty();
	}

	public static String etat() {
		if (file.isEmpty()) {
			return total == 0 ? "Aucune génération lancée depuis le démarrage." : "Carte terminée (" + total + " tronçons).";
		}
		return "Génération : " + faits + " / " + total + " tronçons (" + (faits * 100 / Math.max(1, total)) + " %).";
	}

	public static void demarrer(MinecraftServer server, boolean raz) {
		Plan p = plan();
		file.clear();
		file.addAll(p.troncons);
		forcer = raz;
		total = file.size();
		faits = 0;
		dernierPalier = 0;
		debut = System.currentTimeMillis();
		reglages(server);
	}

	/** Règles du monde d'extraction : pas de cycle jour/nuit, pas de monstres vanilla, pas de casse. */
	public static void reglages(MinecraftServer server) {
		ServerLevel l = server.overworld();
		GameRules g = l.getGameRules();
		g.set(GameRules.ADVANCE_TIME, false, server);
		g.set(GameRules.ADVANCE_WEATHER, false, server);
		g.set(GameRules.SPAWN_MOBS, false, server);
		g.set(GameRules.SPAWN_MONSTERS, false, server);
		g.set(GameRules.SPAWN_PHANTOMS, false, server);
		g.set(GameRules.SPAWN_PATROLS, false, server);
		g.set(GameRules.SPAWN_WANDERING_TRADERS, false, server);
		g.set(GameRules.BLOCK_DROPS, false, server);
		g.set(GameRules.MOB_DROPS, false, server);
		g.set(GameRules.MOB_GRIEFING, false, server);
		g.set(GameRules.IMMEDIATE_RESPAWN, true, server);
		g.set(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0, server);
		g.set(GameRules.SHOW_ADVANCEMENT_MESSAGES, false, server);
		g.set(GameRules.RANDOM_TICK_SPEED, 0, server);
		commande(server, "time set 12600");
		commande(server, "weather clear");
		commande(server, "setworldspawn " + Carte.CAMP_X + " " + (Carte.SOL + 1) + " " + (Carte.CAMP_Z - 6));
	}

	public static void commande(MinecraftServer server, String cmd) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), cmd);
	}

	private static void tick(MinecraftServer server) {
		if (file.isEmpty()) {
			return;
		}
		ServerLevel l = server.overworld();
		long t0 = System.nanoTime();
		while (!file.isEmpty() && System.nanoTime() - t0 < BUDGET_NS) {
			long k = file.poll();
			int cx = (int) (k >> 32), cz = (int) k;
			l.getChunk(cx, cz);
			Ecrivain w = new Ecrivain(l, cx, cz);
			if (forcer) {
				w.raz(Carte.SOL + 64);
			}
			for (Element e : plan.dans(k)) {
				try {
					e.dessiner(w);
				} catch (RuntimeException ex) {
					OrbitExtractionMod.LOG.warn("[Cendreville] {} en ({}, {}) : {}", e.getClass().getSimpleName(), cx, cz, ex.toString());
				}
			}
			faits++;
		}
		int palier = faits * 10 / Math.max(1, total);
		if (palier != dernierPalier) {
			dernierPalier = palier;
			String msg = "[Cendreville] " + (palier * 10) + " % (" + faits + " / " + total + ")";
			OrbitExtractionMod.LOG.info(msg);
			server.getPlayerList().broadcastSystemMessage(Component.literal("§9" + msg), true);
		}
		if (file.isEmpty()) {
			long s = (System.currentTimeMillis() - debut) / 1000;
			OrbitExtractionMod.LOG.info("[Cendreville] Carte générée en {} s.", s);
			server.getPlayerList().broadcastSystemMessage(Component.literal("§6Cendreville est prête (" + s + " s)."), false);
			try {
				Files.writeString(repere(server), "Cendreville générée le " + java.time.LocalDateTime.now() + "\n");
			} catch (IOException ex) {
				OrbitExtractionMod.LOG.warn("[Cendreville] Repère non écrit : {}", ex.toString());
			}
			l.getChunkSource().save(false);
			image(server);
		}
	}

	/**
	 * Vue de dessus de la carte (1 pixel = 1 bloc, couleurs de carte Minecraft, relief ombré, sorties en jaune)
	 * écrite dans le dossier du monde : cendreville-plan.png.
	 */
	public static Path image(MinecraftServer server) {
		ServerLevel l = server.overworld();
		int zMin = Carte.CAMP_Z - Carte.CAMP_R - 4;
		int w = Carte.MAX - Carte.MIN + 1, h = Carte.MAX - zMin + 1;
		java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
		net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
		for (int x = Carte.MIN; x <= Carte.MAX; x++) {
			int hautNord = -1;
			for (int z = zMin; z <= Carte.MAX; z++) {
				net.minecraft.world.level.chunk.ChunkAccess ch = l.getChunk(x >> 4, z >> 4);
				int y = ch.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
				net.minecraft.world.level.block.state.BlockState s = l.getBlockState(pos.set(x, y, z));
				while (y > Carte.SOL - 10 && (s.is(net.minecraft.world.level.block.Blocks.BARRIER) || s.getMapColor(l, pos).col == 0)) {
					s = l.getBlockState(pos.set(x, --y, z));
				}
				int c = s.getMapColor(l, pos).col;
				double f = hautNord < 0 || y == hautNord ? 0.86 : y > hautNord ? 1.0 : 0.71;
				f *= 0.85 + Math.min(0.15, Math.max(-0.25, (y - Carte.SOL) * 0.012));
				int r = (int) Math.min(255, ((c >> 16) & 255) * f), g = (int) Math.min(255, ((c >> 8) & 255) * f), b = (int) Math.min(255, (c & 255) * f);
				img.setRGB(x - Carte.MIN, z - zMin, (r << 16) | (g << 8) | b);
				hautNord = y;
			}
		}
		java.awt.Graphics2D g2 = img.createGraphics();
		g2.setColor(new java.awt.Color(0xF2C14E));
		for (Carte.Point p : Carte.SORTIES) {
			g2.drawOval(p.x() - Carte.MIN - 6, p.z() - zMin - 6, 12, 12);
		}
		g2.setColor(new java.awt.Color(0x2E6BD6));
		for (Carte.Point p : Carte.LARGAGES) {
			g2.drawRect(p.x() - Carte.MIN - 3, p.z() - zMin - 3, 6, 6);
		}
		g2.dispose();
		Path out = server.getWorldPath(LevelResource.ROOT).resolve("cendreville-plan.png");
		try {
			javax.imageio.ImageIO.write(img, "png", out.toFile());
			OrbitExtractionMod.LOG.info("[Cendreville] Plan dessiné : {}", out.toAbsolutePath().normalize());
		} catch (IOException ex) {
			OrbitExtractionMod.LOG.warn("[Cendreville] Plan non dessiné : {}", ex.toString());
		}
		return out;
	}
}
