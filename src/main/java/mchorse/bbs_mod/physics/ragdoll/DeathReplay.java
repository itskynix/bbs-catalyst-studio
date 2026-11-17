package mchorse.bbs_mod.physics.ragdoll;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.physics.actions.DeathActionClip;

public interface DeathReplay
{
    ValueBoolean bbs_physics$deathEnabled();
    ValueFloat bbs_physics$deathStrength();

    static DeathActionClip at(Replay replay, int localTick)
    {
        DeathActionClip first = null;
        for (DeathActionClip clip : replay.actions.getClips(DeathActionClip.class))
        {
            if (clip.enabled.get() && clip.tick.get() <= localTick
                && (first == null || clip.tick.get() < first.tick.get())) first = clip;
        }
        return first;
    }
}
