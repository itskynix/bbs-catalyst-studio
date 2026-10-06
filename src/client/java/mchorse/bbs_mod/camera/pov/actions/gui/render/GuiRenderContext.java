/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.client.util.math.MatrixStack
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.render;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.render.GuiPointerHover;
import mchorse.bbs_mod.camera.pov.actions.gui.render.LiveGuiPreviewRenderer;
import mchorse.bbs_mod.camera.pov.actions.gui.render.common.GuiTextRenderer;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.minecraft.HandledScreenPovAccess;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;

public final class GuiRenderContext {
    public final MatrixStack matrices;
    public final Batcher2D batcher;
    public final ReplayKeyframes replayKeyframes;
    public final GuiPovActionClip clip;
    public final RecordedHandData handData;
    public final RecordedHudData hudData;
    public final float globalTick;
    public final float localTick;
    public final int screenWidth;
    public final int screenHeight;
    public final boolean allowCursor;
    public final GuiTypeEntry entry;
    public final String guiId;
    public final Transform transform;
    public final float scaleX;
    public final float scaleY;
    public final float opacity;
    public final float bgOpacity;
    public final float originX;
    public final float originY;
    public final float unshiftedOriginX;
    public final boolean recipeOpen;
    public final boolean cursorVisible;
    public final boolean cursorHasItem;
    public final boolean isDragging;
    public final ItemStack cursorStack;
    public final String dragEncoded;
    public final float curScreenX;
    public final float curScreenY;
    public final float cursorGuiX;
    public final float cursorGuiY;
    public final GuiPointerHover hover = new GuiPointerHover();
    public boolean skipEntityPreview;

    private GuiRenderContext(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, GuiPovActionClip clip, RecordedHandData handData, RecordedHudData hudData, float globalTick, float localTick, int screenWidth, int screenHeight, boolean allowCursor, GuiTypeEntry entry, String guiId, Transform transform, float scaleX, float scaleY, float opacity, float bgOpacity, float originX, float originY, float unshiftedOriginX, boolean recipeOpen, boolean cursorVisible, boolean cursorHasItem, boolean isDragging, ItemStack cursorStack, String dragEncoded, float curScreenX, float curScreenY, float cursorGuiX, float cursorGuiY) {
        this.matrices = matrices;
        this.batcher = batcher;
        this.replayKeyframes = replayKeyframes;
        this.clip = clip;
        this.handData = handData;
        this.hudData = hudData;
        this.globalTick = globalTick;
        this.localTick = localTick;
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.allowCursor = allowCursor;
        this.entry = entry;
        this.guiId = guiId;
        this.transform = transform;
        this.scaleX = scaleX;
        this.scaleY = scaleY;
        this.opacity = opacity;
        this.bgOpacity = bgOpacity;
        this.originX = originX;
        this.originY = originY;
        this.unshiftedOriginX = unshiftedOriginX;
        this.recipeOpen = recipeOpen;
        this.cursorVisible = cursorVisible;
        this.cursorHasItem = cursorHasItem;
        this.isDragging = isDragging;
        this.cursorStack = cursorStack;
        this.dragEncoded = dragEncoded;
        this.curScreenX = curScreenX;
        this.curScreenY = curScreenY;
        this.cursorGuiX = cursorGuiX;
        this.cursorGuiY = cursorGuiY;
    }

    public static GuiRenderContext create(MatrixStack matrices, Batcher2D batcher, ReplayKeyframes replayKeyframes, GuiPovActionClip clip, RecordedHandData handData, float globalTick, int screenWidth, int screenHeight, boolean allowCursor) {
        String dragEncoded;
        HandledScreen handled;
        Screen screen;
        float visibleTick;
        RecordedHudData recordedHudData;
        String guiId;
        KeyframeChannel<Transform> layout;
        float localTick = clip.getLocalTick(globalTick);
        String stateId = clip.state.isEmpty() ? "inventory" : (String)clip.state.interpolate(localTick, "inventory");
        GuiTypeEntry entry = GuiTypeEntry.findById(stateId);
        if (entry == null) {
            entry = GuiTypeEntry.INVENTORY;
        }
        Transform transform = ((layout = clip.getLayout(guiId = entry.id)) == null || layout.isEmpty() ? new Transform() : (Transform)layout.interpolate(localTick, new Transform())).copy();
        float scaleX = Math.max(0.001f, transform.scale.x);
        float scaleY = Math.max(0.001f, transform.scale.y);
        KeyframeChannel<Float> opacityChannel = clip.getOpacity(guiId);
        float opacity = opacityChannel == null || opacityChannel.isEmpty() ? 1.0f : ((Float)opacityChannel.interpolate(localTick, Float.valueOf(1.0f))).floatValue();
        opacity = Math.max(0.0f, Math.min(1.0f, opacity));
        KeyframeChannel<Float> darkness = clip.getDarknessOpacity(guiId);
        float defaultDarkness = "gamemode_switcher".equals(guiId) ? 0.0f : 1.0f;
        float bgOpacity = darkness == null || darkness.isEmpty() ? defaultDarkness : ((Float)darkness.interpolate(localTick, Float.valueOf(defaultDarkness))).floatValue();
        bgOpacity = Math.max(0.0f, Math.min(1.0f, bgOpacity));
        if (opacity <= 0.0f) {
            return null;
        }
        if (replayKeyframes instanceof ReplayKeyframesPovAccess) {
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            recordedHudData = access.bbsPov$getHud();
        } else {
            recordedHudData = null;
        }
        RecordedHudData hudData = recordedHudData;
        KeyframeChannel<Boolean> clipCursorVisible = clip.getCursorVisible(guiId);
        KeyframeChannel<Transform> clipCursorLayout = clip.getCursorLayout(guiId);
        KeyframeChannel<ItemStack> clipCursorItem = clip.getCursorItem(guiId);
        boolean useHudLayout = hudData != null && !hudData.cursorLayout.isEmpty();
        boolean useHudVisible = hudData != null && !hudData.cursorVisible.isEmpty();
        boolean useHudItem = hudData != null && !hudData.cursorItem.isEmpty();
        KeyframeChannel<Boolean> cursorVisibility = useHudVisible ? hudData.cursorVisible : clipCursorVisible;
        float f = visibleTick = useHudVisible ? globalTick : localTick;
        boolean cursorVisible = allowCursor && (cursorVisibility == null || cursorVisibility.isEmpty() ? GuiTextRenderer.sampleBool(clipCursorVisible, localTick, true) : (Boolean)cursorVisibility.interpolate(visibleTick, true) != false);
        ItemStack cursorStack = ItemStack.EMPTY;
        boolean liveDragging = false;
        if (LiveGuiPreviewRenderer.isRenderingLive() && (screen = MinecraftClient.getInstance().currentScreen) instanceof HandledScreen && (handled = (HandledScreen)screen).getScreenHandler() != null) {
            HandledScreenPovAccess dragAccess;
            ItemStack held = handled.getScreenHandler().getCursorStack();
            ItemStack itemStack = cursorStack = held == null ? ItemStack.EMPTY : held;
            if (handled instanceof HandledScreenPovAccess && (liveDragging = (dragAccess = (HandledScreenPovAccess)handled).bbsPov$isCursorDragging()) && dragAccess.bbsPov$getCursorDragSlots() != null && dragAccess.bbsPov$getCursorDragSlots().size() > 1) {
                ItemStack itemStack2 = cursorStack = cursorStack.isEmpty() ? ItemStack.EMPTY : cursorStack.copyWithCount(Math.max(0, dragAccess.bbsPov$getDraggedStackRemainder()));
            }
        }
        if (cursorStack == null || cursorStack.isEmpty()) {
            float itemTick;
            KeyframeChannel<ItemStack> cursorItemChannel = useHudItem ? hudData.cursorItem : clipCursorItem;
            float f2 = itemTick = useHudItem ? globalTick : localTick;
            if (cursorItemChannel != null && !cursorItemChannel.isEmpty()) {
                cursorStack = (ItemStack)cursorItemChannel.interpolate(itemTick, ItemStack.EMPTY);
            }
        }
        boolean isDragging = (dragEncoded = GuiTextRenderer.sampleString(clip.getDragSlots(guiId), localTick, "")) != null && !dragEncoded.isEmpty() || liveDragging;
        boolean cursorHasItem = allowCursor && (cursorStack != null && !cursorStack.isEmpty() || isDragging);
        int guiW = entry.regionWidth;
        int guiH = entry.regionHeight;
        boolean recipeOpen = GuiRecipeBook.supports(guiId) && GuiTextRenderer.sampleBool(clip.getRecipeOpen(guiId), localTick, false);
        float originX = ((float)screenWidth - (float)guiW * scaleX) / 2.0f + transform.translate.x * 2.0f;
        float originY = screenHeight > guiH + 20 ? ("book".equals(entry.id) ? 2.0f * scaleY - transform.translate.y * 2.0f : ("gamemode_switcher".equals(entry.id) ? (float)screenHeight / 2.0f - 58.0f * scaleY - transform.translate.y * 2.0f : ((float)screenHeight - (float)guiH * scaleY) / 2.0f - transform.translate.y * 2.0f)) : ((float)screenHeight - (float)guiH * scaleY) / 2.0f - transform.translate.y * 2.0f;
        float unshiftedOriginX = originX;
        if (recipeOpen) {
            originX += 77.0f * scaleX;
        }
        KeyframeChannel<Transform> cursorLayout = useHudLayout ? hudData.cursorLayout : clipCursorLayout;
        float layoutTick = useHudLayout ? globalTick : localTick;
        Transform cursorTransform = (cursorLayout == null || cursorLayout.isEmpty() ? new Transform() : (Transform)cursorLayout.interpolate(layoutTick, new Transform())).copy();
        float curScreenX = (float)screenWidth / 2.0f + cursorTransform.translate.x * 2.0f;
        float curScreenY = (float)screenHeight / 2.0f - cursorTransform.translate.y * 2.0f;
        float cursorGuiX = (curScreenX - originX) / scaleX;
        float cursorGuiY = (curScreenY - originY) / scaleY;
        return new GuiRenderContext(matrices, batcher, replayKeyframes, clip, handData, hudData, globalTick, localTick, screenWidth, screenHeight, allowCursor, entry, guiId, transform, scaleX, scaleY, opacity, bgOpacity, originX, originY, unshiftedOriginX, recipeOpen, cursorVisible, cursorHasItem, isDragging, cursorStack, dragEncoded, curScreenX, curScreenY, cursorGuiX, cursorGuiY);
    }
}

