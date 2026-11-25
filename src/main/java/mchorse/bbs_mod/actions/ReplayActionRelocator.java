package mchorse.bbs_mod.actions;

import mchorse.bbs_mod.actions.types.ActionClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

/**
 * Remembers where each replay stood when each of its actions happened, then, after its keyframes were
 * rewritten (a batch process, a formation), carries the actions by however far and however much the
 * replay moved at that very tick. An axis the replay had no keyframes on counts as not moved: a
 * process that creates the first keyframes of an axis says nothing about where it was before.
 */
public class ReplayActionRelocator
{
    private record Mark(ActionClip clip, Vec3d position, double yaw, boolean x, boolean y, boolean z, boolean turned) {}

    private final Replay replay;
    private final List<Mark> marks = new ArrayList<>();

    public ReplayActionRelocator(Replay replay)
    {
        this.replay = replay;

        ReplayKeyframes keyframes = replay.keyframes;

        for (Clip clip : replay.actions.get())
        {
            if (clip instanceof ActionClip action)
            {
                float tick = action.tick.get();

                this.marks.add(new Mark(action, position(keyframes, tick), keyframes.yaw.interpolate(tick),
                    has(keyframes.x), has(keyframes.y), has(keyframes.z), has(keyframes.yaw)));
            }
        }
    }

    private static boolean has(KeyframeChannel<?> channel)
    {
        return !channel.getKeyframes().isEmpty();
    }

    private static Vec3d position(ReplayKeyframes keyframes, float tick)
    {
        return new Vec3d(keyframes.x.interpolate(tick), keyframes.y.interpolate(tick), keyframes.z.interpolate(tick));
    }

    /** Moves the actions along with the replay; returns how many of them moved. */
    public int apply()
    {
        List<Mark> moved = new ArrayList<>();
        List<ActionRelocation> relocations = new ArrayList<>();

        for (Mark mark : this.marks)
        {
            float tick = mark.clip.tick.get();
            Vec3d now = position(this.replay.keyframes, tick);
            Vec3d to = new Vec3d(mark.x ? now.x : mark.position.x, mark.y ? now.y : mark.position.y, mark.z ? now.z : mark.position.z);
            double turn = mark.turned ? this.replay.keyframes.yaw.interpolate(tick) - mark.yaw : 0D;
            ActionRelocation relocation = new ActionRelocation(mark.position, to, turn);

            if (!relocation.isIdentity())
            {
                moved.add(mark);
                relocations.add(relocation);
            }
        }

        if (!moved.isEmpty())
        {
            /* One edit of the whole timeline, so one undo takes it all back */
            BaseValue.edit(this.replay.actions, (__) ->
            {
                for (int i = 0; i < moved.size(); i++)
                {
                    moved.get(i).clip.relocate(relocations.get(i));
                }
            });
        }

        return moved.size();
    }
}
