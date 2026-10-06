/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.item.ItemGroups
 *  net.minecraft.item.ItemStack
 *  net.minecraft.registry.RegistryWrapper$WrapperLookup
 *  net.minecraft.resource.featuretoggle.FeatureSet
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.common;

import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.resource.featuretoggle.FeatureSet;

public final class GuiItemRenderer {
    private static boolean itemGroupsPopulated = false;

    private GuiItemRenderer() {
    }

    public static void drawSlotItem(Batcher2D batcher, ItemStack stack, int x, int y) {
        GuiSlotRenderer.drawSlotItem(batcher, stack, x, y);
    }

    public static void ensureItemGroupsPopulated() {
        if (itemGroupsPopulated) {
            return;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world != null) {
            try {
                ItemGroups.updateDisplayContext((FeatureSet)client.world.getEnabledFeatures(), (boolean)true, (RegistryWrapper.WrapperLookup)client.world.getRegistryManager());
                itemGroupsPopulated = true;
            }
            catch (Exception exception) {
                // empty catch block
            }
        }
    }
}

