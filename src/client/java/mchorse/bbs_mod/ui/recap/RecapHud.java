package mchorse.bbs_mod.ui.recap;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.modifiers.ActorEyeClip;
import mchorse.bbs_mod.mixin.client.RecapHudInvoker;
import mchorse.bbs_mod.mixin.client.RecapInGameHudAccessor;
import mchorse.bbs_mod.recap.RecapGear;
import mchorse.bbs_mod.recap.RecapLend;
import mchorse.bbs_mod.recap.RecapOption;
import mchorse.bbs_mod.recap.RecapScene;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardDisplaySlot;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.Team;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

/**
 * The game's interface over a recap's frame while it looks out of the recorded player's eyes,
 * drawn into the frame itself, so the viewport and a video show it alike.
 *
 * <p>Laid out the game's way on the frame's own size, at the interface scale the game would pick
 * for a window that size. The point of view clip lets through the hotbar with the hearts, food, air
 * and experience (its interface switch) and the crosshair (its crosshair switch), with what the
 * recorded player had and how it was doing lent for the draw; the view settings let through the
 * chat, the boss bars, the title, the scoreboard and the action bar.</p>
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

        if (mc.world == null || mc.player == null || !RecapScene.isFirstPerson())
        {
            return;
        }

        ActorEyeClip clip = RecapScene.povClip();
        RecapGear.Frame gear = RecapScene.gearNow();
        int mode = gear == null ? -1 : gear.mode;
        boolean spectator = mode == 3;
        boolean hud = clip != null && clip.outHud.get() && !spectator;
        boolean crosshair = clip != null && clip.outCrosshair.get() && !spectator;
        boolean pieces = false;

        for (RecapOption piece : PIECES)
        {
            pieces |= RecapScene.showsHud(piece);
        }

        if (!hud && !crosshair && !pieces)
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
        InGameHud game = mc.inGameHud;
        RecapInGameHudAccessor access = (RecapInGameHudAccessor) game;
        RecapHudInvoker invoke = (RecapHudInvoker) game;
        ClientPlayerEntity player = mc.player;
        Perspective perspective = mc.options.getPerspective();
        RecapLend lent = gear == null ? null : RecapLend.lend(player, gear, true);
        float tickDelta = mc.getTickDelta();

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
            mc.options.setPerspective(Perspective.FIRST_PERSON);

            if (hud)
            {
                invoke.bbs$renderHotbar(tickDelta, draw);

                /* No bars in creative, as the game draws none */
                if (mode != 1)
                {
                    invoke.bbs$renderStatusBars(draw);
                    game.renderExperienceBar(draw, w / 2 - 91);
                }
            }

            if (crosshair)
            {
                invoke.bbs$renderCrosshair(draw);
            }

            renderPieces(mc, draw, access, w, h, tickDelta);
            draw.draw();
        }
        catch (RuntimeException e)
        {
            /* An interface that cannot be drawn is left out of this frame, nothing else */
        }
        finally
        {
            mc.options.setPerspective(perspective);

            if (lent != null)
            {
                lent.giveBack(player);
            }

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

    /** The chat, the boss bars, the title, the scoreboard and the action bar, each as the view settings let it. */
    private static void renderPieces(MinecraftClient mc, DrawContext draw, RecapInGameHudAccessor access, int sw, int sh, float tickDelta)
    {
        InGameHud hud = mc.inGameHud;
        TextRenderer font = mc.textRenderer;
        MatrixStack matrices = draw.getMatrices();

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
