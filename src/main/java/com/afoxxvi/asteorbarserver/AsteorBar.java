package com.afoxxvi.asteorbarserver;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

public final class AsteorBar extends JavaPlugin implements Listener {
    private static final Map<String, Float> EXHAUSTION = new HashMap<>();
    private static final Map<String, Float> SATURATION = new HashMap<>();
    private static float saturationUpdateThreshold = 0.01F;
    private static float exhaustionUpdateThreshold = 0.01F;
    private static final Set<String> activatedPlayers = new HashSet<>();

    @Override
    public void onEnable() {
        var dataFolder = getDataFolder();
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        var configFile = new File(dataFolder, "config.yml");
        if (!configFile.exists()) {
            saveDefaultConfig();
        }
        var messenger = getServer().getMessenger();
        messenger.registerOutgoingPluginChannel(this, "asteorbar:network");
        messenger.registerIncomingPluginChannel(this, "asteorbar:network", (channel, player, bytes) -> {
            getLogger().info("Received asteorbar:network activation from " + player.getName() + ", start sending packets.");
            activatedPlayers.add(player.getName());
        });
        getServer().getPluginManager().registerEvents(this, this);
        reload();
        var command = getCommand("asteorbar");
        if (command != null) {
            command.setExecutor((commandSender, command1, s, strings) -> {
                reload();
                commandSender.sendMessage(ChatColor.GREEN + "AsteorBar Reloaded.");
                return true;
            });
        }
    }

    private void reload() {
        reloadConfig();
        boolean dirty = false;
        if (!getConfig().contains("updateInterval")) {
            getConfig().set("updateInterval", 2);
            getConfig().setComments("updateInterval", List.of("How often the plugin will send packets to players, in ticks."));
            dirty = true;
        }
        if (!getConfig().contains("saturationUpdateThreshold")) {
            getConfig().set("saturationUpdateThreshold", 0.01F);
            getConfig().setComments("saturationUpdateThreshold", List.of("Only when player's saturation difference is greater than this value, the plugin will send a packet to the player."));
            dirty = true;
        }
        if (!getConfig().contains("exhaustionUpdateThreshold")) {
            getConfig().set("exhaustionUpdateThreshold", 0.01F);
            getConfig().setComments("exhaustionUpdateThreshold", List.of("Only when player's saturation difference is greater than this value, the plugin will send a packet to the player."));
            dirty = true;
        }
        var interval = getConfig().getInt("updateInterval");
        exhaustionUpdateThreshold = (float) getConfig().getDouble("exhaustionUpdateThreshold");
        saturationUpdateThreshold = (float) getConfig().getDouble("saturationUpdateThreshold");
        if (dirty) {
            saveConfig();
        }
        getServer().getScheduler().cancelTasks(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::sendPacket, 0, interval);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        var player = event.getPlayer();
        new BukkitRunnable() {
            int tries = 0;

            @Override
            public void run() {
                tries++;
                if (!player.isOnline() || tries >= 10 || activatedPlayers.contains(player.getName())) {
                    cancel();
                    return;
                }
                player.sendPluginMessage(AsteorBar.this, "asteorbar:network", ByteBuffer.allocate(2).put((byte) 3).put((byte) 1).array());
            }
        }.runTaskTimerAsynchronously(this, 20, 20);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        activatedPlayers.remove(event.getPlayer().getName());
    }

    private void sendPacket() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            var exhaustionLevel = player.getExhaustion();
            var oldExhaustion = EXHAUSTION.get(player.getName());
            if (oldExhaustion == null || Math.abs(oldExhaustion - exhaustionLevel) >= exhaustionUpdateThreshold) {
                EXHAUSTION.put(player.getName(), exhaustionLevel);
                player.sendPluginMessage(this, "asteorbar:network", ByteBuffer.allocate(1 + Float.BYTES).put((byte) 0).putFloat(exhaustionLevel).array());
            }
            var saturationLevel = player.getSaturation();
            var oldSaturation = SATURATION.get(player.getName());
            if (oldSaturation == null || Math.abs(oldSaturation - saturationLevel) >= saturationUpdateThreshold) {
                SATURATION.put(player.getName(), saturationLevel);
                player.sendPluginMessage(this, "asteorbar:network", ByteBuffer.allocate(1 + Float.BYTES).put((byte) 1).putFloat(saturationLevel).array());
            }
        }
    }
}
