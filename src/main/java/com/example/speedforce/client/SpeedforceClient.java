package com.example.speedforce.client;

import com.example.speedforce.Speedforce;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

/** Second @Mod class for the same id: only ever loaded on the physical client. */
@Mod(value = Speedforce.MODID, dist = Dist.CLIENT)
public class SpeedforceClient {
    public SpeedforceClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(ClientEvents::registerKeys);
        modBus.addListener(ClientEvents::registerLayers);
        modBus.addListener(ClientEvents::registerParticles);

        NeoForge.EVENT_BUS.addListener(ClientEvents::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onFov);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onRender);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onPlayerClone);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onEntityLeave);
    }
}
