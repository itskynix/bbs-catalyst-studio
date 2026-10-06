/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Recorder
 *  mchorse.bbs_mod.utils.interps.Interpolations
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.client.gui.screen.GameModeSelectionScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.ingame.BookEditScreen
 *  net.minecraft.client.gui.screen.ingame.BookScreen
 *  net.minecraft.client.gui.screen.ingame.HandledScreen
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions.gui.recording;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiCapture;
import mchorse.bbs_mod.camera.pov.actions.gui.data.GuiSnapshot;
import mchorse.bbs_mod.camera.pov.actions.gui.recording.GuiSnapshotCapture;
import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import java.util.List;
import java.util.Objects;
import mchorse.bbs_mod.film.Recorder;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.item.ItemStack;

public final class GuiRecorder {
    private GuiPovActionClip recordingGuiClip;
    private float lastRecordedCurTx = Float.NaN;
    private float lastRecordedCurTy = Float.NaN;
    private float lastCursorKeyTick = Float.NaN;
    private boolean cursorWasMoving;

    public void reset() {
        this.recordingGuiClip = null;
        this.lastRecordedCurTx = Float.NaN;
        this.lastRecordedCurTy = Float.NaN;
        this.lastCursorKeyTick = Float.NaN;
        this.cursorWasMoving = false;
    }

    public void finish(ReplayKeyframesPovAccess access, int tick) {
        this.finalizeGuiClip(access, tick);
    }

    public void record(ReplayKeyframesPovAccess access, Recorder recorder) {
        if (!PovSettings.isBakeActions()) {
            return;
        }
        if (recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        Screen screen = MinecraftClient.getInstance().currentScreen;
        if (screen instanceof BookEditScreen || screen instanceof BookScreen) {
            this.recordBookGui(access, recorder, screen);
        } else if (screen instanceof GameModeSelectionScreen) {
            this.recordGamemodeSwitcherGui(access, recorder, screen);
        } else if (screen instanceof HandledScreen) {
            String recordingType;
            HandledScreen handled = (HandledScreen)screen;
            MinecraftClient mc = MinecraftClient.getInstance();
            GuiCapture captured = GuiSnapshotCapture.captureOrInventory(handled, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
            GuiSnapshot snapshot = captured.snapshot;
            String guiType = snapshot.guiType;
            String string = recordingType = this.recordingGuiClip == null || this.recordingGuiClip.state.isEmpty() ? null : (String)this.recordingGuiClip.state.get(0).getValue();
            if (recordingType != null && !recordingType.equals(guiType)) {
                this.finalizeGuiClip(access, recorder.tick);
            }
            if (this.recordingGuiClip == null) {
                RecordedHudData hotbar;
                this.recordingGuiClip = (GuiPovActionClip)access.bbsPov$getActions().add(PovActionType.GUI, recorder.tick, 1);
                this.recordingGuiClip.state.insert(0.0f, guiType);
                RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
                if (hotbar != null) {
                    hotbar.cursorVisible.insert((float)recorder.tick, true);
                }
                this.recordingGuiClip.getCursorVisible(guiType).insert(0.0f, true);
            }
            float localTick = recorder.tick - (Integer)this.recordingGuiClip.tick.get();
            this.recordingGuiClip.duration.set(Math.max(1, (int)localTick + 1));
            this.recordCapturedExtras(captured, guiType, localTick);
            this.recordCursorMotion(access, recorder, guiType, snapshot, 0.0f);
            this.recordValueChannel(this.recordingGuiClip.getDragSlots(guiType), snapshot.dragging ? snapshot.dragEncoded : "", localTick);
            RecordedHudData hotbar = access.bbsPov$getHud();
            if (hotbar != null) {
                this.recordItemChannel(hotbar.cursorItem, snapshot.cursorItem, recorder.tick);
            }
            this.recordItemChannel(this.recordingGuiClip.getCursorItem(guiType), snapshot.cursorItem, localTick);
            GuiSlotSchema schema = GuiSlotSchema.get(guiType);
            boolean creativeInventoryTab = "creative_inventory".equals(guiType) && captured.creative != null && captured.creative.inventoryTab;
            boolean captureSlots = !"creative_inventory".equals(guiType) || creativeInventoryTab;
            boolean primaryChanged = false;
            boolean craftingChanged = false;
            boolean recordChest = !"donkey".equals(guiType) || this.recordingGuiClip.isMountChestOpen(guiType, localTick);
            for (GuiSlotSchema.Slot slot : captureSlots ? schema.slots : List.<GuiSlotSchema.Slot>of()) {
                if (schema.isChestSlot(slot) && !recordChest) continue;
                ItemStack stack = snapshot.slots.getOrDefault(slot.id(), ItemStack.EMPTY);
                boolean changed = this.recordItemChannel(this.recordingGuiClip.getGuiSlot(guiType, slot.id()), stack, localTick);
                if (schema.isCraftingSlot(slot)) {
                    craftingChanged |= changed;
                    continue;
                }
                if (schema.groupedSlots && schema.isEquipmentSlot(slot)) continue;
                primaryChanged |= changed;
            }
            if (primaryChanged) {
                this.insertAnchor(this.recordingGuiClip.getPrimarySlotAnchor(guiType), localTick);
            }
            if (craftingChanged) {
                this.insertAnchor(this.recordingGuiClip.getCraftingSlotAnchor(guiType), localTick);
            }
        } else {
            this.finalizeGuiClip(access, recorder.tick);
        }
    }

    private void finalizeGuiClip(ReplayKeyframesPovAccess access, int tick) {
        RecordedHudData hotbar;
        if (this.recordingGuiClip == null) {
            return;
        }
        String finalGuiType = this.recordingGuiClip.state.isEmpty() ? "inventory" : (String)this.recordingGuiClip.state.get(0).getValue();
        float localEndTick = tick - (Integer)this.recordingGuiClip.tick.get();
        if (!Float.isNaN(this.lastRecordedCurTx) && !Float.isNaN(this.lastCursorKeyTick)) {
            this.insertCursorKey(this.recordingGuiClip.getCursorLayout(finalGuiType), localEndTick, this.lastRecordedCurTx, this.lastRecordedCurTy);
        }
        this.recordingGuiClip.getCursorVisible(finalGuiType).insert(localEndTick, false);
        this.recordingGuiClip.getCursorItem(finalGuiType).insert(localEndTick, ItemStack.EMPTY);
        RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
        if (hotbar != null) {
            float absTick = tick;
            if (!Float.isNaN(this.lastRecordedCurTx) && !Float.isNaN(this.lastCursorKeyTick) && absTick > this.lastCursorKeyTick) {
                this.insertCursorKey(hotbar.cursorLayout, absTick, this.lastRecordedCurTx, this.lastRecordedCurTy);
            }
            if (MinecraftClient.getInstance().currentScreen == null) {
                hotbar.cursorVisible.insert(absTick, false);
            }
            hotbar.cursorItem.insert(absTick, ItemStack.EMPTY);
        }
        this.recordingGuiClip.ensureBakingBounds();
        this.recordingGuiClip = null;
        this.lastRecordedCurTx = Float.NaN;
        this.lastRecordedCurTy = Float.NaN;
        this.lastCursorKeyTick = Float.NaN;
        GuiSnapshotCapture.resetScreenCaches();
    }

    private void recordGamemodeSwitcherGui(ReplayKeyframesPovAccess access, Recorder recorder, Screen screen) {
        String recordingType;
        String guiType = "gamemode_switcher";
        String string = recordingType = this.recordingGuiClip == null || this.recordingGuiClip.state.isEmpty() ? null : (String)this.recordingGuiClip.state.get(0).getValue();
        if (recordingType != null && !recordingType.equals(guiType)) {
            this.finalizeGuiClip(access, recorder.tick);
        }
        if (this.recordingGuiClip == null) {
            RecordedHudData hotbar;
            this.recordingGuiClip = (GuiPovActionClip)access.bbsPov$getActions().add(PovActionType.GUI, recorder.tick, 1);
            this.recordingGuiClip.state.insert(0.0f, guiType);
            this.recordingGuiClip.getDarknessOpacity(guiType).insert(0.0f, Float.valueOf(0.0f));
            RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
            if (hotbar != null) {
                hotbar.cursorVisible.insert((float)recorder.tick, true);
            }
            this.recordingGuiClip.getCursorVisible(guiType).insert(0.0f, true);
        }
        float localTick = recorder.tick - (Integer)this.recordingGuiClip.tick.get();
        this.recordingGuiClip.duration.set(Math.max(1, (int)localTick + 1));
        MinecraftClient mc = MinecraftClient.getInstance();
        GuiCapture captured = GuiSnapshotCapture.capture(screen, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        int selected = captured != null && captured.gamemode != null ? captured.gamemode.selection : 0;
        GuiSnapshot snapshot = captured == null ? null : captured.snapshot;
        this.recordValueChannel(this.recordingGuiClip.gamemodeSelection, selected, localTick);
        this.recordCursorMotion(access, recorder, guiType, snapshot, 0.0f);
        this.recordItemChannel(this.recordingGuiClip.getCursorItem(guiType), ItemStack.EMPTY, localTick);
        RecordedHudData hotbar = access.bbsPov$getHud();
        if (hotbar != null) {
            this.recordItemChannel(hotbar.cursorItem, ItemStack.EMPTY, recorder.tick);
        }
    }

    private void recordBookGui(ReplayKeyframesPovAccess access, Recorder recorder, Screen screen) {
        String recordingType;
        String guiType = "book";
        String string = recordingType = this.recordingGuiClip == null || this.recordingGuiClip.state.isEmpty() ? null : (String)this.recordingGuiClip.state.get(0).getValue();
        if (recordingType != null && !recordingType.equals(guiType)) {
            this.finalizeGuiClip(access, recorder.tick);
        }
        if (this.recordingGuiClip == null) {
            RecordedHudData hotbar;
            this.recordingGuiClip = (GuiPovActionClip)access.bbsPov$getActions().add(PovActionType.GUI, recorder.tick, 1);
            this.recordingGuiClip.state.insert(0.0f, guiType);
            RecordedHudData recordedHudData = hotbar = access != null ? access.bbsPov$getHud() : null;
            if (hotbar != null) {
                hotbar.cursorVisible.insert((float)recorder.tick, true);
            }
            this.recordingGuiClip.getCursorVisible(guiType).insert(0.0f, true);
        }
        float localTick = recorder.tick - (Integer)this.recordingGuiClip.tick.get();
        this.recordingGuiClip.duration.set(Math.max(1, (int)localTick + 1));
        MinecraftClient mc = MinecraftClient.getInstance();
        GuiCapture captured = GuiSnapshotCapture.capture(screen, mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
        this.recordCapturedExtras(captured, guiType, localTick);
        this.recordCursorMotion(access, recorder, guiType, captured == null ? null : captured.snapshot, 0.0f);
        this.recordItemChannel(this.recordingGuiClip.getCursorItem(guiType), ItemStack.EMPTY, localTick);
        RecordedHudData hotbar = access.bbsPov$getHud();
        if (hotbar != null) {
            this.recordItemChannel(hotbar.cursorItem, ItemStack.EMPTY, recorder.tick);
        }
    }

    private void recordCapturedExtras(GuiCapture captured, String guiType, float localTick) {
        if (captured == null || this.recordingGuiClip == null) {
            return;
        }
        if (captured.anvil != null) {
            this.recordValueChannel(this.recordingGuiClip.anvilName, captured.anvil.name, localTick);
            this.recordValueChannel(this.recordingGuiClip.anvilNameFocus, captured.anvil.focused, localTick);
            this.recordValueChannel(this.recordingGuiClip.anvilNameSelStart, captured.anvil.selStart, localTick);
            this.recordValueChannel(this.recordingGuiClip.anvilNameSelEnd, captured.anvil.selEnd, localTick);
            this.recordValueChannel(this.recordingGuiClip.anvilError, captured.anvil.error, localTick);
        }
        if (captured.creative != null) {
            this.recordValueChannel(this.recordingGuiClip.creativeTab, captured.creative.tab, localTick);
            this.recordValueChannel(this.recordingGuiClip.creativePage, captured.creative.page, localTick);
            this.recordHeldRow(this.recordingGuiClip.creativeRow, captured.creative.row, localTick);
            this.recordValueChannel(this.recordingGuiClip.creativeSearch, captured.creative.search, localTick);
            this.recordValueChannel(this.recordingGuiClip.creativeSearchFocus, captured.creative.searchFocused, localTick);
            this.recordValueChannel(this.recordingGuiClip.creativeSearchSelStart, captured.creative.searchSelStart, localTick);
            this.recordValueChannel(this.recordingGuiClip.creativeSearchSelEnd, captured.creative.searchSelEnd, localTick);
        }
        if (captured.loom != null) {
            this.recordHeldRow(this.recordingGuiClip.loomRow, captured.loom.row, localTick);
        }
        if (captured.stonecutter != null) {
            this.recordHeldRow(this.recordingGuiClip.stonecutterRow, captured.stonecutter.row, localTick);
        }
        if (captured.enchantment != null) {
            this.recordValueChannel(this.recordingGuiClip.enchantOffers, captured.enchantment.offers, localTick);
            this.recordValueChannel(this.recordingGuiClip.enchantSeed, captured.enchantment.seed, localTick);
            this.recordValueChannel(this.recordingGuiClip.enchantPlayerLevel, captured.enchantment.playerLevel, localTick);
            this.recordValueChannel(this.recordingGuiClip.enchantCreative, captured.enchantment.creative, localTick);
            this.recordValueChannel(this.recordingGuiClip.enchantBookOpen, Float.valueOf(captured.enchantment.bookOpen), localTick);
        }
        if (captured.beacon != null) {
            this.recordValueChannel(this.recordingGuiClip.beaconPrimary, captured.beacon.primary, localTick);
            this.recordValueChannel(this.recordingGuiClip.beaconSecondary, captured.beacon.secondary, localTick);
            this.recordValueChannel(this.recordingGuiClip.beaconLevel, captured.beacon.level, localTick);
        }
        if (captured.furnace != null) {
            this.recordValueChannel(this.recordingGuiClip.getFurnaceLit(guiType), Float.valueOf(captured.furnace.lit), localTick);
            this.recordValueChannel(this.recordingGuiClip.getFurnaceCook(guiType), Float.valueOf(captured.furnace.cook), localTick);
        }
        if (captured.mount != null) {
            if ("horse".equals(guiType)) {
                this.recordValueChannel(this.recordingGuiClip.getHorseVariant(guiType), captured.mount.horseVariant, localTick);
            }
            if ("donkey".equals(guiType)) {
                this.recordValueChannel(this.recordingGuiClip.getMountChest(guiType), captured.mount.chestOpen, localTick);
            }
        }
        if (captured.brewing != null) {
            this.recordValueChannel(this.recordingGuiClip.getBrewProgress(guiType), Float.valueOf(captured.brewing.progress), localTick);
            this.recordValueChannel(this.recordingGuiClip.getBrewFuel(guiType), Float.valueOf(captured.brewing.fuel), localTick);
            this.recordValueChannel(this.recordingGuiClip.getBrewBubbles(guiType), captured.brewing.bubbles, localTick);
        }
        if (captured.merchant != null) {
            this.recordValueChannel(this.recordingGuiClip.getMerchantOffers(guiType), captured.merchant.offers, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantProfession(guiType), captured.merchant.profession, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantLevel(guiType), captured.merchant.level, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantExperience(guiType), captured.merchant.experience, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantSelectedOffer(guiType), captured.merchant.selectedOffer, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantScrollOffset(guiType), captured.merchant.scrollOffset, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantTitle(guiType), captured.merchant.title, localTick);
            this.recordValueChannel(this.recordingGuiClip.getMerchantCanLevel(guiType), captured.merchant.canLevel, localTick);
        }
        if (captured.recipeBook != null && GuiRecipeBook.supports(guiType)) {
            this.recordValueChannel(this.recordingGuiClip.getRecipeOpen(guiType), captured.recipeBook.open, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeSearch(guiType), captured.recipeBook.search, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeSearchFocus(guiType), captured.recipeBook.searchFocused, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeSearchSelStart(guiType), captured.recipeBook.searchSelStart, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeSearchSelEnd(guiType), captured.recipeBook.searchSelEnd, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeShowing(guiType), captured.recipeBook.showing, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipePage(guiType), captured.recipeBook.page, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeButton(guiType), captured.recipeBook.buttonSelected, localTick);
            this.recordValueChannel(this.recordingGuiClip.getRecipeSelected(guiType), captured.recipeBook.selected, localTick);
            if (GuiRecipeBook.hasCategories(guiType)) {
                this.recordValueChannel(this.recordingGuiClip.getRecipeCategory(guiType), captured.recipeBook.category, localTick);
            }
        }
        if (captured.book != null) {
            this.recordValueChannel(this.recordingGuiClip.getBookWritable(guiType), captured.book.writable, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookSigning(guiType), captured.book.signing, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookPage(guiType), captured.book.page, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookPages(guiType), captured.book.pages, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookTitle(guiType), captured.book.title, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookAuthor(guiType), captured.book.author, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookSelStart(guiType), captured.book.selStart, localTick);
            this.recordValueChannel(this.recordingGuiClip.getBookSelEnd(guiType), captured.book.selEnd, localTick);
        }
    }

    public void sampleCursor(ReplayKeyframesPovAccess access, Recorder recorder, float tickDelta) {
        if (this.recordingGuiClip == null || recorder.hasNotStarted() || recorder.tick < 0) {
            return;
        }
        String guiType = this.recordingGuiClip.state.isEmpty() ? "inventory" : (String)this.recordingGuiClip.state.get(0).getValue();
        this.recordCursorMotion(access, recorder, guiType, null, tickDelta);
    }

    private void recordCursorMotion(ReplayKeyframesPovAccess access, Recorder recorder, String guiType, GuiSnapshot snapshot, float tickDelta) {
        boolean posChanged;
        float curTy;
        float curTx;
        if (!PovSettings.isBakeActions()) {
            return;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (snapshot != null) {
            curTx = snapshot.cursorTx;
            curTy = snapshot.cursorTy;
        } else {
            double mouseX = mc.mouse.getX() * (double)mc.getWindow().getScaledWidth() / (double)mc.getWindow().getWidth();
            double mouseY = mc.mouse.getY() * (double)mc.getWindow().getScaledHeight() / (double)mc.getWindow().getHeight();
            double screenCenterX = (double)mc.getWindow().getScaledWidth() / 2.0;
            double screenCenterY = (double)mc.getWindow().getScaledHeight() / 2.0;
            curTx = (float)((mouseX - screenCenterX) / 2.0);
            curTy = (float)((screenCenterY - mouseY) / 2.0);
        }
        float fraction = Math.max(0.0f, Math.min(1.0f, tickDelta));
        float absTick = (float)recorder.tick + fraction;
        float localTick = absTick - (float)((Integer)this.recordingGuiClip.tick.get()).intValue();
        RecordedHudData hotbar = access != null ? access.bbsPov$getHud() : null;
        KeyframeChannel<Transform> clipCursorLayout = this.recordingGuiClip.getCursorLayout(guiType);
        KeyframeChannel<Transform> hudCursorLayout = hotbar != null ? hotbar.cursorLayout : null;
        boolean bl = posChanged = Math.abs(curTx - this.lastRecordedCurTx) > 0.005f || Math.abs(curTy - this.lastRecordedCurTy) > 0.005f;
        if (Float.isNaN(this.lastCursorKeyTick)) {
            if (hotbar != null) {
                hotbar.cursorVisible.insert(absTick, true);
            }
            this.recordingGuiClip.getCursorVisible(guiType).insert(localTick, true);
            if (hudCursorLayout != null && !hudCursorLayout.getKeyframes().isEmpty()) {
                int lastIdx = hudCursorLayout.getKeyframes().size() - 1;
                ((Keyframe)hudCursorLayout.getKeyframes().get(lastIdx)).getInterpolation().setInterp(Interpolations.CONST);
            }
            this.insertCursorKey(clipCursorLayout, localTick, curTx, curTy);
            if (hudCursorLayout != null) {
                this.insertCursorKey(hudCursorLayout, absTick, curTx, curTy);
            }
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
            this.cursorWasMoving = false;
        } else if (posChanged) {
            if (absTick > this.lastCursorKeyTick + 0.05f) {
                if (absTick - this.lastCursorKeyTick > 0.5f && !this.cursorWasMoving) {
                    this.insertCursorKey(clipCursorLayout, localTick - 0.05f, this.lastRecordedCurTx, this.lastRecordedCurTy);
                    if (hudCursorLayout != null) {
                        this.insertCursorKey(hudCursorLayout, absTick - 0.05f, this.lastRecordedCurTx, this.lastRecordedCurTy);
                    }
                }
                this.insertCursorKey(clipCursorLayout, localTick, curTx, curTy);
                if (hudCursorLayout != null) {
                    this.insertCursorKey(hudCursorLayout, absTick, curTx, curTy);
                }
                this.lastRecordedCurTx = curTx;
                this.lastRecordedCurTy = curTy;
                this.lastCursorKeyTick = absTick;
                this.cursorWasMoving = true;
            }
        } else if (this.cursorWasMoving) {
            this.insertCursorKey(clipCursorLayout, localTick, curTx, curTy);
            if (hudCursorLayout != null) {
                this.insertCursorKey(hudCursorLayout, absTick, curTx, curTy);
            }
            this.lastRecordedCurTx = curTx;
            this.lastRecordedCurTy = curTy;
            this.lastCursorKeyTick = absTick;
            this.cursorWasMoving = false;
        }
    }

    private boolean recordItemChannel(KeyframeChannel<ItemStack> channel, ItemStack stack, float tick) {
        ItemStack current;
        if (channel == null) {
            return false;
        }
        ItemStack itemStack = current = stack == null ? ItemStack.EMPTY : stack.copy();
        if (channel.isEmpty()) {
            channel.insert(0.0f, current);
            return true;
        }
        List keyframes = channel.getKeyframes();
        ItemStack previous = (ItemStack)((Keyframe)keyframes.get(keyframes.size() - 1)).getValue();
        if (!ItemStack.areEqual((ItemStack)previous, (ItemStack)current)) {
            channel.insert(tick, current);
            return true;
        }
        return false;
    }

    private void insertAnchor(KeyframeChannel<Boolean> anchor, float tick) {
        if (anchor != null) {
            anchor.insert(tick, true);
        }
    }

    private <T> void recordValueChannel(KeyframeChannel<T> channel, T value, float tick) {
        if (channel == null) {
            return;
        }
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
            return;
        }
        List<Keyframe<T>> keyframes = channel.getKeyframes();
        Keyframe<T> prevKey = keyframes.get(keyframes.size() - 1);
        T previous = prevKey.getValue();
        if (!Objects.equals(previous, value)) {
            if (tick - prevKey.getTick() > 1.0f) {
                channel.insert(tick - 1.0f, previous);
            }
            channel.insert(tick, value);
        }
    }

    private void recordHeldRow(KeyframeChannel<Integer> channel, int row, float tick) {
        if (channel.isEmpty()) {
            channel.insert(0.0f, row);
            return;
        }
        List keyframes = channel.getKeyframes();
        Keyframe previous = (Keyframe)keyframes.get(keyframes.size() - 1);
        if ((Integer)previous.getValue() != row) {
            if (tick - previous.getTick() > 1.0f) {
                channel.insert(tick - 1.0f, ((Integer)previous.getValue()));
            }
            channel.insert(tick, row);
        }
    }

    private void insertCursorKey(KeyframeChannel<Transform> cursorLayout, float tick, float tx, float ty) {
        if (cursorLayout == null) {
            return;
        }
        Transform transform = new Transform();
        transform.translate.set(tx, ty, 0.0f);
        int index = cursorLayout.insert(tick, transform);
        if (index >= 0 && index < cursorLayout.getKeyframes().size()) {
            ((Keyframe)cursorLayout.getKeyframes().get(index)).getInterpolation().setInterp(Interpolations.LINEAR);
        }
    }
}

