/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.film.replays.ReplayKeyframes
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.interps.Lerps
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 */
package mchorse.bbs_mod.camera.pov.camera.clip;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.integration.access.bbs.ReplayKeyframesPovAccess;
import mchorse.bbs_mod.camera.pov.replay.PovReplaySettings;
import mchorse.bbs_mod.camera.pov.replay.ReplayPovAccess;
import java.util.List;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

public final class PovCameraClips {
    private PovCameraClips() {
    }

    public static double getStandingEyeHeight(IEntity entity, Form form) {
        if (form != null && ((Boolean)form.hitbox.get()).booleanValue()) {
            float height = ((Float)form.hitboxHeight.get()).floatValue();
            float eyeRatio = ((Float)form.hitboxEyeHeight.get()).floatValue();
            if (eyeRatio <= 0.0f) {
                eyeRatio = 0.9f;
            }
            return height * eyeRatio;
        }
        return entity != null ? entity.getEyeHeight() : 1.62;
    }

    public static double getSneakingEyeHeight(IEntity entity, Form form) {
        if (form != null && ((Boolean)form.hitbox.get()).booleanValue()) {
            float height = ((Float)form.hitboxHeight.get()).floatValue() * ((Float)form.hitboxSneakMultiplier.get()).floatValue();
            float eyeRatio = ((Float)form.hitboxEyeHeight.get()).floatValue();
            if (eyeRatio <= 0.0f) {
                eyeRatio = 0.9f;
            }
            return height * eyeRatio;
        }
        return 1.27;
    }

    public static boolean isSneakingAt(Replay replay, IEntity entity, int tick) {
        if (replay != null && replay.keyframes != null && replay.keyframes.sneaking != null && !replay.keyframes.sneaking.isEmpty()) {
            int looping = (Integer)replay.looping.get();
            int t = looping > 0 ? (tick % looping + looping) % looping : tick;
            return (Double)replay.keyframes.sneaking.interpolate((float)t) > 0.5;
        }
        return entity != null && entity.isSneaking();
    }

    public static double getSmoothPovEyeHeight(Replay replay, IEntity entity, Form form, float replayTick) {
        double sneakingEye;
        double standingEye = PovCameraClips.getStandingEyeHeight(entity, form);
        if (Math.abs(standingEye - (sneakingEye = PovCameraClips.getSneakingEyeHeight(entity, form))) < 1.0E-5) {
            return standingEye;
        }
        int curTick = (int)Math.floor(replayTick);
        float frac = replayTick - (float)curTick;
        int startTick = curTick - 8;
        double sim = PovCameraClips.isSneakingAt(replay, entity, startTick) ? sneakingEye : standingEye;
        for (int t = startTick + 1; t <= curTick; ++t) {
            double target = PovCameraClips.isSneakingAt(replay, entity, t) ? sneakingEye : standingEye;
            sim += (target - sim) * 0.5;
        }
        double prev = sim;
        double nextTarget = PovCameraClips.isSneakingAt(replay, entity, curTick + 1) ? sneakingEye : standingEye;
        double next = sim + (nextTarget - sim) * 0.5;
        return Lerps.lerp((double)prev, (double)next, (double)frac);
    }

    public static double getPovEyeHeight(IEntity entity) {
        if (entity == null) {
            return 1.62;
        }
        return PovCameraClips.getPovEyeHeight(entity, entity.getForm());
    }

    public static double getPovEyeHeight(IEntity entity, Form form) {
        return PovCameraClips.getSmoothPovEyeHeight(null, entity, form, 0.0f);
    }

    public static PovCameraClip resolve(Film film, float filmTick) {
        if (film == null) {
            return null;
        }
        int tick = (int)Math.floor(filmTick);
        PovCameraClip result = null;
        int topLayer = Integer.MIN_VALUE;
        for (Clip clip : film.camera.getClips(tick)) {
            if (!(clip instanceof PovCameraClip)) continue;
            PovCameraClip pov = (PovCameraClip)clip;
            if (!((Boolean)pov.enabled.get()).booleanValue() || (Integer)pov.layer.get() < topLayer) continue;
            result = pov;
            topLayer = (Integer)pov.layer.get();
        }
        return result;
    }

    public static Replay resolveReplay(Film film, float filmTick) {
        return PovCameraClips.resolveReplay(film, PovCameraClips.resolve(film, filmTick));
    }

    public static Replay resolveReplay(Film film, PovCameraClip clip) {
        if (film == null || clip == null) {
            return null;
        }
        List replays = film.replays.getList();
        int index = (Integer)clip.selector.get();
        return index >= 0 && index < replays.size() ? (Replay)replays.get(index) : null;
    }

    public static int indexOfReplay(Film film, Replay replay) {
        return film == null || replay == null ? -1 : film.replays.getList().indexOf(replay);
    }

    public static boolean hasAny(Film film) {
        return film != null && !film.camera.getClips(PovCameraClip.class).isEmpty();
    }

    public static boolean isActive(UIFilmPanel panel) {
        return panel != null && PovCameraClips.resolve((Film)panel.getData(), panel.getCursor()) != null;
    }

    public static void ensureLegacyClip(Film film) {
        if (film == null || PovCameraClips.hasAny(film)) {
            return;
        }
        List<Replay> replays = film.replays.getList();
        Replay source = film.getFirstPersonReplay();
        if (source == null) {
            for (Replay replay : replays) {
                if (!PovReplaySettings.isOverlayEnabled(replay)) continue;
                source = replay;
                break;
            }
        }
        if (source == null) {
            return;
        }
        int selector = replays.indexOf(source);
        if (selector < 0) {
            return;
        }
        source.fp.set(false);
        if (source instanceof ReplayPovAccess) {
            ReplayPovAccess access = (ReplayPovAccess)source;
            access.bbsPov$getOverlayEnabled().set(false);
        }
        PovCameraClip clip = new PovCameraClip();
        clip.tick.set(0);
        clip.duration.set(PovCameraClips.inferDuration(film));
        clip.layer.set(Math.max(0, film.camera.getTopLayer() + 1));
        clip.selector.set(selector);
        clip.hands.set(true);
        clip.hud.set(true);
        clip.crosshair.set(true);
        clip.actions.set(true);
        clip.cursor.set(true);
        clip.headLook.set(true);
        film.camera.addClip((Clip)clip);
        film.camera.sync();
    }

    private static int inferDuration(Film film) {
        int duration = Math.max(1, film.camera.calculateDuration());
        for (Replay replay : film.replays.getList()) {
            ReplayKeyframesPovAccess access;
            for (KeyframeChannel channel : replay.keyframes.getChannels()) {
                duration = Math.max(duration, (int)Math.ceil(channel.getLength()) + 1);
            }
            duration = Math.max(duration, replay.actions.calculateDuration());
            ReplayKeyframes replayKeyframes = replay.keyframes;
            if (!(replayKeyframes instanceof ReplayKeyframesPovAccess) || (access = (ReplayKeyframesPovAccess)replayKeyframes).bbsPov$getActions() == null) continue;
            duration = Math.max(duration, access.bbsPov$getActions().calculateDuration());
        }
        return Math.max(1, duration);
    }
}

