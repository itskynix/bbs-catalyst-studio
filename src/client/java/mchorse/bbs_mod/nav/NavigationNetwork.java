package mchorse.bbs_mod.nav;

import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * What the navigation wand leaves in a scene: boxes of ground to walk about on, and painted path
 * blocks. Kept as plain text, a line for each - {@code B minX minY minZ sizeX sizeY sizeZ} for a box
 * and {@code P x y z} for a path block - so it can be saved in a film, or in a file, without more
 * than a string. Where blocks of a path are in neighbouring columns they are joined; that is all a
 * junction is.
 */
public final class NavigationNetwork
{
    /** A box of ground, the corner of lowest coordinates and the size in blocks */
    public record Box(int x, int y, int z, int sx, int sy, int sz) {}

    public final List<Box> boxes = new ArrayList<>();
    public final Set<BlockPos> path = new LinkedHashSet<>();

    public boolean isEmpty()
    {
        return this.boxes.isEmpty() && this.path.isEmpty();
    }

    public NavigationNetwork copy()
    {
        NavigationNetwork copy = new NavigationNetwork();

        copy.boxes.addAll(this.boxes);
        copy.path.addAll(this.path);

        return copy;
    }

    public String toText()
    {
        StringBuilder out = new StringBuilder();

        for (Box box : this.boxes)
        {
            out.append("B ").append(box.x).append(' ').append(box.y).append(' ').append(box.z).append(' ').append(box.sx).append(' ').append(box.sy).append(' ').append(box.sz).append('\n');
        }

        for (BlockPos pos : this.path)
        {
            out.append("P ").append(pos.getX()).append(' ').append(pos.getY()).append(' ').append(pos.getZ()).append('\n');
        }

        return out.toString();
    }

    public static NavigationNetwork fromText(String text)
    {
        NavigationNetwork network = new NavigationNetwork();

        if (text == null)
        {
            return network;
        }

        for (String line : text.split("\n"))
        {
            String[] parts = line.trim().split(" ");

            try
            {
                if (parts[0].equals("B") && parts.length >= 7)
                {
                    network.boxes.add(new Box(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), Integer.parseInt(parts[5]), Integer.parseInt(parts[6])));
                }
                else if (parts[0].equals("P") && parts.length >= 4)
                {
                    network.path.add(new BlockPos(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
                }
            }
            catch (NumberFormatException e)
            {
                /* a damaged line is a line less */
            }
        }

        return network;
    }
}
