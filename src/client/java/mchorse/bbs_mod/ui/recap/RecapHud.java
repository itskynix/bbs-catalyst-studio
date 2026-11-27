package mchorse.bbs_mod.ui.recap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.mixin.client.RecapInGameHudAccessor;
import mchorse.bbs_mod.recap.RecapHudStream;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * The recorded messages over a recap's frame while it shows the recorded player's point of view,
 * drawn into the frame itself, so the viewport and a video show them alike: the chat, the boss bars,
 * the title, the scoreboard and the action bar, as the view settings let them through.
 *
 * <p>The hotbar, the bars, the crosshair, the screens opened, the status effects and the toasts are
 * the film studio's point of view ({@code PovHud}), which plays the recorded one.</p>
 */
public final class RecapHud
{
    private static final RecapOption[] PIECES = {RecapOption.CHAT, RecapOption.BOSS_BAR, RecapOption.TITLE, RecapOption.SCOREBOARD, RecapOption.ACTION_BAR};

    private RecapHud()
    {}

    /** A frame overlay: only while a recap is shown through a point of view clip. */
    public static void render(MatrixStack stack, Batcher2D unused, ClipContext context)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.player == null || !RecapScene.isPointOfView())
        {
            return;
        }

        boolean pieces = false;

        for (RecapOption piece : PIECES)
        {
            pieces |= RecapScene.showsHud(piece);
        }

        if (!pieces)
        {
            return;
        }

        Framebuffer fb = mc.getFramebuffer();
        int scale = scaleFor(mc, fb.textureWidth, fb.textureHeight);
        int w = (int) Math.ceil(fb.textureWidth / (double) scale);
        int h = (int) Math.ceil(fb.textureHeight / (double) scale);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorting = RenderSystem.getVertexSorting();
        DrawContext draw = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
        RecapInGameHudAccessor access = (RecapInGameHudAccessor) mc.inGameHud;
        float tickDelta = RecapScene.subTick();

        /* The interface's own space over the frame; the world's depth is done with */
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, w, h, 0, -10000F, 10000F), VertexSorter.BY_Z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DiffuseLighting.enableGuiDepthLighting();

        try
        {
            access.bbs$setScaledWidth(w);
            access.bbs$setScaledHeight(h);

            renderPieces(mc, draw, access, w, h, tickDelta);
            draw.draw();
        }
        catch (RuntimeException e)
        {
            /* An interface that cannot be drawn is left out of this frame, nothing else */
        }
        finally
        {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.defaultBlendFunc();
        }
    }

    /** The interface scale the game would pick for a window of the frame's size. */
    private static int scaleFor(MinecraftClient mc, int width, int height)
    {
        int chosen = mc.options.getGuiScale().getValue();
        int scale = 1;

        while ((chosen == 0 || scale < chosen) && width / (scale + 1) >= 320 && height / (scale + 1) >= 240)
        {
            scale += 1;
        }

        return scale;
    }

    /** The chat, the boss bars, the title, the scoreboard and the action bar, from the recorded HUD stream when present. */
    private static void renderPieces(MinecraftClient mc, DrawContext draw, RecapInGameHudAccessor access, int sw, int sh, float tickDelta)
    {
        InGameHud hud = mc.inGameHud;
        TextRenderer font = mc.textRenderer;
        MatrixStack matrices = draw.getMatrices();
        RecapHudStream.Snapshot recorded = RecapScene.hudNow();

        if (RecapScene.showsHud(RecapOption.SCOREBOARD))
        {
            renderScoreboard(mc, draw, access);
        }

        if (recorded != null)
        {
            if (RecapScene.showsHud(RecapOption.BOSS_BAR))
            {
                renderRecordedBossBars(draw, font, recorded.bossBars, sw);
            }

            if (RecapScene.showsHud(RecapOption.TITLE))
            {
                renderRecordedTitle(draw, font, recorded, sw, sh);
            }

            if (RecapScene.showsHud(RecapOption.ACTION_BAR))
            {
                renderRecordedActionBar(draw, font, recorded, sw, sh);
            }

            if (RecapScene.showsHud(RecapOption.CHAT))
            {
                renderRecordedChat(draw, font, recorded.chat, sw, sh);
            }

            return;
        }

        if (RecapScene.showsHud(RecapOption.BOSS_BAR))
        {
            hud.getBossBarHud().render(draw);
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
    }

    private static void renderRecordedBossBars(DrawContext draw, TextRenderer font, List<RecapHudStream.BossBarView> bars, int sw)
    {
        int y = 12;
        int bx = sw / 2 - 91;

        for (RecapHudStream.BossBarView bar : bars)
        {
            int fillW = MathHelper.clamp(Math.round(bar.percent() * 182F), 0, 182);
            int color = bossColorArgb(bar.colorOrdinal());

            draw.fill(bx - 1, y - 1, bx + 183, y + 6, 0xaa0c0e14);
            draw.fill(bx, y, bx + 182, y + 5, 0xaa242834);

            if (fillW > 0)
            {
                draw.fill(bx, y, bx + fillW, y + 5, color);
                draw.fill(bx, y, bx + fillW, y + 1, 0x44ffffff);
            }

            if (!bar.name().isEmpty())
            {
                draw.drawTextWithShadow(font, bar.name(), sw / 2 - font.getWidth(bar.name()) / 2, y - 9, 0xffffffff);
            }

            y += 19;
        }
    }

    private static int bossColorArgb(int ordinal)
    {
        return switch (ordinal)
        {
            case 0 -> 0xffe85d9e;
            case 1 -> 0xff3ba4e6;
            case 2 -> 0xffe0423b;
            case 3 -> 0xff3ec95c;
            case 4 -> 0xffe6c83b;
            case 5 -> 0xff9b4de0;
            default -> 0xffe8eef5;
        };
    }

    private static void renderRecordedTitle(DrawContext draw, TextRenderer font, RecapHudStream.Snapshot snap, int sw, int sh)
    {
        if (snap.title.isEmpty() || snap.titleRemain <= 0F)
        {
            return;
        }

        int fadeIn = Math.max(1, snap.titleFadeIn);
        int stay = snap.titleStay;
        int fadeOut = Math.max(1, snap.titleFadeOut);
        float left = snap.titleRemain;
        int alpha = 255;

        if (left > fadeOut + stay)
        {
            alpha = (int) ((fadeIn + stay + fadeOut - left) * 255F / fadeIn);
        }

        if (left <= fadeOut)
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
        draw.drawTextWithShadow(font, snap.title, -font.getWidth(snap.title) / 2, -10, color);
        matrices.pop();

        if (!snap.subtitle.isEmpty())
        {
            matrices.push();
            matrices.scale(2F, 2F, 2F);
            draw.drawTextWithShadow(font, snap.subtitle, -font.getWidth(snap.subtitle) / 2, 5, color);
            matrices.pop();
        }

        matrices.pop();
    }

    private static void renderRecordedActionBar(DrawContext draw, TextRenderer font, RecapHudStream.Snapshot snap, int sw, int sh)
    {
        if (snap.actionBar.isEmpty() || snap.actionRemain <= 0F)
        {
            return;
        }

        float left = snap.actionRemain;
        int alpha = MathHelper.clamp((int) (left * 255F / 20F), 0, 255);

        if (alpha <= 8)
        {
            return;
        }

        int base = snap.actionTinted ? MathHelper.hsvToRgb(left / 50F, 0.7F, 0.6F) & 0xffffff : 0xffffff;

        draw.drawTextWithShadow(font, snap.actionBar, sw / 2 - font.getWidth(snap.actionBar) / 2, sh - 72, base | (alpha << 24));
    }

    private static void renderRecordedChat(DrawContext draw, TextRenderer font, List<RecapHudStream.ChatEntry> chat, int sw, int sh)
    {
        if (chat.isEmpty())
        {
            return;
        }

        int maxW = Math.max(80, Math.min(320, sw - 20));
        int y = sh - 48;

        for (int i = chat.size() - 1; i >= 0 && y > 24; i--)
        {
            RecapHudStream.ChatEntry entry = chat.get(i);
            float fade = entry.ageTicks() > 180F ? (200F - entry.ageTicks()) / 20F : 1F;
            int alpha = MathHelper.clamp(Math.round(fade * 255F), 0, 255);

            if (alpha <= 8 || entry.text().isEmpty())
            {
                continue;
            }

            List<OrderedText> wrapped = font.wrapLines(Text.literal(entry.text()), maxW);
            int bg = ((alpha / 2) << 24);
            int fg = 0xffffff | (alpha << 24);

            for (int l = wrapped.size() - 1; l >= 0 && y > 24; l--)
            {
                y -= 9;
                draw.fill(2, y - 1, 6 + maxW, y + 8, bg);
                draw.drawTextWithShadow(font, wrapped.get(l), 4, y, fg);
            }
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
