package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Where the navigation wand's boxes and paths live, and the mesh made of them.
 *
 * <p>With a film open in the editor they are kept in the film (so they go where the film goes and
 * what the film's AI walks on is in it); with none they are kept for the world, in a file under
 * BBS's settings folder. The mesh the AI walks on, {@link #scene}, is the meshes of the film's
 * navigation volumes and of these joined into one, so a volume and a painted path work together.</p>
 */
public final class NavigationNetworks
{
    /** How long a mesh of the network is kept before the blocks are looked at again, in ticks */
    private static final long RESCAN_TICKS = 200L;

    private static class Entry
    {
        String text = "";
        long scanned = Long.MIN_VALUE;
        NavGrid grid;
    }

    /** The meshes made lately, by the text they were made of */
    private static final Map<String, Entry> MESHES = new java.util.LinkedHashMap<>();

    /** The last mesh joined, and what it was joined from, so that asking again every frame costs nothing */
    private static NavGrid lastVolume;
    private static NavGrid lastNetwork;
    private static NavGrid lastScene;

    private NavigationNetworks()
    {}

    /** The film open in the editor, or null */
    public static Film activeFilm()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        return panel == null ? null : panel.getData();
    }

    /* Storage */

    private static File file()
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        String name = "world";

        if (mc.getServer() != null)
        {
            name = mc.getServer().getSaveProperties().getLevelName();
        }
        else if (mc.getCurrentServerEntry() != null)
        {
            name = mc.getCurrentServerEntry().address;
        }

        return new File(new File(BBSMod.getSettingsFolder(), "navigation"), name.replaceAll("[^a-zA-Z0-9._-]", "_") + ".txt");
    }

    /** The network of the film when it has one, else the world's */
    public static NavigationNetwork get(Film film)
    {
        if (film != null && !film.navigation.get().isEmpty())
        {
            return NavigationNetwork.fromText(film.navigation.get());
        }

        return NavigationNetwork.fromText(readWorld());
    }

    private static String readWorld()
    {
        try
        {
            File file = file();

            return file.exists() ? new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8) : "";
        }
        catch (Exception e)
        {
            return "";
        }
    }

    /** Writes the network where {@link #get} reads it from: the film when one is open, else the world's file */
    public static void save(Film film, NavigationNetwork network)
    {
        String text = network.toText();

        if (film != null)
        {
            film.navigation.set(text);

            return;
        }

        try
        {
            File file = file();

            file.getParentFile().mkdirs();
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    /* Meshes */

    /**
     * Everything saved for the scene, as one text: what the given film holds (the one being played),
     * what the film open in the editor holds when it is another, and what the world's file holds.
     * A box or a path block saved in any of them is walked on in all, whichever one the wand wrote to.
     */
    private static String sceneText(Film film)
    {
        java.util.LinkedHashSet<String> lines = new java.util.LinkedHashSet<>();
        Film editor = activeFilm();

        for (String text : new String[] {film == null ? "" : film.navigation.get(), editor == null || editor == film ? "" : editor.navigation.get(), readWorldCached()})
        {
            for (String line : text.split("\n"))
            {
                if (!line.isBlank())
                {
                    lines.add(line.trim());
                }
            }
        }

        return String.join("\n", lines);
    }

    /** The mesh of what is saved for the scene, or null when there is none; made again when that changes, and every ten seconds for the blocks */
    public static NavGrid grid(Film film)
    {
        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null)
        {
            return null;
        }

        String text = sceneText(film);

        if (text.isEmpty())
        {
            return null;
        }

        Entry entry = MESHES.get(text);
        long now = world.getTime();

        if (entry != null && entry.grid != null && now - entry.scanned < RESCAN_TICKS && now >= entry.scanned)
        {
            return entry.grid;
        }

        if (entry == null)
        {
            entry = new Entry();
            entry.text = text;
            MESHES.put(text, entry);

            while (MESHES.size() > 4)
            {
                MESHES.remove(MESHES.keySet().iterator().next());
            }
        }

        entry.scanned = now;
        entry.grid = build(world, NavigationNetwork.fromText(text));

        return entry.grid;
    }

    private static String cachedWorldText = "";
    private static long cachedWorldRead = Long.MIN_VALUE;

    /** The world's file is read once in a while, not every frame */
    private static String readWorldCached()
    {
        long now = System.currentTimeMillis();

        if (now - cachedWorldRead > 2000L)
        {
            cachedWorldText = readWorld();
            cachedWorldRead = now;
        }

        return cachedWorldText;
    }

    /** Makes the next read of the world's file a fresh one, after it was written */
    public static void invalidate()
    {
        cachedWorldRead = Long.MIN_VALUE;
        MESHES.clear();
        lastScene = null;
    }

    private static NavGrid build(ClientWorld world, NavigationNetwork network)
    {
        List<NavGrid> grids = new ArrayList<>();

        for (NavigationNetwork.Box box : network.boxes)
        {
            /* One block under the box too: a box selected on the ground has its floor in the block below it */
            grids.add(NavGrid.build(world, box.x(), box.y() - 1, box.z(), box.sx(), box.sy() + 1, box.sz(), 0.3F, 1.8F, 1F));
        }

        if (!network.path.isEmpty())
        {
            grids.add(NavGrid.buildFromBlocks(world, network.path, 1.8F, 1F));
        }

        return grids.isEmpty() ? null : NavGrid.merge(grids);
    }

    /**
     * The mesh a scene's AI and followers walk on: the mesh of the navigation volume, when there is
     * one, and the wand's boxes and paths joined to it. Null when there is nothing to walk on.
     */
    public static NavGrid scene(Film film, NavGrid volume)
    {
        NavGrid network = grid(film);

        if (network == null || network.getNodes().isEmpty())
        {
            return volume;
        }

        if (volume == null || volume.getNodes().isEmpty())
        {
            return network;
        }

        if (volume != lastVolume || network != lastNetwork || lastScene == null)
        {
            lastScene = NavGrid.merge(List.of(volume, network));
            lastVolume = volume;
            lastNetwork = network;
        }

        return lastScene;
    }
}
