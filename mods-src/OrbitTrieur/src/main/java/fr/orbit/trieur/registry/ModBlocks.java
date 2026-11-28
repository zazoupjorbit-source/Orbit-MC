package fr.orbit.trieur.registry;

import fr.orbit.trieur.OrbitTrieur;
import fr.orbit.trieur.bloc.TrieurBlock;
import fr.orbit.trieur.bloc.TrieurBlockEntity;
import fr.orbit.trieur.menu.TrieurMenu;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.Set;

public final class ModBlocks {
	private ModBlocks() {
	}

	private static final ResourceKey<Block> CLE_BLOC = ResourceKey.create(Registries.BLOCK, OrbitTrieur.id("trieur"));
	private static final ResourceKey<Item> CLE_OBJET = ResourceKey.create(Registries.ITEM, OrbitTrieur.id("trieur"));

	public static final TrieurBlock TRIEUR = Registry.register(BuiltInRegistries.BLOCK, CLE_BLOC,
			new TrieurBlock(BlockBehaviour.Properties.of().setId(CLE_BLOC).mapColor(MapColor.COLOR_BLUE)
					.strength(3.0f, 6.0f).requiresCorrectToolForDrops().sound(SoundType.METAL)));

	public static final BlockItem TRIEUR_OBJET = Registry.register(BuiltInRegistries.ITEM, CLE_OBJET,
			new BlockItem(TRIEUR, new Item.Properties().setId(CLE_OBJET).useBlockDescriptionPrefix()));

	public static final BlockEntityType<TrieurBlockEntity> TRIEUR_BE = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE,
			OrbitTrieur.id("trieur"), new BlockEntityType<>(TrieurBlockEntity::new, Set.of(TRIEUR)));

	public static final ExtendedMenuType<TrieurMenu, BlockPos> TRIEUR_MENU = Registry.register(BuiltInRegistries.MENU,
			OrbitTrieur.id("trieur"), new ExtendedMenuType<>(TrieurMenu::new, BlockPos.STREAM_CODEC));

	public static void register() {
		// Chargement de la classe = enregistrement.
	}
}
