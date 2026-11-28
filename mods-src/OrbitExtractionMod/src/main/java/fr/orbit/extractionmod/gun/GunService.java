package fr.orbit.extractionmod.gun;

import fr.orbit.extractionmod.net.Payloads;
import fr.orbit.extractionmod.raid.InfecteService;
import fr.orbit.extractionmod.registry.ModComponents;
import fr.orbit.extractionmod.registry.ModItems;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Logique des armes, côté serveur. Le client demande, le serveur vérifie tout :
 * arme en main, cadence (anti-macro), munitions, ligne de tir, dégâts.
 */
public final class GunService {
	private GunService() {
	}

	private static final Map<UUID, Integer> lastShot = new HashMap<>();
	private static final Map<UUID, Integer> reloadEnd = new HashMap<>();
	private static final RandomSource RNG = RandomSource.create();

	public static void register() {
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Fire.TYPE, (p, ctx) -> fire(ctx.player()));
		ServerPlayNetworking.registerGlobalReceiver(Payloads.Reload.TYPE, (p, ctx) -> startReload(ctx.player()));
		ServerTickEvents.END_SERVER_TICK.register(GunService::tick);
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			lastShot.remove(handler.player.getUUID());
			reloadEnd.remove(handler.player.getUUID());
		});
	}

	// ------------------------------------------------------------------ tir

	private static void fire(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun) || player.isSpectator() || player.isDeadOrDying() || player.isUsingItem()) {
			return;
		}
		GunStats s = gun.stats();
		UUID id = player.getUUID();
		int now = player.level().getServer().getTickCount();
		if (reloadEnd.containsKey(id)) {
			return;
		}
		// Cadence : un tick de tolérance pour la gigue réseau, pas plus (une macro ne tire pas plus vite).
		Integer last = lastShot.get(id);
		if (last != null && now - last < Math.max(1, s.intervalTicks() - 1)) {
			return;
		}
		int ammo = GunItem.ammo(stack);
		ServerLevel level = (ServerLevel) player.level();
		if (ammo <= 0) {
			lastShot.put(id, now + 6);
			level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6f, 1.6f);
			player.sendOverlayMessage(Component.literal("Chargeur vide : R pour recharger").withColor(0xE74C3C));
			return;
		}
		lastShot.put(id, now);
		stack.set(ModComponents.AMMO, ammo - 1);

		Vec3 eye = player.getEyePosition();
		double disp = s.spread() * (player.isShiftKeyDown() ? 0.7 : 1.0) * (player.onGround() ? 1.0 : 2.5)
				* (player.isSprinting() ? 1.8 : 1.0);
		for (int i = 0; i < s.pellets(); i++) {
			balle(player, level, s, eye, spread(player.getViewVector(1f), disp), i == 0);
		}

		float vol = s.son() == GunStats.Son.SILENCIEUX ? 0.5f : 1.2f + s.noise() / 60f;
		switch (s.son()) {
			case LEGER -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, vol, s.pitch());
			case LOURD -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, vol, s.pitch());
			case EXPLOSION -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, vol * 0.6f, s.pitch());
			case SILENCIEUX -> level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, vol, s.pitch());
		}
		InfecteService.alerter(level, player, s.noise());
	}

	private static void balle(ServerPlayer player, ServerLevel level, GunStats s, Vec3 eye, Vec3 dir, boolean trainee) {
		Vec3 end = eye.add(dir.scale(s.range()));
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
		AABB zone = player.getBoundingBox().expandTowards(dir.scale(s.range())).inflate(1.0);
		EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, player, eye, stop, zone,
				e -> e instanceof LivingEntity le && le.isAlive() && e.isPickable() && !e.isSpectator(), 0.3f);

		if (hit != null && hit.getEntity() instanceof LivingEntity target) {
			stop = hit.getLocation();
			boolean head = stop.y >= target.getEyeY() - 0.3;
			// Au-delà de 60 % de la portée, les dégâts baissent (jusqu'à -40 % en bout de portée).
			double d = eye.distanceTo(stop) / s.range();
			float chute = d <= 0.6 ? 1f : (float) (1 - (d - 0.6) * 1.0);
			float dmg = s.damage() * (head ? s.headshot() : 1f) * chute;
			target.setInvulnerableTime(0); // arme automatique : chaque balle compte
			target.hurtServer(level, level.damageSources().playerAttack(player), dmg);
			level.sendParticles(ParticleTypes.CRIT, stop.x, stop.y, stop.z, head ? 10 : 5, 0.1, 0.1, 0.1, 0.2);
			if (head) {
				level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.NOTE_BLOCK_HAT, SoundSource.PLAYERS, 0.8f, 1.8f);
			}
		} else if (block.getType() != HitResult.Type.MISS) {
			level.sendParticles(ParticleTypes.SMOKE, stop.x, stop.y, stop.z, 3, 0.03, 0.03, 0.03, 0.01);
		}
		if (!trainee) {
			return;
		}
		// Traînée : quelques étincelles le long de la balle.
		Vec3 start = eye.add(dir.scale(0.8)).add(0, -0.15, 0);
		double len = start.distanceTo(stop);
		for (double d = 1.5; d < len; d += 2.5) {
			Vec3 p = start.add(dir.scale(d));
			level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
		}
	}

	private static Vec3 spread(Vec3 dir, double degrees) {
		double r = Math.toRadians(degrees) * 0.5;
		return dir.add(RNG.triangle(0, r), RNG.triangle(0, r), RNG.triangle(0, r)).normalize();
	}

	// ------------------------------------------------------------------ rechargement

	private static void startReload(ServerPlayer player) {
		ItemStack stack = player.getMainHandItem();
		if (!(stack.getItem() instanceof GunItem gun) || reloadEnd.containsKey(player.getUUID())) {
			return;
		}
		if (GunItem.ammo(stack) >= gun.stats().mag()) {
			return;
		}
		if (!player.isCreative() && countAmmo(player, gun.stats().ammo()) == 0) {
			player.sendOverlayMessage(Component.literal("Plus de munitions " + gun.stats().ammo().nom).withColor(0xE74C3C));
			return;
		}
		int ticks = gun.stats().reloadTicks();
		reloadEnd.put(player.getUUID(), player.level().getServer().getTickCount() + ticks);
		ServerPlayNetworking.send(player, new Payloads.ReloadStarted(ticks));
		player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARMOR_EQUIP_IRON, SoundSource.PLAYERS, 0.8f, 1.4f);
	}

	private static void tick(MinecraftServer server) {
		if (reloadEnd.isEmpty()) {
			return;
		}
		int now = server.getTickCount();
		reloadEnd.entrySet().removeIf(e -> {
			ServerPlayer player = server.getPlayerList().getPlayer(e.getKey());
			if (player == null) {
				return true;
			}
			ItemStack stack = player.getMainHandItem();
			if (!(stack.getItem() instanceof GunItem gun)) {
				// Arme rangée pendant le rechargement : annulé.
				ServerPlayNetworking.send(player, new Payloads.ReloadStarted(0));
				return true;
			}
			if (now < e.getValue()) {
				return false;
			}
			int need = gun.stats().mag() - GunItem.ammo(stack);
			int got = player.isCreative() ? need : takeAmmo(player, gun.stats().ammo(), need);
			stack.set(ModComponents.AMMO, GunItem.ammo(stack) + got);
			player.level().playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.9f, 1.5f);
			return true;
		});
	}

	/** Balles d'un calibre dans l'inventaire (utilisé aussi par le HUD côté client). */
	public static int countAmmo(Player player, Ammo type) {
		Item item = ModItems.MUNITIONS.get(type);
		int n = 0;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack it = player.getInventory().getItem(i);
			if (it.getItem() == item) {
				n += it.getCount();
			}
		}
		return n;
	}

	private static int takeAmmo(ServerPlayer player, Ammo type, int need) {
		Item item = ModItems.MUNITIONS.get(type);
		int got = 0;
		for (int i = 0; i < player.getInventory().getContainerSize() && got < need; i++) {
			ItemStack it = player.getInventory().getItem(i);
			if (it.getItem() == item) {
				int t = Math.min(need - got, it.getCount());
				it.shrink(t);
				got += t;
			}
		}
		return got;
	}
}
