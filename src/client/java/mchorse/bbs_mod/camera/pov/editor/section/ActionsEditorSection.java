/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.utils.clips.Clips
 */
package mchorse.bbs_mod.camera.pov.editor.section;

import mchorse.bbs_mod.camera.pov.actions.RecordedPovActions;
import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.section.PovEditorSection;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.utils.clips.Clips;

public final class ActionsEditorSection
implements PovEditorSection {
    public static final ActionsEditorSection INSTANCE = new ActionsEditorSection();
    public static final IKey TITLE = L10n.lang("bbs.pov.editor.tab.actions", "Point of View Actions");

    private ActionsEditorSection() {
    }

    @Override
    public void fillSheets(UIPovEditor editor, boolean resetView) {
        RecordedPovActions actions = editor.getActions();
        editor.actionTimeline.setClips((Clips)actions);
    }
}

