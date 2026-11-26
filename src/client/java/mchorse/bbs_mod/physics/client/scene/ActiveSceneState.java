package mchorse.bbs_mod.physics.client.scene;

import java.util.Map;
import java.util.TreeMap;

/**
 * What a scene's active ragdolls share in one tick: the tick itself, the work budget, and the
 * changes a trigger flow sent to the film while it played.
 *
 * <p><b>The budget is handed out in the scene's own order</b>, which is the cast order and the same
 * on every run, so who runs at full rate and who every second or fourth tick is decided by nothing
 * that changes between a live run, a re-simulation after a scrub and a video export. That is also
 * why it does not look at the camera: a viewport that moves would make the same tick simulate two
 * ways. The first {@link #FULL_RATE} controllers run every tick, the next as many every second,
 * the rest every fourth; one that is off, held by the animation or asleep takes no slot at all.</p>
 *
 * <p><b>A flow's change is pinned to the film tick it arrived on</b>, never read off the clock. The
 * scene is re-simulated from that tick, and from then on the change is part of the film like a
 * keyframe: going back past it and forward again plays it out the same. Only scenes of films played
 * in the world take them; the editor's scene answers to the film alone.</p>
 */
public final class ActiveSceneState
{
    /** Controllers per tick that run every tick before the budget starts thinning them out. */
    public static final int FULL_RATE = 12;

    /** One change a flow made: a strength to use from its tick (NaN keeps the form's, below 0 goes back to it), and a one-off stagger or knockdown on its tick. */
    public record Event(float strength, float stagger, boolean knockdown)
    {
        Event merge(Event next)
        {
            return new Event(Float.isNaN(next.strength) ? this.strength : next.strength, Math.max(this.stagger, next.stagger), this.knockdown || next.knockdown);
        }
    }

    private final TreeMap<Integer, Event> events = new TreeMap<>();
    private int tick;
    private int slots;

    /** A new tick of the scene: the budget starts over. */
    public void begin(int tick)
    {
        this.tick = tick;
        this.slots = 0;
    }

    public int getTick()
    {
        return this.tick;
    }

    /** The next slot of this tick's budget, for a controller that has work to do. */
    public int take()
    {
        return this.slots++;
    }

    /** How often the controller in {@code slot} runs its heavier work: every tick, every second or every fourth. */
    public static int interval(int slot)
    {
        return slot < FULL_RATE ? 1 : slot < FULL_RATE * 2 ? 2 : 4;
    }

    public void add(int tick, Event event)
    {
        this.events.merge(tick, event, Event::merge);
    }

    public boolean hasEvents()
    {
        return !this.events.isEmpty();
    }

    /** The one-off event of this very tick, or null. */
    public Event now()
    {
        return this.events.isEmpty() ? null : this.events.get(this.tick);
    }

    /** The strength a flow set for this tick: NaN when no flow did, or it went back to the form's. */
    public float strength()
    {
        if (this.events.isEmpty())
        {
            return Float.NaN;
        }

        /* The latest change at or before this tick that said anything about the strength */
        for (Map.Entry<Integer, Event> entry = this.events.floorEntry(this.tick); entry != null; entry = this.events.lowerEntry(entry.getKey()))
        {
            float strength = entry.getValue().strength();

            if (!Float.isNaN(strength))
            {
                return strength < 0F ? Float.NaN : strength;
            }
        }

        return Float.NaN;
    }

    public void clear()
    {
        this.events.clear();
    }
}
