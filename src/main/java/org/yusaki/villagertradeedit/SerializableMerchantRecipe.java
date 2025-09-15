package org.yusaki.villagertradeedit;

import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.Serializable;

public class SerializableMerchantRecipe implements Serializable {

    private static final long serialVersionUID = 1876190161652285790L;

    private final Material resultType;
    private final int resultAmount;
    private final String resultMeta;

    private final Material ingredient1Type;
    private final int ingredient1Amount;
    private final String ingredient1Meta;

    private final Material ingredient2Type;
    private final int ingredient2Amount;
    private final String ingredient2Meta;

    public SerializableMerchantRecipe(MerchantRecipe recipe) {
        ItemStack result = recipe.getResult();
        this.resultType = result.getType();
        this.resultAmount = result.getAmount();
        this.resultMeta = serializeItemMeta(result.getItemMeta());

        ItemStack ingredient1 = recipe.getIngredients().get(0);
        this.ingredient1Type = ingredient1.getType();
        this.ingredient1Amount = ingredient1.getAmount();
        this.ingredient1Meta = serializeItemMeta(ingredient1.getItemMeta());

        if (recipe.getIngredients().size() > 1) {
            ItemStack ingredient2 = recipe.getIngredients().get(1);
            this.ingredient2Type = ingredient2.getType();
            this.ingredient2Amount = ingredient2.getAmount();
            this.ingredient2Meta = serializeItemMeta(ingredient2.getItemMeta());
        } else {
            this.ingredient2Type = null;
            this.ingredient2Amount = 0;
            this.ingredient2Meta = null;
        }
    }

    public MerchantRecipe toMerchantRecipe() {
        ItemStack result = new ItemStack(resultType, resultAmount);
        result.setItemMeta(deserializeItemMeta(resultMeta));

        ItemStack ingredient1 = new ItemStack(ingredient1Type, ingredient1Amount);
        ingredient1.setItemMeta(deserializeItemMeta(ingredient1Meta));

        ItemStack ingredient2 = ingredient2Type != null ? new ItemStack(ingredient2Type, ingredient2Amount) : null;
        if (ingredient2 != null) {
            ingredient2.setItemMeta(deserializeItemMeta(ingredient2Meta));
        }

        MerchantRecipe recipe = new MerchantRecipe(result, 9999);
        recipe.addIngredient(ingredient1);
        if (ingredient2 != null) {
            recipe.addIngredient(ingredient2);
        }

        // Neutralize dynamic price modifiers (Hero of the Village, reputation, demand)
        try {
            recipe.setSpecialPrice(0);
        } catch (Throwable ignored) { }
        try {
            recipe.setDemand(0);
        } catch (Throwable ignored) { }
        try {
            recipe.setPriceMultiplier(0.0f);
        } catch (Throwable ignored) { }
        try {
            // Use reflection for compatibility across API versions
            java.lang.reflect.Method m = MerchantRecipe.class.getMethod("setIgnoreDiscounts", boolean.class);
            m.invoke(recipe, true);
        } catch (Throwable ignored) { }

        return recipe;
    }

    private String serializeItemMeta(ItemMeta meta) {
        if (meta == null) {
            return null;
        }

        YamlConfiguration config = new YamlConfiguration();
        config.set("meta", meta);
        return config.saveToString();
    }

    private ItemMeta deserializeItemMeta(String metaString) {
        if (metaString == null) {
            return null;
        }

        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(metaString);
        } catch (InvalidConfigurationException e) {
            e.printStackTrace();
            return null;
        }

        return (ItemMeta) config.get("meta");
    }
}
