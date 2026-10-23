package com.example.model;

import com.meekdev.amnetic.client.model.HandModels;
import com.meekdev.amnetic.client.model.Models;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public final class ModelDemo {

    public static final Identifier BLOCK_ID = Identifier.fromNamespaceAndPath("example", "model_demo");
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("example", "models3d/refrigerator.glb");

    public static Block BLOCK;
    public static Item ITEM;
    public static BlockEntityType<ModelDemoBlockEntity> BLOCK_ENTITY;

    private ModelDemo() {
    }

    public static void register() {
        ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, BLOCK_ID);
        BLOCK = Registry.register(BuiltInRegistries.BLOCK, BLOCK_ID,
                new ModelDemoBlock(BlockBehaviour.Properties.of()
                        .mapColor(MapColor.STONE)
                        .strength(1.0f)
                        .noOcclusion()
                        .setId(blockKey)));

        ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, BLOCK_ID);
        ITEM = Registry.register(BuiltInRegistries.ITEM, BLOCK_ID,
                new BlockItem(BLOCK, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey)));

        BLOCK_ENTITY = Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, BLOCK_ID,
                FabricBlockEntityTypeBuilder.create(ModelDemoBlockEntity::new, BLOCK).build());
    }

    public static void registerClient() {
        HandModels.bind(ITEM, () -> Models.load(MODEL))
                .fit(0.3f);
    }
}
