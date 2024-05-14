package org.yusaki.villagertradeedit;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStreamReader;
import java.util.List;
import java.util.Objects;


public final class VillagerTradeEdit extends JavaPlugin {

    //TODO stop villager moving if already pathfind
    //TODO add villager rotate to player
    //TODO add VillagerName
    //TODO Cancel Name change event
    //TODO Save Button Instead of Closing button
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

    public boolean canExecuteInWorld(World world) {
        // Get a reference to your plugin's configuration.
        // How you do this will likely be different based on your plugin's structure.
        FileConfiguration config = getConfig();

        // Load the enabled worlds from the config into a list.
        List<String> enabledWorlds = config.getStringList("enabled-worlds");

        // Check if the current world's name is in the list of enabled worlds.
        return enabledWorlds.contains(world.getName());
    }

    void sendMessage(CommandSender sender, String key , Object... args) {
        // Retrieve the message from the configuration
        String message = getConfig().getString("messages." + key);
        String prefix = getConfig().getString("messages.prefix");
        if (prefix == null) {
            prefix = "";
        }
        if (message != null) {
            // Format the message with the provided arguments
            message = String.format(message, args);

            // Translate color codes
            message = ChatColor.translateAlternateColorCodes('&', message);
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);

            sender.sendMessage(prefix + message);
        } else {

            key = ChatColor.translateAlternateColorCodes('&', key);
            prefix = ChatColor.translateAlternateColorCodes('&', prefix);

            sender.sendMessage(prefix + key);
        }
    }

    public void logDebug(String message) {
        if (getConfig().getBoolean("debug")) {
            getLogger().info(message);
        }
    }

    public void logDebugPlayer(Player player, String message) {
        if (getConfig().getBoolean("debug")) {
            sendMessage(player, message);
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

    void reloadPlugin() {
        reloadConfig();
    }
}
