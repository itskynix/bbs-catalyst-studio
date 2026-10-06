/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.recipebook.RecipeResultCollection
 *  net.minecraft.client.recipebook.ClientRecipeBook
 *  net.minecraft.client.recipebook.RecipeBookGroup
 *  net.minecraft.recipe.RecipeType
 *  net.minecraft.recipe.book.RecipeBook
 *  net.minecraft.recipe.book.RecipeBookCategory
 */
package mchorse.bbs_mod.camera.pov.actions.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.recipebook.RecipeResultCollection;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.recipebook.RecipeBookGroup;
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.book.RecipeBook;
import net.minecraft.recipe.book.RecipeBookCategory;

public final class GuiRecipeBook {
    private static final Set<String> ALL = Set.of("inventory", "crafting_table", "furnace", "blast_furnace", "smoker");

    private GuiRecipeBook() {
    }

    public static boolean supports(String guiId) {
        return ALL.contains(guiId);
    }

    public static boolean isFurnace(String guiId) {
        return "furnace".equals(guiId) || "blast_furnace".equals(guiId) || "smoker".equals(guiId);
    }

    public static boolean hasCategories(String guiId) {
        return GuiRecipeBook.groups(guiId).size() > 1;
    }

    public static RecipeBookCategory category(String guiId) {
        return switch (guiId) {
            case "furnace" -> RecipeBookCategory.FURNACE;
            case "blast_furnace" -> RecipeBookCategory.BLAST_FURNACE;
            case "smoker" -> RecipeBookCategory.SMOKER;
            default -> RecipeBookCategory.CRAFTING;
        };
    }

    public static RecipeType<?> recipeType(String guiId) {
        return switch (guiId) {
            case "furnace" -> RecipeType.SMELTING;
            case "blast_furnace" -> RecipeType.BLASTING;
            case "smoker" -> RecipeType.SMOKING;
            default -> RecipeType.CRAFTING;
        };
    }

    public static List<RecipeBookGroup> groups(String guiId) {
        return RecipeBookGroup.getGroups((RecipeBookCategory)GuiRecipeBook.category(guiId));
    }

    public static List<RecipeBookGroup> visibleGroups(String guiId) {
        List<RecipeBookGroup> all = GuiRecipeBook.groups(guiId);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            return all;
        }
        ClientRecipeBook book = client.player.getRecipeBook();
        ArrayList<RecipeBookGroup> visible = new ArrayList<RecipeBookGroup>();
        for (RecipeBookGroup group : all) {
            if (!GuiRecipeBook.isSearch(group) && !GuiRecipeBook.hasKnownRecipes(book, group)) continue;
            visible.add(group);
        }
        return visible.isEmpty() ? all : visible;
    }

    public static boolean isSearch(RecipeBookGroup group) {
        return group == RecipeBookGroup.CRAFTING_SEARCH || group == RecipeBookGroup.FURNACE_SEARCH || group == RecipeBookGroup.BLAST_FURNACE_SEARCH || group == RecipeBookGroup.SMOKER_SEARCH;
    }

    private static boolean hasKnownRecipes(ClientRecipeBook book, RecipeBookGroup group) {
        List<RecipeResultCollection> results = book.getResultsForGroup(group);
        if (results == null || results.isEmpty()) {
            return false;
        }
        for (RecipeResultCollection collection : results) {
            if (!collection.isInitialized()) {
                collection.initialize((RecipeBook)book);
            }
            if (!collection.hasFittingRecipes()) continue;
            return true;
        }
        return false;
    }

    public static int gridWidth(String guiId) {
        return switch (guiId) {
            case "inventory" -> 2;
            case "furnace", "blast_furnace", "smoker" -> 1;
            default -> 3;
        };
    }

    public static int gridHeight(String guiId) {
        return GuiRecipeBook.gridWidth(guiId);
    }

    public static int resultSlotIndex(String guiId) {
        return switch (guiId) {
            case "furnace", "blast_furnace", "smoker" -> 2;
            default -> 0;
        };
    }
}

