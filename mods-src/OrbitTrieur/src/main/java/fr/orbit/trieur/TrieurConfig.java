package fr.orbit.trieur;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Réglages globaux du serveur (config/orbittrieur.json), rechargés par /orbittrieur recharger.
 * Les réglages de chaque trieur (filtres, rayon, modes) se font dans son panel en jeu.
 */
public final class TrieurConfig {
	/** Version du fichier : sert à faire évoluer les anciennes valeurs par défaut. */
	public int version;
	/** Rayon maximal qu'un joueur peut choisir dans le panel (1 à 128). */
	public int rayonMax = 64;
	/** Rayon d'un trieur tout juste posé. */
	public int rayonDefaut = 16;
	/** Un cycle de tri tous les N ticks (20 ticks = 1 s). */
	public int intervalleTicks = 8;
	/** Nombre d'objets déplacés au plus par cycle. */
	public int objetsParCycle = 64;
	/** Nombre maximal d'inventaires gérés par un trieur. */
	public int maxCibles = 128;
	/** Blocs jamais pris comme destination (identifiants, « mod:* » accepté). */
	public List<String> blocsIgnores = new ArrayList<>(List.of("minecraft:hopper", "minecraft:dropper",
			"minecraft:dispenser", "minecraft:crafter", "minecraft:furnace", "minecraft:blast_furnace",
			"minecraft:smoker", "minecraft:brewing_stand", "minecraft:jukebox", "minecraft:chiseled_bookshelf",
			"minecraft:decorated_pot", "minecraft:lectern", "minecraft:campfire", "minecraft:soul_campfire"));

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	private static TrieurConfig instance = new TrieurConfig();

	public static TrieurConfig get() {
		return instance;
	}

	private static Path fichier() {
		return FabricLoader.getInstance().getConfigDir().resolve("orbittrieur.json");
	}

	/** Lit le fichier (le crée avec les valeurs par défaut s'il manque) et borne les valeurs. */
	public static synchronized void charger() {
		Path f = fichier();
		TrieurConfig c = new TrieurConfig();
		if (Files.exists(f)) {
			try {
				TrieurConfig lu = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), TrieurConfig.class);
				if (lu != null) {
					c = lu;
					if (c.version < 2) {
						// v1 : rayon max 8, défaut 4, 64 cibles → v2 : max 64, défaut 16, 128 cibles.
						if (c.rayonMax == 8) {
							c.rayonMax = 64;
						}
						if (c.rayonDefaut == 4) {
							c.rayonDefaut = 16;
						}
						if (c.maxCibles == 64) {
							c.maxCibles = 128;
						}
					}
					if (c.version < 3 && c.objetsParCycle == 16) {
						// v3 : tampon de 54 cases → débit par défaut 64 objets par cycle.
						c.objetsParCycle = 64;
					}
				}
			} catch (Exception e) {
				OrbitTrieur.LOG.warn("orbittrieur.json illisible, valeurs par défaut : {}", e.getMessage());
			}
		}
		c.version = 3;
		c.rayonMax = Math.clamp(c.rayonMax, 1, 128);
		c.rayonDefaut = Math.clamp(c.rayonDefaut, 1, c.rayonMax);
		c.intervalleTicks = Math.clamp(c.intervalleTicks, 1, 200);
		c.objetsParCycle = Math.clamp(c.objetsParCycle, 1, 4096);
		c.maxCibles = Math.clamp(c.maxCibles, 1, 256);
		if (c.blocsIgnores == null) {
			c.blocsIgnores = new ArrayList<>();
		}
		instance = c;
		try {
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(c), StandardCharsets.UTF_8);
		} catch (IOException e) {
			OrbitTrieur.LOG.warn("Impossible d'écrire orbittrieur.json : {}", e.getMessage());
		}
	}

	public boolean estIgnore(String idBloc) {
		for (String s : blocsIgnores) {
			if (s.equals(idBloc) || (s.endsWith(":*") && idBloc.startsWith(s.substring(0, s.length() - 1)))) {
				return true;
			}
		}
		return false;
	}
}
