/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiActionRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.AnvilGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.BeaconGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.BookGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.BrewingGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.CartographyGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.ContainerGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.CreativeGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.EnchantingGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.FurnaceGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.GamemodeGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.LoomGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.MerchantGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.MountGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.SmithingGuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.screen.StonecutterGuiRenderer;
import java.util.HashMap;
import java.util.Map;

public final class GuiRendererRegistry {
    private static final Map<String, GuiRenderer> RENDERERS = new HashMap<String, GuiRenderer>();
    private static final GuiRenderer FALLBACK = GuiActionRenderer::renderFallback;

    private GuiRendererRegistry() {
    }

    public static void register(String guiId, GuiRenderer renderer) {
        if (guiId == null || guiId.isBlank() || renderer == null) {
            return;
        }
        RENDERERS.put(guiId, renderer);
    }

    public static GuiRenderer get(String guiId) {
        if (guiId == null) {
            return FALLBACK;
        }
        GuiRenderer renderer = RENDERERS.get(guiId);
        return renderer == null ? FALLBACK : renderer;
    }

    static {
        GuiRendererRegistry.register("villager", MerchantGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("beacon", BeaconGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("enchanting_table", EnchantingGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("loom", LoomGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("book", BookGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("creative_inventory", CreativeGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("horse", MountGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("donkey", MountGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("furnace", FurnaceGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("blast_furnace", FurnaceGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("smoker", FurnaceGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("inventory", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("crafting_table", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("grindstone", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("chest", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("large_chest", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("barrel", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("ender_chest", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("shulker_box", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("hopper", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("dispenser", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("dropper", ContainerGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("anvil", AnvilGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("smithing_table", SmithingGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("brewing_stand", BrewingGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("stonecutter", StonecutterGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("cartography_table", CartographyGuiRenderer.INSTANCE);
        GuiRendererRegistry.register("gamemode_switcher", GamemodeGuiRenderer.INSTANCE);
    }
}

