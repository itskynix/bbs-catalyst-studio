package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;

/**
 * Puts an AI actor where its walk (see {@link AiSimulation}) has it, every tick, after the keyframes:
 * the place and the way it faces. The actor's legs and arms swing as it walks because the entity
 * works its limb swing out of how far it moved in the tick, and it moved exactly as far as the walk
 * says - fast when it walks, not at all when it waits.
 */
public final class AiDriver
{
    private AiDriver()
    {}

    public static void apply(Replay replay, Film film, int ticks, IEntity entity)
    {
        if (replay == null || entity == null || replay.form.get() == null || !replay.form.get().hasAi.get())
        {
            return;
        }

        AiSimulation.Track track = AiSimulation.track(film, replay);

        if (track == null)
        {
            return;
        }

        int i = Math.max(0, Math.min(track.length() - 1, ticks));

        entity.setPosition(track.x[i], track.y[i], track.z[i]);
        entity.setYaw(track.yaw[i]);
        entity.setHeadYaw(track.yaw[i]);
        entity.setBodyYaw(track.yaw[i]);
    }

    /**
     * Writes the walk into the replay's own keyframes - the place and the way it faces, a key every
     * second tick, joined in a straight line - and turns the AI off, so that what is left is plain
     * animation to edit. Returns how many ticks were baked, 0 when there was no walk.
     */
    public static int bake(Replay replay, Film film)
    {
        AiSimulation.Track track = AiSimulation.track(film, replay);

        if (track == null)
        {
            return 0;
        }

        KeyframeChannel<Double>[] channels = new KeyframeChannel[] {replay.keyframes.x, replay.keyframes.y, replay.keyframes.z, replay.keyframes.yaw, replay.keyframes.headYaw, replay.keyframes.bodyYaw};

        for (KeyframeChannel<Double> channel : channels)
        {
            channel.removeAll();
        }

        int last = track.length() - 1;
        double previous = 0D;

        for (int tick = 0; tick <= last; tick += 2)
        {
            /* The yaw is unwrapped so that a turn through 180 degrees is not a spin the long way round */
            double yaw = track.yaw[tick];

            if (tick > 0)
            {
                while (yaw - previous > 180D)
                {
                    yaw -= 360D;
                }

                while (yaw - previous < -180D)
                {
                    yaw += 360D;
                }
            }

            previous = yaw;

            double[] values = {track.x[tick], track.y[tick], track.z[tick], yaw, yaw, yaw};

            for (int c = 0; c < channels.length; c++)
            {
                int index = channels[c].insert(tick, values[c]);
                Keyframe<Double> keyframe = channels[c].get(index);

                if (keyframe != null)
                {
                    keyframe.getInterpolation().setInterp(Interpolations.LINEAR);
                }
            }
        }

        if (replay.form.get() != null)
        {
            replay.form.get().hasAi.set(false);
        }

        return last / 2 + 1;
    }
}
