package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The game's own interface pieces, which Recap draws over its frame with the recorded player's numbers lent. */
@Mixin(InGameHud.class)
public interface RecapHudInvoker
{
    @Invoker("renderHotbar")
    void bbs$renderHotbar(float tickDelta, DrawContext context);

    @Invoker("renderStatusBars")
    void bbs$renderStatusBars(DrawContext context);

    @Invoker("renderCrosshair")
    void bbs$renderCrosshair(DrawContext context);
}
