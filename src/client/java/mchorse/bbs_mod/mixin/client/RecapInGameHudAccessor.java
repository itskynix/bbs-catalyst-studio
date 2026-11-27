package mchorse.bbs_mod.mixin.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What Recap Studio reads of the game's interface to draw it over its viewport. */
@Mixin(InGameHud.class)
public interface RecapInGameHudAccessor
{
    @Accessor("overlayMessage")
    Text bbs$getOverlayMessage();

    @Accessor("overlayRemaining")
    int bbs$getOverlayRemaining();

    @Accessor("overlayTinted")
    boolean bbs$isOverlayTinted();

    @Accessor("title")
    Text bbs$getTitle();

    @Accessor("subtitle")
    Text bbs$getSubtitle();

    @Accessor("titleRemainTicks")
    int bbs$getTitleRemainTicks();

    @Accessor("titleFadeInTicks")
    int bbs$getTitleFadeInTicks();

    @Accessor("titleStayTicks")
    int bbs$getTitleStayTicks();

    @Accessor("titleFadeOutTicks")
    int bbs$getTitleFadeOutTicks();

    @Accessor("scaledWidth")
    void bbs$setScaledWidth(int width);

    @Accessor("scaledHeight")
    void bbs$setScaledHeight(int height);

    @Invoker("renderScoreboardSidebar")
    void bbs$renderScoreboardSidebar(DrawContext context, ScoreboardObjective objective);
}
