package mchorse.bbs_mod.nav;

/**
 * What an AI actor knows and is in the middle of, kept apart for each actor: the memory a decision
 * tree reads and writes, the way a blackboard is in a behaviour tree of Unreal Engine. The decisions
 * themselves are made in {@link AiDecisionEngine}; this holds only the facts they stand on, so a
 * tree of nodes can later take the place of the engine without the state moving anywhere.
 */
public final class AiBlackboard
{
    /** What the actor is doing right now */
    public enum Action
    {
        /** On its way, at a walk */
        WALK,
        /** On its way, at a run, for as long as it has the breath */
        RUN,
        /** Standing, looking about */
        IDLE_LOOK,
        /** Stopped half way, as if it thought better of it: it turns and goes back the way it came */
        HESITATE_TURN_BACK,
        /** In the air for a hop, over a step or for the fun of it */
        JUMP
    }

    /** The node it is walking to */
    public NavGrid.Node currentGoalNode;

    /** The node it came from, which it does not just go back to */
    public NavGrid.Node previousNode;

    /** The node it last stood on, the one it chooses the way on from */
    public NavGrid.Node currentNode;

    public Action activeAction = Action.WALK;

    /** Ticks left of what it is doing, for the actions that last a set time */
    public int actionTimer;

    /** Breath, 0 to 1: a run uses it up, a walk and a rest give it back */
    public float stamina = 1F;

    /** Where the head looks while it stands, off the body, in degrees yaw and pitch */
    public float lookYawOffset;
    public float lookPitchOffset;

    /** Ticks before it is let to think of stopping again, so it is not at every node */
    public int decisionCooldown;

    /** How many nodes it has gone on in much the same direction */
    public int stepsTakenInDirection;

    /* What the engine keeps for itself */

    /** The way it went on the last leg, as a unit vector on the ground */
    double directionX;
    double directionZ;

    /** Whether it is at a run, apart from what else it is doing: a hop does not end a run */
    boolean running;
    int runTimer;

    /** Ticks of the hop left, and how many it takes */
    int jumpTicks;

    /** Ticks until the head picks a new place to look at while it stands */
    int lookRetarget;

    /** It means to go back the way it came when it moves again */
    boolean reverse;
}
