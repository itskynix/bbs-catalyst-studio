/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.UISection
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiRecipeBook;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiSlotSchema;
import mchorse.bbs_mod.camera.pov.actions.gui.GuiTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.gui.editor.UIGuiSlotEditor;
import mchorse.bbs_mod.camera.pov.actions.gui.editor.UIGuiTypeOverlayPanel;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;

public class UIGuiActionClip
extends UIPovActionClip<GuiPovActionClip> {
    public UIButton editKeyframes;
    public UIButton guiType;
    public UIKeyframeEditor keyframes;
    public UIElement guiKeyframesSection;
    public UISection guiSettingsSection;
    public UIGuiSlotEditor slotEditor;
    private boolean wasSlotSelected = false;
    private Keyframe<?> lastSelectedKeyframe = null;

    public UIGuiActionClip(GuiPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> (Integer)((GuiPovActionClip)this.clip).duration.get());
        this.slotEditor = new UIGuiSlotEditor((GuiPovActionClip)this.clip, this.editor);
        this.editKeyframes = new UIButton(IKey.constant((String)"Edit Keyframes"), button -> {
            this.updateKeyframeSheets();
            this.editor.embedView((UIElement)this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.guiType = new UIButton(L10n.lang("bbs.pov.actions.gui.type", "GUI Screen Type"), button -> {
            String currentId = ((GuiPovActionClip)this.clip).state.isEmpty() ? "inventory" : (String)((GuiPovActionClip)this.clip).state.get(0).getValue();
            UIOverlay.addOverlay((UIContext)this.getContext(), (UIOverlayPanel)new UIGuiTypeOverlayPanel(entry -> {
                this.editor.editMultiple(((GuiPovActionClip)this.clip).state, channel -> {
                    if (channel.isEmpty()) {
                        channel.insert(0.0f, entry.id);
                    } else {
                        channel.get(0).setValue(entry.id);
                    }
                });
                this.updateGuiTypeButton(entry.id);
                this.updateKeyframeSheets();
            }, currentId), (int)340, (int)360);
        });
    }

    private void updateKeyframeSheets() {
        KeyframeChannel<Boolean> craftingAnchor;
        String currentId = ((GuiPovActionClip)this.clip).state.isEmpty() ? "inventory" : (String)((GuiPovActionClip)this.clip).state.get(0).getValue();
        GuiSlotSchema schema = GuiSlotSchema.get(currentId);
        this.keyframes.view.removeAllSheets();
        float defaultDarkness = "gamemode_switcher".equals(currentId) ? 0.0f : 1.0f;
        this.keyframes.view.addSheet(new UIKeyframeSheet("gui_layout", IKey.constant((String)"GUI Layout"), 4891615, ((GuiPovActionClip)this.clip).getLayout(currentId), null).icon(Icons.LAYOUT).seed(Transform::new));
        this.keyframes.view.addSheet(new UIKeyframeSheet("gui_opacity", IKey.constant((String)"GUI Opacity"), 0xFFFFFF, ((GuiPovActionClip)this.clip).getOpacity(currentId), null).icon(Icons.COLOR).seed(() -> Float.valueOf(1.0f)));
        this.keyframes.view.addSheet(new UIKeyframeSheet("bg_opacity", IKey.constant((String)"Darkness Opacity"), 0x888888, ((GuiPovActionClip)this.clip).getDarknessOpacity(currentId), null).icon(Icons.SHAPES).seed(() -> Float.valueOf(defaultDarkness)));
        if ("donkey".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("gui_slot_saddle", IKey.constant((String)"Saddle"), 0x55FFFF, ((GuiPovActionClip)this.clip).getGuiSlot(currentId, "saddle"), null).icon(Icons.KEY_CAP));
            this.keyframes.view.addSheet(new UIKeyframeSheet("gui_slots", IKey.constant((String)"Donkey Slots"), 0x55FFFF, ((GuiPovActionClip)this.clip).getPrimarySlotAnchor(currentId), null).icon(Icons.KEY_CAP).seed(() -> true));
        } else if (schema.groupedSlots && !schema.slots.isEmpty()) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("gui_slots", IKey.constant((String)(GuiTypeEntry.findById((String)currentId).name + " Slots")), 0x55FFFF, ((GuiPovActionClip)this.clip).getPrimarySlotAnchor(currentId), null).icon(Icons.KEY_CAP).seed(() -> true));
        } else {
            int index = 0;
            for (GuiSlotSchema.Slot slot : schema.slots) {
                if (schema.isCraftingSlot(slot)) continue;
                this.keyframes.view.addSheet(new UIKeyframeSheet("gui_slot_" + slot.id(), IKey.constant((String)slot.label()), index++ % 2 == 0 ? 0x55FFFF : 0xFFAA55, ((GuiPovActionClip)this.clip).getGuiSlot(currentId, slot.id()), null).icon(Icons.KEY_CAP));
            }
        }
        if (("inventory".equals(currentId) || "crafting_table".equals(currentId)) && (craftingAnchor = ((GuiPovActionClip)this.clip).getCraftingSlotAnchor(currentId)) != null) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("crafting_grid", IKey.constant((String)("inventory".equals(currentId) ? "Crafting 2x2" : "Crafting 3x3")), 0xFFAA55, craftingAnchor, null).icon(Icons.KEY_CAP).seed(() -> true));
        }
        if (GuiRecipeBook.supports(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_open", IKey.constant((String)"Recipe Book"), 0x55CC88, ((GuiPovActionClip)this.clip).getRecipeOpen(currentId), null).icon(Icons.LOOKING).seed(() -> false));
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_showing", IKey.constant((String)"Showing Craftable"), 0x88DD55, ((GuiPovActionClip)this.clip).getRecipeShowing(currentId), null).icon(Icons.VISIBLE).seed(() -> false));
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_search", IKey.constant((String)"Recipe Search"), 0xFFFFFF, ((GuiPovActionClip)this.clip).getRecipeSearch(currentId), null).icon(Icons.SEARCH).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_search_focus", IKey.constant((String)"Recipe Search Focus"), 0xDDDDDD, ((GuiPovActionClip)this.clip).getRecipeSearchFocus(currentId), null).icon(Icons.POINTER).seed(() -> false));
            if (GuiRecipeBook.hasCategories(currentId)) {
                this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_category", IKey.constant((String)"Recipe Category"), 0xAA88FF, ((GuiPovActionClip)this.clip).getRecipeCategory(currentId), null).icon(Icons.LIST).seed(() -> 0));
            }
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_page", IKey.constant((String)"Recipe Page"), 0x88BBFF, ((GuiPovActionClip)this.clip).getRecipePage(currentId), null).icon(Icons.CURVES).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_selected", IKey.constant((String)"Selected Recipe"), 0xFFCC66, ((GuiPovActionClip)this.clip).getRecipeSelected(currentId), null).icon(Icons.KEY_CAP).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("recipe_button", IKey.constant((String)"Recipe Button"), 0x5599FF, ((GuiPovActionClip)this.clip).getRecipeButton(currentId), null).icon(Icons.POINTER).seed(() -> false));
        }
        if (GuiRecipeBook.isFurnace(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("furnace_lit", IKey.constant((String)"Furnace Fire"), 0xFF6622, ((GuiPovActionClip)this.clip).getFurnaceLit(currentId), null).icon(Icons.CURVES).seed(() -> Float.valueOf(0.0f)));
            this.keyframes.view.addSheet(new UIKeyframeSheet("furnace_cook", IKey.constant((String)"Cook Arrow"), 0xFFCC66, ((GuiPovActionClip)this.clip).getFurnaceCook(currentId), null).icon(Icons.CURVES).seed(() -> Float.valueOf(0.0f)));
        }
        if ("brewing_stand".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("brew_progress", IKey.constant((String)"Brew Arrow"), 0xFFFFFF, ((GuiPovActionClip)this.clip).getBrewProgress(currentId), null).icon(Icons.CURVES).seed(() -> Float.valueOf(0.0f)));
            this.keyframes.view.addSheet(new UIKeyframeSheet("brew_fuel", IKey.constant((String)"Fuel Bar"), 0xFFCC44, ((GuiPovActionClip)this.clip).getBrewFuel(currentId), null).icon(Icons.CURVES).seed(() -> Float.valueOf(0.0f)));
            this.keyframes.view.addSheet(new UIKeyframeSheet("brew_bubbles", IKey.constant((String)"Bubbles"), 0x88DDFF, ((GuiPovActionClip)this.clip).getBrewBubbles(currentId), null).icon(Icons.BUBBLE).seed(() -> false));
        }
        if ("anvil".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("anvil_name", IKey.constant((String)"Anvil Name"), 0xFFFFFF, ((GuiPovActionClip)this.clip).anvilName, null).icon(Icons.SEARCH).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("anvil_name_focus", IKey.constant((String)"Anvil Caret"), 0xDDDDDD, ((GuiPovActionClip)this.clip).anvilNameFocus, null).icon(Icons.POINTER).seed(() -> false));
            this.keyframes.view.addSheet(new UIKeyframeSheet("anvil_error", IKey.constant((String)"Anvil Cross"), 0xFF5555, ((GuiPovActionClip)this.clip).anvilError, null).icon(Icons.CLOSE).seed(() -> false));
        }
        if ("gamemode_switcher".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("gamemode_selection", IKey.constant((String)"Gamemode"), 0x55FFAA, ((GuiPovActionClip)this.clip).gamemodeSelection, null).icon(Icons.WRENCH).seed(() -> 0));
        }
        if ("beacon".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("beacon_level", IKey.constant((String)"Beacon Level"), 0x55FF88, ((GuiPovActionClip)this.clip).beaconLevel, null).icon(Icons.CURVES).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("beacon_primary", IKey.constant((String)"Primary Power"), 0x55FFAA, ((GuiPovActionClip)this.clip).beaconPrimary, null).icon(Icons.KEY_CAP).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("beacon_secondary", IKey.constant((String)"Secondary Power"), 0xFF5555, ((GuiPovActionClip)this.clip).beaconSecondary, null).icon(Icons.KEY_CAP).seed(() -> 0));
        }
        if ("creative_inventory".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("creative_tab", IKey.constant((String)"Creative Tab"), 0xAA88FF, ((GuiPovActionClip)this.clip).creativeTab, null).icon(Icons.LIST).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("creative_page", IKey.constant((String)"Creative Page"), 0x9988FF, ((GuiPovActionClip)this.clip).creativePage, null).icon(Icons.LIST).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("creative_row", IKey.constant((String)"Creative Scroll Row"), 0x88BBFF, ((GuiPovActionClip)this.clip).creativeRow, null).icon(Icons.CURVES).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("creative_search", IKey.constant((String)"Creative Search"), 0xFFFFFF, ((GuiPovActionClip)this.clip).creativeSearch, null).icon(Icons.SEARCH).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("creative_search_focus", IKey.constant((String)"Creative Search Focus"), 0xDDDDDD, ((GuiPovActionClip)this.clip).creativeSearchFocus, null).icon(Icons.POINTER).seed(() -> false));
        } else if ("loom".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("loom_row", IKey.constant((String)"Loom Scroll Row"), 0x88BBFF, ((GuiPovActionClip)this.clip).loomRow, null).icon(Icons.CURVES).seed(() -> 0));
        } else if ("stonecutter".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("stonecutter_row", IKey.constant((String)"Stonecutter Scroll Row"), 0x88BBFF, ((GuiPovActionClip)this.clip).stonecutterRow, null).icon(Icons.CURVES).seed(() -> 0));
        }
        if ("book".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_page", IKey.constant((String)"Book Page"), 0x88BBFF, ((GuiPovActionClip)this.clip).getBookPage(currentId), null).icon(Icons.LIST).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_pages", IKey.constant((String)"Book Pages"), 0xFFFFFF, ((GuiPovActionClip)this.clip).getBookPages(currentId), null).icon(Icons.SEARCH).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_writable", IKey.constant((String)"Writable"), 0x88CC88, ((GuiPovActionClip)this.clip).getBookWritable(currentId), null).icon(Icons.LOCKED).seed(() -> false));
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_signing", IKey.constant((String)"Signing"), 0xFFCC66, ((GuiPovActionClip)this.clip).getBookSigning(currentId), null).icon(Icons.KEY_CAP).seed(() -> false));
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_title", IKey.constant((String)"Book Title"), 0xDDDDDD, ((GuiPovActionClip)this.clip).getBookTitle(currentId), null).icon(Icons.SEARCH).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("book_author", IKey.constant((String)"Book Author"), 12887172, ((GuiPovActionClip)this.clip).getBookAuthor(currentId), null).icon(Icons.PLAYER).seed(() -> ""));
        }
        if ("donkey".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("donkey_chest", IKey.constant((String)"Donkey Chest"), 0xFFCC66, ((GuiPovActionClip)this.clip).getMountChest(currentId), null).icon(Icons.LOCKED).seed(() -> false));
        } else if ("horse".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("horse_variant", IKey.constant((String)"Horse Variant"), 12887172, ((GuiPovActionClip)this.clip).getHorseVariant(currentId), null).icon(Icons.PLAYER).seed(() -> 0));
        } else if ("villager".equals(currentId)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_profession", IKey.constant((String)"Profession"), 0x44AA44, ((GuiPovActionClip)this.clip).getMerchantProfession(currentId), null).icon(Icons.PLAYER).seed(() -> 1));
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_level", IKey.constant((String)"Villager Level"), 0xFFCC00, ((GuiPovActionClip)this.clip).getMerchantLevel(currentId), null).icon(Icons.MORE).seed(() -> 1));
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_experience", IKey.constant((String)"Villager XP"), 0x55FF55, ((GuiPovActionClip)this.clip).getMerchantExperience(currentId), null).icon(Icons.CURVES).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_can_level", IKey.constant((String)"Show XP Bar"), 0x88FF88, ((GuiPovActionClip)this.clip).getMerchantCanLevel(currentId), null).icon(Icons.VISIBLE).seed(() -> true));
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_selected", IKey.constant((String)"Selected Trade"), 0x4488FF, ((GuiPovActionClip)this.clip).getMerchantSelectedOffer(currentId), null).icon(Icons.POINTER).seed(() -> 0));
            this.keyframes.view.addSheet(new UIKeyframeSheet("merchant_scroll", IKey.constant((String)"Trade Scroll"), 0xAAAAAA, ((GuiPovActionClip)this.clip).getMerchantScrollOffset(currentId), null).icon(Icons.CURVES).seed(() -> 0));
        }
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.guiKeyframesSection = this.section(L10n.lang("bbs.pov.actions.gui.keyframes", "GUI Screen Keyframes"), new UIElement[]{this.editKeyframes});
        this.panels.add((IUIElement)this.guiKeyframesSection);
        this.guiSettingsSection = this.section(L10n.lang("bbs.pov.actions.gui.settings", "GUI Screen Settings"), new UIElement[]{this.guiType});
        this.panels.add((IUIElement)this.guiSettingsSection);
        this.slotEditor.full((UIElement)this);
        this.slotEditor.setVisible(false);
        this.add((IUIElement)this.slotEditor);
    }

    @Override
    public void fillData() {
        super.fillData();
        String currentId = ((GuiPovActionClip)this.clip).state.isEmpty() ? "inventory" : (String)((GuiPovActionClip)this.clip).state.get(0).getValue();
        this.updateGuiTypeButton(currentId);
        this.updateKeyframeSheets();
        this.wasSlotSelected = false;
        if (this.panels != null) {
            this.panels.setVisible(true);
        }
        if (this.slotEditor != null) {
            this.slotEditor.setVisible(false);
        }
        this.updateSelectedKeyframeView();
    }

    public void render(UIContext context) {
        this.updateSelectedKeyframeView();
        super.render(context);
    }

    private void updateSelectedKeyframeView() {
        boolean inKeyframeEditor;
        Keyframe selected = null;
        UIKeyframeSheet selectedSheet = null;
        boolean bl = inKeyframeEditor = this.keyframes != null && this.keyframes.hasParent();
        if (inKeyframeEditor && this.keyframes.view != null) {
            if (this.keyframes.view.getGraph() != null && (selected = this.keyframes.view.getGraph().getSelected()) != null) {
                selectedSheet = this.keyframes.view.getGraph().getSheet(selected);
            }
            if (selected == null && this.keyframes.view.getDopeSheet() != null && (selected = this.keyframes.view.getDopeSheet().getSelected()) != null) {
                selectedSheet = this.keyframes.view.getDopeSheet().getSheet(selected);
            }
        } else if (!inKeyframeEditor && this.keyframes != null && this.keyframes.view != null) {
            if (this.keyframes.view.getGraph() != null && this.keyframes.view.getGraph().getSelected() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
            if (this.keyframes.view.getDopeSheet() != null && this.keyframes.view.getDopeSheet().getSelected() != null) {
                this.keyframes.view.getDopeSheet().clearSelection();
            }
        }
        String currentId = ((GuiPovActionClip)this.clip).state.isEmpty() ? "inventory" : (String)((GuiPovActionClip)this.clip).state.get(0).getValue();
        GuiSlotSchema schema = GuiSlotSchema.get(currentId);
        boolean isLargeSlotKeyframe = false;
        if (selected != null && selectedSheet != null) {
            String id;
            String string = id = selectedSheet.id != null ? selectedSheet.id : "";
            if ("gui_slots".equals(id) && schema.getGroupedSlots().size() > 9) {
                isLargeSlotKeyframe = true;
            }
        }
        if (isLargeSlotKeyframe) {
            if (this.lastSelectedKeyframe != selected) {
                this.lastSelectedKeyframe = selected;
                this.slotEditor.configure(currentId, UIGuiSlotEditor.Mode.GUI, selected.getTick());
            }
        } else {
            this.lastSelectedKeyframe = null;
        }
        if (this.wasSlotSelected != isLargeSlotKeyframe) {
            this.wasSlotSelected = isLargeSlotKeyframe;
            if (this.panels != null) {
                this.panels.setVisible(!isLargeSlotKeyframe);
            }
            if (this.slotEditor != null) {
                this.slotEditor.setVisible(isLargeSlotKeyframe);
            }
            this.resize();
        }
    }

    private void updateGuiTypeButton(String id) {
        GuiTypeEntry entry = GuiTypeEntry.findById(id);
        String name = entry == null ? (id == null || id.isBlank() ? "None" : id) : entry.name;
        this.guiType.label = IKey.constant((String)("GUI: " + name));
    }
}

