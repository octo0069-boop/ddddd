package com.example.speedforce;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

/** The rare, glowing anomaly block that seeds the first step of the Speedforce path. */
public class TemporalAnchorBlock extends Block {
    public TemporalAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        if (params.getOptionalParameter(LootContextParams.TOOL) != null
                && params.getOptionalParameter(LootContextParams.TOOL).isEnchantedWith(Enchantments.SILK_TOUCH)) {
            return List.of(new ItemStack(this));
        }

        var random = params.getLootContext().getRandom();
        List<ItemStack> drops = new java.util.ArrayList<>();
        int shardCount = 1 + random.nextInt(3);
        drops.add(new ItemStack(ModContent.TEMPORAL_SHARD.get(), shardCount));
        if (random.nextFloat() < 0.35F) {
            drops.add(new ItemStack(ModContent.CHRONAL_FRAGMENT.get(), 1 + random.nextInt(2)));
        }
        return drops;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state, net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level instanceof ServerLevel serverLevel && !tool.isEnchantedWith(Enchantments.SILK_TOUCH)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW_SQUID_INK, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, 20, 0.4D, 0.4D, 0.4D, 0.05D);
        }
    }
}
