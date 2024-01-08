package com.afoxxvi.asteorbarserver;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.*;

public final class AsteorBar extends JavaPlugin {
    private static final Map<String, Float> EXHAUSTION = new HashMap<>();
    private static final Map<String, Float> SATURATION = new HashMap<>();
    private static float saturationUpdateThreshold = 0.01F;
    private static float exhaustionUpdateThreshold = 0.01F;
    private static final Set<String> REGISTERED_PLAYERS = new HashSet<>();
    private static boolean registerNeeded = false;

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
        reload();
        var registerCommand = getCommand("asteorbar");
        if (registerCommand != null) {
            registerCommand.setExecutor((commandSender, command1, s, strings) -> {
                if (commandSender instanceof Player p) {
                    var name = p.getName();
                    if (REGISTERED_PLAYERS.contains(name)) {
                        REGISTERED_PLAYERS.remove(name);
                        commandSender.sendMessage(ChatColor.GREEN + "AsteorBar Unregistered. The server will no longer send you saturation and exhaustion updates.");
                    } else {
                        REGISTERED_PLAYERS.add(name);
                        commandSender.sendMessage(ChatColor.GREEN + "AsteorBar Registered. The server will send you saturation and exhaustion updates. (No effect if you are not using AsteorBar client mod)");
                    }
                } else {
                    commandSender.sendMessage(ChatColor.RED + "Only players can use this command.");
                }
                return true;
            });
        }
        var command = getCommand("asteorbarreload");
        if (command != null) {
            command.setExecutor((commandSender, command1, s, strings) -> {
                reload();
                commandSender.sendMessage(ChatColor.GREEN + "AsteorBar Reloaded.");
                return true;
            });
        }
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::savePlayers, 0, 20 * 60 * 20);
    }

    private void reload() {
        //collect all players to list
        var oldValue = REGISTERED_PLAYERS.stream().toList();
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
        if (!getConfig().contains("registerNeeded")) {
            getConfig().set("registerNeeded", false);
            getConfig().setComments("registerNeeded", List.of("If true, the plugin will only send packets to players who are registered, players can use /asteorbar to register and unregister."));
            dirty = true;
        }
        var interval = getConfig().getInt("updateInterval");
        exhaustionUpdateThreshold = (float) getConfig().getDouble("exhaustionUpdateThreshold");
        saturationUpdateThreshold = (float) getConfig().getDouble("saturationUpdateThreshold");
        registerNeeded = getConfig().getBoolean("registerNeeded");
        REGISTERED_PLAYERS.clear();
        var players = getConfig().getStringList("registeredPlayers");
        REGISTERED_PLAYERS.addAll(players);
        if (!oldValue.isEmpty()) {
            getConfig().set("registeredPlayers", oldValue);
            dirty = true;
        }
        if (dirty) {
            saveConfig();
        }
        getServer().getScheduler().cancelTasks(this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, this::sendPacket, 0, interval);
    }

    private void savePlayers() {
        getConfig().set("registeredPlayers", REGISTERED_PLAYERS.stream().toList());
        saveConfig();
    }

    @Override
    public void onDisable() {
        savePlayers();
    }

    private void sendPacket() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (registerNeeded && !REGISTERED_PLAYERS.contains(player.getName())) {
                continue;
            }
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
