package com.jose.pokecraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.ForgeRegistries;

@Mod("pokecraft")
public class PokeCraftMod {
    public static final String MOD_ID = "pokecraft";

    // Registradores oficiales para NeoForge 1.21.1
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    // Registro de Ítems (Aljaba y Poké Balls por Tiers usando DeferredHolder)
    public static final DeferredHolder<Item, Item> QUIVER = ITEMS.register("quiver", () -> new com.jose.pokecraft.item.QuiverItem(new Item.Properties().stacksTo(1)));
    
    public static final DeferredHolder<Item, Item> POKEBALL = ITEMS.register("pokeball", () -> new com.jose.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.jose.pokecraft.item.PokeballItem.Tier.POKEBALL));
    public static final DeferredHolder<Item, Item> GREATBALL = ITEMS.register("greatball", () -> new com.jose.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.jose.pokecraft.item.PokeballItem.Tier.GREATBALL));
    public static final DeferredHolder<Item, Item> SUPERBALL = ITEMS.register("superball", () -> new com.jose.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.jose.pokecraft.item.PokeballItem.Tier.SUPERBALL));
    public static final DeferredHolder<Item, Item> ULTRABALL = ITEMS.register("ultraball", () -> new com.jose.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.jose.pokecraft.item.PokeballItem.Tier.ULTRABALL));
    public static final DeferredHolder<Item, Item> MASTERBALL = ITEMS.register("masterball", () -> new com.jose.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(1), com.jose.pokecraft.item.PokeballItem.Tier.MASTERBALL));

    // Registro del Tarro de Mobs (Bloque e Item asociado)
    public static final DeferredHolder<Block, Block> MOB_JAR_BLOCK = BLOCKS.register("mob_jar", () -> new com.jose.pokecraft.block.MobJarBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5F).sound(SoundType.GLASS).noOcclusion()
    ));
    public static final DeferredHolder<Item, Item> MOB_JAR_ITEM = ITEMS.register("mob_jar", () -> new BlockItem(MOB_JAR_BLOCK.get(), new Item.Properties().stacksTo(64)));

    // Registro de la BlockEntity del Tarro
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<com.jose.pokecraft.block.MobJarBlockEntity>> MOB_JAR_BE = BLOCK_ENTITIES.register("mob_jar_be", () ->
        BlockEntityType.Builder.of(com.jose.pokecraft.block.MobJarBlockEntity::new, MOB_JAR_BLOCK.get()).build(null)
    );

    public PokeCraftMod(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}