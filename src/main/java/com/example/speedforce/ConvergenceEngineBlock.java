package com.example.speedforce;

import com.example.speedforce.net.ActionPayload;
import com.example.speedforce.server.ServerLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** A research-consuming progression machine. The server validates and commits every transaction. */
public final class ConvergenceEngineBlock extends Block {
    private static final String AWAKENED_KEY = "speedforce_awakened";
    private static final String LEVEL_KEY = "speedforce_progression_level";
    private static final String NEGATIVE_KEY = "speedforce_negative_path";

    public ConvergenceEngineBlock(Properties properties) { super(properties); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                net.minecraft.world.entity.player.Player player,
                                                net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.PASS;

        if (!serverPlayer.getPersistentData().getBoolean(AWAKENED_KEY)) {
            // Validate the entire ingredient set before consuming any item (failed activation is lossless).
            if (!has(serverPlayer, ModContent.TEMPORAL_SHARD.get(), 3)
                    || !has(serverPlayer, ModContent.RESONANCE_CORE.get(), 1)
                    || !has(serverPlayer, ModContent.UNSTABLE_ESSENCE.get(), 1)) {
                serverPlayer.displayClientMessage(Component.translatable("message.speedforce.engine_needs_materials"), true);
                return InteractionResult.CONSUME;
            }
            consume(serverPlayer, ModContent.TEMPORAL_SHARD.get(), 3);
            consume(serverPlayer, ModContent.RESONANCE_CORE.get(), 1);
            consume(serverPlayer, ModContent.UNSTABLE_ESSENCE.get(), 1);
            serverPlayer.getPersistentData().putBoolean(AWAKENED_KEY, true);
            serverPlayer.getPersistentData().putInt(LEVEL_KEY, 1);
            boolean negative = serverPlayer.isCrou crouching();
            serverPlayer.getPersistentData().putBoolean(NEGATIVE_KEY, negative);
            ServerLogic.setLevel(serverPlayer, 1);
            if (negative) ServerLogic.onAction(serverPlayer, ActionPayload.THEME);
            serverPlayer.displayClientMessage(Component.translatable(negative
                    ? "message.speedforce.awakening_negative" : "message.speedforce.awakening_positive"), false);
            level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_IMPACT, SoundSource.BLOCKS, 1.0F, negative ? 0.75F : 1.35F);
            level.levelEvent(3002, pos, 0);
            return InteractionResult.CONSUME;
        }

        int current = Math.max(1, serverPlayer.getPersistentData().getInt(LEVEL_KEY));
        if (current >= 5) {
            serverPlayer.displayClientMessage(Component.translatable("message.speedforce.progression_max"), true);
            return InteractionResult.CONSUME;
        }
        if (!has(serverPlayer, ModContent.CHRONAL_FRAGMENT.get(), 2)
                || !has(serverPlayer, ModContent.RESONANCE_CORE.get(), 1)) {
            serverPlayer.displayClientMessage(Component.translatable("message.speedforce.engine_upgrade_materials"), true);
            return InteractionResult.CONSUME;
        }
        consume(serverPlayer, ModContent.CHRONAL_FRAGMENT.get(), 2);
        consume(serverPlayer, ModContent.RESONANCE_CORE.get(), 1);
        int next = current + 1;
        serverPlayer.getPersistentData().putInt(LEVEL_KEY, next);
        ServerLogic.setLevel(serverPlayer, next);
        serverPlayer.displayClientMessage(Component.translatable("message.speedforce.progression_level", next), false);
        level.playSound(null, pos, SoundEvents.RESPAWN_ANCHOR_CHARGE, SoundSource.BLOCKS, 0.8F, 0.8F + next * 0.12F);
        level.levelEvent(3002, pos, next);
        return InteractionResult.CONSUME;
    }

    private static boolean has(ServerPlayer player, Item item, int amount) {
        int count = 0;
        for (var stack : player.getInventory().items) if (stack.is(item)) count += stack.getCount();
        return count >= amount;
    }

    private static void consume(ServerPlayer player, Item item, int amount) {
        int remaining = amount;
        for (var stack : player.getInventory().items) {
            if (remaining == 0) break;
            if (!stack.is(item)) continue;
            int take = Math.min(remaining, stack.getCount());
            stack.shrink(take);
            remaining -= take;
        }
    }

    /** Called after transient server state has been reset on login or respawn. */
    public static void restore(ServerPlayer player) {
        var data = player.getPersistentData();
        if (!data.getBoolean(AWAKENED_KEY)) return;
        ServerLogic.setLevel(player, Math.max(1, Math.min(5, data.getInt(LEVEL_KEY))));
        if (data.getBoolean(NEGATIVE_KEY)) ServerLogic.onAction(player, ActionPayload.THEME);
    }

    @EventBusSubscriber(modid = Speedforce.MODID)
    public static final class Persistence {
        private Persistence() {}

        @SubscribeEvent
        public static void onClone(PlayerEvent.Clone event) {
            var from = event.getOriginal().getPersistentData();
            var to = event.getEntity().getPersistentData();
            for (String key : new String[]{AWAKENED_KEY, LEVEL_KEY, NEGATIVE_KEY}) {
                if (from.contains(key)) to.put(key, from.get(key).copy());
            }
        }
    }
}
