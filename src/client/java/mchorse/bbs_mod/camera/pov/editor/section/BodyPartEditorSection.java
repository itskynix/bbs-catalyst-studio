/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.FormProperties
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.film.replays.tracks.TrackCatalog
 *  mchorse.bbs_mod.film.replays.tracks.TrackDescriptor
 *  mchorse.bbs_mod.forms.FormUtils
 *  mchorse.bbs_mod.forms.forms.BodyPart
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.ui.film.ICursor
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils
 *  mchorse.bbs_mod.ui.framework.UIContext
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet
 *  mchorse.bbs_mod.utils.Pair
 */
package mchorse.bbs_mod.camera.pov.editor.section;

import mchorse.bbs_mod.camera.pov.editor.UIPovEditor;
import mchorse.bbs_mod.camera.pov.editor.section.PovEditorSection;
import mchorse.bbs_mod.camera.pov.hand.RecordedHandData;
import mchorse.bbs_mod.camera.pov.hand.editor.PovHandPicking;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.FormProperties;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.tracks.TrackCatalog;
import mchorse.bbs_mod.film.replays.tracks.TrackDescriptor;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.BodyPart;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.ui.film.ICursor;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditorUtils;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.utils.Pair;

public final class BodyPartEditorSection
implements PovEditorSection {
    public static final BodyPartEditorSection INSTANCE = new BodyPartEditorSection();

    private BodyPartEditorSection() {
    }

    @Override
    public void fillSheets(UIPovEditor editor, boolean resetView) {
        Replay replay = editor.getReplay();
        if (replay == null) {
            return;
        }
        ReplayKeyframes replayKeyframes = replay.keyframes;
        if (replayKeyframes instanceof ReplayKeyframesPovAccess) {
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            RecordedHandData hand = access.bbsPov$getHand();
            ModelForm root = this.root(hand);
            if (hand != null && root != null) {
                List<TrackDescriptor> descriptors;
                RecordedHandData.ensureOverlays((Form)root);
                this.enableBoneTracks((Form)root);
                String selected = editor.getSelectedBodyPart();
                if (selected != null && !selected.isBlank()) {
                    descriptors = TrackCatalog.forPart((Form)root, (FormProperties)hand.bodyPartTracks, (String)selected);
                } else {
                    descriptors = new ArrayList<TrackDescriptor>();
                    BodyPartEditorSection.collectAllPartDescriptors((Form)root, (Form)root, hand.bodyPartTracks, (List<TrackDescriptor>)descriptors);
                }
                ArrayList<UIKeyframeSheet> sheets = new ArrayList<UIKeyframeSheet>();
                UIReplaysEditorUtils.buildSheets(descriptors, sheets);
                for (UIKeyframeSheet sheet : sheets) {
                    editor.addPendingSheet(sheet);
                }
            }
        }
    }

    private void enableBoneTracks(Form form) {
        if (form == null) {
            return;
        }
        if (form instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)form;
            modelForm.boneTracks.set(true);
        }
        for (BodyPart part : form.parts.getAllTyped()) {
            if (part == null || part.getForm() == null) continue;
            this.enableBoneTracks(part.getForm());
        }
    }

    public static void collectAllPartDescriptors(Form root, Form current, FormProperties properties, List<TrackDescriptor> out) {
        if (current == null) {
            return;
        }
        for (BodyPart part : current.parts.getAllTyped()) {
            if (part == null || part.getForm() == null) continue;
            out.addAll(TrackCatalog.forPart((Form)root, (FormProperties)properties, (String)FormUtils.getPath((Form)part.getForm())));
            BodyPartEditorSection.collectAllPartDescriptors(root, part.getForm(), properties, out);
        }
    }

    public boolean pick(UIPovEditor editor, UIContext context) {
        String formPath;
        if (context.mouseButton != 0) {
            return false;
        }
        String bone = PovHandPicking.getPickedBone();
        Form pickedForm = PovHandPicking.getPickedForm();
        if (pickedForm == null) {
            ReplayKeyframes replayKeyframes;
            int pickedIndex = PovHandPicking.getPickedBodyPart();
            if (pickedIndex < 0) {
                return false;
            }
            Replay replay = editor.getReplay();
            if (replay == null || !((replayKeyframes = replay.keyframes) instanceof ReplayKeyframesPovAccess)) {
                return false;
            }
            ReplayKeyframesPovAccess access = (ReplayKeyframesPovAccess)replayKeyframes;
            RecordedHandData hand = access.bbsPov$getHand();
            ModelForm root = this.root(hand);
            if (hand == null || root == null) {
                return false;
            }
            List parts = root.parts.getAllTyped();
            if (pickedIndex >= parts.size()) {
                return false;
            }
            BodyPart pickedPart = (BodyPart)parts.get(pickedIndex);
            if (pickedPart != null) {
                pickedForm = pickedPart.getForm();
            }
        }
        if (pickedForm == null) {
            return false;
        }
        Film film = (Film)editor.getFilmPanel().getData();
        if (film != null) {
            Form root = FormUtils.getRoot((Form)pickedForm);
            for (Replay r : film.replays.getList()) {
                if (r == null || r.form.get() == null) continue;
                Form rRoot = FormUtils.getRoot((Form)((Form)r.form.get()));
                if (r.form.get() != root && rRoot != root) continue;
                if (editor.getReplay() == r || editor.getFilmPanel().replayEditor == null) break;
                editor.getFilmPanel().replayEditor.setReplay(r);
                break;
            }
        }
        if ((formPath = FormUtils.getPath((Form)pickedForm)) != null && !formPath.isBlank() && !formPath.equals(editor.getSelectedBodyPart())) {
            editor.selectBodyPart(formPath);
        }
        UIReplaysEditorUtils.pickForm((UIKeyframeEditor)editor.keyframeEditor, (ICursor)editor.getFilmPanel(), (Form)pickedForm, (String)bone, (boolean)false);
        return true;
    }

    public ModelForm root(RecordedHandData hand) {
        Form root;
        Form replayForm;
        Form root2;
        if (hand != null && hand.baseForm.get() != null && (root2 = FormUtils.getRoot((Form)((Form)hand.baseForm.get()))) instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)root2;
            return modelForm;
        }
        UIFilmPanel panel = PovReplaySettings.getFilmPanel();
        if (panel != null && panel.replayEditor != null && panel.replayEditor.getReplay() != null && (replayForm = (Form)panel.replayEditor.getReplay().form.get()) != null && (root = FormUtils.getRoot((Form)replayForm)) instanceof ModelForm) {
            ModelForm modelForm = (ModelForm)root;
            return modelForm;
        }
        return null;
    }

    public String gizmoBone(UIPovEditor editor) {
        Pair selected = editor.keyframeEditor.getBone();
        String partPath = editor.getSelectedBodyPart();
        if (selected == null || selected.a == null || ((String)selected.a).isBlank()) {
            return partPath != null && !partPath.isBlank() ? partPath : null;
        }
        String bone = (String)selected.a;
        if (partPath != null && !partPath.isBlank()) {
            if (bone.equals(partPath) || bone.startsWith(partPath + "/")) {
                return bone;
            }
            if (!bone.contains("/")) {
                return partPath + "/" + bone;
            }
        }
        return bone;
    }
}

