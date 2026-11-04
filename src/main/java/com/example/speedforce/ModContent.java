package com.example.speedforce;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registered gameplay content. */
public final class ModContent {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(Speedforce.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(Speedforce.MODID);

    public static final DeferredHolder<Block, Block> CONVERGENCE_ENGINE = BLOCKS.register("convergence_engine",
            () -> new ConvergenceEngineBlock(BlockBehaviour.Properties.of().strength(5.0F, 1200.0F).requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, Item> CONVERGENCE_ENGINE_ITEM = ITEMS.register("convergence_engine",
            () -> new BlockItem(CONVERGENCE_ENGINE.get(), new Item.Properties()));
    public static final DeferredHolder<Block, Block> TEMPORAL_ANCHOR = BLOCKS.register("temporal_anchor",
            () -> new TemporalAnchorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.CRYING_OBSIDIAN)
                    .lightLevel(state -> 8)
                    .strength(6.0F, 2400.0F)
                    .requiresCorrectToolForDrops()));
    public static final DeferredHolder<Item, Item> TEMPORAL_ANCHOR_ITEM = ITEMS.register("temporal_anchor",
            () -> new BlockItem(TEMPORAL_ANCHOR.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> TEMPORAL_SHARD = ITEMS.register("temporal_shard", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> CHRONAL_FRAGMENT = ITEMS.register("chronal_fragment", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> RESEARCH_RECORD = ITEMS.register("research_record", () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> RESONANCE_CORE = ITEMS.register("resonance_core", () -> new Item(new Item.Properties()));
    public static final DeferredHolder<Item, Item> UNSTABLE_ESSENCE = ITEMS.register("unstable_essence", () -> new Item(new Item.Properties()));

    private ModContent() {}
}
