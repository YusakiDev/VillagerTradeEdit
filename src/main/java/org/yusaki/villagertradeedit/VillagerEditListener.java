package org.yusaki.villagertradeedit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VillagerEditListener implements Listener {

    //VillagerTradeEdit plugin = VillagerTradeEdit.getPlugin(VillagerTradeEdit.class);
    private final Map<Inventory, Villager> inventoryMap = new HashMap<>();

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
        villager.setAI(false);

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
        meta.setDisplayName("Toggle AI");
        toggleAIItem.setItemMeta(meta);
        inv.setItem(27, toggleAIItem);

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
        if (event.getSlot() >= 27 && clickedItem != null){
            // Cancel the event to prevent the player from picking up the glass
            event.setCancelled(true);
        }

        // Check if the clicked item is the special item
        if (event.getSlot() == 27 && clickedItem != null) {
            // Get the villager associated with this inventory
            Villager villager = inventoryMap.get(event.getClickedInventory());

            //TODO: Not working
            // Toggle the villager's AI
            if (villager.hasAI()) {
                event.getWhoClicked().sendMessage("AI Disabled");
                villager.setAI(false);
                ItemStack toggleAIItem = new ItemStack(Material.REDSTONE_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.setDisplayName("Toggle AI");
                toggleAIItem.setItemMeta(meta);
                //set the item in the inventory
                event.getClickedInventory().setItem(27, toggleAIItem);
            } else {
                event.getWhoClicked().sendMessage("AI Enabled");
                villager.setAI(true);
                ItemStack toggleAIItem = new ItemStack(Material.SOUL_TORCH);
                ItemMeta meta = toggleAIItem.getItemMeta();
                meta.setDisplayName("Toggle AI");
                toggleAIItem.setItemMeta(meta);
                //set the item in the inventory
                event.getClickedInventory().setItem(27, toggleAIItem);
            }

            // Cancel the event to prevent the player from picking up the special item
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
    }
}