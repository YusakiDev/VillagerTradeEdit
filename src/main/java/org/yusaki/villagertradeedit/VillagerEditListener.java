package org.yusaki.villagertradeedit;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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

public class VillagerEditListener implements Listener {

    VillagerTradeEdit plugin;
    private final Map<Inventory, Villager> inventoryMap = new HashMap<>();
    private final Map<Villager, Boolean> staticMap = new HashMap<>();
    private final Set<UUID> retrievedVillagers = new HashSet<>();


    public VillagerEditListener(VillagerTradeEdit plugin) {
        this.plugin = plugin;
    }



    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity instanceof Villager) {
                Villager villager = (Villager) entity;
                PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

                // Check if the villager has data stored
                NamespacedKey staticKey = new NamespacedKey(plugin, "static");

                if (dataContainer.has(staticKey, PersistentDataType.STRING) && !retrievedVillagers.contains(villager.getUniqueId())) {
                    plugin.getLogger().info("Found villager with data in loaded chunk, attempting to retrieve data");
                    retrieveVillagerData(villager);
                    retrievedVillagers.add(villager.getUniqueId());
                }
            }
        }
    }

    public void storeVillagerData(Villager villager) {
        plugin.getLogger().info("Storing data for villager " + villager.getUniqueId());

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

        NamespacedKey staticKey = new NamespacedKey(plugin, "static");
        dataContainer.set(staticKey, PersistentDataType.STRING, staticMap.get(villager).toString());

        NamespacedKey professionKey = new NamespacedKey(plugin, "profession");
        dataContainer.set(professionKey, PersistentDataType.STRING, villager.getProfession().name());

        NamespacedKey tradesKey = new NamespacedKey(plugin, "trades");
        String tradesData = serializeMerchantRecipes(villager.getRecipes());
        dataContainer.set(tradesKey, PersistentDataType.STRING, tradesData);

        plugin.getLogger().info("Stored data for villager " + villager.getUniqueId());
    }

    public void retrieveVillagerData(Villager villager) {
        plugin.getLogger().info("Retrieving data for villager " + villager.getUniqueId());

        PersistentDataContainer dataContainer = villager.getPersistentDataContainer();

        NamespacedKey staticKey = new NamespacedKey(plugin, "static");
        String staticValue = dataContainer.get(staticKey, PersistentDataType.STRING);
        staticMap.put(villager, Boolean.valueOf(staticValue));
        villager.setCollidable(!Boolean.parseBoolean(staticValue));

        NamespacedKey professionKey = new NamespacedKey(plugin, "profession");
        String professionName = dataContainer.get(professionKey, PersistentDataType.STRING);
        if (professionName != null) {
            villager.setProfession(Villager.Profession.valueOf(professionName));
        } else {
            // Set a default profession if professionName is null
            villager.setProfession(Villager.Profession.NONE);
        }

        NamespacedKey tradesKey = new NamespacedKey(plugin, "trades");
        String tradesData = dataContainer.get(tradesKey, PersistentDataType.STRING);
        villager.setRecipes(deserializeMerchantRecipes(tradesData));

        plugin.getLogger().info("Retrieved data for villager " + villager.getUniqueId());
    }
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

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        if (!player.hasPermission("villagertradeedit.open")) {
            plugin.SendMessage(player, "You do not have permission to edit villager trades.");
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
        meta.displayName(Component.text("Toggle Static Mode"));
        toggleAIItem.setItemMeta(meta);
        inv.setItem(27, toggleAIItem);

        ItemStack changeProfessionItem = new ItemStack(Material.LEATHER_CHESTPLATE);
        meta = changeProfessionItem.getItemMeta();
        meta.displayName(Component.text("Change Profession"));
        changeProfessionItem.setItemMeta(meta);
        inv.setItem(28, changeProfessionItem);

        // Store the villager associated with this inventory
        inventoryMap.put(inv, villager);

        // Open the inventory for the player
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the clicked inventory is one of the villager edit inventories
        if (!inventoryMap.containsKey(event.getClickedInventory())) {
            return;
        }

        // Check if the clicked item is glass
        ItemStack clickedItem = event.getCurrentItem();
        if (event.getSlot() >= 27 && clickedItem != null) {
            // Cancel the event to prevent the player from picking up the glass
            event.setCancelled(true);
        }

        // Check if the clicked item is the special item
        if (event.getSlot() == 27 && clickedItem != null) {
            // Get the villager associated with this inventory
            Villager villager = inventoryMap.get(event.getClickedInventory());

            if (staticMap.get(villager) != null && staticMap.get(villager)){
                plugin.SendMessage((Player) event.getWhoClicked(), "Static Mode Deactivated");
                staticMap.remove(villager);
                villager.setInvulnerable(false);
                plugin.SendMessage((Player) event.getWhoClicked(), villager.isInvulnerable() + "");
                ItemStack toggleAIItem = new ItemStack(Material.REDSTONE_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.displayName(Component.text("Toggle Static Mode"));
                toggleAIItem.setItemMeta(meta);
                //set the item in the inventory
                event.getClickedInventory().setItem(27, toggleAIItem);
            } else {
                plugin.SendMessage((Player) event.getWhoClicked(), "Static Mode Activated");
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
                ItemStack toggleAIItem = new ItemStack(Material.SOUL_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.displayName(Component.text("Toggle Static Mode"));
                toggleAIItem.setItemMeta(meta);
                //set the item in the inventory
                event.getClickedInventory().setItem(27, toggleAIItem);
            }

            // Cancel the event to prevent the player from picking up the special item
            event.setCancelled(true);
            plugin.SendMessage((Player) event.getWhoClicked(), villager.isInvulnerable() + "");
        }


        if (event.getSlot() == 28 && clickedItem != null) {
            // Get the villager associated with this inventory
            Villager villager = inventoryMap.get(event.getClickedInventory());

            // Get the current profession of the villager
            Villager.Profession currentProfession = villager.getProfession();

            // Get the next profession
            Villager.Profession[] professions = Villager.Profession.values();
            int currentIndex = currentProfession.ordinal();
            int nextIndex = (currentIndex + 1) % professions.length;
            Villager.Profession nextProfession = professions[nextIndex];

            //Skip the NONE and NITWIT professions
            if (nextProfession == Villager.Profession.NONE || nextProfession == Villager.Profession.NITWIT) {
                nextIndex = (nextIndex + 1) % professions.length;
                nextProfession = professions[nextIndex];
            }

            plugin.getLogger().info("Current profession: " + currentProfession);
            plugin.getLogger().info("Next profession: " + nextProfession);
            // Set the new profession for the villager
            villager.setProfession(nextProfession);

            plugin.getLogger().info("Profession after change: " + villager.getProfession());

            ItemStack changeProfessionItem = event.getClickedInventory().getItem(28);
            ItemMeta meta = changeProfessionItem.getItemMeta();
            String professionName = nextProfession.name();
            meta.displayName(Component.text("(" + professionName + ")"));
            changeProfessionItem.setItemMeta(meta);

            // Cancel the event to prevent the player from picking up the change profession item
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityPathFind(EntityPathfindEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) {
            return;
        }

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onVillagerCareerChange(VillagerCareerChangeEvent event) {

        Villager villager = event.getEntity();

        if (Boolean.TRUE.equals(staticMap.get(villager))) {
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
            plugin.SendMessage((Player) event.getPlayer(), "Inventory closed, storing data for villager " + villager.getUniqueId());
            storeVillagerData(villager);
        } else {
            plugin.SendMessage((Player) event.getPlayer(), "Inventory closed, Villager is not static, trades not updated");
        }

        // Remove the inventory from the map
        inventoryMap.remove(inv);
        villager.setAware(true);
    }
}