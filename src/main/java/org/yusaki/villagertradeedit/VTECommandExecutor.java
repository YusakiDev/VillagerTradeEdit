package org.yusaki.villagertradeedit;

import com.tcoded.folialib.FoliaLib;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.BlockIterator;
import org.bukkit.util.RayTraceResult;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class VTECommandExecutor implements CommandExecutor, TabCompleter {
    private final VillagerTradeEdit plugin;
    private final YskLibWrapper wrapper;
    private final VillagerEditListener villagerEditListener;
    private final FoliaLib foliaLib;
    private final Component prefixComponent;
    private final boolean prefixEnabled;
    private final NamespacedKey forceSpawnKey;
    private final Map<UUID, Integer> selections = new HashMap<>();
    private final VillagerRegistry registry;
    private final NamespacedKey vteIdKey;
    private final NamespacedKey staticKey;

    public VTECommandExecutor(VillagerTradeEdit plugin, VillagerEditListener villagerEditListener) {
        this.plugin = plugin;
        this.wrapper = VillagerTradeEdit.getInstance().wrapper;
        this.villagerEditListener = villagerEditListener;
        this.foliaLib = plugin.getFoliaLib();
        this.forceSpawnKey = plugin.getForceSpawnKey();
        this.registry = plugin.getVillagerRegistry();
        this.vteIdKey = plugin.getVteIdKey();
        this.staticKey = new NamespacedKey(plugin, "static");

        Component fromYskLib = wrapper.getPrefixComponent();
        Component resolved = fromYskLib != null ? fromYskLib : Component.empty();

        // Fallback: if YskLib has no prefix (loadMessages not yet run, or section missing),
        // read directly from config so prefix still renders.
        if (resolved.equals(Component.empty())) {
            String fromConfig = plugin.getConfig().getString("messages.prefix", "");
            if (fromConfig != null && !fromConfig.isBlank()) {
                resolved = LegacyComponentSerializer.legacyAmpersand().deserialize(fromConfig);
            }
        }

        this.prefixEnabled = !resolved.equals(Component.empty());
        this.prefixComponent = resolved;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }


        if (!player.hasPermission("villagertradeedit.command")) {
            wrapper.sendMessage(player, "noPermission");
            return true;
        }

        if (args.length == 0 || "help".equalsIgnoreCase(args[0])) {
            sendPluginInfo(player);
            return true;
        }

        if ("summon".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("villagertradeedit.command.summon")) {
                wrapper.sendMessage(player, "noPermission");
                return true;
            }
            if (!wrapper.canExecuteInWorld(player.getWorld())) {
                wrapper.sendMessage(player, "disabledWorld");
                return true;
            }
            BlockIterator iterator = new BlockIterator(player, 5);
            while (iterator.hasNext()) {
                Block block = iterator.next();
                if (block.getType() != Material.AIR) {
                    Location spawnLocation = block.getLocation().add(0, 1, 0);
                    // Adjust the spawn location to be at the center of the block
                    spawnLocation.setX(Math.floor(spawnLocation.getX()) + 0.5);
                    spawnLocation.setZ(Math.floor(spawnLocation.getZ()) + 0.5);

                    Villager villager = player.getWorld().spawn(spawnLocation, Villager.class, spawned ->
                            spawned.getPersistentDataContainer().set(forceSpawnKey, PersistentDataType.BYTE, (byte) 1));
                    foliaLib.getScheduler().runAtEntity(villager, task -> {
                        villagerEditListener.activateStaticMode(villager, player);
                        villager.teleportAsync(spawnLocation);
                    });
                    return true;
                }
            }
            wrapper.sendMessage(player, "noBlockInRange");
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            if (!player.hasPermission("villagertradeedit.command.reload")) {
                wrapper.sendMessage(player, "noPermission");
                return true;
            }
            plugin.reloadPluginConfig();
            wrapper.sendMessage(player, "configReloaded");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "select" -> { return handleSelect(player, args); }
            case "deselect" -> { return handleDeselect(player); }
            case "tp" -> { return handleTp(player, args); }
            case "tphere" -> { return handleTpHere(player, args); }
            case "moveto" -> { return handleMoveTo(player, args); }
            case "list" -> { return handleList(player, args); }
            default -> { return false; }
        }
    }

    private boolean requireMovePermission(Player player) {
        if (!player.hasPermission("villagertradeedit.command.move")) {
            wrapper.sendMessage(player, "noPermission");
            return false;
        }
        return true;
    }

    private boolean handleSelect(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        if (args.length >= 2) {
            try {
                int id = Integer.parseInt(args[1]);
                VillagerEntry entry = registry.getById(id);
                if (entry == null) {
                    wrapper.sendMessage(player, "villagerIdUnknown", "0", String.valueOf(id));
                    return true;
                }
                selections.put(player.getUniqueId(), id);
                wrapper.sendMessage(player, "villagerSelected", "0", String.valueOf(id), "1", entry.name());
                return true;
            } catch (NumberFormatException ex) {
                wrapper.sendMessage(player, "villagerIdUnknown", "0", args[1]);
                return true;
            }
        }
        if (!wrapper.canExecuteInWorld(player.getWorld())) {
            wrapper.sendMessage(player, "disabledWorld");
            return true;
        }
        RayTraceResult result = player.getWorld().rayTraceEntities(
                player.getEyeLocation(),
                player.getEyeLocation().getDirection(),
                10.0,
                e -> e instanceof Villager && !e.equals(player));
        if (result == null || !(result.getHitEntity() instanceof Villager villager)) {
            wrapper.sendMessage(player, "noVillagerInSight");
            return true;
        }
        PersistentDataContainer pdc = villager.getPersistentDataContainer();
        if (!pdc.has(staticKey, PersistentDataType.STRING)) {
            wrapper.sendMessage(player, "notManagedVillager");
            return true;
        }
        Integer existingId = pdc.get(vteIdKey, PersistentDataType.INTEGER);
        int id = (existingId != null) ? existingId : registry.assignId(villager);
        if (existingId == null) {
            pdc.set(vteIdKey, PersistentDataType.INTEGER, id);
        }
        selections.put(player.getUniqueId(), id);
        String name = villager.getCustomName() != null ? villager.getCustomName() : "";
        wrapper.sendMessage(player, "villagerSelected", "0", String.valueOf(id), "1", name);
        return true;
    }

    private boolean handleDeselect(Player player) {
        if (!requireMovePermission(player)) return true;
        selections.remove(player.getUniqueId());
        wrapper.sendMessage(player, "selectionCleared");
        return true;
    }

    private boolean handleTp(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        Integer targetId;
        if (args.length >= 2) {
            try {
                targetId = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                wrapper.sendMessage(player, "villagerIdUnknown", "0", args[1]);
                return true;
            }
        } else {
            targetId = selections.get(player.getUniqueId());
            if (targetId == null) {
                wrapper.sendMessage(player, "noSelection");
                return true;
            }
        }
        VillagerEntry entry = registry.getById(targetId);
        if (entry == null) {
            if (args.length < 2) selections.remove(player.getUniqueId());
            wrapper.sendMessage(player, "villagerIdUnknown", "0", String.valueOf(targetId));
            return true;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            wrapper.sendMessage(player, "worldNotFound", "0", entry.world());
            return true;
        }
        Location dest = new Location(world, entry.x(), entry.y(), entry.z());
        if (!world.getWorldBorder().isInside(dest)) {
            wrapper.sendMessage(player, "outsideWorldBorder");
            return true;
        }
        int chunkX = (int) Math.floor(entry.x()) >> 4;
        int chunkZ = (int) Math.floor(entry.z()) >> 4;
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk ->
                foliaLib.getScheduler().runAtLocation(dest, t -> {
                    player.teleportAsync(dest);
                    wrapper.sendMessage(player, "playerTeleported", "0", String.valueOf(entry.id()));
                }));
        return true;
    }

    private boolean handleTpHere(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        Integer selectedId = selections.get(player.getUniqueId());
        if (selectedId == null) {
            wrapper.sendMessage(player, "noSelection");
            return true;
        }
        VillagerEntry entry = registry.getById(selectedId);
        if (entry == null) {
            selections.remove(player.getUniqueId());
            wrapper.sendMessage(player, "selectionGone");
            return true;
        }
        Location dest = player.getLocation();
        if (!dest.getWorld().getWorldBorder().isInside(dest)) {
            wrapper.sendMessage(player, "outsideWorldBorder");
            return true;
        }
        resolveVillager(entry, villager -> {
            if (villager == null) {
                selections.remove(player.getUniqueId());
                wrapper.sendMessage(player, "selectionGone");
                return;
            }
            foliaLib.getScheduler().runAtEntity(villager, t -> {
                villager.teleportAsync(dest);
                String name = villager.getCustomName() != null ? villager.getCustomName() : "";
                registry.updateLocation(villager.getUniqueId(), dest, name);
                wrapper.sendMessage(player, "villagerMoved", "0", String.valueOf(entry.id()));
            });
        });
        return true;
    }

    private void resolveVillager(VillagerEntry entry, java.util.function.Consumer<Villager> callback) {
        Entity direct = Bukkit.getEntity(entry.uuid());
        if (direct instanceof Villager v && v.isValid()) {
            callback.accept(v);
            return;
        }
        World world = Bukkit.getWorld(entry.world());
        if (world == null) {
            callback.accept(null);
            return;
        }
        int chunkX = (int) Math.floor(entry.x()) >> 4;
        int chunkZ = (int) Math.floor(entry.z()) >> 4;
        world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
            Entity retry = Bukkit.getEntity(entry.uuid());
            callback.accept(retry instanceof Villager vv && vv.isValid() ? vv : null);
        });
    }

    private boolean handleMoveTo(Player player, String[] args) {
        if (!requireMovePermission(player)) return true;
        if (args.length < 4) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        Integer selectedId = selections.get(player.getUniqueId());
        if (selectedId == null) {
            wrapper.sendMessage(player, "noSelection");
            return true;
        }
        VillagerEntry entry = registry.getById(selectedId);
        if (entry == null) {
            selections.remove(player.getUniqueId());
            wrapper.sendMessage(player, "selectionGone");
            return true;
        }
        double x, y, z;
        try {
            x = Double.parseDouble(args[1]);
            y = Double.parseDouble(args[2]);
            z = Double.parseDouble(args[3]);
        } catch (NumberFormatException ex) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            wrapper.sendMessage(player, "invalidCoords");
            return true;
        }
        World world;
        if (args.length >= 5) {
            world = Bukkit.getWorld(args[4]);
            if (world == null) {
                wrapper.sendMessage(player, "worldNotFound", "0", args[4]);
                return true;
            }
        } else {
            world = player.getWorld();
        }
        Location dest = new Location(world, x, y, z);
        if (!world.getWorldBorder().isInside(dest)) {
            wrapper.sendMessage(player, "outsideWorldBorder");
            return true;
        }
        resolveVillager(entry, villager -> {
            if (villager == null) {
                selections.remove(player.getUniqueId());
                wrapper.sendMessage(player, "selectionGone");
                return;
            }
            foliaLib.getScheduler().runAtEntity(villager, t -> {
                villager.teleportAsync(dest);
                String name = villager.getCustomName() != null ? villager.getCustomName() : "";
                registry.updateLocation(villager.getUniqueId(), dest, name);
                wrapper.sendMessage(player, "villagerMoved", "0", String.valueOf(entry.id()));
            });
        });
        return true;
    }

    private boolean handleList(Player player, String[] args) {
        if (!player.hasPermission("villagertradeedit.command.list")) {
            wrapper.sendMessage(player, "noPermission");
            return true;
        }
        List<VillagerEntry> all = registry.all();
        List<VillagerEntry> visible = new ArrayList<>();
        for (VillagerEntry e : all) {
            World w = Bukkit.getWorld(e.world());
            if (w != null && wrapper.canExecuteInWorld(w)) {
                visible.add(e);
            }
        }
        if (visible.isEmpty()) {
            wrapper.sendMessage(player, "listEmpty");
            return true;
        }
        int perPage = 10;
        int total = visible.size();
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        int page = 1;
        if (args.length >= 2) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ex) {
                page = 1;
            }
        }
        if (page < 1) page = 1;
        if (page > pages) page = pages;

        Map<String, String> headerPh = new HashMap<>();
        headerPh.put("0", String.valueOf(page));
        headerPh.put("1", String.valueOf(pages));
        String header = wrapper.getMessage("listHeader", headerPh);
        sendPrefixed(player, LegacyComponentSerializer.legacyAmpersand().deserialize(header));

        LegacyComponentSerializer legacy = LegacyComponentSerializer.legacyAmpersand();
        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, total);
        for (int i = start; i < end; i++) {
            VillagerEntry e = visible.get(i);
            Map<String, String> rowPh = new HashMap<>();
            rowPh.put("0", String.valueOf(e.id()));
            rowPh.put("1", e.name() == null ? "" : e.name());
            rowPh.put("2", e.world());
            rowPh.put("3", String.format("%.1f", e.x()));
            rowPh.put("4", String.format("%.1f", e.y()));
            rowPh.put("5", String.format("%.1f", e.z()));
            String rowText = wrapper.getMessage("listEntry", rowPh);
            Component row = legacy.deserialize(rowText)
                    .append(Component.text(" "))
                    .append(Component.text("[tp]")
                            .color(NamedTextColor.AQUA)
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/vte tp " + e.id()))
                            .hoverEvent(HoverEvent.showText(Component.text("/vte tp " + e.id()))));
            sendPrefixed(player, row);
        }

        if (pages > 1) {
            Map<String, String> footerPh = new HashMap<>();
            footerPh.put("0", String.valueOf(page));
            footerPh.put("1", String.valueOf(pages));
            footerPh.put("2", String.valueOf(total));
            String footer = wrapper.getMessage("listFooter", footerPh);
            sendPrefixed(player, legacy.deserialize(footer));
        }
        return true;
    }

    public Map<UUID, Integer> getSelections() {
        return selections;
    }

    private void sendPluginInfo(Player player) {
        String name = plugin.getDescription().getName();
        String version = plugin.getDescription().getVersion();
        String author = String.join(", ", plugin.getDescription().getAuthors());

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text(name + " v" + version)
                .color(NamedTextColor.GOLD)
                .decorate(TextDecoration.BOLD));
        sendPrefixed(player, Component.text("Custom villager trade editor (Paper/Folia).")
                .color(NamedTextColor.GRAY));
        sendPrefixed(player, Component.text("Author: ")
                .color(NamedTextColor.DARK_GRAY)
                .append(Component.text(author).color(NamedTextColor.WHITE)));

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text("Commands:").color(NamedTextColor.GOLD));
        sendPrefixed(player, Component.text(" • /vte summon").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Spawn managed static villager").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte select [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Select managed villager (look-at or by ID)").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte deselect").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Clear selection").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte tp [id]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Teleport to selected villager").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte tphere").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Teleport selected villager to you").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte moveto <x> <y> <z> [world]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Move selected to coords").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte list [page]").color(NamedTextColor.YELLOW)
                .append(Component.text(" – List managed villagers").color(NamedTextColor.GRAY)));
        sendPrefixed(player, Component.text(" • /vte reload").color(NamedTextColor.YELLOW)
                .append(Component.text(" – Reload configuration").color(NamedTextColor.GRAY)));

        player.sendMessage(Component.text(""));
        sendPrefixed(player, Component.text("Interactions:").color(NamedTextColor.GOLD));
        sendPrefixed(player, Component.text(" • Right-click: trade (permission if set)").color(NamedTextColor.GRAY));
        sendPrefixed(player, Component.text(" • Shift-right-click: open editor").color(NamedTextColor.GRAY));
        player.sendMessage(Component.text(""));
    }

    private void sendPrefixed(Player player, Component message) {
        if (prefixEnabled) {
            player.sendMessage(prefixComponent.append(message));
        } else {
            player.sendMessage(message);
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (!(sender instanceof Player)) return null;
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("summon");
            completions.add("select");
            completions.add("deselect");
            completions.add("tp");
            completions.add("tphere");
            completions.add("moveto");
            completions.add("list");
            completions.add("reload");
            completions.add("help");
            String prefix = args[0].toLowerCase();
            List<String> filtered = new ArrayList<>();
            for (String c : completions) {
                if (c.startsWith(prefix)) filtered.add(c);
            }
            return filtered;
        }
        String sub = args[0].toLowerCase();
        if (args.length == 2 && (sub.equals("select") || sub.equals("tp"))) {
            List<String> ids = new ArrayList<>();
            for (VillagerEntry e : registry.all()) {
                ids.add(String.valueOf(e.id()));
            }
            return ids;
        }
        if (sub.equals("moveto")) {
            if (args.length == 5) {
                List<String> worlds = new ArrayList<>();
                for (World w : Bukkit.getWorlds()) {
                    worlds.add(w.getName());
                }
                return worlds;
            }
            return new ArrayList<>();
        }
        return null;
    }
}
