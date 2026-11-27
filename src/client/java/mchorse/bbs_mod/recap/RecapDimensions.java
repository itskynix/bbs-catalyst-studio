package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Which dimension the recorded player was in, tick by tick: a table of the dimensions the recap
 * went through (their ids, such as {@code minecraft:the_nether}) and the stretches of it spent in
 * each, a stretch starting at a tick and lasting until the next one starts.
 *
 * <p>The camera and the movement have a frame a tick, so the stretch a frame's tick falls in is the
 * dimension of that frame; the block changes carry their dimension of their own. Kept with the
 * recap's other data in its file. A recap kept before the dimensions were has none
 * ({@link #isEmpty}), and is taken to be in whatever dimension it is shown in.</p>
 */
public class RecapDimensions
{
    private final List<String> ids = new ArrayList<>();
    private int[] starts = new int[4];
    private int[] indices = new int[4];
    private int size;

    public boolean isEmpty()
    {
        return this.size == 0;
    }

    /** How many stretches there are. */
    public int size()
    {
        return this.size;
    }

    public int getStart(int stretch)
    {
        return this.starts[stretch];
    }

    public String getId(int stretch)
    {
        return this.ids.get(this.indices[stretch]);
    }

    /** Whether the recap went through more than one dimension. */
    public boolean isMixed()
    {
        return this.ids.size() > 1;
    }

    public void clear()
    {
        this.ids.clear();
        this.size = 0;
    }

    private int index(String id)
    {
        int index = this.ids.indexOf(id);

        if (index < 0)
        {
            index = this.ids.size();
            this.ids.add(id);
        }

        return index;
    }

    /** The frame of {@code tick} was in dimension {@code id}: a new stretch only when it is another one. */
    public void mark(int tick, String id)
    {
        if (id == null || id.isEmpty())
        {
            return;
        }

        int index = this.index(id);

        if (this.size > 0 && this.indices[this.size - 1] == index)
        {
            return;
        }

        if (this.size > 0 && this.starts[this.size - 1] >= tick)
        {
            /* Two marks on one tick: the later one is what the frame was in */
            this.indices[this.size - 1] = index;

            return;
        }

        this.push(tick, index);
    }

    private void push(int tick, int index)
    {
        if (this.size == this.starts.length)
        {
            this.starts = Arrays.copyOf(this.starts, this.size * 2);
            this.indices = Arrays.copyOf(this.indices, this.size * 2);
        }

        this.starts[this.size] = tick;
        this.indices[this.size] = index;
        this.size += 1;
    }

    /** The stretch {@code tick} falls in; before the first one, the first. -1 when empty. */
    public int stretchAt(int tick)
    {
        if (this.size == 0)
        {
            return -1;
        }

        int low = 0;
        int high = this.size - 1;

        while (low < high)
        {
            int middle = (low + high + 1) >>> 1;

            if (this.starts[middle] <= tick)
            {
                low = middle;
            }
            else
            {
                high = middle - 1;
            }
        }

        return low;
    }

    /** The dimension at {@code tick}, or null when none was kept. */
    public String at(int tick)
    {
        int stretch = this.stretchAt(tick);

        return stretch < 0 ? null : this.getId(stretch);
    }

    /** The first tick spent in {@code id}, or -1 when the recap was never there. */
    public int firstTickIn(String id)
    {
        for (int i = 0; i < this.size; i++)
        {
            if (this.getId(i).equals(id))
            {
                return i == 0 ? 0 : this.starts[i];
            }
        }

        return -1;
    }

    /** The stretches from {@code from} on, their ticks counted from there. */
    public static RecapDimensions tail(RecapDimensions source, int from)
    {
        RecapDimensions dimensions = new RecapDimensions();
        int first = source.stretchAt(from);

        if (first < 0)
        {
            return dimensions;
        }

        for (int i = first; i < source.size; i++)
        {
            dimensions.mark(Math.max(0, source.starts[i] - from), source.getId(i));
        }

        return dimensions;
    }

    /** Forgets the stretches wholly before {@code oldest}; the one it falls in is kept. */
    public void dropBefore(int oldest)
    {
        int first = this.stretchAt(oldest);

        if (first <= 0)
        {
            return;
        }

        System.arraycopy(this.starts, first, this.starts, 0, this.size - first);
        System.arraycopy(this.indices, first, this.indices, 0, this.size - first);
        this.size -= first;
    }

    public MapType toData()
    {
        MapType data = new MapType();
        ListType ids = new ListType();
        ListType stretches = new ListType();

        for (String id : this.ids)
        {
            ids.addString(id);
        }

        for (int i = 0; i < this.size; i++)
        {
            stretches.addInt(this.starts[i]);
            stretches.addInt(this.indices[i]);
        }

        data.put("ids", ids);
        data.put("stretches", stretches);

        return data;
    }

    public static RecapDimensions fromData(MapType data)
    {
        RecapDimensions dimensions = new RecapDimensions();

        if (data == null)
        {
            return dimensions;
        }

        List<String> ids = new ArrayList<>();

        for (BaseType id : data.getList("ids"))
        {
            if (id.isString())
            {
                ids.add(id.asString());
            }
        }

        ListType stretches = data.getList("stretches");

        for (int i = 0; i + 1 < stretches.size(); i += 2)
        {
            BaseType start = stretches.get(i);
            BaseType index = stretches.get(i + 1);

            if (BaseType.isNumeric(start) && BaseType.isNumeric(index))
            {
                int at = index.asNumeric().intValue();

                if (at >= 0 && at < ids.size())
                {
                    dimensions.mark(Math.max(0, start.asNumeric().intValue()), ids.get(at));
                }
            }
        }

        return dimensions;
    }

    public void copy(RecapDimensions other)
    {
        this.clear();

        for (int i = 0; i < other.size; i++)
        {
            this.mark(other.starts[i], other.getId(i));
        }
    }
}
