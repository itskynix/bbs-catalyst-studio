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
 *  mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarLooks;
import mchorse.bbs_mod.camera.pov.actions.bossbar.BossBarTypeEntry;
import mchorse.bbs_mod.camera.pov.actions.clip.BossBarPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionClip;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.utils.keyframes.UIFilmKeyframes;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public class UIBossBarActionClip
extends UIPovActionClip<BossBarPovActionClip> {
    public UIButton bossType;
    public UIButton color;
    public UIButton style;
    public UITextbox name;
    public UIButton editKeyframes;
    public UIKeyframeEditor keyframes;

    public UIBossBarActionClip(BossBarPovActionClip clip, IUIClipsDelegate editor) {
        super(clip, editor);
    }

    @Override
    protected void registerUI() {
        super.registerUI();
        this.keyframes = new UIKeyframeEditor(consumer -> new UIFilmKeyframes(this.editor, consumer));
        this.keyframes.view.duration(() -> (Integer)((BossBarPovActionClip)this.clip).duration.get());
        this.editKeyframes = new UIButton(IKey.constant((String)"Edit Keyframes"), button -> {
            this.updateKeyframeSheets();
            this.editor.embedView((UIElement)this.keyframes);
            this.keyframes.view.resetView();
            if (this.keyframes.view.getGraph() != null) {
                this.keyframes.view.getGraph().clearSelection();
            }
        });
        this.bossType = new UIButton(IKey.constant((String)"Boss"), button -> {
            BossBarTypeEntry next = BossBarTypeEntry.next(((BossBarPovActionClip)this.clip).resolveType());
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).state, channel -> UIBossBarActionClip.setFirst(channel, next.id));
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).name, channel -> UIBossBarActionClip.setFirst(channel, next.defaultTitle));
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).color, channel -> UIBossBarActionClip.setFirst(channel, next.defaultColor));
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).style, channel -> UIBossBarActionClip.setFirst(channel, next.defaultStyle));
            this.updateButtons();
        });
        this.color = new UIButton(IKey.constant((String)"Color"), button -> {
            String next = BossBarLooks.nextColor(this.current(((BossBarPovActionClip)this.clip).color, "pink"));
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).color, channel -> UIBossBarActionClip.setFirst(channel, next));
            this.updateButtons();
        });
        this.style = new UIButton(IKey.constant((String)"Style"), button -> {
            String next = BossBarLooks.nextStyle(this.current(((BossBarPovActionClip)this.clip).style, "progress"));
            this.editor.editMultiple(((BossBarPovActionClip)this.clip).style, channel -> UIBossBarActionClip.setFirst(channel, next));
            this.updateButtons();
        });
        this.name = new UITextbox(10000, text -> this.editor.editMultiple(((BossBarPovActionClip)this.clip).name, channel -> UIBossBarActionClip.setFirst(channel, text)));
    }

    private void updateKeyframeSheets() {
        this.keyframes.view.removeAllSheets();
        this.keyframes.view.addSheet(new UIKeyframeSheet("name", IKey.constant((String)"Name"), 0xFFFFFF, ((BossBarPovActionClip)this.clip).name, null).icon(Icons.FONT).seed(() -> "Ender Dragon"));
        this.keyframes.view.addSheet(new UIKeyframeSheet("percent", IKey.constant((String)"Health"), 0xFF5555, ((BossBarPovActionClip)this.clip).percent, null).icon(Icons.HEART).seed(() -> Float.valueOf(1.0f)));
        this.keyframes.view.addSheet(new UIKeyframeSheet("bossbar_color", IKey.constant((String)"Color"), 0xCC44EE, ((BossBarPovActionClip)this.clip).color, null).icon(Icons.COLOR).seed(() -> "pink"));
        this.keyframes.view.addSheet(new UIKeyframeSheet("bossbar_style", IKey.constant((String)"Style"), 0xAAAAAA, ((BossBarPovActionClip)this.clip).style, null).icon(Icons.LAYOUT).seed(() -> "progress"));
    }

    @Override
    protected void registerPanels() {
        super.registerPanels();
        this.panels.add((IUIElement)this.section(IKey.constant((String)"Boss Bar"), new UIElement[]{this.bossType, this.name, this.color, this.style, this.editKeyframes}));
    }

    @Override
    public void fillData() {
        super.fillData();
        ((BossBarPovActionClip)this.clip).ensureDefaults();
        this.updateButtons();
        this.name.setText(this.current(((BossBarPovActionClip)this.clip).name, "Ender Dragon"));
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

    private void updateButtons() {
        BossBarTypeEntry type = BossBarTypeEntry.findById(((BossBarPovActionClip)this.clip).resolveType());
        String typeName = type == null ? "Ender Dragon" : type.name;
        this.bossType.label = IKey.constant((String)("Boss: " + typeName));
        this.color.label = IKey.constant((String)("Color: " + BossBarLooks.displayColor(this.current(((BossBarPovActionClip)this.clip).color, "pink"))));
        this.style.label = IKey.constant((String)("Style: " + BossBarLooks.displayStyle(this.current(((BossBarPovActionClip)this.clip).style, "progress"))));
    }

    private String current(KeyframeChannel<String> channel, String fallback) {
        return channel == null || channel.isEmpty() ? fallback : (String)channel.get(0).getValue();
    }

    private static <T> void setFirst(KeyframeChannel<T> channel, T value) {
        if (channel.isEmpty()) {
            channel.insert(0.0f, value);
        } else {
            channel.get(0).setValue(value);
        }
    }
}

