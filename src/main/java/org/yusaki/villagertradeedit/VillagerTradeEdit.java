package org.yusaki.villagertradeedit;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.util.Objects;


public final class VillagerTradeEdit extends JavaPlugin {


    //TODO add permission
    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO Toggle item Visibility (On open inventory)
    @Override
    public void onEnable() {
        saveDefaultConfig();
        updateConfig();
        getLogger().info("VillagerTradeEdit enabled!");
        VillagerEditListener villagerEditListener = new VillagerEditListener(this);
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
    }

    void sendMessage(CommandSender sender, String key , Object... args) {
        // Retrieve the message from the configuration
        String message = getConfig().getString("messages." + key);
        String prefix = getConfig().getString("messages.prefix");
        if (message != null && prefix != null) {
            // Format the message with the provided arguments
            message = String.format(message, args);

            // Translate color codes
            message = ChatColor.translateAlternateColorCodes('&', message);
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);

            sender.sendMessage(prefix + message);
        } else {
            sender.sendMessage("Raw message: " + key);
        }
    }

    public void logDebug(String message) {
        if (getConfig().getBoolean("debug")) {
            getLogger().info(message);
        }
    }

    public void logDebugPlayer(Player player, String message) {
        if (getConfig().getBoolean("debug")) {
            player.sendMessage(message);
        }
    }

    public void updateConfig() {
        reloadConfig();
        // Load the default configuration from the JAR file
        YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Objects.requireNonNull(getResource("config.yml"))));

        // Get the version of the default configuration
        double defaultVersion = defaultConfig.getDouble("version");
        logDebug("Plugin config version: " + defaultVersion);


        // Get the version of the configuration on the file system
        double currentVersion = getConfig().getDouble("version");
        logDebug("Current config version: " + currentVersion);
        // If the default configuration is newer
        if (defaultVersion > currentVersion) {

            logDebug("Config Version Mismatched, Updating config file...");

            for (String key : defaultConfig.getKeys(true)) {
                getLogger().info("Checking key: " + key);
                if (!getConfig().isSet(key)) {
                    logDebug("Missing Config, Adding new config value: " + key);

                    getConfig().set(key, defaultConfig.get(key));
                } else {
                    logDebug("Config value already exists: " + key);
                }
                // change the version to the default version
                getConfig().set("version", defaultVersion);
            }
            // Save the configuration file
            saveConfig();
        }
        else {
            logDebug("Config file is up to date.");

        }

        // Reload the configuration file to get any changes
        reloadConfig();
    }

    private void reloadPlugin() {
        reloadConfig();
    }
}
