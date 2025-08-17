package com.example.limbodatasync;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

@Plugin(
        id = "limbodatasync",
        name = "LimboDataSync",
        version = "1.0-SNAPSHOT",
        authors = {"YourName"}
)
public class LimboDataSync {

    private final ProxyServer server;
    private final Logger logger;

    @Inject
    public LimboDataSync(ProxyServer server, Logger logger) {
        this.server = server;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        logger.info("LimboDataSync plugin has been initialized.");
    }
}