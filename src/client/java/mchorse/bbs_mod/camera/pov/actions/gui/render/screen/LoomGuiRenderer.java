/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  com.mojang.datafixers.util.Pair
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  net.minecraft.block.entity.BannerBlockEntity
 *  net.minecraft.block.entity.BannerPattern
 *  net.minecraft.block.entity.BannerPattern$Patterns
 *  net.minecraft.block.entity.BannerPatterns
 *  net.minecraft.block.entity.BlockEntityType
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.model.ModelPart
 *  net.minecraft.client.network.ClientPlayerEntity
 *  net.minecraft.client.render.DiffuseLighting
 *  net.minecraft.client.render.OverlayTexture
 *  net.minecraft.client.render.VertexConsumerProvider
 *  net.minecraft.client.render.block.entity.BannerBlockEntityRenderer
 *  net.minecraft.client.render.entity.model.EntityModelLayers
 *  net.minecraft.client.render.model.ModelLoader
 *  net.minecraft.client.util.SpriteIdentifier
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.BannerItem
 *  net.minecraft.item.BlockItem
 *  net.minecraft.item.DyeItem
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.nbt.NbtCompound
 *  net.minecraft.nbt.NbtElement
 *  net.minecraft.nbt.NbtList
 *  net.minecraft.registry.entry.RegistryEntry
 *  net.minecraft.screen.LoomScreenHandler
 *  net.minecraft.util.DyeColor
 *  net.minecraft.util.Identifier
 *  net.minecraft.util.math.MathHelper
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render.screen;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderContext;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiScreenChrome;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiSlotRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiStandardLayoutRenderer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import java.util.List;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import net.minecraft.block.entity.BannerBlockEntity;
import net.minecraft.block.entity.BannerPattern;
import net.minecraft.block.entity.BannerPatterns;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BannerBlockEntityRenderer;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.client.render.model.ModelLoader;
import net.minecraft.client.util.SpriteIdentifier;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.BannerItem;
import net.minecraft.item.BlockItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.screen.LoomScreenHandler;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

public final class LoomGuiRenderer
implements GuiRenderer,
GuiScreenChrome {
    public static final LoomGuiRenderer INSTANCE = new LoomGuiRenderer();
    private static ModelPart loomBannerField;

    private LoomGuiRenderer() {
    }

    @Override
    public void render(GuiRenderContext ctx) {
        GuiStandardLayoutRenderer.render(ctx, this);
    }

    @Override
    public void drawEarlyChrome(GuiRenderContext ctx) {
        float cursorX = ctx.cursorVisible ? ctx.cursorGuiX : -1000.0f;
        float cursorY = ctx.cursorVisible ? ctx.cursorGuiY : -1000.0f;
        LoomGuiRenderer.drawChrome(ctx.batcher, ctx.clip, ctx.localTick, ctx.originX, ctx.originY, ctx.scaleX, ctx.scaleY, cursorX, cursorY);
    }

    private static void drawChrome(Batcher2D batcher, GuiPovActionClip clip, float tick, float originX, float originY, float scaleX, float scaleY, float cursorX, float cursorY) {
        int i;
        ItemStack banner = GuiSlotRenderer.sampleSlot(clip, "loom", "banner", tick);
        ItemStack dye = GuiSlotRenderer.sampleSlot(clip, "loom", "dye", tick);
        ItemStack patternItem = GuiSlotRenderer.sampleSlot(clip, "loom", "pattern", tick);
        ItemStack output = GuiSlotRenderer.sampleSlot(clip, "loom", "result", tick);
        boolean canApply = banner.getItem() instanceof BannerItem && dye.getItem() instanceof DyeItem && BannerBlockEntity.getPatternCount((ItemStack)banner) < 6;
        List<RegistryEntry<BannerPattern>> patterns = canApply ? LoomGuiRenderer.getLoomPatterns(banner, dye, patternItem) : List.of();
        int selected = LoomGuiRenderer.findSelectedLoomPattern(patterns, output);
        int rows = MathHelper.ceilDiv((int)patterns.size(), (int)4);
        int maxTopRow = Math.max(0, rows - 4);
        int fallbackRow = selected < 0 ? 0 : selected / 4;
        int topRow = MathHelper.clamp((int)((Integer)clip.loomRow.interpolate(tick, fallbackRow)), (int)0, (int)maxTopRow);
        int scrollY = maxTopRow == 0 ? 0 : Math.round((float)topRow * 41.0f / (float)maxTopRow);
        float screenScale = Math.min(scaleX, scaleY);
        batcher.drawGuiTexture(new Identifier(canApply ? "container/loom/scroller" : "container/loom/scroller_disabled"), 119, 13 + scrollY, 12, 15);
        int firstPattern = topRow * 4;
        int shown = Math.min(16, patterns.size() - firstPattern);
        for (i = 0; i < shown; ++i) {
            boolean hover;
            int patternIndex = firstPattern + i;
            int x = 60 + i % 4 * 14;
            int y = 13 + i / 4 * 14;
            boolean bl = hover = cursorX >= (float)x && cursorX < (float)(x + 14) && cursorY >= (float)y && cursorY < (float)(y + 14);
            String background = patternIndex == selected ? "container/loom/pattern_selected" : (hover ? "container/loom/pattern_highlighted" : "container/loom/pattern");
            batcher.drawGuiTexture(new Identifier(background), x, y, 14, 14);
        }
        batcher.getContext().draw();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask((boolean)true);
        RenderSystem.clear((int)256, (boolean)MinecraftClient.IS_SYSTEM_MAC);
        DiffuseLighting.disableGuiDepthLighting();
        for (i = 0; i < shown; ++i) {
            int x = 60 + i % 4 * 14;
            int y = 13 + i / 4 * 14;
            LoomGuiRenderer.renderLoomPattern(batcher, patterns.get(firstPattern + i), originX + (float)x * scaleX, originY + (float)y * scaleY, 6.0f * screenScale, screenScale);
        }
        Item x = output.getItem();
        if (x instanceof BannerItem) {
            BannerItem outputBanner = (BannerItem)x;
            NbtList patternNbt = BannerBlockEntity.getPatternListNbt((ItemStack)output);
            List outputPatterns = BannerBlockEntity.getPatternsFromNbt((DyeColor)outputBanner.getColor(), (NbtList)(patternNbt == null ? new NbtList() : patternNbt));
            LoomGuiRenderer.renderBannerCanvas(batcher, outputPatterns, originX + 139.0f * scaleX, originY + 52.0f * scaleY, 24.0f * screenScale, screenScale, false);
        }
        batcher.getContext().draw();
        DiffuseLighting.enableGuiDepthLighting();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask((boolean)false);
    }

    private static List<RegistryEntry<BannerPattern>> getLoomPatterns(ItemStack banner, ItemStack dye, ItemStack pattern) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return List.of();
        }
        LoomScreenHandler handler = new LoomScreenHandler(0, player.getInventory());
        handler.getBannerSlot().setStack(banner.copy());
        handler.getDyeSlot().setStack(dye.copy());
        handler.getPatternSlot().setStack(pattern.copy());
        handler.onContentChanged(handler.getBannerSlot().inventory);
        return List.copyOf(handler.getBannerPatterns());
    }

    private static int findSelectedLoomPattern(List<RegistryEntry<BannerPattern>> patterns, ItemStack output) {
        NbtList patternNbt = BannerBlockEntity.getPatternListNbt((ItemStack)output);
        if (patternNbt == null || patternNbt.isEmpty()) {
            return -1;
        }
        String selectedId = patternNbt.getCompound(patternNbt.size() - 1).getString("Pattern");
        for (int i = 0; i < patterns.size(); ++i) {
            if (!((BannerPattern)patterns.get(i).value()).getId().equals(selectedId)) continue;
            return i;
        }
        return -1;
    }

    private static void renderLoomPattern(Batcher2D batcher, RegistryEntry<BannerPattern> pattern, float x, float y, float scale, float screenScale) {
        NbtCompound nbt = new NbtCompound();
        nbt.put("Patterns", (NbtElement)new BannerPattern.Patterns().add(BannerPatterns.BASE, DyeColor.GRAY).add(pattern, DyeColor.WHITE).toNbt());
        ItemStack stack = new ItemStack((ItemConvertible)Items.GRAY_BANNER);
        BlockItem.setBlockEntityNbt((ItemStack)stack, (BlockEntityType)BlockEntityType.BANNER, (NbtCompound)nbt);
        LoomGuiRenderer.renderBannerCanvas(batcher, BannerBlockEntity.getPatternsFromNbt((DyeColor)DyeColor.GRAY, (NbtList)BannerBlockEntity.getPatternListNbt((ItemStack)stack)), x, y, scale, screenScale, true);
    }

    private static void renderBannerCanvas(Batcher2D batcher, List<Pair<RegistryEntry<BannerPattern>, DyeColor>> patterns, float x, float y, float scale, float screenScale, boolean thumbnail) {
        if (loomBannerField == null) {
            loomBannerField = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(EntityModelLayers.BANNER).getChild("flag");
        }
        MatrixStack matrices = new MatrixStack();
        matrices.push();
        if (thumbnail) {
            matrices.translate(x + 0.5f * screenScale, y + 16.0f * screenScale, 0.0f);
        } else {
            matrices.translate(x, y, 0.0f);
        }
        matrices.scale(scale, -scale, 1.0f);
        if (thumbnail) {
            matrices.translate(0.5f, 0.5f, 0.0f);
        }
        matrices.translate(0.5f, 0.5f, 0.5f);
        matrices.scale(0.6666667f, -0.6666667f, -0.6666667f);
        LoomGuiRenderer.loomBannerField.pitch = 0.0f;
        LoomGuiRenderer.loomBannerField.pivotY = -32.0f;
        BannerBlockEntityRenderer.renderCanvas((MatrixStack)matrices, (VertexConsumerProvider)batcher.getContext().getVertexConsumers(), (int)0xF000F0, (int)OverlayTexture.DEFAULT_UV, (ModelPart)loomBannerField, (SpriteIdentifier)ModelLoader.BANNER_BASE, (boolean)true, patterns);
        matrices.pop();
    }
}

