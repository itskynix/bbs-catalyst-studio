/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.forms.ModelForm
 *  mchorse.bbs_mod.morphing.Morph
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  net.minecraft.entity.Entity
 *  net.minecraft.entity.player.PlayerEntity
 */
package mchorse.bbs_mod.camera.pov.actions.chat;

import mchorse.bbs_mod.camera.pov.playback.PovPlaybackContext;
import mchorse.bbs_mod.camera.pov.recording.PovRecordingSession;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import java.util.ArrayList;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ModelForm;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

public final class ChatMorphHelper {
    private ChatMorphHelper() {
    }

    public static String getPlayerMorphName(PlayerEntity player) {
        if (player == null) {
            return null;
        }
        try {
            Morph morph = Morph.getMorph((Entity)player);
            if (morph != null && morph.getForm() != null) {
                Form form = morph.getForm();
                if (form.name.get() != null && !((String)form.name.get()).trim().isEmpty()) {
                    return ((String)form.name.get()).trim();
                }
                if (form instanceof ModelForm) {
                    ModelForm modelForm = (ModelForm)form;
                    String model = (String)modelForm.model.get();
                    if (model != null && !model.trim().isEmpty()) {
                        return model.trim();
                    }
                }
                if (form.getId() != null && !form.getId().trim().isEmpty()) {
                    return form.getId().trim();
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    public static String getReplayName(Replay replay) {
        if (replay == null) {
            return null;
        }
        if (replay.label.get() != null && !((String)replay.label.get()).trim().isEmpty()) {
            return ((String)replay.label.get()).trim();
        }
        if (replay.nameTag.get() != null && !((String)replay.nameTag.get()).trim().isEmpty()) {
            return ((String)replay.nameTag.get()).trim();
        }
        if (replay.form.get() != null) {
            Form form = (Form)replay.form.get();
            if (form.name.get() != null && !((String)form.name.get()).trim().isEmpty()) {
                return ((String)form.name.get()).trim();
            }
            if (form instanceof ModelForm) {
                ModelForm modelForm = (ModelForm)form;
                String model = (String)modelForm.model.get();
                if (model != null && !model.trim().isEmpty()) {
                    return model.trim();
                }
            }
            if (form.getId() != null && !form.getId().trim().isEmpty()) {
                return form.getId().trim();
            }
        }
        return null;
    }

    public static String getActiveReplayName() {
        try {
            String name;
            Replay replay;
            PovRecordingSession session = PovRecordingSession.getCurrent();
            if (session != null && (replay = session.getReplay()) != null && (name = ChatMorphHelper.getReplayName(replay)) != null && !name.isEmpty()) {
                return name;
            }
            PovPlaybackContext.Frame frame = PovPlaybackContext.getActive();
            if (frame != null && frame.replay() != null && (name = ChatMorphHelper.getReplayName(frame.replay())) != null && !name.isEmpty()) {
                return name;
            }
            UIFilmPanel panel = PovReplaySettings.getFilmPanel();
            if (panel != null && panel.replayEditor != null) {
                Replay replay2 = panel.replayEditor.getReplay();
                return ChatMorphHelper.getReplayName(replay2);
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return null;
    }

    public static List<String> getAllFilmReplayNames() {
        ArrayList<String> names = new ArrayList<String>();
        try {
            UIFilmPanel panel;
            PovPlaybackContext.Frame frame;
            Film film = null;
            PovRecordingSession session = PovRecordingSession.getCurrent();
            if (session != null) {
                film = session.getFilm();
            }
            if (film == null && (frame = PovPlaybackContext.getActive()) != null) {
                film = frame.film();
            }
            if (film == null && (panel = PovReplaySettings.getFilmPanel()) != null) {
                film = (Film)panel.getData();
            }
            if (film != null && film.replays != null) {
                for (Replay replay : film.replays.getList()) {
                    String name = ChatMorphHelper.getReplayName(replay);
                    if (name == null || name.isEmpty() || names.contains(name)) continue;
                    names.add(name);
                }
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return names;
    }
}

