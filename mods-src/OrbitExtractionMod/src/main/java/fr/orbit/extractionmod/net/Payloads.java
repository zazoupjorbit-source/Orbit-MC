package fr.orbit.extractionmod.net;

import fr.orbit.extractionmod.OrbitExtractionMod;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Paquets échangés entre le mod client et le serveur moddé. */
public final class Payloads {
	private Payloads() {
	}

	/** Client → serveur : « je tire » (envoyé à la cadence de l'arme tant que le clic droit est maintenu). */
	public record Fire() implements CustomPacketPayload {
		public static final Fire INSTANCE = new Fire();
		public static final Type<Fire> TYPE = new Type<>(OrbitExtractionMod.id("tirer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Fire> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Fire> type() {
			return TYPE;
		}
	}

	/** Client → serveur : touche de rechargement. */
	public record Reload() implements CustomPacketPayload {
		public static final Reload INSTANCE = new Reload();
		public static final Type<Reload> TYPE = new Type<>(OrbitExtractionMod.id("recharger"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reload> CODEC = StreamCodec.unit(INSTANCE);

		@Override
		public Type<Reload> type() {
			return TYPE;
		}
	}

	/** Serveur → client : rechargement accepté, durée en ticks (0 = annulé). Sert à la barre du HUD. */
	public record ReloadStarted(int ticks) implements CustomPacketPayload {
		public static final Type<ReloadStarted> TYPE = new Type<>(OrbitExtractionMod.id("rechargement"));
		public static final StreamCodec<RegistryFriendlyByteBuf, ReloadStarted> CODEC =
				ByteBufCodecs.VAR_INT.map(ReloadStarted::new, ReloadStarted::ticks).cast();

		@Override
		public Type<ReloadStarted> type() {
			return TYPE;
		}
	}

	/**
	 * Serveur → client : état du raid pour le HUD.
	 *
	 * @param etat     0 = hors Cendreville, 1 = au camp, 2 = en raid
	 * @param restant  ticks avant la fin du raid
	 * @param sortie   progression de l'extraction en cours (0 à {@link #SORTIE_TICKS})
	 */
	public record RaidSync(int etat, int restant, int sortie) implements CustomPacketPayload {
		public static final int SORTIE_TICKS = 160;
		public static final Type<RaidSync> TYPE = new Type<>(OrbitExtractionMod.id("raid"));
		public static final StreamCodec<RegistryFriendlyByteBuf, RaidSync> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, RaidSync::etat,
				ByteBufCodecs.VAR_INT, RaidSync::restant,
				ByteBufCodecs.VAR_INT, RaidSync::sortie,
				RaidSync::new);

		@Override
		public Type<RaidSync> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Fire.TYPE, Fire.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Reload.TYPE, Reload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ReloadStarted.TYPE, ReloadStarted.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(RaidSync.TYPE, RaidSync.CODEC);
	}
}
