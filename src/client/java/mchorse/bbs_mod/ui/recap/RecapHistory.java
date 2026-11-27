package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.recap.RecapRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The recap editor's history: every state the recap and the layout went through since the recap
 * was opened, oldest first, and which one is on screen. Undo and redo step through it, and any
 * state can be gone back to at once; an edit made after stepping back drops the states after it.
 *
 * <p>Each state is a full copy of what can be edited: the recap's name, markers and view settings,
 * and the panel layout. Going to a state puts that copy back, so steps never drift. A run of edits
 * to the same thing, a trackpad being dragged say, shares a merge key and becomes one step.</p>
 */
public class RecapHistory
{
    public static final int LIMIT = 100;

    public record State(String label, String merge, RecapRecord record, RecapLayout layout)
    {}

    private final List<State> states = new ArrayList<>();
    private int index;

    /** Starts over from the recap as it is now. */
    public void reset(String label, RecapRecord record, RecapLayout layout)
    {
        this.states.clear();
        this.states.add(new State(label, null, copy(record), layout.copy()));
        this.index = 0;
    }

    /**
     * Adds the state after an edit; false when nothing changed. With the same merge key as the step
     * on screen, it takes that step's place instead of adding one.
     */
    public boolean push(String label, String merge, RecapRecord record, RecapLayout layout)
    {
        if (this.states.isEmpty())
        {
            this.reset(label, record, layout);

            return false;
        }

        State current = this.states.get(this.index);

        if (current.record.sameEditable(record) && current.layout.same(layout))
        {
            return false;
        }

        while (this.states.size() > this.index + 1)
        {
            this.states.remove(this.states.size() - 1);
        }

        State state = new State(label, merge, copy(record), layout.copy());

        if (merge != null && this.index > 0 && merge.equals(current.merge))
        {
            this.states.set(this.index, state);

            /* Dragged back to where it started, the step is no step at all */
            State before = this.states.get(this.index - 1);

            if (before.record.sameEditable(record) && before.layout.same(layout))
            {
                this.states.remove(this.index);
                this.index -= 1;
            }

            return true;
        }

        this.states.add(state);
        this.index = this.states.size() - 1;

        while (this.states.size() > LIMIT)
        {
            this.states.remove(0);
            this.index -= 1;
        }

        return true;
    }

    public boolean canUndo()
    {
        return this.index > 0;
    }

    public boolean canRedo()
    {
        return this.index < this.states.size() - 1;
    }

    public State undo()
    {
        return this.canUndo() ? this.jump(this.index - 1) : null;
    }

    public State redo()
    {
        return this.canRedo() ? this.jump(this.index + 1) : null;
    }

    public State jump(int index)
    {
        if (index < 0 || index >= this.states.size())
        {
            return null;
        }

        this.index = index;

        return this.states.get(index);
    }

    public int getIndex()
    {
        return this.index;
    }

    public List<State> getStates()
    {
        return Collections.unmodifiableList(this.states);
    }

    private static RecapRecord copy(RecapRecord record)
    {
        return RecapRecord.fromData(record.id, record.toData());
    }
}
