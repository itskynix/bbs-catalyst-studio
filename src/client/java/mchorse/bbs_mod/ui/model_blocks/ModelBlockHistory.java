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
 *
 * <p>A snapshot keeps the data itself and is restored from it. Its text is only ever compared, never
 * read back: the text format cannot parse every key a form or an item may hold.</p>
 */
public class ModelBlockHistory
{
    private static final int LIMIT = 100;
    private static final int SETTLE_TICKS = 6;

    private record Step(String key, MapType data) {}

    private final List<Step> steps = new ArrayList<>();
    private int index;
    private String pending;
    private int quiet;

    public ModelBlockHistory(ModelProperties properties)
    {
        this.steps.add(read(properties));
    }

    private static Step read(ModelProperties properties)
    {
        MapType data = (MapType) properties.toData().copy();

        return new Step(DataToString.toString(data), data);
    }

    /** Called every tick; {@code busy} while the mouse is held or a value is being edited. */
    public void poll(ModelProperties properties, boolean busy)
    {
        Step now = read(properties);

        if (now.key.equals(this.steps.get(this.index).key))
        {
            this.pending = null;
        }
        else if (!now.key.equals(this.pending))
        {
            this.pending = now.key;
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
        Step now = read(properties);

        if (!now.key.equals(this.steps.get(this.index).key))
        {
            this.push(now);
        }

        this.pending = null;
    }

    private void push(Step step)
    {
        while (this.steps.size() > this.index + 1)
        {
            this.steps.remove(this.steps.size() - 1);
        }

        this.steps.add(step);

        if (this.steps.size() > LIMIT)
        {
            this.steps.remove(0);
        }

        this.index = this.steps.size() - 1;
        this.pending = null;
    }

    /**
     * Steps back and puts that state into the properties. Returns false when there is nothing older.
     */
    public boolean undo(ModelProperties properties)
    {
        this.commit(properties);

        if (this.index <= 0)
        {
            return false;
        }

        this.index -= 1;
        this.apply(properties);

        return true;
    }

    /** Steps forward again. Returns false when there is nothing newer. */
    public boolean redo(ModelProperties properties)
    {
        this.commit(properties);

        if (this.index >= this.steps.size() - 1)
        {
            return false;
        }

        this.index += 1;
        this.apply(properties);

        return true;
    }

    private void apply(ModelProperties properties)
    {
        MapType data = this.steps.get(this.index).data;

        properties.fromData((MapType) data.copy());
        /* What the properties now write is this step, even if loading tidied something up: otherwise
         * the next tick would take it for a new edit and throw the redo steps away */
        this.steps.set(this.index, new Step(read(properties).key, data));
        this.pending = null;
    }

    /** Whether there is an older state, counting a change that has not settled yet. */
    public boolean canUndo()
    {
        return this.index > 0 || this.pending != null;
    }

    public boolean canRedo()
    {
        return this.pending == null && this.index < this.steps.size() - 1;
    }
}
