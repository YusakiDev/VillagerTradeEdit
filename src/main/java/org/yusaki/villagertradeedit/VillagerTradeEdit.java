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
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        yskLib = (YskLib) Bukkit.getPluginManager().getPlugin("YskLib");
        updateConfigSchema();
        yskLib.loadMessages(this);
        wrapper = new YskLibWrapper(yskLib);
        foliaLib = new FoliaLib(this);
        getLogger().info("VillagerTradeEdit enabled!");
        VillagerEditListener villagerEditListener = new VillagerEditListener();
        getServer().getPluginManager().registerEvents(villagerEditListener, this);
        VTECommandExecutor vteCommandExecutor = new VTECommandExecutor(this, villagerEditListener);
        this.getCommand("vte").setExecutor(vteCommandExecutor);
        this.getCommand("vte").setTabCompleter(vteCommandExecutor);
        applyCommandAliases();
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerTradeEdit disabled!");
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
