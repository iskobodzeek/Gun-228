package com.alienguns;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlienGuns implements ModInitializer {
    public static final String MOD_ID = "alienguns";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        ModItems.init();
        ServerTickEvents.END_SERVER_TICK.register(server -> Fx.tickAll());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> Fx.clear());
        LOGGER.info("Alien Guns loaded: {} guns", ModItems.ALL.size());
    }
}
