package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.entities.IEntity;

/**
 * Client session state tracking the currently active POV clip and its target actor.
 *
 * <p>Used by the first-person hand renderer, arm dynamics, view bobbing, and
 * replay actor culling pipeline to sample actor state at rendering time.</p>
 */
public class POVClientState
{
    private static POVClip activeClip;
    private static IEntity activeActor;
    private static Replay activeReplay;
    private static float activeTransition;
    private static long lastTimestamp;

    public static void setActive(POVClip clip, IEntity actor, float transition)
    {
        setActive(clip, actor, null, transition);
    }

    public static void setActive(POVClip clip, IEntity actor, Replay replay, float transition)
    {
        activeClip = clip;
        activeActor = actor;
        activeReplay = replay;
        activeTransition = transition;
        lastTimestamp = System.currentTimeMillis();
    }

    public static POVClip getActiveClip()
    {
        if (System.currentTimeMillis() - lastTimestamp > 250)
        {
            clear();
        }

        return activeClip;
    }

    public static IEntity getActiveActor()
    {
        if (System.currentTimeMillis() - lastTimestamp > 250)
        {
            clear();
        }

        return activeActor;
    }

    public static Replay getActiveReplay()
    {
        if (System.currentTimeMillis() - lastTimestamp > 250)
        {
            clear();
        }

        return activeReplay;
    }

    public static float getActiveTransition()
    {
        return activeTransition;
    }

    /**
     * Checks whether the given entity or replay should be culled from world rendering.
     * In first-person PoV mode, the actor's head and body model are suppressed to prevent
     * near-plane camera clipping into the skull and eyes, while native first-person hands are drawn.
     */
    public static boolean isActorCulled(IEntity entity, Replay replay)
    {
        POVClip clip = getActiveClip();
        IEntity actor = getActiveActor();

        if (clip == null || !clip.isFirstPerson() || !clip.povOutput.get())
        {
            return false;
        }

        if (actor != null && entity != null && actor == entity)
        {
            return true;
        }

        if (replay != null)
        {
            String id = clip.selector.get();

            if (!id.isEmpty())
            {
                if (replay.getId().equals(id) || replay.getName().equalsIgnoreCase(id))
                {
                    return true;
                }
            }
        }

        return false;
    }

    public static void clear()
    {
        activeClip = null;
        activeActor = null;
        activeReplay = null;
    }
}
