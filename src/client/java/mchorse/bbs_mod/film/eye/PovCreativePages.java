package mchorse.bbs_mod.film.eye;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemGroups;

/**
 * The pages of the creative tabs, which Fabric adds when mods bring more tabs than fit: the page a
 * tab is on, and the page an open creative screen shows. Read through Fabric's implementation, as
 * there is no public way to; without it (or with another one) everything is on page 0.
 */
public final class PovCreativePages
{
    private PovCreativePages()
    {}

    /** The page a tab is on; the search, the inventory and the saved hotbars are on every page */
    public static boolean isOnPage(ItemGroup group, int page)
    {
        if (group.getType() != ItemGroup.Type.CATEGORY)
        {
            return true;
        }

        try
        {
            return (group instanceof net.fabricmc.fabric.impl.itemgroup.FabricItemGroup fabric ? fabric.getPage() : 0) == page;
        }
        catch (LinkageError e)
        {
            return page == 0;
        }
    }

    /** How many pages the tabs take: one, unless mods bring more tabs than fit */
    public static int pageCount()
    {
        int pages = 1;

        for (ItemGroup group : ItemGroups.getGroupsToDisplay())
        {
            try
            {
                if (group instanceof net.fabricmc.fabric.impl.itemgroup.FabricItemGroup fabric)
                {
                    pages = Math.max(pages, fabric.getPage() + 1);
                }
            }
            catch (LinkageError e)
            {
                return 1;
            }
        }

        return pages;
    }

    /** The page an open creative screen shows */
    public static int currentPage(Screen screen)
    {
        try
        {
            return screen instanceof net.fabricmc.fabric.impl.client.itemgroup.CreativeGuiExtensions fabric ? fabric.fabric_currentPage() : 0;
        }
        catch (LinkageError e)
        {
            return 0;
        }
    }
}
