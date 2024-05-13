package org.yusaki.villagertradeedit;

import com.destroystokyo.paper.event.entity.EntityPathfindEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.VillagerCareerChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.*;

public class VillagerEditListener implements Listener {

    VillagerTradeEdit plugin = VillagerTradeEdit.getPlugin(VillagerTradeEdit.class);
    private final Map<Inventory, Villager> inventoryMap = new HashMap<>();
    private final Map<Villager, Boolean> staticMap = new HashMap<>();
    private VillagerDataHandler villagerDataHandler = new VillagerDataHandler(new File(plugin.getDataFolder(), "villagerData.yml"));


    public Map<UUID, VillagerData> loadAllVillagersData() {
        return villagerDataHandler.loadAllVillagersData();
    }

    public Map<Villager, Boolean> getStaticMap() {
        return staticMap;
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

        event.setCancelled(true);
        // Editing Mode: Make villager static at that moment
        staticMap.put(villager, true);
        villager.setInvulnerable(true);
        villager.setCollidable(false);


        Inventory inv = Bukkit.createInventory(null,9*4, "Villager Trade Edit");

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
        meta.setDisplayName("Toggle Static Mode");
        toggleAIItem.setItemMeta(meta);
        inv.setItem(27, toggleAIItem);

        ItemStack changeProfessionItem = new ItemStack(Material.LEATHER_CHESTPLATE);
        meta = changeProfessionItem.getItemMeta();
        meta.setDisplayName("Change Profession");
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

            //TODO: Not working
            // Toggle the villager's AI
            if (staticMap.get(villager) != null && staticMap.get(villager)){
                plugin.SendMessage((Player) event.getWhoClicked(), "Static Mode Deactivated");
                staticMap.remove(villager);
                villager.setInvulnerable(false);
                plugin.SendMessage((Player) event.getWhoClicked(), villager.isInvulnerable() + "");
                ItemStack toggleAIItem = new ItemStack(Material.REDSTONE_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.setDisplayName("Toggle Static Mode");
                toggleAIItem.setItemMeta(meta);
                //set the item in the inventory
                event.getClickedInventory().setItem(27, toggleAIItem);
            } else {
                plugin.SendMessage((Player) event.getWhoClicked(), "Static Mode Activated");
                staticMap.put(villager, true);
                villager.setInvulnerable(true);
                ItemStack toggleAIItem = new ItemStack(Material.SOUL_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.setDisplayName("Toggle Static Mode");
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

            plugin.getLogger().info("Current profession: " + currentProfession);
            plugin.getLogger().info("Next profession: " + nextProfession);
            // Set the new profession for the villager
            villager.setProfession(nextProfession);

            plugin.getLogger().info("Profession after change: " + villager.getProfession());

            ItemStack changeProfessionItem = event.getClickedInventory().getItem(28);
            ItemMeta meta = changeProfessionItem.getItemMeta();
            String professionName = nextProfession.name();
            meta.setDisplayName("(" + professionName + ")");
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

        // Update the villager's trades
        villager.setRecipes(newRecipes);

        // Remove the inventory from the map
        inventoryMap.remove(inv);

        if (staticMap.get(villager) != null && staticMap.get(villager)) {
            villagerDataHandler.saveVillagerData(villager);
        } else {
            villagerDataHandler.removeVillagerData(villager);
        }
    }
}