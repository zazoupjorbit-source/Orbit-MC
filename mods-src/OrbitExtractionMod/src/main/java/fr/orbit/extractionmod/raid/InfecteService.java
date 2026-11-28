package fr.orbit.extractionmod.raid;

import fr.orbit.extractionmod.carte.Carte;
import fr.orbit.extractionmod.gun.Ammo;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import java.util.List;
import java.util.Random;

/**
 * Les infectés de Cendreville : apparaissent dans les rues autour des joueurs en raid (jamais sur les toits ni au camp),
 * entendent les coups de feu (rayon « bruit » de chaque arme) et disparaissent quand plus personne n'est près d'eux.
 */
public final class InfecteService {
	private InfecteService() {
	}

	public static final String TAG = "orbit_infecte";
	private static final String TAG_MASSIF = "orbit_infecte_massif";
	private static final int MAX_TOTAL = 150;
	private static final Random RNG = new Random();
	private static final AABB CARTE = new AABB(Carte.MIN - 2, Carte.SOL - 8, Carte.MIN - 2, Carte.MAX + 3, Carte.SOL + 80, Carte.MAX + 3);

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(s -> {
			if (RaidService.actif() && s.getTickCount() % 20 == 0) {
				tick(s);
			}
		});
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Zombie z && z.entityTags().contains(TAG) && z.level() instanceof ServerLevel l) {
				butin(l, z, z.entityTags().contains(TAG_MASSIF));
			}
		});
	}

	private static List<Zombie> tous(ServerLevel l) {
		return l.getEntities(EntityTypeTest.forClass(Zombie.class), CARTE, z -> z.entityTags().contains(TAG));
	}

	private static void tick(MinecraftServer server) {
		ServerLevel l = server.overworld();
		List<Zombie> infectes = new java.util.ArrayList<>(tous(l));
		List<ServerPlayer> joueurs = l.players().stream().filter(p -> !p.isSpectator() && Carte.dansCarte(p.getX(), p.getZ())).toList();
		// Ménage : plus personne à moins de 80 blocs → l'infecté s'efface.
		infectes.removeIf(z -> {
			for (ServerPlayer p : joueurs) {
				if (p.distanceToSqr(z) < 80 * 80) {
					return false;
				}
			}
			z.discard();
			return true;
		});
		int total = infectes.size();
		for (ServerPlayer p : joueurs) {
			if (!RaidService.enRaid(p) && !p.isCreative()) {
				continue;
			}
			int proches = 0;
			for (Zombie z : infectes) {
				if (z.distanceToSqr(p) < 40 * 40) {
					proches++;
				}
			}
			int cap = 10 + (int) (Math.max(0, 1 - centre(p)) * 6); // plus dense en centre-ville
			for (int essai = 0; essai < 3 && proches < cap && total < MAX_TOTAL; essai++) {
				if (apparaitre(l, p) != null) {
					proches++;
					total++;
				}
			}
		}
	}

	/** 0 au centre de la ville, 1 en périphérie. */
	private static double centre(Player p) {
		return Math.min(1, Math.max(Math.abs(p.getX()), Math.abs(p.getZ())) / 200.0);
	}

	public static Zombie apparaitre(ServerLevel l, Player p) {
		double a = RNG.nextDouble() * Math.PI * 2, d = 20 + RNG.nextDouble() * 18;
		int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
		if (!Carte.dansCarte(x, z) || x <= Carte.MIN + 1 || x >= Carte.MAX - 1 || z <= Carte.MIN + 1 || z >= Carte.MAX - 1) {
			return null;
		}
		if (!l.isLoaded(new BlockPos(x, Carte.SOL, z))) {
			return null;
		}
		int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		if (y > Carte.SOL + 2 || y < Carte.SOL - 6) {
			return null; // toit, mur, trou : on ne fait pas apparaître là
		}
		return creer(l, x + 0.5, y, z + 0.5);
	}

	public static Zombie creer(ServerLevel l, double x, double y, double z) {
		double q = RNG.nextDouble();
		boolean massif = q < 0.07, coureur = !massif && q < 0.22;
		Zombie zb = (massif || RNG.nextDouble() < 0.3) ? EntityTypes.ZOMBIE.create(l, EntitySpawnReason.EVENT)
				: EntityTypes.HUSK.create(l, EntitySpawnReason.EVENT);
		if (zb == null) {
			return null;
		}
		zb.snapTo(x, y, z, RNG.nextFloat() * 360f, 0f);
		zb.addTag(TAG);
		zb.setPersistenceRequired();
		zb.setCanPickUpLoot(false);
		zb.setItemSlot(EquipmentSlot.HEAD, new ItemStack(massif ? Items.IRON_HELMET : Items.LEATHER_HELMET)); // pas de combustion au soleil
		zb.setDropChance(EquipmentSlot.HEAD, 0f);
		double vie, vitesse, degats;
		if (massif) {
			zb.addTag(TAG_MASSIF);
			zb.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
			zb.setDropChance(EquipmentSlot.CHEST, 0f);
			zb.setCustomName(Component.literal("Infecté massif").withColor(0xE67E22));
			vie = 50;
			vitesse = 0.19;
			degats = 7;
		} else if (coureur) {
			zb.setCustomName(Component.literal("Coureur").withColor(0xE74C3C));
			vie = 14;
			vitesse = 0.33;
			degats = 3;
		} else {
			zb.setCustomName(Component.literal("Infecté").withColor(0x8A919B));
			vie = 22;
			vitesse = 0.24;
			degats = 4;
		}
		zb.setCustomNameVisible(false);
		zb.getAttribute(Attributes.MAX_HEALTH).setBaseValue(vie);
		zb.setHealth((float) vie);
		zb.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(vitesse);
		zb.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(degats);
		zb.getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(36);
		if (zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE) != null) {
			zb.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE).setBaseValue(0);
		}
		l.addFreshEntity(zb);
		return zb;
	}

	/** Un coup de feu : les infectés à portée d'oreille se tournent vers le tireur. */
	public static void alerter(ServerLevel l, Player tireur, int rayon) {
		if (!RaidService.actif() || rayon <= 0) {
			return;
		}
		AABB zone = tireur.getBoundingBox().inflate(rayon, 12, rayon);
		for (Zombie z : l.getEntities(EntityTypeTest.forClass(Zombie.class), zone, z -> z.entityTags().contains(TAG))) {
			if (z.getTarget() == null && z.distanceToSqr(tireur) <= (double) rayon * rayon && !tireur.isCreative()) {
				z.setTarget(tireur);
			}
		}
	}

	private static void butin(ServerLevel l, Zombie z, boolean massif) {
		double q = RNG.nextDouble();
		ItemStack s = null;
		if (massif) {
			s = RNG.nextBoolean() ? new ItemStack(ModItems.MUNITIONS.get(Ammo.R556), 10 + RNG.nextInt(15))
					: new ItemStack(ModItems.TROUSSE);
		} else if (q < 0.30) {
			s = new ItemStack(ModItems.AMMO_9MM, 3 + RNG.nextInt(8));
		} else if (q < 0.40) {
			s = new ItemStack(ModItems.BANDAGE);
		} else if (q < 0.45) {
			s = new ItemStack(ModItems.MUNITIONS.get(Ammo.G12), 2 + RNG.nextInt(4));
		} else if (q < 0.48) {
			s = new ItemStack(ModItems.MUNITIONS.get(Ammo.R556), 5 + RNG.nextInt(10));
		}
		if (s != null) {
			l.addFreshEntity(new ItemEntity(l, z.getX(), z.getY() + 0.5, z.getZ(), s));
		}
	}

	/** Pour les tests : n infectés autour d'un joueur. */
	public static int invoquer(ServerPlayer p, int n) {
		ServerLevel l = (ServerLevel) p.level();
		int ok = 0;
		for (int i = 0; i < n * 6 && ok < n; i++) {
			double a = RNG.nextDouble() * Math.PI * 2, d = 8 + RNG.nextDouble() * 10;
			int x = (int) Math.floor(p.getX() + Math.cos(a) * d), z = (int) Math.floor(p.getZ() + Math.sin(a) * d);
			int y = l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			if (Math.abs(y - p.getY()) < 4 && creer(l, x + 0.5, y, z + 0.5) != null) {
				ok++;
			}
		}
		return ok;
	}

	public static int nettoyer(ServerLevel l) {
		List<Zombie> t = tous(l);
		t.forEach(Zombie::discard);
		return t.size();
	}
}
