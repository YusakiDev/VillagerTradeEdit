package org.yusaki.villagertradeedit;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;

import java.util.List;

public class VillagerEditListener implements Listener {

    //VillagerTradeEdit plugin = VillagerTradeEdit.getPlugin(VillagerTradeEdit.class);

    @EventHandler
    public void PlayerInteractEntityEvent(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Villager villager)) {
            return;
        }

        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return;
        }

        event.setCancelled(true);

        // Create a new inventory with 3 rows of 9 slots each
        Inventory inv = Bukkit.createInventory(null,9*3, "Villager Trade Edit");

        // Get the villager's trades
        List<MerchantRecipe> recipes = villager.getRecipes();

        // For each trade, add the input items and output item to the inventory
        for (int i = 0; i < recipes.size(); i++) {
            MerchantRecipe recipe = recipes.get(i);

            // Get the input items and output item
            List<ItemStack> ingredients = recipe.getIngredients();
            ItemStack result = recipe.getResult();

            // Add the items to the inventory
            inv.setItem(i * 3, ingredients.get(0));
            if (ingredients.size() > 1) {
                inv.setItem(i * 3 + 1, ingredients.get(1));
            } else {
                // If there is only one input item, add an empty slot
                inv.setItem(i * 3 + 1, new ItemStack(Material.AIR));
            }
            inv.setItem(i * 3 + 2, result);
        }

        // Open the inventory for the player
        player.openInventory(inv);
    }
}