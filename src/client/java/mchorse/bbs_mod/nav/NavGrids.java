package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.forms.forms.NavigationVolumeForm;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The meshes of the navigation volumes in the world, scanned when first asked for and kept. A mesh is
 * found by the box and the agent it was scanned for, so the volume's form in the editor, the copy of it
 * being drawn and the film's AI all share one scan. With the volume's auto update on, it is scanned
 * again once the blocks have had two seconds to change; the rebuild button scans again at once, by
 * counting up a number the key includes.
 */
public final class NavGrids
{
    private static final long RESCAN_TICKS = 40L;
    private static final int MAX_ENTRIES = 8;

    private static final Map<String, Entry> GRIDS = new LinkedHashMap<>();

    private static class Entry
    {
        NavGrid grid;
        long scanned;
    }

    private NavGrids()
    {}

    /**
     * The mesh of a volume whose box has its bottom middle at the given world point, or null when
     * there is no world.
     */
    public static NavGrid get(NavigationVolumeForm form, double cx, double cy, double cz)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return null;
        }

        int sizeX = (int) Math.ceil(Math.min(form.width.get(), 128F));
        int sizeZ = (int) Math.ceil(Math.min(form.length.get(), 128F));
        /* One block under the bottom of the box too: a volume set on the ground has its floor in the block below it */
        int sizeY = (int) Math.ceil(Math.min(form.height.get(), 64F)) + 1;
        int minX = (int) Math.floor(cx - sizeX / 2D);
        int minZ = (int) Math.floor(cz - sizeZ / 2D);
        int minY = (int) Math.floor(cy) - 1;
        float radius = form.agentRadius.get();
        float height = form.agentHeight.get();
        float step = form.maxStepHeight.get();

        String key = minX + "," + minY + "," + minZ + "," + sizeX + "," + sizeY + "," + sizeZ + "," + radius + "," + height + "," + step + "," + form.rebuild.get();
        Entry entry = GRIDS.get(key);
        long now = world.getTime();

        if (entry != null && (!form.autoUpdate.get() || now - entry.scanned < RESCAN_TICKS))
        {
            return entry.grid;
        }

        if (entry == null)
        {
            entry = new Entry();
            GRIDS.put(key, entry);

            while (GRIDS.size() > MAX_ENTRIES)
            {
                GRIDS.remove(GRIDS.keySet().iterator().next());
            }
        }

        entry.grid = NavGrid.build(world, minX, minY, minZ, sizeX, sizeY, sizeZ, radius, height, step);
        entry.scanned = now;

        return entry.grid;
    }

    public static void clear()
    {
        GRIDS.clear();
    }
}
