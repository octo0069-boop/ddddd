package com.example.speedforce;

import com.example.speedforce.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Speedforce.MODID)
public class Speedforce {
    public static final String MODID = "speedforce";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MODID, path);
    }

    public Speedforce(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModParticles.PARTICLES.register(modBus);
        ModContent.BLOCKS.register(modBus);
        ModContent.ITEMS.register(modBus);
        modBus.addListener(Network::register);
    }
}
