package com.example.limbodatasync;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import dev.unitydev.limbo.Limbo;
import dev.unitydev.limbo.LimboAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.slf4j.Logger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Plugin(
        id = "limbodatasync",
        name = "LimboDataSync",
        version = "1.0-SNAPSHOT",
        authors = {"YourName"}
)
public class LimboDataSync {

    private final ProxyServer server;
    private final Logger logger;
    private final Path dataDirectory;
    private LimboAPI limboAPI;
    private Config config;

    private final ConcurrentHashMap<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();

    @Inject
    public LimboDataSync(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
        try {
            this.limboAPI = Limbo.getAPI();
        } catch (Exception e) {
            logger.error("LimboAPI not found. The plugin will be disabled.");
            return;
        }

        logger.info("LimboDataSync plugin has been initialized.");
    }

    private void loadConfig() {
        Path configPath = dataDirectory.resolve("config.conf");
        try {
            if (!Files.exists(dataDirectory)) {
                Files.createDirectories(dataDirectory);
            }
            if (!Files.exists(configPath)) {
                try (InputStream in = getClass().getClassLoader().getResourceAsStream("config.conf")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    }
                }
            }
            config = ConfigFactory.parseFile(configPath.toFile()).resolve();
        } catch (IOException e) {
            logger.error("Failed to load config", e);
            config = ConfigFactory.load();
        }
    }

    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        RegisteredServer targetServer = event.getOriginalServer();

        event.setResult(ServerPreConnectEvent.ServerResult.denied());

        if (playerTasks.containsKey(player.getUniqueId())) {
            playerTasks.get(player.getUniqueId()).cancel();
        }

        limboAPI.sendPlayer(player, limboAPI.createLimbo(true));

        int delaySeconds = config.getInt("delay-seconds");
        AtomicInteger countdown = new AtomicInteger(delaySeconds);

        ScheduledTask task = server.getScheduler().buildTask(this, () -> {
            int remaining = countdown.getAndDecrement();
            if (remaining > 0) {
                player.showTitle(Title.title(
                        Component.text("Transporting to " + targetServer.getServerInfo().getName()),
                        Component.text("Arriving in " + remaining + " seconds..."),
                        Title.Times.times(Duration.ZERO, Duration.ofSeconds(1), Duration.ZERO)
                ));
            } else {
                player.createConnectionRequest(targetServer).fireAndForget();
                playerTasks.remove(player.getUniqueId()).cancel();
            }
        }).repeat(1, TimeUnit.SECONDS).schedule();

        playerTasks.put(player.getUniqueId(), task);
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (playerTasks.containsKey(player.getUniqueId())) {
            playerTasks.remove(player.getUniqueId()).cancel();
        }
    }
}