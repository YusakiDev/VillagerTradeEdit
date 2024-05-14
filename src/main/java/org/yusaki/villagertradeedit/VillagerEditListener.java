package org.yusaki.villagertradeedit;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import com.tcoded.folialib.FoliaLib;
import io.papermc.paper.threadedregions.scheduler.AsyncScheduler;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.bukkit.util.Vector;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;

/**
 * A class representing a listener for villager trading events in the VillagerTradeEdit plugin.
 * Inherits from the Object and Listener classes.
 */
public class VillagerEditListener implements Listener {

    static VillagerTradeEdit plugin;
    FoliaLib foliaLib;
    private final Map<Inventory, Villager> inventoryMap = new HashMap<>();
    private final Map<Villager, Boolean> staticMap = new HashMap<>();
    private final Set<UUID> retrievedVillagers = new HashSet<>();

    private final NamespacedKey STATIC_KEY;
    private final NamespacedKey PROFESSION_KEY;
    private final NamespacedKey TRADES_KEY;


    public VillagerEditListener(VillagerTradeEdit plugin) {
        VillagerEditListener.plugin = plugin;
        foliaLib = new FoliaLib(plugin);
        STATIC_KEY = new NamespacedKey(plugin, "static");
        PROFESSION_KEY = new NamespacedKey(plugin, "profession");
        TRADES_KEY = new NamespacedKey(plugin, "trades");
    }




    /**
     * The onChunkLoad method is an event handler method that is called when a chunk is loaded in the world.
     * It iterates through all entities in the loaded chunk and checks if they are instances of Villager.
     * If a Villager entity has persistent data stored with a specific key ,and it has not been retrieved
     * before, it retrieves the stored data and adds the villager's UUID to the retrievedVillagers set.
     *
     * @param event The ChunkLoadEvent object representing the event that occurred.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!plugin.canExecuteInWorld(event.getWorld())) {
            return;
        }
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

                // Check if the villager has data stored
                NamespacedKey staticKey = new NamespacedKey(plugin, "static");

                if (dataContainer.has(staticKey, PersistentDataType.STRING) && !retrievedVillagers.contains(villager.getUniqueId())) {
                    plugin.logDebug("Found villager with data in loaded chunk, attempting to retrieve data");
                    retrieveVillagerData(villager);
                    retrievedVillagers.add(villager.getUniqueId());
                }
            }
        }
    }

    /**
     * The storeVillagerData method stores the data of a Villager entity in its persistent data container.
     * It stores the static status of the villager, its profession, and its trades in the data container.
     *
     * @param villager The Villager entity whose data is to be stored.
     */
    public void storeVillagerData(Villager villager) {
        plugin.logDebug("Storing data for villager " + villager.getUniqueId());

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

        dataContainer.set(STATIC_KEY, PersistentDataType.STRING, staticMap.get(villager).toString());
        dataContainer.set(PROFESSION_KEY, PersistentDataType.STRING, villager.getProfession().name());
        String tradesData = serializeMerchantRecipes(villager.getRecipes());
        dataContainer.set(TRADES_KEY, PersistentDataType.STRING, tradesData);

        plugin.logDebug("Stored data for villager " + villager.getUniqueId());
    }

    /**
     * The retrieveVillagerData method retrieves the data of a Villager entity from its persistent data container.
     * It retrieves the static status of the villager, its profession, and its trades from the data container.
     *
     * @param villager The Villager entity whose data is to be retrieved.
     */
    public void retrieveVillagerData(Villager villager) {
        plugin.logDebug("Retrieving data for villager " + villager.getUniqueId());

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

        String staticValue = dataContainer.get(STATIC_KEY, PersistentDataType.STRING);
        staticMap.put(villager, Boolean.valueOf(staticValue));
        villager.setCollidable(!Boolean.parseBoolean(staticValue));

        String professionName = dataContainer.get(PROFESSION_KEY, PersistentDataType.STRING);
        if (professionName != null) {
            villager.setProfession(Villager.Profession.valueOf(professionName));
        } else {
            // Set a default profession if professionName is null
            villager.setProfession(Villager.Profession.NONE);
        }

        String tradesData = dataContainer.get(TRADES_KEY, PersistentDataType.STRING);
        villager.setRecipes(deserializeMerchantRecipes(tradesData));

        plugin.logDebug("Retrieved data for villager " + villager.getUniqueId());
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
                recipes.add(recipe);
            }

            dataInput.close();
            return recipes;
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException("Unable to load trades.", e);
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
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        if (!plugin.canExecuteInWorld(villager.getWorld())) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!player.hasPermission("villagertradeedit.open")) {
            plugin.logDebugPlayer(player, "&cNo permission to open villager trade edit");
            return;
        }

        event.setCancelled(true);
        // Editing Mode: Make villager static at that moment


        Inventory inv = Bukkit.createInventory(null,9*4, Component.text("Villager Trade Edit"));

        // Get the villager's trades
        List<MerchantRecipe> recipes = villager.getRecipes();

        // For each trade, add the input items and output item to the inventory
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);

            // Get the input items and output item
            List<ItemStack> ingredients = recipe.getIngredients();
            ItemStack result = recipe.getResult();

            // Add the items to the inventory
            inv.setItem(i, ingredients.get(0));
            if (ingredients.size() > 1) {
                inv.setItem(i + 9, ingredients.get(1));
            } else {
                // If there is only one input item, add an empty slot
                inv.setItem(i + 9, new ItemStack(Material.AIR));
            }
            inv.setItem(i + 18, result);
        }

        // Fill the remaining slots with glass
        for (int i = 27; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, new ItemStack(Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        ItemStack toggleAIItem = new ItemStack(Material.REDSTONE_TORCH);
        ItemMeta meta = toggleAIItem.getItemMeta();
        meta.displayName(Component.text("Static Mode: False"));

        if (staticMap.get(villager) != null && staticMap.get(villager)){
            toggleAIItem = new ItemStack(Material.SOUL_TORCH);
            meta.displayName(Component.text("Static Mode: True"));
        }

        toggleAIItem.setItemMeta(meta);
        inv.setItem(27, toggleAIItem);

        ItemStack changeProfessionItem = new ItemStack(Material.LEATHER_CHESTPLATE);
        meta = changeProfessionItem.getItemMeta();
        meta.displayName(Component.text(("(" + villager.getProfession().name() + ")")));
        changeProfessionItem.setItemMeta(meta);
        inv.setItem(28, changeProfessionItem);

        ItemStack setNameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta setNameMeta = setNameItem.getItemMeta();
        setNameMeta.displayName(Component.text("Set Name"));
        setNameItem.setItemMeta(setNameMeta);
        inv.setItem(29, setNameItem);

        // Store the villager associated with this inventory
        inventoryMap.put(inv, villager);

        // Open the inventory for the player
        player.openInventory(inv);
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
        if (!inventoryMap.containsKey(event.getClickedInventory())) {
            return;
        }

        Villager villager = inventoryMap.get(event.getClickedInventory());
        Inventory inv = event.getClickedInventory();
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (event.getSlot() >= 27 && clickedItem != null) {
            event.setCancelled(true);
        }

        if (event.getSlot() == 27 && clickedItem != null) {
            handleStaticModeToggle(villager, player, inv);
            event.setCancelled(true);
        }

        if (event.getSlot() == 28 && clickedItem != null) {
            handleProfessionChange(villager, player, inv);
            event.setCancelled(true);
        }

        if (event.getSlot() == 29 && clickedItem != null) {
            handleSetName(villager, player, inv);
            event.setCancelled(true);
        }
    }

    private void handleSetName(Villager villager, Player player, Inventory inv) {
        // Prompt the player to enter the new name
        plugin.sendMessage(player, "This Feature is not implemented yet.)");
        updateNameDisplayItem(inv, "Set Name");
    }

    /**
     * The handleStaticModeToggle method toggles the static mode of a Villager entity.
     * If the Villager is in static mode, it deactivates the static mode and vice versa.
     * It updates the display item in the inventory to reflect the new static mode status.
     *
     * @param villager The Villager entity whose static mode is to be toggled.
     * @param player The Player who toggled the static mode.
     * @param inv The Inventory associated with the Villager entity.
     */
    private void handleStaticModeToggle(Villager villager, Player player, Inventory inv) {
        Boolean isStatic = staticMap.get(villager);
        if (isStatic == null) {
            // Handle the case where the villager is not in the map, for example by setting isStatic to false
            isStatic = false;
        }
        if (isStatic){
            deactivateStaticMode(villager, player);
        } else {
            activateStaticMode(villager, player);
        }

        if (isStatic){
            updateStaticModeDisplayItem(inv, Material.REDSTONE_TORCH, "Static Mode: False");
        } else {
            updateStaticModeDisplayItem(inv, Material.SOUL_TORCH, "Static Mode: True");
        }
    }

    /**
     * The handleProfessionChange method changes the profession of a Villager entity to the next profession in the list.
     * It updates the display item in the inventory to reflect the new profession.
     *
     * @param villager The Villager entity whose profession is to be changed.
     * @param player The Player who changed the profession.
     * @param inv The Inventory associated with the Villager entity.
     */
    private void handleProfessionChange(Villager villager, Player player, Inventory inv) {
        Villager.Profession currentProfession = villager.getProfession();
        Villager.Profession nextProfession = getNextProfession(currentProfession);

        villager.setProfession(nextProfession);
        updateProfessionDisplayItem(inv, nextProfession);
    }

    /**
     * Activates static mode for a Villager entity.
     *
     * @param villager The Villager entity to activate static mode for.
     * @param player The Player who triggered the activation.
     */
    void activateStaticMode(Villager villager, Player player) {
        plugin.logDebugPlayer(player, "Static Mode Activated");
        staticMap.put(villager, true);
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
        if (villager.getProfession() == Villager.Profession.NONE || villager.getProfession() == Villager.Profession.NITWIT){
            villager.setProfession(Villager.Profession.ARMORER);
        }
    }

    /**
     * Deactivates the static mode of a Villager entity.
     *
     * @param villager The Villager entity whose static mode is to be deactivated.
     * @param player The Player who is deactivating the static mode.
     */
    void deactivateStaticMode(Villager villager, Player player) {
        plugin.logDebugPlayer(player, "Static Mode Deactivated");
        staticMap.remove(villager);
        villager.setInvulnerable(false);
    }

    /**
     * Retrieves the next profession in the list of professions for a Villager.
     *
     * @param currentProfession - The current profession of the Villager.
     * @return The next profession in the list.
     */
    private Villager.Profession getNextProfession(Villager.Profession currentProfession) {
        Villager.Profession[] professions = Villager.Profession.values();
        int currentIndex = currentProfession.ordinal();
        int nextIndex = (currentIndex + 1) % professions.length;
        Villager.Profession nextProfession = professions[nextIndex];

        if (nextProfession == Villager.Profession.NONE || nextProfession == Villager.Profession.NITWIT) {
            nextIndex = (nextIndex + 1) % professions.length;
            nextProfession = professions[nextIndex];
        }

        return nextProfession;
    }

    /**
     * Updates the display item in the inventory to reflect the new profession.
     *
     * @param inv The Inventory object associated with the Villager entity.
     * @param nextProfession The Profession object representing the next profession for the Villager entity.
     */
    private void updateProfessionDisplayItem(Inventory inv, Villager.Profession nextProfession) {

        ItemStack changeProfessionItem = inv.getItem(28);
        ItemMeta meta = changeProfessionItem.getItemMeta();
        String professionName = nextProfession.name();
        meta.displayName(Component.text("(" + professionName + ")"));
        changeProfessionItem.setItemMeta(meta);
    }

    /**
     * Updates the display item in the inventory to reflect the given static mode.
     *
     * @param inv The inventory where the display item is updated.
     * @param material The material of the display item.
     * @param displayName The display name of the item.
     */
    private void updateStaticModeDisplayItem(Inventory inv, Material material, String displayName) {
        ItemStack toggleAIItem = new ItemStack(material);
        ItemMeta meta = toggleAIItem.getItemMeta();
        meta.displayName(Component.text(displayName));
        toggleAIItem.setItemMeta(meta);
        inv.setItem(27, toggleAIItem);
    }

    private void updateNameDisplayItem(Inventory inv, String displayName) {
        ItemStack setNameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta setNameMeta = setNameItem.getItemMeta();
        setNameMeta.displayName(Component.text(displayName));
        setNameItem.setItemMeta(setNameMeta);
        inv.setItem(29, setNameItem);
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

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
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

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
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

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
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
        // Get the inventory that was closed
        Inventory inv = event.getInventory();

        // Get the villager associated with this inventory
        Villager villager = inventoryMap.get(inv);
        if (villager == null) {
            return;
        }

        // Create a new list to store the updated trades
        List<MerchantRecipe> newRecipes = new ArrayList<>();

        // For each slot in the inventory, create a new MerchantRecipe and add it to the list
        for (int i = 0; i < inv.getSize() / 3; i++) {
            // Get the input items and output item from the inventory
            ItemStack ingredient1 = inv.getItem(i);
            ItemStack ingredient2 = inv.getItem(i + 9);
            ItemStack result = inv.getItem(i + 18);

            // If the result is null or AIR, skip this slot
            if (result == null || result.getType() == Material.AIR) {
                continue;
            }

            // Create a new MerchantRecipe
            MerchantRecipe newRecipe = new MerchantRecipe(result, 9999);
            if (ingredient1 == null || ingredient1.getType() == Material.AIR) {
                continue;
            }
            newRecipe.addIngredient(ingredient1);
            if (ingredient2 != null && ingredient2.getType() != Material.AIR) {
                newRecipe.addIngredient(ingredient2);
            }

            newRecipes.add(newRecipe);
        }

        // Check if the villager is static
        if (staticMap.get(villager) != null && staticMap.get(villager)) {
            // If the villager is static, update the villager's trades and store the villager data
            villager.setRecipes(newRecipes);
            plugin.logDebugPlayer((Player) event.getPlayer(), "Inventory closed, storing data for villager " + villager.getUniqueId());
            storeVillagerData(villager);
        } else {
            plugin.logDebugPlayer((Player) event.getPlayer(), "Inventory closed, Villager is not static, trades not updated");
        }

        // Remove the inventory from the map
        inventoryMap.remove(inv);
        villager.setAware(true);
    }
}