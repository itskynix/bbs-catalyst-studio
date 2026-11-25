package mchorse.bbs_mod.ui.model_blocks;

import mchorse.bbs_mod.blocks.entities.ModelProperties;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;

/**
 * Undo and redo of one model block, as whole snapshots of its properties: whatever changes them (a
 * field, a gizmo drag, the form editor, a key) ends up in the history, without every control having
 * to say so. A change becomes a step only once it has settled: nothing is being held or typed and it
 * stayed the same for a few ticks, so a drag or a typed number is one step, not a hundred.
 */
public class ModelBlockHistory
{
    private static final int LIMIT = 100;
    private static final int SETTLE_TICKS = 6;

    private final List<String> steps = new ArrayList<>();
    private int index;
    private String pending;
    private int quiet;

    public ModelBlockHistory(ModelProperties properties)
    {
        this.steps.add(write(properties));
    }

    private static String write(ModelProperties properties)
    {
        return DataToString.toString(properties.toData());
    }

    /** Called every tick; {@code busy} while the mouse is held or a value is being edited. */
    public void poll(ModelProperties properties, boolean busy)
    {
        String now = write(properties);

        if (now.equals(this.steps.get(this.index)))
        {
            this.pending = null;
        }
        else if (!now.equals(this.pending))
        {
            this.pending = now;
            this.quiet = 0;
        }
        else if (!busy && ++this.quiet >= SETTLE_TICKS)
        {
            this.push(now);
        }
    }

    /** Makes whatever is not on the history yet a step of its own. */
    public void commit(ModelProperties properties)
    {
        String now = write(properties);

        if (!now.equals(this.steps.get(this.index)))
        {
            this.push(now);
        }

        this.pending = null;
    }

    private void push(String state)
    {
        while (this.steps.size() > this.index + 1)
        {
            this.steps.remove(this.steps.size() - 1);
        }

        this.steps.add(state);

        if (this.steps.size() > LIMIT)
        {
            this.steps.remove(0);
        }

        this.index = this.steps.size() - 1;
        this.pending = null;
    }

    /** The properties to go back to, or null when there is nothing older. */
    public MapType undo(ModelProperties properties)
    {
        this.commit(properties);

        if (this.index <= 0)
        {
            return null;
        }

        this.index -= 1;

        return DataToString.mapFromString(this.steps.get(this.index));
    }

    /** The properties to go forward to, or null when there is nothing newer. */
    public MapType redo(ModelProperties properties)
    {
        this.commit(properties);

        if (this.index >= this.steps.size() - 1)
        {
            return null;
        }

        this.index += 1;

        return DataToString.mapFromString(this.steps.get(this.index));
    }

    public boolean canUndo()
    {
        return this.index > 0;
    }

    public boolean canRedo()
    {
        return this.index < this.steps.size() - 1;
    }
}
