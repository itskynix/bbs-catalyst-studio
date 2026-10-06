/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.inventory.Inventory
 *  net.minecraft.inventory.SimpleInventory
 *  net.minecraft.item.ItemStack
 *  net.minecraft.recipe.RecipeEntry
 *  net.minecraft.recipe.RecipeType
 *  net.minecraft.recipe.StonecuttingRecipe
 *  net.minecraft.registry.DynamicRegistryManager
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 *  net.minecraft.world.World
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
//? if >=1.20.4 {
import net.minecraft.recipe.RecipeEntry;
//?}
import net.minecraft.recipe.RecipeType;
import net.minecraft.recipe.StonecuttingRecipe;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;

public final class StonecutterGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final StonecutterGuiRenderer INSTANCE = new StonecutterGuiRenderer();
    private static final Identifier STONECUTTER_SCROLLER = new Identifier("container/stonecutter/scroller");
    private static final Identifier STONECUTTER_SCROLLER_DISABLED = new Identifier("container/stonecutter/scroller_disabled");
    private static final Identifier STONECUTTER_RECIPE = new Identifier("container/stonecutter/recipe");
    private static final Identifier STONECUTTER_RECIPE_SELECTED = new Identifier("container/stonecutter/recipe_selected");
    private static final Identifier STONECUTTER_RECIPE_HOVER = new Identifier("container/stonecutter/recipe_highlighted");

    private StonecutterGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        StonecutterGuiRenderer.drawChrome(ctx.batcher, ctx.clip, ctx.localTick, cursorX, cursorY, ctx.hover);
    }

    @Override
    public void drawLateItems(GuiRenderContext ctx) {
        StonecutterGuiRenderer.drawRecipeItems(ctx.batcher, ctx.clip, ctx.localTick);
    }

    private static void drawChrome(Batcher2D batcher, GuiPovActionClip clip, float tick, float cursorX, float cursorY, GuiPointerHover hover) {
        List<ItemStack> recipes = StonecutterGuiRenderer.listStonecutterRecipes(GuiSlotRenderer.sampleSlot(clip, "stonecutter", "input", tick));
        ItemStack output = GuiSlotRenderer.sampleSlot(clip, "stonecutter", "result", tick);
        int selected = StonecutterGuiRenderer.findStonecutterSelection(recipes, output);
        int topRow = StonecutterGuiRenderer.stonecutterTopRow(clip, tick, recipes.size(), selected);
        int maxTopRow = Math.max(0, MathHelper.ceilDiv((int)recipes.size(), (int)4) - 3);
        boolean canScroll = recipes.size() > 12;
        int scrollY = maxTopRow == 0 ? 0 : Math.round((float)topRow * 41.0f / (float)maxTopRow);
        batcher.drawGuiTexture(canScroll ? STONECUTTER_SCROLLER : STONECUTTER_SCROLLER_DISABLED, 119, 15 + scrollY, 12, 15);
        int first = topRow * 4;
        int shown = Math.min(12, recipes.size() - first);
        for (int i = 0; i < shown; ++i) {
            int index = first + i;
            int x = 52 + i % 4 * 16;
            int y = 14 + i / 4 * 18 + 2;
            boolean hovered = GuiTextRenderer.inBounds(cursorX, cursorY, x, y - 1, 16, 18);
            Identifier texture = index == selected ? STONECUTTER_RECIPE_SELECTED : (hovered ? STONECUTTER_RECIPE_HOVER : STONECUTTER_RECIPE);
            batcher.drawGuiTexture(texture, x, y - 1, 16, 18);
            if (!hovered) continue;
            hover.item = recipes.get(index);
        }
    }

    private static void drawRecipeItems(Batcher2D batcher, GuiPovActionClip clip, float tick) {
        List<ItemStack> recipes = StonecutterGuiRenderer.listStonecutterRecipes(GuiSlotRenderer.sampleSlot(clip, "stonecutter", "input", tick));
        ItemStack output = GuiSlotRenderer.sampleSlot(clip, "stonecutter", "result", tick);
        int selected = StonecutterGuiRenderer.findStonecutterSelection(recipes, output);
        int topRow = StonecutterGuiRenderer.stonecutterTopRow(clip, tick, recipes.size(), selected);
        int first = topRow * 4;
        int shown = Math.min(12, recipes.size() - first);
        DiffuseLighting.enableGuiDepthLighting();
        for (int i = 0; i < shown; ++i) {
            int x = 52 + i % 4 * 16;
            int y = 14 + i / 4 * 18 + 2;
            batcher.getContext().getMatrices().push();
            batcher.getContext().getMatrices().translate(0.0f, 0.0f, 100.0f);
            batcher.getContext().drawItem(recipes.get(first + i), x, y);
            batcher.getContext().getMatrices().pop();
        }
    }

    private static List<ItemStack> listStonecutterRecipes(ItemStack input) {
        ArrayList<ItemStack> recipes = new ArrayList<ItemStack>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || input == null || input.isEmpty()) {
            return recipes;
        }
        DynamicRegistryManager registries = client.world.getRegistryManager();
        SimpleInventory inventory = new SimpleInventory(new ItemStack[]{input.copy()});
        //? if >=1.20.4 {
        for (RecipeEntry entry : client.world.getRecipeManager().getAllMatches(RecipeType.STONECUTTING, (Inventory)inventory, (World)client.world)) {
            ItemStack result = ((StonecuttingRecipe)entry.value()).getResult(registries);
            if (result == null || result.isEmpty()) continue;
            recipes.add(result.copy());
        }
        //?} else {
        /*for (StonecuttingRecipe recipe : client.world.getRecipeManager().getAllMatches(RecipeType.STONECUTTING, (Inventory)inventory, (World)client.world)) {
            ItemStack result = recipe.getOutput(registries);
            if (result == null || result.isEmpty()) continue;
            recipes.add(result.copy());
        }
        *///?}
        return recipes;
    }

    private static int findStonecutterSelection(List<ItemStack> recipes, ItemStack output) {
        if (output == null || output.isEmpty()) {
            return -1;
        }
        for (int i = 0; i < recipes.size(); ++i) {
            if (!ItemStack.areItemsEqual((ItemStack)recipes.get(i), (ItemStack)output)) continue;
            return i;
        }
        return -1;
    }

    private static int stonecutterTopRow(GuiPovActionClip clip, float tick, int recipeCount, int selected) {
        int fallback;
        int maxTopRow = Math.max(0, MathHelper.ceilDiv((int)recipeCount, (int)4) - 3);
        int n = fallback = selected < 0 ? 0 : MathHelper.clamp((int)(selected / 4), (int)0, (int)maxTopRow);
        if (clip.stonecutterRow == null || clip.stonecutterRow.isEmpty()) {
            return fallback;
        }
        return MathHelper.clamp((int)((Integer)clip.stonecutterRow.interpolate(tick, fallback)), (int)0, (int)maxTopRow);
    }
}

