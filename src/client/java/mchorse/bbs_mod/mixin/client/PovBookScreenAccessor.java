package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.ingame.BookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The written book open and its page, for a live take's interface clips */
@Mixin(BookScreen.class)
public interface PovBookScreenAccessor
{
    @Accessor("contents")
    BookScreen.Contents bbs$getContents();

    @Accessor("pageIndex")
    int bbs$getPageIndex();
}
