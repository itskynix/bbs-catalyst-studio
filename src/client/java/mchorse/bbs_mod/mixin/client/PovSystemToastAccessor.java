package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The title and the lines of a system toast, for a live take's toast clips */
@Mixin(SystemToast.class)
public interface PovSystemToastAccessor
{
    @Accessor("title")
    Text bbs$getTitle();

    @Accessor("lines")
    List<OrderedText> bbs$getLines();
}
