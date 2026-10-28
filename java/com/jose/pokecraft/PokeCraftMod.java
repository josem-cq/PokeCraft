package com.tuusuario.pokecraft;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod("pokecraft")
public class PokeCraftMod {
    public static final String MOD_ID = "pokecraft";

    // Registradores oficiales para NeoForge
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MOD_ID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MOD_ID);

    // Registro de Ítems (Aljaba y Poké Balls por Tiers)
    public static final RegistryObject<Item> QUIVER = ITEMS.register("quiver", () -> new com.tuusuario.pokecraft.item.QuiverItem(new Item.Properties().stacksTo(1)));
    
    public static final RegistryObject<Item> POKEBALL = ITEMS.register("pokeball", () -> new com.tuusuario.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.tuusuario.pokecraft.item.PokeballItem.Tier.POKEBALL));
    public static final RegistryObject<Item> GREATBALL = ITEMS.register("greatball", () -> new com.tuusuario.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.tuusuario.pokecraft.item.PokeballItem.Tier.GREATBALL));
    public static final RegistryObject<Item> SUPERBALL = ITEMS.register("superball", () -> new com.tuusuario.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.tuusuario.pokecraft.item.PokeballItem.Tier.SUPERBALL));
    public static final RegistryObject<Item> ULTRABALL = ITEMS.register("ultraball", () -> new com.tuusuario.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(16), com.tuusuario.pokecraft.item.PokeballItem.Tier.ULTRABALL));
    public static final RegistryObject<Item> MASTERBALL = ITEMS.register("masterball", () -> new com.tuusuario.pokecraft.item.PokeballItem(new Item.Properties().stacksTo(1), com.tuusuario.pokecraft.item.PokeballItem.Tier.MASTERBALL));

    // Registro del Tarro de Mobs (Bloque e Item asociado)
    public static final RegistryObject<Block> MOB_JAR_BLOCK = BLOCKS.register("mob_jar", () -> new com.tuusuario.pokecraft.block.MobJarBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(0.5F).sound(SoundType.GLASS).noOcclusion()
    ));
    public static final RegistryObject<Item> MOB_JAR_ITEM = ITEMS.register("mob_jar", () -> new BlockItem(MOB_JAR_BLOCK.get(), new Item.Properties().stacksTo(64)));

    public PokeCraftMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(modEventBus);
        BLOCKS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}