package com.ichezzy.evolutionboost.logging;

import com.ichezzy.evolutionboost.configs.CommandLogConfig;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;

public class EvolutionboostLoggingInit implements DedicatedServerModInitializer {
    @Override
    public void onInitializeServer() {
        CommandLogConfig cfg = CommandLogConfig.loadOrCreate();
        CommandLogManager.init(cfg);

        ServerLifecycleEvents.SERVER_STOPPING.register((MinecraftServer server) -> {
            CommandLogManager.close();
        });
    }
}
