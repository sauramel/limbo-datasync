package com.example.limbodatasync;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import net.elytrium.limboapi.api.LimboFactory;
import net.elytrium.limboapi.api.player.LimboPlayer;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final LimboFactory limboFactory;
    private Config config;

    private final ConcurrentHashMap<UUID, ScheduledTask> playerTasks = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, LimboPlayer> limboPlayers = new ConcurrentHashMap<>();

    @Inject
    public LimboDataSync(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory, LimboFactory limboFactory) {
        this.server = server;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.limboFactory = limboFactory;
    }

    @Subscribe
    public void onProxyInitialization(ProxyInitializeEvent event) {
        loadConfig();
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
        if (limboPlayers.containsKey(player.getUniqueId())) {
            limboPlayers.get(player.getUniqueId()).disconnect();
        }

        LimboPlayer limboPlayer = this.limboFactory.createVirtualPlayer(player, limboPlayer1 -> {
            limboPlayers.put(player.getUniqueId(), limboPlayer1);

            int delaySeconds = config.getInt("limbo.delay-seconds");
            AtomicInteger countdown = new AtomicInteger(delaySeconds);

            ScheduledTask task = server.getScheduler().buildTask(this, () -> {
                int remaining = countdown.getAndDecrement();
                if (remaining > 0) {
                    limboPlayer1.sendTitle(
                            Component.text("Transporting to " + targetServer.getServerInfo().getName()),
                            Component.text("Arriving in " + remaining + " seconds..."),
                            0, 20, 0
                    );
                } else {
                    player.createConnectionRequest(targetServer).fireAndForget();
                    limboPlayers.remove(player.getUniqueId()).disconnect();
                    playerTasks.remove(player.getUniqueId()).cancel();
                }
            }).repeat(1, TimeUnit.SECONDS).schedule();

            playerTasks.put(player.getUniqueId(), task);
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        Player player = event.getPlayer();
        if (playerTasks.containsKey(player.getUniqueId())) {
            playerTasks.remove(player.getUniqueId()).cancel();
        }
        if (limboPlayers.containsKey(player.getUniqueId())) {
            limboPlayers.remove(player.getUniqueId()).disconnect();
        }
    }
}