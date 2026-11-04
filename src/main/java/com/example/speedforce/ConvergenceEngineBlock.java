package com.example.speedforce;

import com.example.speedforce.server.ServerLogic;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** A real resource-consuming machine interaction: it consumes research materials and unlocks the player's bond. */
public final class ConvergenceEngineBlock extends Block {
    private static final String AWAKENED_KEY = "speedforce_awakened";

    public ConvergenceEngineBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                net.minecraft.world.entity.player.Player player,
                                                net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;
        if (serverPlayer.getPersistentData().getBoolean(AWAKENED_KEY)) {
            serverPlayer.displayClientMessage(Component.translatable("message.speedforce.engine_already_awakened"), true);
            return InteractionResult.CONSUME;
        }
        if (!consume(serverPlayer, ModContent.TEMPORAL_SHARD.get(), 3)
                || !consume(serverPlayer, ModContent.RESONANCE_CORE.get(), 1)
                || !consume(serverPlayer, ModContent.UNSTABLE_ESSENCE.get(), 1)) {
            serverPlayer.displayClientMessage(Component.translatable("message.speedforce.engine_needs_materials"), true);
            return InteractionResult.CONSUME;
        }
        serverPlayer.getPersistentData().putBoolean(AWAKENED_KEY, true);
        ServerLogic.setLevel(serverPlayer, 1);
        serverPlayer.displayClientMessage(Component.translatable("message.speedforce.engine_awakened"), false);
        level.levelEvent(3002, pos, 0);
        return InteractionResult.CONSUME;
    }

    private static boolean consume(ServerPlayer player, Item item, int amount) {
        int found = 0;
        for (var stack : player.getInventory().items) if (stack.is(item)) found += stack.getCount();
        if (found < amount) return false;
        int remaining = amount;
        for (var stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
        return true;
    }

    /** Restore the ability after login; the unlock is stored on the player, not in transient server memory. */
    @EventBusSubscriber(modid = Speedforce.MODID)
    public static final class Persistence {
        private Persistence() {}

        @SubscribeEvent
        public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().getBoolean(AWAKENED_KEY)) {
                ServerLogic.setLevel(player, 1);
            }
        }

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            event.getOriginal().reviveCaps();
            if (event.getOriginal().getPersistentData().getBoolean(AWAKENED_KEY)) {
                event.getEntity().getPersistentData().putBoolean(AWAKENED_KEY, true);
            }
            event.getOriginal().invalidateCaps();
        }
    }
}
