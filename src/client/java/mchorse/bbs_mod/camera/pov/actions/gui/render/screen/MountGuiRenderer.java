/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.entity.EntityType
 *  net.minecraft.entity.EquipmentSlot
 *  net.minecraft.entity.LivingEntity
 *  net.minecraft.entity.passive.AbstractHorseEntity
 *  net.minecraft.entity.passive.DonkeyEntity
 *  net.minecraft.entity.passive.HorseEntity
 *  net.minecraft.inventory.SimpleInventory
 *  net.minecraft.item.ItemStack
 *  net.minecraft.world.World
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiEntityPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.AbstractHorseEntityPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HorseEntityPovAccess;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.DonkeyEntity;
import net.minecraft.entity.passive.HorseEntity;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

public final class MountGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final MountGuiRenderer INSTANCE = new MountGuiRenderer();
    private static final int HORSE_SADDLED_FLAG = 4;
    private static HorseEntity previewHorse;
    private static DonkeyEntity previewDonkey;

    private MountGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawPreview(GuiRenderContext ctx) {
        if (ctx.skipEntityPreview) {
            return;
        }
        MountGuiRenderer.drawMount(ctx);
    }

    /*
     * WARNING - Removed try catching itself - possible behaviour change.
     */
    private static void drawMount(GuiRenderContext ctx) {
        AbstractHorseEntity mount;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            return;
        }
        mount = (AbstractHorseEntity)("donkey".equals(ctx.guiId) ? MountGuiRenderer.donkeyPreview((World)client.world) : MountGuiRenderer.horsePreview((World)client.world));
        if (mount == null) {
            return;
        }
        MountGuiRenderer.applyMountPreviewState((AbstractHorseEntity)mount, ctx.clip, ctx.guiId, ctx.localTick);
        float centerX = 52.0f;
        float centerY = 44.0f;
        int size = 17;
        float lookX = ctx.cursorVisible ? ctx.cursorGuiX : centerX;
        float lookY = ctx.cursorVisible ? ctx.cursorGuiY : centerY;
        try {
            ctx.batcher.flush();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask((boolean)true);
            RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
            GuiEntityPreviewRenderer.draw(ctx.batcher.getContext(), centerX, centerY, size, 0.25f, lookX, lookY, (LivingEntity)mount);
            ctx.batcher.flush();
        }
        catch (Exception exception) {
        }
        finally {
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask((boolean)false);
        }
    }

    private static HorseEntity horsePreview(World world) {
        if ((previewHorse == null || previewHorse.getWorld() != world) && (previewHorse = (HorseEntity)EntityType.HORSE.create(world)) != null) {
            previewHorse.setSilent(true);
        }
        return previewHorse;
    }

    private static DonkeyEntity donkeyPreview(World world) {
        if ((previewDonkey == null || previewDonkey.getWorld() != world) && (previewDonkey = (DonkeyEntity)EntityType.DONKEY.create(world)) != null) {
            previewDonkey.setSilent(true);
        }
        return previewDonkey;
    }

    private static void applyMountPreviewState(AbstractHorseEntity mount, GuiPovActionClip clip, String guiId, float tick) {
        ItemStack saddle;
        if (mount instanceof HorseEntity) {
            HorseEntity horse = (HorseEntity)mount;
            ((HorseEntityPovAccess)horse).bbsPov$setHorseVariant(GuiPovActionClip.packHorseVariant(clip.sampleHorseVariant(guiId, tick)));
        }
        if (mount instanceof DonkeyEntity) {
            DonkeyEntity donkey = (DonkeyEntity)mount;
            donkey.setHasChest(clip.isMountChestOpen(guiId, tick));
        }
        boolean hasSaddle = (saddle = GuiSlotRenderer.sampleSlot(clip, guiId, "saddle", tick)) != null && !saddle.isEmpty();
        AbstractHorseEntityPovAccess access = (AbstractHorseEntityPovAccess)mount;
        access.bbsPov$setHorseFlag(4, hasSaddle);
        SimpleInventory items = access.bbsPov$getItems();
        if (items != null) {
            items.setStack(0, hasSaddle ? saddle.copy() : ItemStack.EMPTY);
        }
        if (mount instanceof HorseEntity) {
            ItemStack armor = GuiSlotRenderer.sampleSlot(clip, guiId, "armor", tick);
            ItemStack worn = armor == null || armor.isEmpty() ? ItemStack.EMPTY : armor.copy();
            mount.equipStack(EquipmentSlot.CHEST, worn);
            if (items != null && items.size() > 1) {
                items.setStack(1, worn.copy());
            }
        }
    }
}

