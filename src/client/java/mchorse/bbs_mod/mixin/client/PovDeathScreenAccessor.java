package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/** The death screen's message, score line and buttons: driven by a POV interface clip instead of the local player, and read by a live take */
@Mixin(DeathScreen.class)
public interface PovDeathScreenAccessor
{
    @Accessor("message")
    Text bbs$getMessage();

    @Accessor("buttons")
    List<ButtonWidget> bbs$getButtons();

    @Accessor("scoreText")
    void bbs$setScoreText(Text text);

    @Invoker("setButtonsActive")
    void bbs$setButtonsActive(boolean active);
}
