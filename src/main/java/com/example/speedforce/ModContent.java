package com.example.speedforce;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

/** Registry for the first research and convergence gameplay loop. */
public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Speedforce.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Speedforce.MODID);

    public static final DeferredHolder<Block, Block> CONVERGENCE_ENGINE = BLOCKS.register("convergence_engine",
            () -> new ConvergenceEngineBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, Item> CONVERGENCE_ENGINE_ITEM = ITEMS.register("convergence_engine",
            () -> new BlockItem(CONVERGENCE_ENGINE.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> TEMPORAL_SHARD = ITEMS.register("temporal_shard",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> RESONANCE_CORE = ITEMS.register("resonance_core",
            () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> UNSTABLE_ESSENCE = ITEMS.register("unstable_essence",
            () -> new Item(new Item.Properties()));

    private ModContent() {}
}
