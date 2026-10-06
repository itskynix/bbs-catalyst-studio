/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.client.gui.screen.ingame.InventoryScreen
 *  net.minecraft.entity.EquipmentSlot
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.decoration.ArmorStandEntity
 *  net.minecraft.item.ArmorItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemStack
 *  net.minecraft.world.World
 *  org.joml.Quaternionf
 *  org.joml.Vector3f
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SmithingGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final SmithingGuiRenderer INSTANCE = new SmithingGuiRenderer();
    private static final Quaternionf SMITHING_ARMOR_STAND_ROTATION = new Quaternionf().rotationXYZ(0.43633232f, 0.0f, (float)Math.PI);

    private SmithingGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        boolean hasInvalidRecipe;
        ItemStack template = GuiSlotRenderer.sampleSlot(ctx.clip, "smithing_table", "template", ctx.localTick);
        ItemStack base = GuiSlotRenderer.sampleSlot(ctx.clip, "smithing_table", "base", ctx.localTick);
        ItemStack addition = GuiSlotRenderer.sampleSlot(ctx.clip, "smithing_table", "addition", ctx.localTick);
        ItemStack result = GuiSlotRenderer.sampleSlot(ctx.clip, "smithing_table", "result", ctx.localTick);
        boolean hasInput = !template.isEmpty() || !base.isEmpty() || !addition.isEmpty();
        boolean bl = hasInvalidRecipe = hasInput && result.isEmpty();
        if (hasInvalidRecipe) {
            ctx.batcher.getContext().drawTexture(ctx.entry.texture, 65, 46, 176.0f, 0.0f, 28, 21, 256, 256);
        }
    }

    @Override
    public void drawPreview(GuiRenderContext ctx) {
        if (ctx.skipEntityPreview) {
            return;
        }
        SmithingGuiRenderer.drawArmorStand(ctx);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void drawArmorStand(GuiRenderContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        ArmorStandEntity stand = new ArmorStandEntity((World)client.world, 0.0, 0.0, 0.0);
        stand.setHideBasePlate(true);
        stand.setShowArms(true);
        stand.bodyYaw = 210.0f;
        stand.setPitch(25.0f);
        stand.headYaw = stand.getYaw();
        stand.prevHeadYaw = stand.getYaw();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            stand.equipStack(slot, ItemStack.EMPTY);
        }
        ItemStack output = GuiSlotRenderer.sampleSlot(ctx.clip, "smithing_table", "result", ctx.localTick);
        if (output != null && !output.isEmpty()) {
            ItemStack copy = output.copy();
            Item item = copy.getItem();
            if (item instanceof ArmorItem) {
                ArmorItem armor = (ArmorItem)item;
                stand.equipStack(armor.getSlotType(), copy);
            } else {
                stand.equipStack(EquipmentSlot.OFFHAND, copy);
            }
        }
        try {
            ctx.batcher.flush();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
            InventoryScreen.drawEntity((DrawContext)ctx.batcher.getContext(), (float)141.0f, (float)75.0f, (int)25, (Vector3f)new Vector3f(), (Quaternionf)SMITHING_ARMOR_STAND_ROTATION, null, (LivingEntity)stand);
            ctx.batcher.getContext().draw();
            ctx.batcher.flush();
        }
        catch (Exception exception) {
        }
        finally {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask((boolean)false);
        }
    }
}

