package org.yusaki.villagertradeedit;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.yusaki.lib.YskLib;
import org.yusaki.lib.config.ConfigMigration;
import org.yusaki.lib.config.ConfigUpdateOptions;
import org.yusaki.lib.modules.CommandAliasManager;


public final class VillagerTradeEdit extends JavaPlugin {

    private YskLib yskLib;
    public YskLibWrapper wrapper;
    private FoliaLib foliaLib;
    private VillagerEditListener villagerEditListener;
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        yskLib = (YskLib) Bukkit.getPluginManager().getPlugin("YskLib");
        updateConfigSchema();
        yskLib.loadMessages(this);
        wrapper = new YskLibWrapper(yskLib);
        foliaLib = new FoliaLib(this);
        getLogger().info("VillagerTradeEdit enabled!");
        villagerEditListener = new VillagerEditListener();
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
        applyCommandAliases();

        // Restore data for any already-loaded villagers (important for plugin reloads and server wake-ups)
        restoreAllLoadedVillagers(villagerEditListener);
    }

    /**
     * Restores data for all already-loaded managed villagers.
     * Called during plugin enable to handle cases where:
     * - Plugin is reloaded while server is running
     * - Server wakes from sleep with chunks already loaded
     * - Plugin loads after villagers are already in memory
     */
    private void restoreAllLoadedVillagers(VillagerEditListener listener) {
        foliaLib.getScheduler().runNextTick((task) -> {
            int restoredCount = 0;
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                if (!wrapper.canExecuteInWorld(world)) {
                    continue;
                }
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (entity instanceof org.bukkit.entity.Villager) {
                        org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                        org.bukkit.persistence.PersistentDataContainer pdc = villager.getPersistentDataContainer();
                        org.bukkit.NamespacedKey staticKey = new org.bukkit.NamespacedKey(this, "static");

                        if (pdc.has(staticKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                            listener.retrieveVillagerData(villager);
                            restoredCount++;
                        }
                    }
                }
            }
            if (restoredCount > 0) {
                getLogger().info("Restored data for " + restoredCount + " already-loaded managed villagers");
            }
        });
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabling, saving all managed villagers...");
        saveAllManagedVillagers();
        getLogger().info("VillagerTradeEdit disabled!");
    }

    /**
     * Saves data for all managed villagers across all enabled worlds.
     * Called during plugin disable to ensure persistence through server restarts and sleep cycles.
     */
    private void saveAllManagedVillagers() {
        int savedCount = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            if (!wrapper.canExecuteInWorld(world)) {
                continue;
            }
            for (org.bukkit.entity.Entity entity : world.getEntities()) {
                if (entity instanceof org.bukkit.entity.Villager) {
                    org.bukkit.entity.Villager villager = (org.bukkit.entity.Villager) entity;
                    org.bukkit.persistence.PersistentDataContainer pdc = villager.getPersistentDataContainer();
                    org.bukkit.NamespacedKey staticKey = new org.bukkit.NamespacedKey(this, "static");

                    if (pdc.has(staticKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                        if (villagerEditListener != null) {
                            // Store data synchronously during shutdown
                            villagerEditListener.storeVillagerDataSync(villager);
                            savedCount++;
                        }
                    }
                }
            }
        }
        getLogger().info("Saved data for " + savedCount + " managed villagers");
    }

    public static VillagerTradeEdit getInstance() {
        return getPlugin(VillagerTradeEdit.class);
    }

    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

    public void reloadPluginConfig() {
        updateConfigSchema();
        yskLib.loadMessages(this);
        applyCommandAliases();
    }

    private void updateConfigSchema() {
        ConfigUpdateOptions configOptions = ConfigUpdateOptions.builder()
                .fileName("config.yml")
                .resourcePath("config.yml")
                .versionPath("version")
                .reloadAction(file -> reloadConfig())
                .resetAction(file -> saveDefaultConfig())
                .skipMergeIfVersionMatches(true)
                .addMigration(ConfigMigration.of(3.0, configuration -> {
                    Object value = configuration.get("debug");
                    if (value instanceof Boolean booleanValue) {
                        configuration.set("debug", booleanValue ? 3 : 0);
                    }
                }, "Convert legacy boolean debug flag to numeric level"))
                .build();
        yskLib.updateConfig(this, configOptions);
    }

    private void applyCommandAliases() {
        CommandAliasManager.applyAliases(this, "vte", getConfig(), "settings.command-aliases.vte");
    }

}
