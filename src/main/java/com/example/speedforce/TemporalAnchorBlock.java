package com.example.speedforce;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A rare glowing anomaly block. Its server-side drops are defined by the block loot table. */
public class TemporalAnchorBlock extends Block {
    public TemporalAnchorBlock(Properties properties) {
        super(properties);
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              net.minecraft.world.level.block.entity.BlockEntity blockEntity, ItemStack tool) {
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
        if (level instanceof ServerLevel serverLevel && !tool.isEnchantedWith(Enchantments.SILK_TOUCH)) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.GLOW_SQUID_INK,
                    pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    20, 0.4D, 0.4D, 0.4D, 0.05D);
        }
    }
}
