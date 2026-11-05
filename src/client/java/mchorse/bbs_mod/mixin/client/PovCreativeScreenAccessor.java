package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.ingame.CreativeInventoryScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.ItemGroup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The creative inventory's tab, scroll and search, for a live take's interface clips */
@Mixin(CreativeInventoryScreen.class)
public interface PovCreativeScreenAccessor
{
    @Accessor("selectedTab")
    static ItemGroup bbs$getSelectedTab()
    {
        throw new AssertionError();
    }

    @Accessor("scrollPosition")
    float bbs$getScrollPosition();

    @Accessor("searchBox")
    TextFieldWidget bbs$getSearchBox();
}
