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
 *  mchorse.bbs_mod.ui.framework.elements.buttons.UIButton
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.MenuPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.menu.MenuTypeEntry;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.utils.icons.Icons;

public class UIMenuActionClip
extends UIPovActionClip<MenuPovActionClip> {
    public UIButton editKeyframes;
    public UIButton menuType;
    public UIKeyframeEditor keyframes;
    public UIElement menuKeyframesSection;

    public UIMenuActionClip(MenuPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> (Integer)((MenuPovActionClip)this.clip).duration.get());
        this.editKeyframes = new UIButton(IKey.constant((String)"Edit Keyframes"), button -> {
            this.updateKeyframeSheets();
            this.editor.embedView((UIElement)this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.menuType = new UIButton(L10n.lang("bbs.pov.actions.menu.type", "GUI Menu Type"), button -> {
            MenuTypeEntry next = MenuTypeEntry.next(((MenuPovActionClip)this.clip).resolveType());
            this.editor.editMultiple(((MenuPovActionClip)this.clip).state, channel -> {
                if (channel.isEmpty()) {
                    channel.insert(0.0f, next.id);
                } else {
                    channel.get(0).setValue(next.id);
                }
            });
            this.updateMenuTypeButton(next.id);
            this.updateKeyframeSectionVisibility();
            this.updateKeyframeSheets();
        });
    }

    private void updateKeyframeSheets() {
        String type = ((MenuPovActionClip)this.clip).resolveType();
        this.keyframes.view.removeAllSheets();
        if ("death".equals(type)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("death_message", IKey.constant((String)"Death Message"), 0xFFFFFF, ((MenuPovActionClip)this.clip).deathMessage, null).icon(Icons.FONT).seed(() -> ""));
            this.keyframes.view.addSheet(new UIKeyframeSheet("score", IKey.constant((String)"Score"), 0xFFFF55, ((MenuPovActionClip)this.clip).score, null).icon(Icons.FONT).seed(() -> "Score: 0"));
            this.keyframes.view.addSheet(new UIKeyframeSheet("bg_opacity", IKey.constant((String)"Red Background"), 0xFF5555, ((MenuPovActionClip)this.clip).bgOpacity, null).icon(Icons.COLOR).seed(() -> Float.valueOf(1.0f)));
            this.keyframes.view.addSheet(new UIKeyframeSheet("buttons_active", IKey.constant((String)"Buttons Active"), 0xAAAAAA, ((MenuPovActionClip)this.clip).buttonsActive, null).icon(Icons.POINTER).seed(() -> false));
        } else if ("sleep".equals(type)) {
            this.keyframes.view.addSheet(new UIKeyframeSheet("opacity", IKey.constant((String)"Darkness"), 0x888888, ((MenuPovActionClip)this.clip).opacity, null).icon(Icons.FADING).seed(() -> Float.valueOf(1.0f)));
            this.keyframes.view.addSheet(new UIKeyframeSheet("leave_bed", IKey.constant((String)"Leave Bed"), 0xCCCCCC, ((MenuPovActionClip)this.clip).leaveBed, null).icon(Icons.POINTER).seed(() -> true));
        }
    }

    private void updateKeyframeSectionVisibility() {
        boolean show;
        boolean bl = show = !"game_menu".equals(((MenuPovActionClip)this.clip).resolveType());
        if (this.menuKeyframesSection == null) {
            return;
        }
        if (show) {
            if (!this.menuKeyframesSection.hasParent()) {
                this.panels.add((IUIElement)this.menuKeyframesSection);
            }
        } else if (this.menuKeyframesSection.hasParent()) {
            this.menuKeyframesSection.removeFromParent();
        }
        this.resize();
        if (this.panels != null) {
            this.panels.resize();
        }
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(L10n.lang("bbs.pov.actions.menu.settings", "GUI Menu Settings"), new UIElement[]{this.menuType}));
        this.menuKeyframesSection = this.section(L10n.lang("bbs.pov.actions.menu.keyframes", "GUI Menu Keyframes"), new UIElement[]{this.editKeyframes});
    }

    @Override
    public void fillData() {
        super.fillData();
        this.updateMenuTypeButton(((MenuPovActionClip)this.clip).resolveType());
        this.updateKeyframeSectionVisibility();
        this.updateKeyframeSheets();
        if (this.keyframes != null && this.keyframes.view != null && this.keyframes.view.getGraph() != null) {
            this.keyframes.view.getGraph().clearSelection();
        }
    }

    public void render(UIContext context) {
        if (this.keyframes != null && !this.keyframes.hasParent() && this.keyframes.view != null && this.keyframes.view.getGraph() != null && this.keyframes.view.getGraph().getSelected() != null) {
            this.keyframes.view.getGraph().clearSelection();
        }
        super.render(context);
    }

    private void updateMenuTypeButton(String id) {
        MenuTypeEntry entry = MenuTypeEntry.findById(id);
        String name = entry == null ? (id == null || id.isBlank() ? "Game Menu" : id) : entry.name;
        this.menuType.label = IKey.constant((String)("Menu: " + name));
    }
}

