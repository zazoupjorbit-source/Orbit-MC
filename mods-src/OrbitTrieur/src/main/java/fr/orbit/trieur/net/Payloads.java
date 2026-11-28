package fr.orbit.trieur.net;

import fr.orbit.trieur.OrbitTrieur;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/** Paquets du panel de réglages. Le serveur valide tout ce qui arrive (distance, bornes, tailles). */
public final class Payloads {
	private Payloads() {
	}

	public static final int MAX_FILTRE = 256;
	public static final int MAX_CIBLES = 256;

	/** Un inventaire détecté, tel que le panel l'affiche. */
	public record Cible(BlockPos pos, ItemStack icone, String nom, String filtre, boolean surplus, List<String> contenu) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Cible> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Cible::pos,
				ItemStack.OPTIONAL_STREAM_CODEC, Cible::icone,
				ByteBufCodecs.stringUtf8(128), Cible::nom,
				ByteBufCodecs.stringUtf8(MAX_FILTRE * 4), Cible::filtre,
				ByteBufCodecs.BOOL, Cible::surplus,
				ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(16)), Cible::contenu,
				Cible::new);
	}

	/** Client → serveur : « ouvre-moi le panel de ce trieur ». */
	public record Demander(BlockPos pos) implements CustomPacketPayload {
		public static final Type<Demander> TYPE = new Type<>(OrbitTrieur.id("demander"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Demander> CODEC =
				BlockPos.STREAM_CODEC.map(Demander::new, Demander::pos).cast();

		@Override
		public Type<Demander> type() {
			return TYPE;
		}
	}

	/** Serveur → client : état du trieur pour le panel. */
	public record Reglages(BlockPos pos, boolean actif, boolean regrouper, int rayon, int rayonMax, int enAttente,
						   List<Cible> cibles) implements CustomPacketPayload {
		public static final Type<Reglages> TYPE = new Type<>(OrbitTrieur.id("reglages"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Reglages> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Reglages::pos,
				ByteBufCodecs.BOOL, Reglages::actif,
				ByteBufCodecs.BOOL, Reglages::regrouper,
				ByteBufCodecs.VAR_INT, Reglages::rayon,
				ByteBufCodecs.VAR_INT, Reglages::rayonMax,
				ByteBufCodecs.VAR_INT, Reglages::enAttente,
				Cible.CODEC.apply(ByteBufCodecs.list(MAX_CIBLES)), Reglages::cibles,
				Reglages::new);

		@Override
		public Type<Reglages> type() {
			return TYPE;
		}
	}

	/** Une règle envoyée par le panel (position absolue + filtre). */
	public record Regle(BlockPos pos, String filtre) {
		public static final StreamCodec<RegistryFriendlyByteBuf, Regle> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Regle::pos,
				ByteBufCodecs.stringUtf8(MAX_FILTRE * 4), Regle::filtre,
				Regle::new);
	}

	/**
	 * Client → serveur : enregistrer les réglages.
	 *
	 * @param rescan vrai = renvoyer le panel à jour après l'enregistrement (bouton « Appliquer »)
	 */
	public record Enregistrer(BlockPos pos, boolean actif, boolean regrouper, int rayon, List<Regle> regles,
							  Optional<BlockPos> surplus, boolean rescan) implements CustomPacketPayload {
		public static final Type<Enregistrer> TYPE = new Type<>(OrbitTrieur.id("enregistrer"));
		public static final StreamCodec<RegistryFriendlyByteBuf, Enregistrer> CODEC = StreamCodec.composite(
				BlockPos.STREAM_CODEC, Enregistrer::pos,
				ByteBufCodecs.BOOL, Enregistrer::actif,
				ByteBufCodecs.BOOL, Enregistrer::regrouper,
				ByteBufCodecs.VAR_INT, Enregistrer::rayon,
				Regle.CODEC.apply(ByteBufCodecs.list(MAX_CIBLES)), Enregistrer::regles,
				ByteBufCodecs.optional(BlockPos.STREAM_CODEC), Enregistrer::surplus,
				ByteBufCodecs.BOOL, Enregistrer::rescan,
				Enregistrer::new);

		@Override
		public Type<Enregistrer> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(Demander.TYPE, Demander.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Enregistrer.TYPE, Enregistrer.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Reglages.TYPE, Reglages.CODEC);
	}
}
