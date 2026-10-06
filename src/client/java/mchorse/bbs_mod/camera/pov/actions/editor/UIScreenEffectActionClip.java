/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.blaze3d.systems.RenderSystem
 *  mchorse.bbs_mod.l10n.keys.IKey
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes
 *  mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.UIElement
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon
 *  mchorse.bbs_mod.ui.framework.elements.input.UIColor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet$Section
 *  mchorse.bbs_mod.ui.framework.elements.input.list.UIList
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay
 *  mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer
 *  mchorse.bbs_mod.ui.framework.elements.utils.RowStyle
 *  mchorse.bbs_mod.ui.utils.UI
 *  mchorse.bbs_mod.ui.utils.context.ContextAction
 *  mchorse.bbs_mod.ui.utils.context.ContextMenuManager
 *  mchorse.bbs_mod.ui.utils.context.MenuIcon
 *  mchorse.bbs_mod.ui.utils.context.MenuVerb
 *  mchorse.bbs_mod.ui.utils.icons.Icon
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.colors.Color
 *  net.minecraft.client.gui.DrawContext
 *  net.minecraft.item.Item
 *  net.minecraft.item.ItemConvertible
 *  net.minecraft.item.ItemStack
 *  net.minecraft.item.Items
 *  net.minecraft.registry.Registries
 *  net.minecraft.util.Identifier
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectEntry;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresetEntry;
import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresets;
import com.mojang.blaze3d.systems.RenderSystem;
import java.util.List;
import java.util.function.Consumer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UIUnifiedPickOverlayPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.RowStyle;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.context.ContextAction;
import mchorse.bbs_mod.ui.utils.context.ContextMenuManager;
import mchorse.bbs_mod.ui.utils.context.MenuIcon;
import mchorse.bbs_mod.ui.utils.context.MenuVerb;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

public class UIScreenEffectActionClip
extends UIPovActionClip<ScreenEffectPovActionClip> {
    public static ScreenEffectPovActionClip currentClip;
    public static UIScreenEffectActionClip currentPanel;
    public UIScreenEffectList effectsList;
    public UIButton editKeyframes;
    public UIKeyframeEditor keyframes;
    public UIButton pickTotemItem;
    public UIIcon clearTotemItem;
    public UIElement totemSection;
    public UIColor effectColor;
    public UIElement colorSection;
    private ScreenEffectEntry selected;

    public UIScreenEffectActionClip(ScreenEffectPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
        currentClip = clip;
        currentPanel = this;
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        currentClip = (ScreenEffectPovActionClip)this.clip;
        currentPanel = this;
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.full((UIElement)this.keyframes).w(1.0f);
        this.keyframes.view.duration(() -> (Integer)((ScreenEffectPovActionClip)this.clip).duration.get());
        this.editKeyframes = new UIButton(IKey.constant((String)"Edit Keyframes"), button -> {
            currentClip = (ScreenEffectPovActionClip)this.clip;
            currentPanel = this;
            this.updateKeyframeSheets();
            this.editor.embedView((UIElement)this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.pickTotemItem = new UIButton(IKey.constant((String)"Pick Item"), b -> this.openTotemItemPicker());
        this.pickTotemItem.tooltip(IKey.constant((String)"Select item to display during totem popup"));
        this.pickTotemItem.h(20);
        this.clearTotemItem = new UIIcon(Icons.CLOSE, b -> {
            ((ScreenEffectPovActionClip)this.clip).totemItem.set("minecraft:totem_of_undying");
            this.fillData();
            this.editor.fillData();
        });
        this.clearTotemItem.tooltip(IKey.constant((String)"Reset to default Totem of Undying"));
        this.clearTotemItem.wh(20, 20);
        this.totemSection = UI.column((UIElement[])new UIElement[]{UI.label((IKey)IKey.constant((String)"Totem Item")), UI.row((UIElement[])new UIElement[]{this.pickTotemItem, this.clearTotemItem})});
        this.effectColor = new UIColor(color -> {
            if (this.selected == null) {
                return;
            }
            String effectId = this.selected.getEffectId().toLowerCase();
            Color c = Color.rgb((int)color);
            switch (effectId) {
                case "fire": {
                    ((ScreenEffectPovActionClip)this.clip).fireColor.set(c);
                    break;
                }
                case "portal": {
                    ((ScreenEffectPovActionClip)this.clip).portalColor.set(c);
                    break;
                }
                case "frost": {
                    ((ScreenEffectPovActionClip)this.clip).frostColor.set(c);
                    break;
                }
                case "underwater": {
                    ((ScreenEffectPovActionClip)this.clip).underwaterColor.set(c);
                }
            }
            this.editor.fillData();
        });
        this.colorSection = UI.column((UIElement[])new UIElement[]{UI.label((IKey)IKey.constant((String)"Tint Color")), this.effectColor});
        this.effectsList = new UIScreenEffectList(this, list -> {
            List current = this.effectsList.getCurrent();
            this.selected = current == null || current.isEmpty() ? null : (ScreenEffectEntry)current.get(0);
            this.updateDetailSections();
        });
        this.effectsList.h(160);
    }

    public void updateKeyframeSheets() {
        this.keyframes.view.removeAllSheets();
        List<String> activeEffects = ((ScreenEffectPovActionClip)this.clip).getActiveEffectList();
        int colorIdx = 0;
        for (String effectId : activeEffects) {
            ScreenEffectPresetEntry preset = ScreenEffectPresets.getById(effectId);
            String title = preset != null ? preset.name : effectId.toUpperCase();
            int folderColor = UIKeyframeEditor.COLORS[colorIdx++ % UIKeyframeEditor.COLORS.length];
            Icon sectionIcon = switch (effectId.toLowerCase()) {
                case "vignette" -> Icons.OUTLINE_SPHERE;
                case "night_vision" -> Icons.LIGHT;
                case "blindness" -> Icons.INVISIBLE;
                case "darkness" -> Icons.SPHERE;
                case "spyglass" -> Icons.LOOKING;
                case "frost" -> Icons.SNOWFLAKE;
                case "portal" -> Icons.MAZE;
                case "fire" -> Icons.SUN;
                case "pumpkin" -> Icons.HEART_ALT;
                case "suffocation" -> Icons.BLOCK;
                case "totem" -> Icons.HEART;
                case "underwater" -> Icons.DROP;
                case "nausea" -> Icons.BUBBLE;
                default -> Icons.IMAGE;
            };
            UIKeyframeSheet.Section section = new UIKeyframeSheet.Section("screen_effect/" + effectId, IKey.constant((String)title), sectionIcon, folderColor);
            switch (effectId.toLowerCase()) {
                case "vignette": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).vignetteVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Opacity"), 0xFFFFFF, ((ScreenEffectPovActionClip)this.clip).vignetteOpacity, null).icon(Icons.FADING));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_color", IKey.constant((String)"Tint Color"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).vignetteColor, null).icon(Icons.COLOR).seed(Color::new));
                    break;
                }
                case "night_vision": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).nightVisionVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Brightness / Opacity"), 0xFFFF55, ((ScreenEffectPovActionClip)this.clip).nightVisionOpacity, null).icon(Icons.LIGHT));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_flash", IKey.constant((String)"Flash"), 0xFFFFFF, ((ScreenEffectPovActionClip)this.clip).nightVisionFlash, null).icon(Icons.FADING));
                    break;
                }
                case "blindness": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).blindnessVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Blindness Opacity"), 0x333333, ((ScreenEffectPovActionClip)this.clip).blindnessOpacity, null).icon(Icons.FADING));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_radius", IKey.constant((String)"Circle Radius (Blocks)"), 0x555555, ((ScreenEffectPovActionClip)this.clip).blindnessRadius, null).icon(Icons.SPHERE));
                    break;
                }
                case "darkness": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).darknessVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Darkness Opacity / Pulse"), 0x333333, ((ScreenEffectPovActionClip)this.clip).darknessOpacity, null).icon(Icons.FADING));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_radius", IKey.constant((String)"Circle Radius (Blocks)"), 0x555555, ((ScreenEffectPovActionClip)this.clip).darknessRadius, null).icon(Icons.SPHERE));
                    break;
                }
                case "spyglass": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).spyglassVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_scale", IKey.constant((String)"Spyglass Scale"), 0x55FFFF, ((ScreenEffectPovActionClip)this.clip).spyglassScale, null).icon(Icons.SCALE).seed(() -> Float.valueOf(1.12f)));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_zoom", IKey.constant((String)"Zoom"), 0xFFFF55, ((ScreenEffectPovActionClip)this.clip).spyglassZoom, null).icon(Icons.SEARCH).seed(() -> Float.valueOf(0.102f)));
                    break;
                }
                case "frost": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).frostVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_progress", IKey.constant((String)"Frost Progress"), 0x55FFFF, ((ScreenEffectPovActionClip)this.clip).frostProgress, null).icon(Icons.SNOWFLAKE).seed(() -> Float.valueOf(1.0f)));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_zoom", IKey.constant((String)"Zoom Multiplier"), 0x55AAFF, ((ScreenEffectPovActionClip)this.clip).frostZoom, null).icon(Icons.SEARCH).seed(() -> Float.valueOf(1.0f)));
                    break;
                }
                case "portal": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).portalVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Portal Opacity"), 0xAA00AA, ((ScreenEffectPovActionClip)this.clip).portalOpacity, null).icon(Icons.FADING));
                    break;
                }
                case "fire": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).fireVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    break;
                }
                case "pumpkin": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).pumpkinVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Pumpkin Opacity"), 0xFFAA00, ((ScreenEffectPovActionClip)this.clip).pumpkinOpacity, null).icon(Icons.FADING));
                    break;
                }
                case "suffocation": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).suffocationVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_block", IKey.constant((String)"Block ID"), 0xCCCCCC, ((ScreenEffectPovActionClip)this.clip).suffocationBlock, null).icon(Icons.BLOCK).seed(() -> "minecraft:stone"));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Suffocation Opacity"), 0x888888, ((ScreenEffectPovActionClip)this.clip).suffocationOpacity, null).icon(Icons.FADING));
                    break;
                }
                case "totem": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).totemVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_progress", IKey.constant((String)"Pop Progress"), 0xFFFF55, ((ScreenEffectPovActionClip)this.clip).totemProgress, null).icon(Icons.PLAY).seed(() -> Float.valueOf(0.0f)));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_flipped", IKey.constant((String)"Flipped (Left Hand)"), 0x55AAFF, ((ScreenEffectPovActionClip)this.clip).totemFlipped, null).icon(Icons.FLIP_HORIZONTAL).seed(() -> false));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_particles", IKey.constant((String)"Totem Particles"), 0x55FFFF, ((ScreenEffectPovActionClip)this.clip).totemParticles, null).icon(Icons.PARTICLE).seed(() -> true));
                    break;
                }
                case "underwater": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).underwaterVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Underwater Opacity"), 0x55AAFF, ((ScreenEffectPovActionClip)this.clip).underwaterOpacity, null).icon(Icons.DROP).seed(() -> Float.valueOf(0.1f)));
                    break;
                }
                case "nausea": {
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_visible", IKey.constant((String)"Visible"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).nauseaVisible, null).icon(Icons.VISIBLE).seed(() -> true));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_distortion", IKey.constant((String)"Distortion"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).nauseaDistortion, null).icon(Icons.CURVES));
                    this.addSheet(section, new UIKeyframeSheet(effectId + "_opacity", IKey.constant((String)"Overlay Opacity"), 0x55FF55, ((ScreenEffectPovActionClip)this.clip).nauseaOpacity, null).icon(Icons.FADING));
                }
            }
        }
    }

    private void addSheet(UIKeyframeSheet.Section section, UIKeyframeSheet sheet) {
        if (sheet != null) {
            sheet.section = section;
            this.keyframes.view.addSheet(sheet);
        }
    }

    public void openAddEffectMenu(UIContext context) {
        context.replaceContextMenu(m -> {
            if (this.selected != null) {
                m.icons.add(new MenuIcon(MenuVerb.REMOVE, () -> {
                    ((ScreenEffectPovActionClip)this.clip).removeEffect(this.selected);
                    this.selected = null;
                    this.refreshList();
                    this.updateKeyframeSheets();
                    this.editor.fillData();
                }));
            }
            m.action(Icons.SUN, IKey.constant((String)"Environment & Vision"), () -> context.replaceContextMenu(sub -> this.populateCategoryMenu(context, (ContextMenuManager)sub, List.of(ScreenEffectPresets.VIGNETTE, ScreenEffectPresets.NIGHT_VISION, ScreenEffectPresets.BLINDNESS, ScreenEffectPresets.DARKNESS, ScreenEffectPresets.SPYGLASS))));
            m.action(Icons.CLOSE, IKey.constant((String)"Hazards & Environment"), () -> context.replaceContextMenu(sub -> this.populateCategoryMenu(context, (ContextMenuManager)sub, List.of(ScreenEffectPresets.FIRE, ScreenEffectPresets.UNDERWATER, ScreenEffectPresets.FROST, ScreenEffectPresets.PORTAL, ScreenEffectPresets.SUFFOCATION, ScreenEffectPresets.PUMPKIN))));
            m.action(Icons.SAVED, IKey.constant((String)"Status & Popups"), () -> context.replaceContextMenu(sub -> this.populateCategoryMenu(context, (ContextMenuManager)sub, List.of(ScreenEffectPresets.NAUSEA, ScreenEffectPresets.TOTEM))));
        });
    }

    private void populateCategoryMenu(UIContext context, ContextMenuManager m, List<ScreenEffectPresetEntry> presets) {
        for (ScreenEffectPresetEntry preset : presets) {
            if (((ScreenEffectPovActionClip)this.clip).hasEffect(preset.id)) continue;
            m.actions.add(new ScreenEffectContextAction(preset, () -> {
                ScreenEffectEntry entry = new ScreenEffectEntry(preset.id);
                ((ScreenEffectPovActionClip)this.clip).addEffect(entry);
                this.selected = entry;
                this.refreshList();
                this.updateKeyframeSheets();
                this.editor.fillData();
            }));
        }
    }

    private void refreshList() {
        this.effectsList.setList(((ScreenEffectPovActionClip)this.clip).getEffects());
        if (this.selected != null && ((ScreenEffectPovActionClip)this.clip).getEffects().contains(this.selected)) {
            this.effectsList.setCurrentScroll(this.selected);
        } else if (!((ScreenEffectPovActionClip)this.clip).getEffects().isEmpty()) {
            this.selected = ((ScreenEffectPovActionClip)this.clip).getEffects().get(0);
            this.effectsList.setCurrentScroll(this.selected);
        } else {
            this.selected = null;
        }
        this.updateKeyframeSheets();
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(IKey.constant((String)"Screen Effects"), new UIElement[]{this.editKeyframes, this.effectsList, this.totemSection, this.colorSection}));
    }

    private void openTotemItemPicker() {
        UIContext context = this.getContext();
        if (context == null) {
            return;
        }
        String currentId = (String)((ScreenEffectPovActionClip)this.clip).totemItem.get();
        ItemStack currentStack = UIScreenEffectActionClip.createItemStack(currentId != null && !currentId.isEmpty() ? currentId : "minecraft:totem_of_undying");
        UIUnifiedPickOverlayPanel panel = UIUnifiedPickOverlayPanel.forItem(stack -> {
            if (stack != null && !stack.isEmpty()) {
                String id = Registries.ITEM.getId(stack.getItem()).toString();
                ((ScreenEffectPovActionClip)this.clip).totemItem.set(id);
                this.fillData();
                this.editor.fillData();
            }
        }, (ItemStack)currentStack);
        UIOverlay.addOverlay((UIContext)context, (UIOverlayPanel)panel, (int)280, (int)240);
    }

    private static ItemStack createItemStack(String id) {
        if (id == null || id.isEmpty()) {
            return new ItemStack((ItemConvertible)Items.TOTEM_OF_UNDYING);
        }
        try {
            Item item = (Item)Registries.ITEM.get(new Identifier(id));
            if (item != null && item != Items.AIR) {
                return new ItemStack((ItemConvertible)item);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return new ItemStack((ItemConvertible)Items.TOTEM_OF_UNDYING);
    }

    private void updateDetailSections() {
        boolean isTotem = this.selected != null && "totem".equalsIgnoreCase(this.selected.getEffectId());
        this.totemSection.setVisible(isTotem);
        if (isTotem) {
            String itemId = (String)((ScreenEffectPovActionClip)this.clip).totemItem.get();
            if (itemId != null && !itemId.isEmpty()) {
                String shortName = itemId.replace("minecraft:", "");
                this.pickTotemItem.label = IKey.constant((String)("Item: " + shortName));
                this.clearTotemItem.setVisible(!"minecraft:totem_of_undying".equals(itemId));
            } else {
                this.pickTotemItem.label = IKey.constant((String)"Pick Item");
                this.clearTotemItem.setVisible(false);
            }
        }
        boolean isColorable = this.selected != null && UIScreenEffectActionClip.isColorableEffect(this.selected.getEffectId());
        this.colorSection.setVisible(isColorable);
        if (isColorable) {
            Color c;
            String effectId = this.selected.getEffectId().toLowerCase();
            switch (effectId) {
                case "fire": {
                    c = (Color)((ScreenEffectPovActionClip)this.clip).fireColor.get();
                    break;
                }
                case "portal": {
                    c = (Color)((ScreenEffectPovActionClip)this.clip).portalColor.get();
                    break;
                }
                case "frost": {
                    c = (Color)((ScreenEffectPovActionClip)this.clip).frostColor.get();
                    break;
                }
                case "underwater": {
                    c = (Color)((ScreenEffectPovActionClip)this.clip).underwaterColor.get();
                    break;
                }
                default: {
                    c = Color.white();
                }
            }
            if (c != null) {
                this.effectColor.picker.setColor(c.getRGBColor());
            }
        }
    }

    private static boolean isColorableEffect(String id) {
        if (id == null) {
            return false;
        }
        String lower = id.toLowerCase();
        return "fire".equals(lower) || "portal".equals(lower) || "frost".equals(lower) || "underwater".equals(lower);
    }

    @Override
    public void fillData() {
        super.fillData();
        currentClip = (ScreenEffectPovActionClip)this.clip;
        currentPanel = this;
        this.refreshList();
        this.updateDetailSections();
    }

    public static class UIScreenEffectList
    extends UIList<ScreenEffectEntry> {
        private final UIScreenEffectActionClip parent;

        public UIScreenEffectList(UIScreenEffectActionClip parent, Consumer<List<ScreenEffectEntry>> callback) {
            super(callback);
            this.parent = parent;
            this.scroll.scrollItemSize = 22;
            this.emptyState(IKey.constant((String)"Right-click to add screen effects"));
        }

        public boolean subMouseClicked(UIContext context) {
            if (context.mouseButton == 1 && this.area.isInside(context)) {
                this.parent.openAddEffectMenu(context);
                return true;
            }
            return super.subMouseClicked(context);
        }

        public void render(UIContext context) {
            context.batcher.box((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), -871099372);
            context.batcher.outline((float)this.area.x, (float)this.area.y, (float)this.area.ex(), (float)this.area.ey(), 0x33FFFFFF);
            super.render(context);
        }

        public void renderListElement(UIContext context, ScreenEffectEntry element, int i, int x, int y, boolean hover, boolean selected) {
            int h = this.scroll.scrollItemSize;
            RowStyle.row((Batcher2D)context.batcher, (int)x, (int)y, (int)this.area.w, (int)h, (int)this.rowColor(element), (boolean)this.isHeader(element), (boolean)hover, (boolean)selected);
            this.renderElementPart(context, element, i, x, y, hover, selected);
        }

        protected void renderElementPart(UIContext context, ScreenEffectEntry element, int i, int x, int y, boolean hover, boolean selected) {
            int h = this.scroll.scrollItemSize;
            int iconX = x + 3;
            int iconY = y + (h - 16) / 2;
            context.batcher.box((float)(iconX - 1), (float)(iconY - 1), (float)(iconX + 17), (float)(iconY + 17), -872415232);
            DrawContext dc = context.batcher.getContext();
            if (dc != null) {
                ItemStack stack = element.createIconStack();
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc((int)515);
                RenderSystem.depthMask((boolean)true);
                dc.drawItem(stack, iconX, iconY);
                RenderSystem.disableDepthTest();
            }
            int textX = x + 24;
            String text = element.getDisplayName();
            context.batcher.textShadow(text, (float)textX, (float)(y + (h - context.batcher.getFont().getHeight()) / 2), RowStyle.textColor((hover || selected ? 1 : 0) != 0));
        }
    }

    public static class ScreenEffectContextAction
    extends ContextAction {
        private final ScreenEffectPresetEntry preset;

        public ScreenEffectContextAction(ScreenEffectPresetEntry preset, Runnable runnable) {
            super(Icons.NONE, IKey.constant((String)preset.name), runnable);
            this.preset = preset;
        }

        public int getWidth(FontRenderer font) {
            return 32 + font.getWidth(this.label.get());
        }

        public void render(UIContext context, FontRenderer font, int x, int y, int w, int h, boolean hover, boolean selected) {
            this.renderBackground(context, x, y, w, h, hover, selected);
            int iconX = x + 3;
            int iconY = y + (h - 16) / 2;
            context.batcher.box((float)(iconX - 1), (float)(iconY - 1), (float)(iconX + 17), (float)(iconY + 17), -872415232);
            DrawContext dc = context.batcher.getContext();
            if (dc != null) {
                ItemStack stack = this.preset.createIconStack();
                RenderSystem.enableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc((int)515);
                RenderSystem.depthMask((boolean)true);
                dc.drawItem(stack, iconX, iconY);
                RenderSystem.disableDepthTest();
            }
            context.batcher.text(this.label.get(), (float)(x + 24), (float)(y + (h - font.getHeight()) / 2 + 1), -1, false);
        }
    }
}

