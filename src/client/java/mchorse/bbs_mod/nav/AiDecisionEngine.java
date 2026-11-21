package mchorse.bbs_mod.nav;

import java.util.ArrayList;
import java.util.List;

/**
 * The decisions of an AI actor that walks about on its own, made on its {@link AiBlackboard}: where
 * to go on at a node and not just back, when to stop and look round, when to run and when it is
 * out of breath, when to change its mind half way and turn back, when to hop.
 *
 * <p>An actor does not walk to a far place picked at random and wait. It goes a node at a time: on
 * reaching one it looks at where it can go, perhaps stops, perhaps breaks into a run, and picks the
 * next with a liking for going on the way it was going. Where the ground is a painted path that is
 * what a junction is - two, three or four ways out - and on open ground it is the same, only with
 * more ways. Every random choice is drawn from the actor's own generator, so the same film is the
 * same walk, and baking it into keyframes keeps all of it.</p>
 */
final class AiDecisionEngine
{
    /** How much faster than a walk a run is */
    static final float RUN_FACTOR = 1.8F;

    /** How high a hop goes, in blocks, and how long it takes, in ticks */
    private static final double HOP_HEIGHT = 0.45D;
    private static final int HOP_TICKS = 12;

    private AiDecisionEngine()
    {}

    /** What the actor's speed is multiplied by: a run is faster than a walk */
    static float speedFactor(AiSimulation.Agent agent)
    {
        return agent.bb.running ? RUN_FACTOR : 1F;
    }

    /** Called once a tick: breath, timers, the head looking round while it stands, the hop */
    static void tick(AiSimulation.Agent agent)
    {
        AiBlackboard bb = agent.bb;
        boolean standing = bb.activeAction == AiBlackboard.Action.IDLE_LOOK || bb.activeAction == AiBlackboard.Action.HESITATE_TURN_BACK;

        if (bb.decisionCooldown > 0)
        {
            bb.decisionCooldown -= 1;
        }

        if (bb.running && agent.moving)
        {
            bb.stamina = Math.max(0F, bb.stamina - 0.0035F);
            bb.runTimer -= 1;

            /* Out of breath, or run long enough: back to a walk */
            if (bb.stamina < 0.2F || bb.runTimer <= 0)
            {
                bb.running = false;
            }
        }
        else
        {
            bb.stamina = Math.min(1F, bb.stamina + (standing ? 0.006F : 0.0025F));
        }

        if (standing)
        {
            bb.actionTimer = Math.max(0, bb.actionTimer - 1);

            if (bb.activeAction == AiBlackboard.Action.IDLE_LOOK && --bb.lookRetarget <= 0)
            {
                /* Somewhere else to look: left or right of where it looked, not the same place again */
                float side = bb.lookYawOffset > 0F ? -1F : 1F;

                bb.lookYawOffset = side * (20F + agent.random.nextFloat() * 50F);
                bb.lookPitchOffset = (agent.random.nextFloat() * 2F - 1F) * 12F;
                bb.lookRetarget = 25 + agent.random.nextInt(30);
            }
        }

        /* The hop is an arc over the ground the walk is on */
        if (bb.jumpTicks > 0)
        {
            double t = 1D - bb.jumpTicks / (double) HOP_TICKS;

            agent.hop = 4D * HOP_HEIGHT * t * (1D - t);
            bb.jumpTicks -= 1;

            if (bb.jumpTicks == 0)
            {
                agent.hop = 0D;
            }
        }

        bb.activeAction = bb.jumpTicks > 0 ? AiBlackboard.Action.JUMP : (standing ? bb.activeAction : (bb.running ? AiBlackboard.Action.RUN : AiBlackboard.Action.WALK));
    }

    /** A hop over a step up, now and then, as it gets to it */
    static void reached(AiSimulation.Agent agent, NavGrid.Node node)
    {
        AiBlackboard bb = agent.bb;

        if (bb.jumpTicks == 0 && node.y - agent.y >= 0.4D && agent.random.nextFloat() < 0.5F)
        {
            bb.jumpTicks = HOP_TICKS;
        }
    }

    /**
     * It has got to a node of its wandering. Decides what next: stops for a look round, stops and
     * thinks better of the way it was going, or goes on to another node, which is put at the end of
     * its path. Returns whether it goes on; when it stops its path is dropped and the time it stops
     * for is its idle time.
     */
    static boolean arrive(AiSimulation.Agent agent, NavGrid grid, NavGrid.Node node)
    {
        AiBlackboard bb = agent.bb;

        bb.previousNode = bb.currentNode == node ? bb.previousNode : bb.currentNode;
        bb.currentNode = node;

        List<NavGrid.Node> open = neighbors(grid, node, null);
        List<NavGrid.Node> ahead = neighbors(grid, node, bb.previousNode);

        /* A junction of the painted paths: three to five ways out; open ground has more and is nothing to stop at */
        boolean junction = open.size() >= 3 && open.size() <= 5;

        if (bb.decisionCooldown == 0 && bb.stepsTakenInDirection >= 2 && bb.previousNode != null && agent.random.nextFloat() < 0.07F)
        {
            /* Half way, it thinks better of it: a pause with the head turned a little, and then back */
            bb.reverse = true;
            pause(agent, AiBlackboard.Action.HESITATE_TURN_BACK, 8 + agent.random.nextInt(7));
            bb.lookYawOffset = (agent.random.nextBoolean() ? 1F : -1F) * (25F + agent.random.nextFloat() * 15F);
            bb.lookPitchOffset = 0F;
            bb.decisionCooldown = 120;

            return false;
        }

        if (bb.decisionCooldown == 0 && agent.random.nextFloat() < (junction ? 0.35F : 0.05F))
        {
            float min = Math.min(agent.settings.aiIdleMin.get(), agent.settings.aiIdleMax.get());
            float max = Math.max(agent.settings.aiIdleMin.get(), agent.settings.aiIdleMax.get());
            float seconds = max <= 0F ? 1.5F + agent.random.nextFloat() * 2.5F : min + agent.random.nextFloat() * (max - min);

            pause(agent, AiBlackboard.Action.IDLE_LOOK, Math.max(10, Math.round(seconds * 20F)));
            bb.decisionCooldown = 60;

            return false;
        }

        if (ahead.isEmpty())
        {
            /* A dead end: a look round, and back the way it came */
            bb.reverse = bb.previousNode != null;
            pause(agent, AiBlackboard.Action.IDLE_LOOK, 20 + agent.random.nextInt(30));

            return false;
        }

        /* Breath, and a mood for running: a run does not start on a tired chest */
        if (!bb.running && bb.stamina > 0.7F && agent.random.nextFloat() < 0.2F)
        {
            bb.running = true;
            bb.runTimer = 60 + agent.random.nextInt(80);
        }

        /* A hop for the fun of it, when it runs */
        if (bb.running && bb.jumpTicks == 0 && agent.random.nextFloat() < 0.08F)
        {
            bb.jumpTicks = HOP_TICKS;
        }

        NavGrid.Node next = pick(agent, node, ahead);

        go(agent, node, next);

        return true;
    }

    /** Nothing to do and nowhere to go yet: picks the first node to go to from where it is */
    static void choose(AiSimulation.Agent agent, NavGrid grid)
    {
        AiBlackboard bb = agent.bb;
        NavGrid.Node here = grid.nearest(agent.x, agent.y, agent.z);

        bb.activeAction = bb.running ? AiBlackboard.Action.RUN : AiBlackboard.Action.WALK;
        bb.lookYawOffset = 0F;
        bb.lookPitchOffset = 0F;

        if (here == null)
        {
            agent.idle = 20;

            return;
        }

        if (bb.currentNode == null || bb.currentNode == here)
        {
            bb.currentNode = here;
        }

        NavGrid.Node next;

        if (bb.reverse && bb.previousNode != null)
        {
            /* It thought better of it: the way it came */
            next = bb.previousNode;
            bb.reverse = false;
            bb.stepsTakenInDirection = 0;
        }
        else
        {
            bb.reverse = false;

            List<NavGrid.Node> ahead = neighbors(grid, here, bb.previousNode);

            if (ahead.isEmpty())
            {
                ahead = neighbors(grid, here, null);
            }

            if (ahead.isEmpty())
            {
                agent.idle = 40;

                return;
            }

            next = pick(agent, here, ahead);
        }

        if (next == bb.previousNode)
        {
            /* going back: what was behind is now ahead */
            bb.previousNode = bb.currentNode;
        }

        go(agent, here, next);
    }

    /** Sets the actor walking from one node to the next */
    private static void go(AiSimulation.Agent agent, NavGrid.Node from, NavGrid.Node to)
    {
        AiBlackboard bb = agent.bb;
        double dx = to.x - from.x;
        double dz = to.z - from.z;
        double length = Math.hypot(dx, dz);

        if (length > 1.0E-6D)
        {
            dx /= length;
            dz /= length;

            /* Going on much the same way, or on another */
            if (dx * bb.directionX + dz * bb.directionZ > 0.6D)
            {
                bb.stepsTakenInDirection += 1;
            }
            else
            {
                bb.stepsTakenInDirection = 1;
            }

            bb.directionX = dx;
            bb.directionZ = dz;
        }

        bb.currentGoalNode = to;

        if (agent.path == null || !agent.wandering)
        {
            agent.path = new ArrayList<>();
            agent.path.add(from);
            agent.next = 1;
        }

        agent.path.add(to);
        agent.moving = true;
        agent.wandering = true;
        agent.goalX = to.centerX();
        agent.goalZ = to.centerZ();
    }

    /** Stops where it is for a number of ticks, as one thing or another */
    private static void pause(AiSimulation.Agent agent, AiBlackboard.Action action, int ticks)
    {
        AiBlackboard bb = agent.bb;

        agent.moving = false;
        agent.path = null;
        agent.wandering = false;
        agent.idle = ticks;
        bb.activeAction = action;
        bb.actionTimer = ticks;
        bb.running = false;
        bb.stepsTakenInDirection = 0;

        if (action == AiBlackboard.Action.IDLE_LOOK)
        {
            bb.lookYawOffset = (agent.random.nextBoolean() ? 1F : -1F) * (20F + agent.random.nextFloat() * 50F);
            bb.lookPitchOffset = (agent.random.nextFloat() * 2F - 1F) * 12F;
            bb.lookRetarget = 25 + agent.random.nextInt(30);
        }
    }

    /** The nodes it could step to from a node, without the one it came from when that is given */
    private static List<NavGrid.Node> neighbors(NavGrid grid, NavGrid.Node node, NavGrid.Node without)
    {
        List<NavGrid.Node> out = new ArrayList<>();

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                if (dx == 0 && dz == 0)
                {
                    continue;
                }

                NavGrid.Node next = grid.neighbor(node, dx, dz);

                if (next != null && next != without && !out.contains(next))
                {
                    out.add(next);
                }
            }
        }

        return out;
    }

    /**
     * One of the ways on, by a liking: going straight on is liked most, a slight bend nearly as
     * much, a right angle less, and what is behind hardly at all; the edge of the ground a little
     * less than the middle of it.
     */
    private static NavGrid.Node pick(AiSimulation.Agent agent, NavGrid.Node from, List<NavGrid.Node> ways)
    {
        AiBlackboard bb = agent.bb;
        double[] weights = new double[ways.size()];
        double total = 0D;

        for (int i = 0; i < ways.size(); i++)
        {
            NavGrid.Node way = ways.get(i);
            double dx = way.x - from.x;
            double dz = way.z - from.z;
            double length = Math.max(1.0E-6D, Math.hypot(dx, dz));
            double dot = bb.directionX == 0D && bb.directionZ == 0D ? 1D : (dx / length) * bb.directionX + (dz / length) * bb.directionZ;
            double weight = dot > 0.9D ? 3D : (dot > 0.5D ? 1.8D : (dot > -0.1D ? 0.9D : 0.15D));

            if (way.edge)
            {
                weight *= 0.6D;
            }

            weights[i] = weight * (0.7D + agent.random.nextDouble() * 0.6D);
            total += weights[i];
        }

        double roll = agent.random.nextDouble() * total;

        for (int i = 0; i < weights.length; i++)
        {
            roll -= weights[i];

            if (roll <= 0D)
            {
                return ways.get(i);
            }
        }

        return ways.get(ways.size() - 1);
    }
}
