package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The book and quill being written: its pages, the page open, the signing and the title, for a live take's interface clips */
@Mixin(BookEditScreen.class)
public interface PovBookEditScreenAccessor
{
    @Accessor("pages")
    List<String> bbs$getPages();

    @Accessor("currentPage")
    int bbs$getCurrentPage();

    @Accessor("signing")
    boolean bbs$isSigning();

    @Accessor("title")
    String bbs$getTitle();
}
