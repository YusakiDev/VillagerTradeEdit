package org.yusaki.villagertradeedit;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import com.tcoded.folialib.FoliaLib;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A class representing a listener for villager trading events in the VillagerTradeEdit plugin.
 * Inherits from the Object and Listener classes.
 */
public class VillagerEditListener implements Listener {

    private VillagerTradeEdit plugin;
    YskLibWrapper wrapper;
    private final Map<Inventory, Villager> inventoryMap = new HashMap<>();
    private final Map<Inventory, Boolean> tradeAlteredMap = new HashMap<>();
    private final Map<Inventory, Integer> pageMap = new HashMap<>();
    private final Map<Villager, List<RecipeRow>> editBuffer = new HashMap<>();
    private final Map<UUID, Boolean> staticMap = new HashMap<>();
    private final Map<UUID, String> permissionMap = new HashMap<>();
    private final Map<Villager, Villager.Profession> pendingProfessionMap = new HashMap<>();
    // Allows profession change events initiated by the plugin to pass
    private final Set<UUID> allowCareerChange = new HashSet<>();
    // Tracks temporarily removed Hero of the Village effects per player during trading
    private final Map<UUID, PotionEffect> removedHotv = new HashMap<>();

    private final NamespacedKey STATIC_KEY;
    private final NamespacedKey PROFESSION_KEY;
    private final NamespacedKey TRADES_KEY;
    private final NamespacedKey PERMISSION_KEY;
    private final NamespacedKey TYPE_KEY;
    private final NamespacedKey LEVEL_KEY;
    private final NamespacedKey FORCE_SPAWN_KEY;
    FoliaLib foliaLib;


    public VillagerEditListener() {
        this.plugin = VillagerTradeEdit.getInstance();
        this.foliaLib = plugin.getFoliaLib();
        this.wrapper = VillagerTradeEdit.getInstance().wrapper;
        STATIC_KEY = new NamespacedKey(plugin, "static");
        PROFESSION_KEY = new NamespacedKey(plugin, "profession");
        TRADES_KEY = new NamespacedKey(plugin, "trades");
        PERMISSION_KEY = new NamespacedKey(plugin, "permission");
        TYPE_KEY = new NamespacedKey(plugin, "type");
        LEVEL_KEY = new NamespacedKey(plugin, "level");
        FORCE_SPAWN_KEY = plugin.getForceSpawnKey();
    }


    /**
     * The onChunkLoad method is an event handler method that is called when a chunk is loaded in the world.
     * It iterates through all entities in the loaded chunk and checks if they are instances of Villager.
     * If a Villager entity has persistent data stored with a specific key, it attempts to retrieve
     * the stored data and reapply it to ensure state consistency after chunk reloads.
     *
     * @param event The ChunkLoadEvent object representing the event that occurred.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!wrapper.canExecuteInWorld(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

                // Check if the villager has data stored
                NamespacedKey staticKey = new NamespacedKey(plugin, "static");

                if (dataContainer.has(staticKey, PersistentDataType.STRING)) {
                    wrapper.logDebug("Found villager with data in loaded chunk, attempting to retrieve data");
                    retrieveVillagerData(villager);
                }
            }
        }
    }

    /**
     * The onEntitiesLoad method is an event handler that fires when entities are loaded.
     * This handles cases where chunks remain loaded (e.g., after server sleep/wake cycles)
     * but individual villager entities need their data restored.
     * This is critical for PebbleHost-style sleep mode servers where ChunkLoadEvent
     * may not fire for already-loaded chunks.
     *
     * @param event The EntitiesLoadEvent object representing the event that occurred.
     */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        if (!wrapper.canExecuteInWorld(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

                if (dataContainer.has(STATIC_KEY, PersistentDataType.STRING)) {
                    wrapper.logDebug("Found villager entity loading with data, attempting to retrieve data (UUID: " + villager.getUniqueId() + ")");
                    retrieveVillagerData(villager);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onVillagerSpawn(CreatureSpawnEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        PersistentDataContainer container = villager.getPersistentDataContainer();
        if (!container.has(FORCE_SPAWN_KEY, PersistentDataType.BYTE)) {
            return;
        }

        event.setCancelled(false);
        container.remove(FORCE_SPAWN_KEY);
    }


    /**
     * The storeVillagerData method stores the data of a Villager entity in its persistent data container.
     * It stores the static status of the villager, its profession, and its trades in the data container.
     *
     * @param villager The Villager entity whose data is to be stored.
     */
    public void storeVillagerData(Villager villager) {
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            storeVillagerDataSync(villager);
        });
    }

    /**
     * Synchronously stores villager data to persistent data container.
     * Used during plugin disable to ensure data is saved before shutdown.
     *
     * @param villager The Villager entity whose data is to be stored.
     */
    public void storeVillagerDataSync(Villager villager) {
        UUID villagerId = villager.getUniqueId();
        int tradeCount = villager.getRecipes().size();

        wrapper.logDebug("Storing data for villager " + villagerId + " (trades: " + tradeCount + ")");

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

        boolean managed = isVillagerManaged(villager);
        staticMap.put(villagerId, managed);

        dataContainer.set(STATIC_KEY, PersistentDataType.STRING, Boolean.toString(managed));
        dataContainer.set(PROFESSION_KEY, PersistentDataType.STRING, villager.getProfession().name());
        dataContainer.set(TYPE_KEY, PersistentDataType.STRING, villager.getVillagerType().name());
        dataContainer.set(LEVEL_KEY, PersistentDataType.INTEGER, villager.getVillagerLevel());
        String tradesData = serializeMerchantRecipes(villager.getRecipes());
        dataContainer.set(TRADES_KEY, PersistentDataType.STRING, tradesData);
        // Persist per-villager trade permission only when explicitly set (non-empty and not "none")
        String perm = permissionMap.get(villagerId);
        if (perm == null) {
            perm = dataContainer.get(PERMISSION_KEY, PersistentDataType.STRING);
        }
        if (perm != null && !perm.isBlank() && !perm.equalsIgnoreCase("none")) {
            dataContainer.set(PERMISSION_KEY, PersistentDataType.STRING, perm);
            permissionMap.put(villagerId, perm);
        } else {
            dataContainer.remove(PERMISSION_KEY);
            permissionMap.remove(villagerId);
        }

        wrapper.logDebug("Successfully stored data for villager " + villagerId + " (profession: " + villager.getProfession().name() + ", trades: " + tradeCount + ")");
    }

    /**
     * The retrieveVillagerData method retrieves the data of a Villager entity from its persistent data container.
     * It retrieves the static status of the villager, its profession, and its trades from the data container.
     *
     * @param villager The Villager entity whose data is to be retrieved.
     */
    public void retrieveVillagerData(Villager villager) {
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
            String tradesData = dataContainer.get(TRADES_KEY, PersistentDataType.STRING);
            int tradeCount = tradesData != null ? deserializeMerchantRecipes(tradesData).size() : 0;

            wrapper.logDebug("Retrieving data for villager " + villager.getUniqueId() + " (trades: " + tradeCount + ")");

            String staticValue = dataContainer.get(STATIC_KEY, PersistentDataType.STRING);
            UUID villagerId = villager.getUniqueId();
            boolean managed = staticValue != null && Boolean.parseBoolean(staticValue);
            staticMap.put(villagerId, managed);
            villager.setCollidable(!managed);

            String professionName = dataContainer.get(PROFESSION_KEY, PersistentDataType.STRING);
            if (professionName != null) {
                allowCareerChange.add(villager.getUniqueId());
                try {
                    villager.setProfession(Villager.Profession.valueOf(professionName));
                } finally {
                    allowCareerChange.remove(villager.getUniqueId());
                }
            } else {
                // Set a default profession if professionName is null
                allowCareerChange.add(villager.getUniqueId());
                try {
                    villager.setProfession(Villager.Profession.NONE);
                } finally {
                    allowCareerChange.remove(villager.getUniqueId());
                }
            }

            // Restore villager type (biome appearance)
            String typeName = dataContainer.get(TYPE_KEY, PersistentDataType.STRING);
            if (typeName != null) {
                try {
                    villager.setVillagerType(Villager.Type.valueOf(typeName));
                } catch (IllegalArgumentException e) {
                    wrapper.logDebug("Invalid villager type: " + typeName);
                }
            }

            // Restore villager level
            Integer level = dataContainer.get(LEVEL_KEY, PersistentDataType.INTEGER);
            if (level != null) {
                villager.setVillagerLevel(level);
            }

            String permission = dataContainer.get(PERMISSION_KEY, PersistentDataType.STRING);
            if (permission == null || permission.isBlank()) {
                permissionMap.remove(villagerId);
            } else {
                permissionMap.put(villagerId, permission);
            }

            villager.setRecipes(deserializeMerchantRecipes(tradesData));

            wrapper.logDebug("Successfully retrieved data for villager " + villager.getUniqueId() + " (profession: " + professionName + ", trades: " + tradeCount + ")");
        });
    }

    /**
     * The serializeMerchantRecipes method serializes a list of MerchantRecipe objects into a Base64-encoded string.
     *
     * @param recipes The list of MerchantRecipe objects to be serialized.
     * @return A Base64-encoded string representing the serialized list of MerchantRecipe objects.
     */
    private String serializeMerchantRecipes(List<MerchantRecipe> recipes) {
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);

            // Write the size of the list
            dataOutput.writeInt(recipes.size());

            // Save every element in the list
            for (MerchantRecipe recipe : recipes) {
                SerializableMerchantRecipe serializableRecipe = new SerializableMerchantRecipe(recipe);
                dataOutput.writeObject(serializableRecipe);
            }

            // Serialize that array
            dataOutput.close();
            return Base64.getEncoder().encodeToString(outputStream.toByteArray());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to save trades.", e);
        }
    }

    /**
     * The deserializeMerchantRecipes method deserializes a Base64-encoded string into a list of MerchantRecipe objects.
     *
     * @param data The Base64-encoded string representing the serialized list of MerchantRecipe objects.
     * @return A list of MerchantRecipe objects deserialized from the Base64-encoded string.
     */
    private List<MerchantRecipe> deserializeMerchantRecipes(String data) {
        if (data == null) {
            // Return an empty list if data is null
            return new ArrayList<>();
        }

        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt(); // Read the size of the list

            List<MerchantRecipe> recipes = new ArrayList<>(size);

            // Read the list
            for (int i = 0; i < size; i++) {
                SerializableMerchantRecipe serializableRecipe = (SerializableMerchantRecipe) dataInput.readObject();
                MerchantRecipe recipe = serializableRecipe.toMerchantRecipe();
                neutralizeRecipeDiscounts(recipe);
                recipes.add(recipe);
            }

            dataInput.close();
            return recipes;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load trades.", e);
        }
    }

    public Inventory getInventoryFromVillager(Villager villager) {
        for (Map.Entry<Inventory, Villager> entry : inventoryMap.entrySet()) {
            if (entry.getValue().equals(villager)) {
                return entry.getKey();
            }
        }
        return null; // Return null if no inventory is associated with the villager
    }

    private void renderPage(Villager villager, Inventory inv, int page) {
        List<RecipeRow> rows = ensureBuffer(villager);
        for (int s = 0; s < 27; s++) inv.setItem(s, new ItemStack(Material.AIR));

        int start = page * 9;
        for (int col = 0; col < 9; col++) {
            int idx = start + col;
            if (idx >= rows.size()) break;
            RecipeRow row = rows.get(idx);
            inv.setItem(col, cloneOrAir(row.ingredient1));
            inv.setItem(col + 9, cloneOrAir(row.ingredient2));
            inv.setItem(col + 18, cloneOrAir(row.result));
        }

        setupControls(inv, villager);
        updatePageIndicator(inv, page, computeTotalPages(villager));
    }

    private void syncVisiblePageToBuffer(Villager villager, Inventory inv) {
        List<RecipeRow> rows = ensureBuffer(villager);
        int page = pageMap.getOrDefault(inv, 0);
        int start = page * 9;
        for (int col = 0; col < 9; col++) {
            int idx = start + col;
            ItemStack i1 = inv.getItem(col);
            ItemStack i2 = inv.getItem(col + 9);
            ItemStack res = inv.getItem(col + 18);
            RecipeRow row = new RecipeRow(i1, i2, res);
            if (idx >= rows.size()) {
                if (!row.isEmpty()) {
                    while (rows.size() <= idx)
                        rows.add(new RecipeRow(new ItemStack(Material.AIR), new ItemStack(Material.AIR), new ItemStack(Material.AIR)));
                    rows.set(idx, row);
                }
            } else {
                rows.set(idx, row);
            }
        }
    }

    private List<RecipeRow> ensureBuffer(Villager villager) {
        return editBuffer.computeIfAbsent(villager, v -> new ArrayList<>());
    }

    private void initBufferFromVillager(Villager villager) {
        // Must run on villager's region thread
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            List<RecipeRow> list = new ArrayList<>();
            for (MerchantRecipe mr : villager.getRecipes()) {
                ItemStack i1 = mr.getIngredients().size() > 0 ? mr.getIngredients().get(0) : null;
                ItemStack i2 = mr.getIngredients().size() > 1 ? mr.getIngredients().get(1) : null;
                ItemStack res = mr.getResult();
                list.add(new RecipeRow(i1, i2, res));
            }
            editBuffer.put(villager, list);
        });
    }

    private ItemStack cloneOrAir(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR) return new ItemStack(Material.AIR);
        return stack.clone();
    }

    private void setupControls(Inventory inv, Villager villager) {
        // New layout: [Prev][Page][Next] [Prof][Type][Level][Name][Perm] [Del]
        //              27    28    29     30    31    32     33    34     35
        for (int i = 27; i < inv.getSize(); i++) {
            inv.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
        }

        // Navigation (left side: 27-29)
        // Previous page button (slot 27)
        ItemStack prev = new ItemStack(Material.ARROW);
        ItemMeta pm = prev.getItemMeta();
        pm.displayName(Component.text("Prev", NamedTextColor.GOLD));
        pm.lore(List.of(Component.text("Previous page", NamedTextColor.GRAY)));
        prev.setItemMeta(pm);
        inv.setItem(27, prev);

        // Page indicator at 28 - set via updatePageIndicator()

        // Next page button (slot 29)
        ItemStack next = new ItemStack(Material.ARROW);
        ItemMeta nm = next.getItemMeta();
        nm.displayName(Component.text("Next", NamedTextColor.GOLD));
        nm.lore(List.of(Component.text("Next page", NamedTextColor.GRAY)));
        next.setItemMeta(nm);
        inv.setItem(29, next);

        // Villager properties (center: 30-34)
        // Profession at 30
        inv.setItem(30, new ItemStack(Material.LEATHER_CHESTPLATE));

        foliaLib.getScheduler().runAtEntity(villager, task -> {
            // Profession at 30
            Villager.Profession displayProfession = pendingProfessionMap.getOrDefault(villager, villager.getProfession());
            updateProfessionDisplayItem(inv, displayProfession);

            // Type at 31
            updateTypeDisplayItem(inv, villager.getVillagerType());

            // Level at 32
            updateLevelDisplayItem(inv, villager.getVillagerLevel());

            // Name at 33
            updateNameDisplayItem(inv, villager.customName());

            // Permission at 34
            String currentPerm = villager.getPersistentDataContainer().get(PERMISSION_KEY, PersistentDataType.STRING);
            updatePermissionDisplayItem(inv, currentPerm);
        });

        // Delete (right side: 35)
        ItemStack delete = new ItemStack(Material.BARRIER);
        ItemMeta dm = delete.getItemMeta();
        dm.displayName(Component.text("Delete Villager", NamedTextColor.RED));
        dm.lore(List.of(Component.text("Click to remove this villager", NamedTextColor.GRAY)));
        delete.setItemMeta(dm);
        inv.setItem(35, delete);
    }

    private void updatePageIndicator(Inventory inv, int page, int totalPages) {
        ItemStack indicator = new ItemStack(Material.PAPER);
        ItemMeta meta = indicator.getItemMeta();
        meta.displayName(styledLabel("Page", (page + 1) + "/" + totalPages));
        indicator.setItemMeta(meta);
        inv.setItem(28, indicator);
    }

    private int countNonEmptyRows(List<RecipeRow> rows) {
        int count = 0;
        for (RecipeRow r : rows) if (!r.isEmpty()) count++;
        return count;
    }

    private int computeTotalPages(Villager villager) {
        List<RecipeRow> rows = ensureBuffer(villager);
        int nonEmpty = countNonEmptyRows(rows);
        int base = (int) Math.ceil(nonEmpty / 9.0);
        return Math.max(1, base + 1); // allow one extra blank page for adding
    }

    private static class RecipeRow {
        final ItemStack ingredient1;
        final ItemStack ingredient2;
        final ItemStack result;

        RecipeRow(ItemStack i1, ItemStack i2, ItemStack res) {
            this.ingredient1 = i1 == null ? new ItemStack(Material.AIR) : i1.clone();
            this.ingredient2 = i2 == null ? new ItemStack(Material.AIR) : i2.clone();
            this.result = res == null ? new ItemStack(Material.AIR) : res.clone();
        }

        boolean isEmpty() {
            boolean ing1 = ingredient1 == null || ingredient1.getType() == Material.AIR;
            boolean ing2 = ingredient2 == null || ingredient2.getType() == Material.AIR;
            boolean res = result == null || result.getType() == Material.AIR;
            return res || (ing1 && ing2);
        }
    }

    /**
     * The onPlayerInteractEntity method is an event handler method that is called when a player interacts with an entity.
     * It checks if the entity is a Villager and if the player is sneaking. If both conditions are met, it opens an inventory
     * for the player to edit the trades of the Villager. The inventory displays the input items and output items of the trades.
     *
     * @param event The PlayerInteractEntityEvent object representing the event that occurred.
     */
    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        Player player = event.getPlayer();
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        if (!wrapper.canExecuteInWorld(villager.getWorld())) {
            return;
        }

        PersistentDataContainer pdc = villager.getPersistentDataContainer();

        // Shift-right-click = open editor for managed villagers
        if (player.isSneaking()) {
            if (pdc.has(STATIC_KEY, PersistentDataType.STRING)) {
                event.setCancelled(true);
                openEditor(villager, player);
            }
            return;
        }

        // Normal right-click on a managed villager: open our own Merchant per player
        // This enables multiple players to trade simultaneously without vanilla's single-user lock
        if (pdc.has(STATIC_KEY, PersistentDataType.STRING)) {
            String perm = pdc.get(PERMISSION_KEY, PersistentDataType.STRING);
            if (perm != null && !perm.isBlank() && !perm.equalsIgnoreCase("none") && !player.hasPermission(perm)) {
                event.setCancelled(true);
                wrapper.sendMessage(player, "noTradePermission");
                return;
            }

            event.setCancelled(true);
            openPerPlayerMerchant(villager, player);
            return;
        }
    }

    private void openPerPlayerMerchant(Villager villager, Player player) {
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            // Copy and neutralize recipes from the villager
            List<MerchantRecipe> recipes = new ArrayList<>();
            for (MerchantRecipe r : villager.getRecipes()) {
                MerchantRecipe copy = new MerchantRecipe(cloneOrAir(r.getResult()), r.getMaxUses());
                for (ItemStack ing : r.getIngredients()) {
                    if (ing != null && ing.getType() != Material.AIR) {
                        copy.addIngredient(cloneOrAir(ing));
                    }
                }
                copy.setExperienceReward(false);
                neutralizeRecipeDiscounts(copy);
                recipes.add(copy);
            }

            Merchant merchant = Bukkit.createMerchant(villager.customName() != null ? PlainTextComponentSerializer.plainText().serialize(villager.customName()) : "Villager");
            merchant.setRecipes(recipes);

            // Open for player on the player's context
            foliaLib.getScheduler().runAtEntity(player, ptask -> {
                player.openMerchant(merchant, true);
            });
        });
    }

    public void openEditor(Villager villager, Player player) {
        if (!wrapper.canExecuteInWorld(villager.getWorld())) {
            wrapper.sendMessage(player, "disabledWorld");
            return;
        }
        // Only allow editing for plugin-managed villagers
        if (!villager.getPersistentDataContainer().has(STATIC_KEY, PersistentDataType.STRING)) {
            wrapper.sendMessage(player, "notManagedVillager");
            return;
        }
        if (!player.hasPermission("villagertradeedit.open")) {
            wrapper.sendMessage(player, "noPermission");
            return;
        }
        Inventory inv = getInventoryFromVillager(villager);
        if (inv == null) {
            inv = Bukkit.createInventory(null, 9 * 4, Component.text("Villager Trade Edit"));
            pageMap.put(inv, 0);
            // Initialize buffer with villager's current trades if not already done
            if (!editBuffer.containsKey(villager)) {
                final Inventory finalInv = inv;
                foliaLib.getScheduler().runAtEntity(villager, task -> {
                    List<RecipeRow> list = new ArrayList<>();
                    for (MerchantRecipe mr : villager.getRecipes()) {
                        ItemStack i1 = mr.getIngredients().size() > 0 ? mr.getIngredients().get(0) : null;
                        ItemStack i2 = mr.getIngredients().size() > 1 ? mr.getIngredients().get(1) : null;
                        ItemStack res = mr.getResult();
                        list.add(new RecipeRow(i1, i2, res));
                    }
                    editBuffer.put(villager, list);

                    // Now render the page with the loaded trades
                    foliaLib.getScheduler().runNextTick((task2) -> {
                        renderPage(villager, finalInv, pageMap.getOrDefault(finalInv, 0));
                        inventoryMap.put(finalInv, villager);
                        player.openInventory(finalInv);
                    });
                });
                return;
            }
        }
        renderPage(villager, inv, pageMap.getOrDefault(inv, 0));
        inventoryMap.put(inv, villager);
        player.openInventory(inv);
    }

    // Attempt to disable all dynamic discounts on a recipe
    private void neutralizeRecipeDiscounts(MerchantRecipe recipe) {
        try {
            recipe.setSpecialPrice(0);
        } catch (Throwable ignored) {
        }
        try {
            recipe.setDemand(0);
        } catch (Throwable ignored) {
        }
        try {
            recipe.setPriceMultiplier(0.0f);
        } catch (Throwable ignored) {
        }
        try {
            Method m = MerchantRecipe.class.getMethod("setIgnoreDiscounts", boolean.class);
            m.invoke(recipe, true);
        } catch (Throwable ignored) {
        }
    }

    // When a merchant UI opens, ensure discounts are neutralized only for managed villagers
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getInventory() instanceof MerchantInventory inv)) {
            return;
        }
        Merchant merchant = inv.getMerchant();
        if (merchant == null) {
            return;
        }
        // Only apply global neutralization if the merchant is a villager managed by this plugin
        if (merchant instanceof Villager villager) {
            PersistentDataContainer pdc = villager.getPersistentDataContainer();
            if (!pdc.has(STATIC_KEY, PersistentDataType.STRING)) {
                return; // normal villagers keep their vanilla discounts
            }

            List<MerchantRecipe> recipes = new ArrayList<>(merchant.getRecipes());
            for (MerchantRecipe r : recipes) {
                neutralizeRecipeDiscounts(r);
            }
            merchant.setRecipes(recipes);

            // Temporarily remove Hero of the Village only for managed villagers to prevent recalculation
            if (event.getPlayer() instanceof Player player) {
                PotionEffect effect = player.getPotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
                if (effect != null) {
                    removedHotv.put(player.getUniqueId(), effect);
                    player.removePotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE);
                }
            }
        }
    }

    private void updatePermissionDisplayItem(Inventory inv, String permission) {
        if (inv == null || inv.getSize() <= 29) {
            wrapper.logWarn("Inventory size is invalid or does not contain slot 29.");
            return;
        }

        ItemStack setPermissionItem = inv.getItem(29);
        if (setPermissionItem == null || setPermissionItem.getType() == Material.BLACK_STAINED_GLASS_PANE) {
            setPermissionItem = new ItemStack(Material.WRITABLE_BOOK);
        }
        ItemMeta setPermissionMeta = setPermissionItem.getItemMeta();
        String value = (permission == null || permission.isBlank() || permission.equalsIgnoreCase("none")) ? "(none)" : permission;
        setPermissionMeta.displayName(styledLabel("Permission", value));
        setPermissionMeta.lore(List.of(Component.text("Click to set required permission", NamedTextColor.GRAY)));
        setPermissionItem.setItemMeta(setPermissionMeta);
        inv.setItem(34, setPermissionItem);
    }


    /**
     * The onInventoryClick method is an event handler method that is called when a player clicks an item in an inventory.
     * It checks if the clicked inventory is associated with a Villager and handles the actions based on the clicked item.
     * If the player clicks on the static mode toggle item, it toggles the static mode of the Villager.
     * If the player clicks on the profession change item, it changes the profession of the Villager.
     *
     * @param event The InventoryClickEvent object representing the event that occurred.
     */
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getView().getTopInventory();
        if (!inventoryMap.containsKey(inv)) {
            return;
        }

        Villager villager = inventoryMap.get(inv);
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        int raw = event.getRawSlot();
        int topSize = inv.getSize();

        // Editing clicks in top trade area (0..26)
        if (raw < topSize && raw < 27) {
            tradeAlteredMap.put(inv, true);
            int currentPage = pageMap.getOrDefault(inv, 0);
            foliaLib.getScheduler().runNextTick((task) -> {
                syncVisiblePageToBuffer(villager, inv);
                updatePageIndicator(inv, currentPage, computeTotalPages(villager));
            });
            return; // allow vanilla placement behavior; no cancel
        }

        // Top control bar (27..35)
        if (raw < topSize && raw >= 27) {
            event.setCancelled(true);
        }

        // New layout: [Prev][Page][Next] [Prof][Type][Level][Name][Perm] [Del]
        //              27    28    29     30    31    32     33    34     35

        // Navigation
        if (raw == 27 && clickedItem != null) {
            // Prev page
            syncVisiblePageToBuffer(villager, inv);
            int page = Math.max(0, pageMap.getOrDefault(inv, 0) - 1);
            pageMap.put(inv, page);
            renderPage(villager, inv, page);
            event.setCancelled(true);
        }
        // Slot 28 is page indicator (no click action)
        if (raw == 29 && clickedItem != null) {
            // Next page
            syncVisiblePageToBuffer(villager, inv);
            int total = computeTotalPages(villager);
            int page = Math.min(total - 1, pageMap.getOrDefault(inv, 0) + 1);
            pageMap.put(inv, page);
            renderPage(villager, inv, page);
            event.setCancelled(true);
        }

        // Villager properties
        if (raw == 30 && clickedItem != null) {
            handleProfessionChange(villager, player, inv);
            event.setCancelled(true);
        }
        if (raw == 31 && clickedItem != null) {
            handleTypeChange(villager, player, inv);
            event.setCancelled(true);
        }
        if (raw == 32 && clickedItem != null) {
            handleLevelChange(villager, player, inv);
            event.setCancelled(true);
        }
        if (raw == 33 && clickedItem != null) {
            handleSetName(villager, player, inv);
            event.setCancelled(true);
        }
        if (raw == 34 && clickedItem != null) {
            handleSetPermission(villager, player, inv);
            event.setCancelled(true);
        }

        // Delete
        if (raw == 35 && clickedItem != null) {
            handleDeleteVillager(villager, player, inv);
            event.setCancelled(true);
        }
        // Removed Add Trade button; placing items on a new page auto-extends the buffer
        // Save button removed; saving occurs automatically on inventory close
        // If click originated in player inventory while our GUI is open, schedule a sync
        if (raw >= topSize) {
            int currentPage = pageMap.getOrDefault(inv, 0);
            foliaLib.getScheduler().runNextTick((task) -> {
                syncVisiblePageToBuffer(villager, inv);
                updatePageIndicator(inv, currentPage, computeTotalPages(villager));
            });
        }
    }

    // Save button removed; saving occurs automatically on inventory close

    private void handleSetPermission(Villager villager, Player player, Inventory inv) {

        player.closeInventory();
        // Prompt the player to enter the new permission
        wrapper.sendMessage(player, "enterPermissionPrompt");
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerChat(AsyncPlayerChatEvent event) {
                if (event.getPlayer().equals(player)) {
                    // Capture and apply new permission
                    String permission = event.getMessage().trim();
                    event.setCancelled(true);

                    // Apply changes on the villager's region thread for Folia safety
                    foliaLib.getScheduler().runAtEntity(villager, task -> {
                        UUID villagerId = villager.getUniqueId();
                        if (permission.equalsIgnoreCase("none") || permission.isBlank()) {
                            permissionMap.remove(villagerId);
                            villager.getPersistentDataContainer().remove(PERMISSION_KEY);
                            wrapper.sendMessage(player, "permissionCleared");
                        } else {
                            permissionMap.put(villagerId, permission);
                            villager.getPersistentDataContainer().set(PERMISSION_KEY, PersistentDataType.STRING, permission);
                            wrapper.sendMessage(player, "permissionSet", permission);
                        }

                        // Reopen GUI and refresh label on next tick
                        foliaLib.getScheduler().runNextTick((task2) -> {
                            inventoryMap.put(inv, villager);
                            int currentPage = pageMap.getOrDefault(inv, 0);
                            renderPage(villager, inv, currentPage);
                            updatePermissionDisplayItem(inv, villager.getPersistentDataContainer().get(PERMISSION_KEY, PersistentDataType.STRING));
                            player.openInventory(inv);
                        });
                    });

                    // Unregister this temporary chat listener
                    HandlerList.unregisterAll(this);
                }
            }
        }, plugin);
    }

    private void handleSetName(Villager villager, Player player, Inventory inv) {
        player.closeInventory();
        wrapper.sendMessage(player, "enterNamePrompt");
        Bukkit.getPluginManager().registerEvents(new Listener() {
            @EventHandler
            public void onPlayerChat(AsyncPlayerChatEvent event) {
                if (event.getPlayer().equals(player)) {
                    event.setCancelled(true);
                    String input = event.getMessage();
                    if (input.equalsIgnoreCase("cancel")) {
                        wrapper.sendMessage(player, "nameCancelled");
                    } else if (input.equalsIgnoreCase("none")) {
                        foliaLib.getScheduler().runAtEntity(villager, task -> {
                            villager.customName(null);
                            villager.setCustomNameVisible(false);
                            wrapper.sendMessage(player, "nameCleared");
                            updateNameDisplayItem(inv, null);
                        });
                    } else {
                        Component comp = parseNameComponent(input);
                        foliaLib.getScheduler().runAtEntity(villager, task -> {
                            villager.customName(comp);
                            villager.setCustomNameVisible(true);
                            wrapper.sendMessage(player, "nameUpdated");
                            updateNameDisplayItem(inv, villager.customName());
                        });
                    }
                    HandlerList.unregisterAll(this);
                    foliaLib.getScheduler().runNextTick((task) -> {
                        inventoryMap.put(inv, villager);
                        int currentPage = pageMap.getOrDefault(inv, 0);
                        renderPage(villager, inv, currentPage);
                        player.openInventory(inv);
                    });
                }
            }
        }, plugin);
    }

    private void handleDeleteVillager(Villager villager, Player player, Inventory inv) {
        // Proactively drop GUI state so InventoryClose doesn't try to save
        inventoryMap.remove(inv);
        tradeAlteredMap.remove(inv);
        pageMap.remove(inv);
        editBuffer.remove(villager);
        pendingProfessionMap.remove(villager);
        permissionMap.remove(villager.getUniqueId());
        staticMap.remove(villager.getUniqueId());

        // Close GUI to avoid further interactions
        player.closeInventory();

        // Schedule removal on the villager's region thread
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            try {
                villager.remove();
            } catch (Throwable ignored) { }
            wrapper.sendMessage(player, "villagerDeleted");
        });
    }

    /**
     * The handleStaticModeToggle method toggles the static mode of a Villager entity.
     * If the Villager is in static mode, it deactivates the static mode and vice versa.
     * It updates the display item in the inventory to reflect the new static mode status.
     *
     * @param villager The Villager entity whose static mode is to be toggled.
     * @param player   The Player who toggled the static mode.
     * @param inv      The Inventory associated with the Villager entity.
     */
    // Static controls removed from GUI; villagers are always static when managed

    /**
     * The handleProfessionChange method changes the profession of a Villager entity to the next profession in the list.
     * It updates the display item in the inventory to reflect the new profession.
     *
     * @param villager The Villager entity whose profession is to be changed.
     * @param player   The Player who changed the profession.
     * @param inv      The Inventory associated with the Villager entity.
     */
    private void handleProfessionChange(Villager villager, Player player, Inventory inv) {
        // Don't change profession immediately - just cycle through the pending profession
        Villager.Profession currentProfession = pendingProfessionMap.getOrDefault(villager, villager.getProfession());
        Villager.Profession nextProfession = getNextProfession(currentProfession);

        // Store the pending profession change for when we save
        pendingProfessionMap.put(villager, nextProfession);

        // Apply immediately to the entity so players see the change live
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            allowCareerChange.add(villager.getUniqueId());
            try {
                villager.setProfession(nextProfession);
                // reflect immediately in PDC so chunk unload/load keeps intended state
                villager.getPersistentDataContainer().set(PROFESSION_KEY, PersistentDataType.STRING, nextProfession.name());
            } finally {
                allowCareerChange.remove(villager.getUniqueId());
            }
        });

        // Update the display immediately
        updateProfessionDisplayItem(inv, nextProfession);
        tradeAlteredMap.put(inv, true);
    }

    /**
     * Activates static mode for a Villager entity.
     *
     * @param villager The Villager entity to activate static mode for.
     * @param player   The Player who triggered the activation.
     */
    void activateStaticMode(Villager villager, Player player) {
        wrapper.logDebugPlayer(player, "Static Mode Activated");
        staticMap.put(villager.getUniqueId(), true);
        villager.setInvulnerable(true);
        villager.setAware(false);
        villager.setVelocity(new Vector(0.0, 0.0, 0.0));
        Location currentLocation = villager.getLocation();
        Location centeredLocation = new Location(
                currentLocation.getWorld(),
                Math.floor(currentLocation.getX()) + 0.5,
                currentLocation.getY(),
                Math.floor(currentLocation.getZ()) + 0.5
        );
        villager.teleportAsync(centeredLocation);
        if (villager.getProfession() == Villager.Profession.NONE || villager.getProfession() == Villager.Profession.NITWIT) {
            villager.setProfession(Villager.Profession.ARMORER);
        }
        // Mark as plugin-managed in PDC and enforce non-collidable
        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();
        dataContainer.set(STATIC_KEY, PersistentDataType.STRING, "true");
        villager.setCollidable(false);
        villager.setRecipes(new ArrayList<>());
    }

    /**
     * Deactivates the static mode of a Villager entity.
     *
     * @param villager The Villager entity whose static mode is to be deactivated.
     * @param player   The Player who is deactivating the static mode.
     */
    void deactivateStaticMode(Villager villager, Player player) {
    }

    /**
     * Retrieves the next profession in the list of professions for a Villager.
     *
     * @param currentProfession - The current profession of the Villager.
     * @return The next profession in the list.
     */
    private Villager.Profession getNextProfession(Villager.Profession currentProfession) {
        // Cycle only through valid working professions in a stable order
        Villager.Profession[] allowed = new Villager.Profession[]{
                Villager.Profession.ARMORER,
                Villager.Profession.BUTCHER,
                Villager.Profession.CARTOGRAPHER,
                Villager.Profession.CLERIC,
                Villager.Profession.FARMER,
                Villager.Profession.FISHERMAN,
                Villager.Profession.FLETCHER,
                Villager.Profession.LEATHERWORKER,
                Villager.Profession.LIBRARIAN,
                Villager.Profession.MASON,
                Villager.Profession.SHEPHERD,
                Villager.Profession.TOOLSMITH,
                Villager.Profession.WEAPONSMITH
        };

        int idx = 0;
        for (int i = 0; i < allowed.length; i++) {
            if (allowed[i] == currentProfession) {
                idx = i;
                break;
            }
        }
        return allowed[(idx + 1) % allowed.length];
    }

    /**
     * Gets the next villager type (biome appearance) in a cyclic order.
     *
     * @param currentType The current type of the villager.
     * @return The next type in the cycle.
     */
    private Villager.Type getNextType(Villager.Type currentType) {
        Villager.Type[] types = new Villager.Type[]{
                Villager.Type.DESERT,
                Villager.Type.JUNGLE,
                Villager.Type.PLAINS,
                Villager.Type.SAVANNA,
                Villager.Type.SNOW,
                Villager.Type.SWAMP,
                Villager.Type.TAIGA
        };

        int idx = 0;
        for (int i = 0; i < types.length; i++) {
            if (types[i] == currentType) {
                idx = i;
                break;
            }
        }
        return types[(idx + 1) % types.length];
    }

    /**
     * Gets the next villager level in a cyclic order (1-5).
     *
     * @param currentLevel The current level of the villager.
     * @return The next level (wraps from 5 back to 1).
     */
    private int getNextLevel(int currentLevel) {
        return (currentLevel % 5) + 1;
    }

    /**
     * Gets the display name for a villager level.
     *
     * @param level The villager level (1-5).
     * @return The display name for the level.
     */
    private String getLevelName(int level) {
        return switch (level) {
            case 1 -> "Novice";
            case 2 -> "Apprentice";
            case 3 -> "Journeyman";
            case 4 -> "Expert";
            case 5 -> "Master";
            default -> "Unknown";
        };
    }

    /**
     * Creates a styled component with gold label and white value.
     *
     * @param label The label text.
     * @param value The value text.
     * @return A styled Component.
     */
    private Component styledLabel(String label, String value) {
        return Component.text(label + ": ", NamedTextColor.GOLD)
                .append(Component.text(value, NamedTextColor.WHITE));
    }

    /**
     * Formats an enum name to title case (e.g., PLAINS -> Plains).
     *
     * @param name The enum name in uppercase.
     * @return The formatted name in title case.
     */
    private String toTitleCase(String name) {
        if (name == null || name.isEmpty()) return name;
        return name.charAt(0) + name.substring(1).toLowerCase();
    }

    /**
     * Updates the display item in the inventory to reflect the new profession.
     *
     * @param inv            The Inventory object associated with the Villager entity.
     * @param nextProfession The Profession object representing the next profession for the Villager entity.
     */
    private void updateProfessionDisplayItem(Inventory inv, Villager.Profession nextProfession) {
        ItemStack changeProfessionItem = inv.getItem(27);
        if (changeProfessionItem == null) {
            changeProfessionItem = new ItemStack(Material.LEATHER_CHESTPLATE);
        }
        ItemMeta meta = changeProfessionItem.getItemMeta();
        meta.displayName(styledLabel("Profession", toTitleCase(nextProfession.name())));
        meta.lore(List.of(Component.text("Click to change profession", NamedTextColor.GRAY)));
        changeProfessionItem.setItemMeta(meta);
        inv.setItem(30, changeProfessionItem);
    }

    /**
     * Updates the type display item in the inventory (slot 30).
     *
     * @param inv  The Inventory object.
     * @param type The villager type to display.
     */
    private void updateTypeDisplayItem(Inventory inv, Villager.Type type) {
        ItemStack typeItem = new ItemStack(Material.GRASS_BLOCK);
        ItemMeta meta = typeItem.getItemMeta();
        meta.displayName(styledLabel("Type", toTitleCase(type.name())));
        meta.lore(List.of(Component.text("Click to change biome appearance", NamedTextColor.GRAY)));
        typeItem.setItemMeta(meta);
        inv.setItem(31, typeItem);
    }

    /**
     * Updates the level display item in the inventory (slot 34).
     *
     * @param inv   The Inventory object.
     * @param level The villager level to display.
     */
    private void updateLevelDisplayItem(Inventory inv, int level) {
        ItemStack levelItem = new ItemStack(Material.EXPERIENCE_BOTTLE);
        ItemMeta meta = levelItem.getItemMeta();
        meta.displayName(styledLabel("Level", getLevelName(level)));
        meta.lore(List.of(Component.text("Click to change trading level", NamedTextColor.GRAY)));
        levelItem.setItemMeta(meta);
        inv.setItem(32, levelItem);
    }

    /**
     * Handles type change when the type button is clicked.
     *
     * @param villager The villager entity.
     * @param player   The player who clicked.
     * @param inv      The inventory.
     */
    private void handleTypeChange(Villager villager, Player player, Inventory inv) {
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            Villager.Type currentType = villager.getVillagerType();
            Villager.Type nextType = getNextType(currentType);
            villager.setVillagerType(nextType);
            villager.getPersistentDataContainer().set(TYPE_KEY, PersistentDataType.STRING, nextType.name());

            foliaLib.getScheduler().runNextTick(t -> {
                updateTypeDisplayItem(inv, nextType);
            });
        });
        tradeAlteredMap.put(inv, true);
    }

    /**
     * Handles level change when the level button is clicked.
     *
     * @param villager The villager entity.
     * @param player   The player who clicked.
     * @param inv      The inventory.
     */
    private void handleLevelChange(Villager villager, Player player, Inventory inv) {
        foliaLib.getScheduler().runAtEntity(villager, task -> {
            int currentLevel = villager.getVillagerLevel();
            int nextLevel = getNextLevel(currentLevel);
            villager.setVillagerLevel(nextLevel);
            villager.getPersistentDataContainer().set(LEVEL_KEY, PersistentDataType.INTEGER, nextLevel);

            foliaLib.getScheduler().runNextTick(t -> {
                updateLevelDisplayItem(inv, nextLevel);
            });
        });
        tradeAlteredMap.put(inv, true);
    }

    private void updateNameDisplayItem(Inventory inv, Component nameComp) {
        ItemStack setNameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta setNameMeta = setNameItem.getItemMeta();
        Component label = (nameComp == null)
                ? styledLabel("Name", "(none)")
                : Component.text("Name: ", NamedTextColor.GOLD).append(nameComp);
        setNameMeta.displayName(label);
        setNameMeta.lore(List.of(Component.text("Click to set custom name", NamedTextColor.GRAY)));
        setNameItem.setItemMeta(setNameMeta);
        inv.setItem(33, setNameItem);
    }

    private String getPlainName(Villager villager) {
        Component comp = villager.customName();
        if (comp == null) return null;
        return PlainTextComponentSerializer.plainText().serialize(comp);
    }

    private Component parseNameComponent(String input) {
        String msg = input.trim();
        if (msg.toLowerCase().startsWith("mini:")) {
            return MiniMessage.miniMessage().deserialize(msg.substring(5));
        }
        if (msg.toLowerCase().startsWith("mm:")) {
            return MiniMessage.miniMessage().deserialize(msg.substring(3));
        }
        // Heuristic: contains MiniMessage-like tags
        if (msg.contains("<") && msg.contains(">")) {
            try {
                return MiniMessage.miniMessage().deserialize(msg);
            } catch (Throwable ignored) {
            }
        }
        // Legacy color codes
        if (msg.indexOf('&') >= 0 || msg.indexOf('§') >= 0) {
            String normalized = msg.replace('§', '&');
            return LegacyComponentSerializer.legacyAmpersand().deserialize(normalized);
        }
        return Component.text(msg);
    }

    private boolean isVillagerManaged(Villager villager) {
        UUID id = villager.getUniqueId();
        Boolean cached = staticMap.get(id);
        if (cached != null) {
            return cached;
        }
        PersistentDataContainer container = villager.getPersistentDataContainer();
        String stored = container.get(STATIC_KEY, PersistentDataType.STRING);
        if (stored != null) {
            boolean managed = Boolean.parseBoolean(stored);
            staticMap.put(id, managed);
            return managed;
        }
        return false;
    }

    /**
     * The onEntityPathFind method is an event handler method that is called when an entity tries to find a path to a target location.
     * It checks if the entity is a Villager, If it is not, the method returns.
     * If the entity is a Villager and its static mode is set to true, the method cancels the pathfinding event.
     *
     * @param event The EntityPathfindEvent object representing the event that occurred.
     */
    @EventHandler
    public void onEntityPathFind(EntityPathfindEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (isVillagerManaged(villager)) {
            event.setCancelled(true);
        }
    }

    /**
     * The onEntityDamage method is an event handler method that is called when an entity is damaged.
     * It checks if the damaged entity is an instance of Villager.
     * If it is, it checks if the villager is in the static mode, and cancels the damage event if it is.
     *
     * @param event The EntityDamageEvent object representing the event that occurred.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (isVillagerManaged(villager)) {
            event.setCancelled(true);
        }
    }

    /**
     * The onVillagerCareerChange method is an event handler method that is called when a Villager's career changes.
     * It cancels the career change event if the Villager is in the static mode.
     *
     * @param event The VillagerCareerChangeEvent object representing the event that occurred.
     */
    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {

        Villager villager = event.getEntity();

        // If this change is initiated by our save flow, allow it
        if (allowCareerChange.contains(villager.getUniqueId())) {
            return;
        }

        if (isVillagerManaged(villager)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Villager) || !(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.NAME_TAG) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inventoryMap.containsKey(inv)) {
            Villager villager = inventoryMap.get(inv);
            Player player = (Player) event.getPlayer();

            // Build new recipes from the visible buffer and save
            syncVisiblePageToBuffer(villager, inv);
            List<MerchantRecipe> newRecipes = new ArrayList<>();
            for (RecipeRow row : ensureBuffer(villager)) {
                if (row.isEmpty()) continue;
                MerchantRecipe newRecipe = new MerchantRecipe(cloneOrAir(row.result), 9999);
                if (row.ingredient1 != null && row.ingredient1.getType() != Material.AIR) {
                    newRecipe.addIngredient(cloneOrAir(row.ingredient1));
                }
                if (row.ingredient2 != null && row.ingredient2.getType() != Material.AIR) {
                    newRecipe.addIngredient(cloneOrAir(row.ingredient2));
                }
                neutralizeRecipeDiscounts(newRecipe);
                newRecipes.add(newRecipe);
            }

            // Only save for managed/static villagers (editor only opens for these)
            if (isVillagerManaged(villager)) {
                foliaLib.getScheduler().runAtEntity(villager, task -> {
                    // Apply pending profession change before saving
                    if (pendingProfessionMap.containsKey(villager)) {
                        // Temporarily allow the career change event from this setProfession call
                        allowCareerChange.add(villager.getUniqueId());
                        try {
                            villager.setProfession(pendingProfessionMap.get(villager));
                        } finally {
                            allowCareerChange.remove(villager.getUniqueId());
                        }
                        pendingProfessionMap.remove(villager);
                    }

                    villager.setRecipes(newRecipes);
                    storeVillagerData(villager);
                    // Silent save; no chat spam
                });
            }

            // Cleanup state for this inventory
            tradeAlteredMap.remove(inv);
            pageMap.remove(inv);
            inventoryMap.remove(inv);
            editBuffer.remove(villager);
            pendingProfessionMap.remove(villager);
        }

        // Restore Hero of the Village if we removed it for a merchant trade
        if (event.getPlayer() instanceof Player player && event.getInventory() instanceof MerchantInventory) {
            UUID id = player.getUniqueId();
            PotionEffect prev = removedHotv.remove(id);
            if (prev != null) {
                player.addPotionEffect(prev);
            }
        }
    }

    private static final double TURN_RADIUS = 5.0;

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location playerLocation = player.getLocation();

        for (Entity entity : player.getNearbyEntities(TURN_RADIUS, TURN_RADIUS, TURN_RADIUS)) {
            if (entity instanceof Villager villager) {
                if (isVillagerManaged(villager)) {
                    turnVillagerTowardsPlayer(villager, playerLocation);
                }
            }
        }
    }

    private void turnVillagerTowardsPlayer(Villager villager, Location playerLocation) {
        Location villagerLocation = villager.getLocation();
        Vector direction = playerLocation.toVector().subtract(villagerLocation.toVector());
        villagerLocation.setDirection(direction);
        villager.teleportAsync(villagerLocation);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        PotionEffect prev = removedHotv.remove(id);
        if (prev != null) {
            // Best-effort restore on next login not feasible; just drop to avoid giving free effect time.
        }
    }


}
