package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where the Recap Studio editor's panels are: the side column (record info over the view settings,
 * or the other way round) on the left or the right and how wide, the timeline above or below and how
 * tall, under the viewport alone or across the whole width, and how the side column is shared.
 *
 * <p>The layout in use and the author's own templates live in {@code <game>/bbs_cs/recap_layouts.json},
 * next to the recaps rather than among them.</p>
 */
public class RecapLayout
{
    public static final int MIN_SIDE = 150;
    public static final int MIN_TIMELINE = 90;

    public boolean sideLeft;
    public int sideWidth = 210;
    public boolean timelineTop;
    public int timelineHeight = 180;
    public boolean timelineFull;
    public boolean infoVisible = true;
    public boolean infoFirst = true;
    public float infoRatio = 0.38F;
    /** Whether the view settings panel is shown. */
    public boolean viewVisible = true;
    /** Whether the picked clip's panel is shown. */
    public boolean clipVisible = true;

    /** A ready made layout: its key (for its name) and how it is arranged. */
    public record Preset(String key, RecapLayout layout)
    {}

    public static List<Preset> presets()
    {
        RecapLayout standard = new RecapLayout();

        RecapLayout stage = new RecapLayout();

        stage.sideWidth = 160;
        stage.timelineHeight = 120;
        stage.infoVisible = false;

        RecapLayout cutting = new RecapLayout();

        cutting.sideLeft = true;
        cutting.sideWidth = 240;
        cutting.timelineHeight = 240;
        cutting.timelineFull = true;
        cutting.infoRatio = 0.45F;

        RecapLayout review = new RecapLayout();

        review.sideWidth = 230;
        review.timelineTop = true;
        review.timelineHeight = 140;
        review.infoFirst = false;
        review.infoRatio = 0.5F;

        return List.of(new Preset("standard", standard), new Preset("stage", stage), new Preset("cutting", cutting), new Preset("review", review));
    }

    public RecapLayout copy()
    {
        RecapLayout layout = new RecapLayout();

        layout.set(this);

        return layout;
    }

    public void set(RecapLayout other)
    {
        this.sideLeft = other.sideLeft;
        this.sideWidth = other.sideWidth;
        this.timelineTop = other.timelineTop;
        this.timelineHeight = other.timelineHeight;
        this.timelineFull = other.timelineFull;
        this.infoVisible = other.infoVisible;
        this.infoFirst = other.infoFirst;
        this.infoRatio = other.infoRatio;
        this.viewVisible = other.viewVisible;
        this.clipVisible = other.clipVisible;
    }

    public boolean same(RecapLayout other)
    {
        return this.toData().toString().equals(other.toData().toString());
    }

    public MapType toData()
    {
        MapType data = new MapType();

        data.putBool("side_left", this.sideLeft);
        data.putInt("side_width", this.sideWidth);
        data.putBool("timeline_top", this.timelineTop);
        data.putInt("timeline_height", this.timelineHeight);
        data.putBool("timeline_full", this.timelineFull);
        data.putBool("info_visible", this.infoVisible);
        data.putBool("info_first", this.infoFirst);
        data.putFloat("info_ratio", this.infoRatio);
        data.putBool("view_visible", this.viewVisible);
        data.putBool("clip_visible", this.clipVisible);

        return data;
    }

    public static RecapLayout fromData(MapType data)
    {
        RecapLayout layout = new RecapLayout();

        if (data == null)
        {
            return layout;
        }

        layout.sideLeft = data.getBool("side_left", layout.sideLeft);
        layout.sideWidth = Math.max(MIN_SIDE, data.getInt("side_width", layout.sideWidth));
        layout.timelineTop = data.getBool("timeline_top", layout.timelineTop);
        layout.timelineHeight = Math.max(MIN_TIMELINE, data.getInt("timeline_height", layout.timelineHeight));
        layout.timelineFull = data.getBool("timeline_full", layout.timelineFull);
        layout.infoVisible = data.getBool("info_visible", layout.infoVisible);
        layout.infoFirst = data.getBool("info_first", layout.infoFirst);
        layout.infoRatio = Math.max(0.15F, Math.min(0.85F, data.getFloat("info_ratio", layout.infoRatio)));
        layout.viewVisible = data.getBool("view_visible", layout.viewVisible);
        layout.clipVisible = data.getBool("clip_visible", layout.clipVisible);

        return layout;
    }

    /* The file: the layout in use and the author's templates */

    public static final class Store
    {
        public final RecapLayout current;
        public final Map<String, RecapLayout> templates = new LinkedHashMap<>();
        /** Whether the panels are held where they are: no moving, no resizing. */
        public boolean locked;

        private Store(RecapLayout current)
        {
            this.current = current;
        }

        private static File file()
        {
            File folder = new File(MinecraftClient.getInstance().runDirectory, "bbs_cs");

            folder.mkdirs();

            return new File(folder, "recap_layouts.json");
        }

        public static Store load()
        {
            try
            {
                File file = file();
                BaseType data = file.isFile() ? DataToString.read(file) : null;

                if (data != null && data.isMap())
                {
                    MapType map = data.asMap();
                    Store store = new Store(fromData(map.getMap("current")));

                    store.locked = map.getBool("locked", false);
                    MapType templates = map.getMap("templates");

                    if (templates != null)
                    {
                        for (String name : templates.keys())
                        {
                            store.templates.put(name, fromData(templates.getMap(name)));
                        }
                    }

                    return store;
                }
            }
            catch (Exception e)
            {
                mchorse.bbs_mod.BBSMod.LOGGER.warn("The recap layouts could not be read", e);
            }

            return new Store(new RecapLayout());
        }

        public void save()
        {
            MapType data = new MapType();
            MapType templates = new MapType();

            for (Map.Entry<String, RecapLayout> entry : this.templates.entrySet())
            {
                templates.put(entry.getKey(), entry.getValue().toData());
            }

            data.put("current", this.current.toData());
            data.put("templates", templates);
            data.putBool("locked", this.locked);
            DataToString.writeSilently(file(), data, true);
        }
    }
}
