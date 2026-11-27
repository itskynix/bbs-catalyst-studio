package mchorse.bbs_mod.ui.recap;

import mchorse.bbs_mod.mixin.client.RecapInGameHudAccessor;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.ui.framework.UIContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

/**
 * The game's interface over the recap viewport, while it looks out of the eye: chat, boss bars,
 * the title, the scoreboard and the action bar, each only while the view settings panel has it on.
 *
 * <p>It is the game's own interface, live, laid out for the whole window and scaled down into the
 * frame, so it sits where the player would see it. The pieces are drawn by the game's own code;
 * the title and the action bar, which the game draws inline, follow its fades.</p>
 */
public final class RecapHud
{
    private static final RecapOption[] PIECES = {RecapOption.CHAT, RecapOption.BOSS_BAR, RecapOption.TITLE, RecapOption.SCOREBOARD, RecapOption.ACTION_BAR};

    private RecapHud()
    {}

    public static void render(UIContext context, int x, int y, int w, int h)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null || w <= 0 || h <= 0 || !RecapScene.isFirstPerson())
        {
            return;
        }

        boolean any = false;

        for (RecapOption piece : PIECES)
        {
            any |= RecapScene.showsHud(piece);
        }

        if (!any)
        {
            return;
        }

        int sw = Math.max(1, mc.getWindow().getScaledWidth());
        int sh = Math.max(1, mc.getWindow().getScaledHeight());
        float tickDelta = context.getTransition();
        InGameHud hud = mc.inGameHud;
        RecapInGameHudAccessor access = (RecapInGameHudAccessor) hud;
        TextRenderer font = mc.textRenderer;

        context.batcher.flush();
        context.batcher.clip(x, y, w, h, context);

        DrawContext draw = context.batcher.getContext();
        MatrixStack matrices = draw.getMatrices();

        matrices.push();
        matrices.translate(x, y, 0F);
        matrices.scale(w / (float) sw, h / (float) sh, 1F);

        try
        {
            access.bbs$setScaledWidth(sw);
            access.bbs$setScaledHeight(sh);

            if (RecapScene.showsHud(RecapOption.BOSS_BAR))
            {
                hud.getBossBarHud().render(draw);
            }

            if (RecapScene.showsHud(RecapOption.SCOREBOARD))
            {
                renderScoreboard(mc, draw, access);
            }

            if (RecapScene.showsHud(RecapOption.TITLE))
            {
                renderTitle(draw, font, access, sw, sh, tickDelta);
            }

            if (RecapScene.showsHud(RecapOption.ACTION_BAR))
            {
                renderActionBar(draw, font, access, sw, sh, tickDelta);
            }

            if (RecapScene.showsHud(RecapOption.CHAT))
            {
                matrices.push();
                matrices.translate(0F, sh - 48, 0F);
                hud.getChatHud().render(draw, hud.getTicks(), -1, -1);
                matrices.pop();
            }

            draw.draw();
        }
        finally
        {
            matrices.pop();
            context.batcher.unclip(context);
        }
    }

    private static void renderScoreboard(MinecraftClient mc, DrawContext draw, RecapInGameHudAccessor access)
    {
        Scoreboard scoreboard = mc.world.getScoreboard();
        ScoreboardObjective objective = null;
        Team team = scoreboard.getScoreHolderTeam(mc.player.getNameForScoreboard());

        if (team != null)
        {
            ScoreboardDisplaySlot slot = ScoreboardDisplaySlot.fromFormatting(team.getColor());

            if (slot != null)
            {
                objective = scoreboard.getObjectiveForSlot(slot);
            }
        }

        if (objective == null)
        {
            objective = scoreboard.getObjectiveForSlot(ScoreboardDisplaySlot.SIDEBAR);
        }

        if (objective != null)
        {
            access.bbs$renderScoreboardSidebar(draw, objective);
        }
    }

    /** The big title and its subtitle in the middle, with the game's fade in, stay and fade out. */
    private static void renderTitle(DrawContext draw, TextRenderer font, RecapInGameHudAccessor access, int sw, int sh, float tickDelta)
    {
        Text title = access.bbs$getTitle();
        int remain = access.bbs$getTitleRemainTicks();

        if (title == null || remain <= 0)
        {
            return;
        }

        int fadeIn = Math.max(1, access.bbs$getTitleFadeInTicks());
        int stay = access.bbs$getTitleStayTicks();
        int fadeOut = Math.max(1, access.bbs$getTitleFadeOutTicks());
        float left = remain - tickDelta;
        int alpha = 255;

        if (remain > fadeOut + stay)
        {
            alpha = (int) ((fadeIn + stay + fadeOut - left) * 255F / fadeIn);
        }

        if (remain <= fadeOut)
        {
            alpha = (int) (left * 255F / fadeOut);
        }

        alpha = MathHelper.clamp(alpha, 0, 255);

        if (alpha <= 8)
        {
            return;
        }

        int color = 0xffffff | (alpha << 24);
        MatrixStack matrices = draw.getMatrices();

        matrices.push();
        matrices.translate(sw / 2F, sh / 2F, 0F);
        matrices.push();
        matrices.scale(4F, 4F, 4F);
        draw.drawTextWithShadow(font, title, -font.getWidth(title) / 2, -10, color);
        matrices.pop();

        Text subtitle = access.bbs$getSubtitle();

        if (subtitle != null)
        {
            matrices.push();
            matrices.scale(2F, 2F, 2F);
            draw.drawTextWithShadow(font, subtitle, -font.getWidth(subtitle) / 2, 5, color);
            matrices.pop();
        }

        matrices.pop();
    }

    /** The line above the hotbar, fading over its last second as the game does. */
    private static void renderActionBar(DrawContext draw, TextRenderer font, RecapInGameHudAccessor access, int sw, int sh, float tickDelta)
    {
        Text message = access.bbs$getOverlayMessage();
        int remain = access.bbs$getOverlayRemaining();

        if (message == null || remain <= 0)
        {
            return;
        }

        float left = remain - tickDelta;
        int alpha = Math.min(255, (int) (left * 255F / 20F));

        if (alpha <= 8)
        {
            return;
        }

        int base = access.bbs$isOverlayTinted() ? MathHelper.hsvToRgb(left / 50F, 0.7F, 0.6F) & 0xffffff : 0xffffff;

        draw.drawTextWithShadow(font, message, sw / 2 - font.getWidth(message) / 2, sh - 72, base | (alpha << 24));
    }
}
