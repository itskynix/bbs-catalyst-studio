/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.ui.film.IUIClipsDelegate
 *  mchorse.bbs_mod.ui.film.UIClipsPanel
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.elements.IUIElement
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 */
package mchorse.bbs_mod.camera.pov.actions.gui.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.gui.editor.UIGuiSlotEditor;
import mchorse.bbs_mod.camera.pov.hud.RecordedHudData;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.UIFilmPanelPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.UIClipsPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.IUIElement;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;

public class UIGuiSlotKeyframeFactory
extends UIKeyframeFactory<Boolean> {
    private final UIGuiSlotEditor slotEditor;

    public UIGuiSlotKeyframeFactory(Keyframe<Boolean> keyframe, UIKeyframes editor) {
        super(keyframe, editor);
        BaseValue baseValue;
        this.scroll.removeAll();
        UIKeyframeSheet sheet = editor != null && editor.getGraph() != null ? editor.getGraph().getSheet(keyframe) : (editor != null && editor.getDopeSheet() != null ? editor.getDopeSheet().getSheet(keyframe) : null);
        String sheetId = sheet == null ? "" : sheet.id;
        UIFilmPanel filmPanel = PovReplaySettings.getFilmPanel();
        UIClipsPanel clipsDelegate = filmPanel != null ? filmPanel.cameraEditor : null;
        BaseValue parentChannel = keyframe.getParent();
        GuiPovActionClip guiClip = null;
        if (parentChannel != null && (baseValue = parentChannel.getParent()) instanceof GuiPovActionClip) {
            GuiPovActionClip clip;
            guiClip = clip = (GuiPovActionClip)baseValue;
        } else if (filmPanel != null && filmPanel.cameraEditor != null && (baseValue = filmPanel.cameraEditor.getClip()) instanceof GuiPovActionClip) {
            GuiPovActionClip clip;
            guiClip = clip = (GuiPovActionClip)baseValue;
        }
        if ("crafting_grid".equals(sheetId) || "gui_slots".equals(sheetId)) {
            if (guiClip != null) {
                String guiId = guiClip.state.isEmpty() ? "inventory" : (String)guiClip.state.get(0).getValue();
                UIGuiSlotEditor.Mode mode = "crafting_grid".equals(sheetId) ? UIGuiSlotEditor.Mode.CRAFTING : UIGuiSlotEditor.Mode.GUI;
                this.slotEditor = new UIGuiSlotEditor(guiClip, (IUIClipsDelegate)clipsDelegate);
                this.slotEditor.configure(guiId, mode, keyframe.getTick());
                this.scroll.add((IUIElement)this.slotEditor);
            } else {
                this.slotEditor = null;
            }
        } else {
            ReplayKeyframes replayKeyframes;
            UIFilmPanelPovAccess access;
            Replay replay = null;
            if (filmPanel instanceof UIFilmPanelPovAccess && (access = (UIFilmPanelPovAccess)filmPanel).bbsPov$getEditor() != null) {
                replay = access.bbsPov$getEditor().getReplay();
            } else if (filmPanel != null && filmPanel.replayEditor != null) {
                replay = filmPanel.replayEditor.getReplay();
            }
            RecordedHudData hud = null;
            if (replay != null && (replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess) {
                ReplayKeyframesPovAccess access2 = (ReplayKeyframesPovAccess)replayKeyframes;
                hud = access2.bbsPov$getHud();
            }
            if (hud != null && filmPanel != null) {
                this.slotEditor = new UIGuiSlotEditor(null, null);
                this.slotEditor.configure(hud.inventory, hud.inventoryAnchor, keyframe.getTick());
                this.scroll.add((IUIElement)this.slotEditor);
            } else {
                this.slotEditor = null;
            }
        }
    }
}

