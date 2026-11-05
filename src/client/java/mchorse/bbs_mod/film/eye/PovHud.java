package mchorse.bbs_mod.film.eye;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import mchorse.bbs_mod.camera.clips.misc.ActorEyeClientClip;
import mchorse.bbs_mod.film.pov.PovScreenEffectsClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * The interface of the replay looked out of, drawn over the finished frame (over the hands, under
 * the images and subtitles): the hotbar with the replay's slots, and above it the hearts, armor,
 * food or mount hearts, air and experience as the POV's Hotbar channels have them, the crosshair,
 * and the cursor of an open inventory with what it carries. Each layer is let through by the Point
 * of View clip's output flags and by the channels' own switches.
 *
 * <p>The layout is the game's, laid out afresh on the frame's own size and an interface scale that
 * fits it, so a 4K export gets an interface as big, relatively, as the one on screen.</p>
 */
public final class PovHud
{
    private static final int HOTBAR_WIDTH = 182;
    private static final int XP_COLOR = 0x80FF20;
    private static final float MAX_AIR = 300F;

    private PovHud()
    {}

    private static Identifier sprite(String path)
    {
        return Ids.of("minecraft", "hud/" + path);
    }

    public static void render(MatrixStack stack, Batcher2D unused, ClipContext context)
    {
        ActorEyeState.Report report = ActorEyeState.current();

        /* What of the screens can be clicked is the frame's own; the screens drawn below say it anew */
        PovGuiRenderer.clearHotspots();

        if (report == null || report.replay() == null)
        {
            return;
        }

        ActorEyeClientClip clip = report.clip();
        Replay replay = report.replay();
        ReplayPov pov = replay.pov;
        float tick = report.tick();

        /* The game's F1: the interface goes, the interfaces opened and the effects on the view stay */
        boolean shown = flag(pov.hudVisible, tick);
        /* The game mode, as the game has it: no bars in creative, no hotbar or crosshair either in spectator */
        int mode = pov.gameMode.isEmpty() ? 0 : pov.gameMode.interpolate(tick, 0);
        boolean spectator = mode == 3;
        boolean bars = mode == 0 || mode == 2;
        boolean hud = clip.outHud.get() && shown && !spectator;
        boolean crosshair = clip.outCrosshair.get() && report.firstPerson() && shown && !spectator && flag(pov.crosshair, tick);
        boolean cursor = clip.outCursor.get() && !pov.cursorVisible.isEmpty() && pov.cursorVisible.interpolate(tick, false);
        boolean actions = clip.outPovActions.get();
        boolean effects = clip.outScreenEffects.get();
        List<PovActions.Active> actives = actions || effects ? PovActions.active(replay, tick) : new ArrayList<>();

        /* Each kind of clip only where the camera lets its kind through */
        actives.removeIf((active) -> active.clip() instanceof PovScreenEffectsClip ? !effects : !actions);
        PovActions.spawnParticles(actives, actions, effects);

        if (!hud && !crosshair && !cursor && actives.isEmpty())
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        Framebuffer fb = mc.getFramebuffer();
        int scale = scaleFor(mc, fb.textureWidth, fb.textureHeight);
        int w = (int) Math.ceil(fb.textureWidth / (double) scale);
        int h = (int) Math.ceil(fb.textureHeight / (double) scale);
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorter sorting = RenderSystem.getVertexSorting();
        DrawContext drawContext = new DrawContext(mc, mc.getBufferBuilders().getEntityVertexConsumers());
        Batcher2D batcher = new Batcher2D(drawContext);

        /* The interface's own space over the frame; the world's depth is done with */
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.setProjectionMatrix(new Matrix4f().ortho(0, w, h, 0, -10000F, 10000F), VertexSorter.BY_Z);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        DiffuseLighting.enableGuiDepthLighting();

        try
        {
            /* Under the interface, as the game puts its vignette and overlays */
            PovActions.renderScreenEffects(drawContext, actives, w, h);

            if (hud)
            {
                renderHotbar(drawContext, batcher, mc, replay, report.entity(), tick, w, h);

                if (bars && flag(pov.statusBars, tick))
                {
                    renderStatus(drawContext, batcher, mc, pov, tick, w, h);
                }
            }

            if (crosshair)
            {
                renderCrosshair(drawContext, batcher, w, h);
            }

            /* Over it: the bars, effects and chat (a part of the interface), the screens, toasts and the totem */
            int mouseX = cursor ? w / 2 + (int) value(pov.cursorX, tick, 0D) : -1000;
            int mouseY = cursor ? h / 2 + (int) value(pov.cursorY, tick, 0D) : -1000;

            ItemStack carried = cursor && !pov.cursorItem.isEmpty() ? pov.cursorItem.interpolate(tick) : null;

            PovGuiRenderer.beginFrame(report.entity(), carried != null && !carried.isEmpty());
            PovActions.renderOverHud(drawContext, batcher, actives, replay, tick, w, h, hud, true, mouseX, mouseY);

            /* The screens' layers, then the cursor's: what it carries, the pointer on it, and the tooltip over both */
            if (cursor)
            {
                renderCursor(drawContext, mouseX, mouseY, carried);
            }

            PovGuiRenderer.renderTooltip(drawContext);

            drawContext.draw();
        }
        finally
        {
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.defaultBlendFunc();
        }
    }

    /**
     * The interface scale the game would pick for a window of the frame's size: the biggest that
     * still leaves 320 by 240 units, and no bigger than the one set in the options.
     */
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

    private static void renderHotbar(DrawContext context, Batcher2D batcher, MinecraftClient mc, Replay replay, IEntity entity, float tick, int w, int h)
    {
        ReplayKeyframes keyframes = replay.keyframes;
        int selected = keyframes.selectedSlot.isEmpty() ? 0 : Math.max(0, Math.min(8, keyframes.selectedSlot.interpolate(tick, 0)));
        int left = w / 2 - HOTBAR_WIDTH / 2;
        int top = h - 22;

        batcher.drawGuiTexture(sprite("hotbar"), left, top, HOTBAR_WIDTH, 22);
        batcher.drawGuiTexture(sprite("hotbar_selection"), left - 1 + selected * 20, top - 1, 24, 23);

        ItemStack offHand = keyframes.offHand.isEmpty() ? entity.getEquipmentStack(EquipmentSlot.OFFHAND) : keyframes.offHand.interpolate(tick);

        if (offHand != null && !offHand.isEmpty())
        {
            batcher.drawGuiTexture(sprite("hotbar_offhand_left"), left - 29, h - 23, 29, 24);
            drawItem(context, mc, offHand, left - 26, h - 19);
        }

        for (int i = 0; i < ReplayKeyframes.HOTBAR_SIZE && i < keyframes.hotbar.size(); i++)
        {
            KeyframeChannel<ItemStack> slot = keyframes.hotbar.get(i);
            ItemStack stack = slot.isEmpty() ? (i == selected ? entity.getEquipmentStack(EquipmentSlot.MAINHAND) : null) : slot.interpolate(tick);

            if (stack != null && !stack.isEmpty())
            {
                drawItem(context, mc, stack, left + 3 + i * 20, h - 19);
            }
        }
    }

    private static void drawItem(DrawContext context, MinecraftClient mc, ItemStack stack, int x, int y)
    {
        context.drawItem(stack, x, y);
        context.drawItemInSlot(mc.textRenderer, stack, x, y);
    }

    private static void renderStatus(DrawContext context, Batcher2D batcher, MinecraftClient mc, ReplayPov pov, float tick, int w, int h)
    {
        int left = w / 2 - HOTBAR_WIDTH / 2;
        int right = w / 2 + HOTBAR_WIDTH / 2;
        int top = h - 39;

        /* Experience: the bar, and the level over it */
        float experience = (float) Math.max(0D, Math.min(1D, held(pov.experience, tick, 0D)));
        int level = held(pov.experienceLevel, tick, 0);
        int barY = h - 29;

        batcher.drawGuiTexture(sprite("experience_bar_background"), left, barY, HOTBAR_WIDTH, 5);

        if (experience > 0F)
        {
            batcher.drawGuiTexture(sprite("experience_bar_progress"), HOTBAR_WIDTH, 5, 0, 0, left, barY, (int) (experience * (HOTBAR_WIDTH + 1)), 5);
        }

        if (level > 0)
        {
            String text = String.valueOf(level);
            int x = (w - mc.textRenderer.getWidth(text)) / 2;
            int y = h - 35;

            context.drawText(mc.textRenderer, text, x + 1, y, 0, false);
            context.drawText(mc.textRenderer, text, x - 1, y, 0, false);
            context.drawText(mc.textRenderer, text, x, y + 1, 0, false);
            context.drawText(mc.textRenderer, text, x, y - 1, 0, false);
            context.drawText(mc.textRenderer, text, x, y, XP_COLOR, false);
        }

        /* Hearts, absorption after them, rows packing closer the more there are */
        float health = (float) Math.max(0D, held(pov.health, tick, 20D));
        float maxHealth = (float) Math.max(1D, held(pov.maxHealth, tick, 20D));
        float absorption = (float) Math.max(0D, held(pov.absorption, tick, 0D));
        boolean hardcore = !pov.hardcore.isEmpty() && pov.hardcore.interpolate(tick, false);
        int hearts = (int) Math.ceil(maxHealth / 2F);
        int absorbing = (int) Math.ceil(absorption / 2F);
        int rows = (int) Math.ceil((hearts + absorbing) / 10F);
        int rowHeight = Math.max(10 - (rows - 2), 3);
        String type = hardcore ? "hardcore_" : "";

        for (int i = hearts + absorbing - 1; i >= 0; i--)
        {
            int x = left + (i % 10) * 8;
            int y = top - (i / 10) * rowHeight;

            batcher.drawGuiTexture(sprite(hardcore ? "heart/container_hardcore" : "heart/container"), x, y, 9, 9);

            if (i < hearts)
            {
                int value = i * 2;

                if (value + 2 <= health)
                {
                    batcher.drawGuiTexture(sprite("heart/" + type + "full"), x, y, 9, 9);
                }
                else if (value + 1 <= health)
                {
                    batcher.drawGuiTexture(sprite("heart/" + type + "half"), x, y, 9, 9);
                }
            }
            else
            {
                int value = (i - hearts) * 2;

                if (value + 2 <= absorption)
                {
                    batcher.drawGuiTexture(sprite("heart/absorbing_" + type + "full"), x, y, 9, 9);
                }
                else if (value + 1 <= absorption)
                {
                    batcher.drawGuiTexture(sprite("heart/absorbing_" + type + "half"), x, y, 9, 9);
                }
            }
        }

        /* Armor, a row over the hearts, only once there is any */
        float armor = (float) Math.max(0D, held(pov.armor, tick, 0D));

        if (armor > 0F)
        {
            int y = top - (rows - 1) * rowHeight - 10;

            for (int i = 0; i < 10; i++)
            {
                int value = i * 2 + 1;
                String piece = value < armor ? "armor_full" : value == armor ? "armor_half" : "armor_empty";

                batcher.drawGuiTexture(sprite(piece), left + i * 8, y, 9, 9);
            }
        }

        /* On the right: the mount's hearts while riding, the food otherwise */
        float mount = (float) Math.max(0D, held(pov.mountHealth, tick, 0D));
        int rightRows = 1;

        if (mount > 0F)
        {
            int mountHearts = Math.max(10, (int) Math.ceil(mount / 2F));

            rightRows = (int) Math.ceil(mountHearts / 10F);

            for (int i = 0; i < mountHearts; i++)
            {
                int x = right - (i % 10) * 8 - 9;
                int y = top - (i / 10) * 10;
                int value = i * 2;

                batcher.drawGuiTexture(sprite("heart/vehicle_container"), x, y, 9, 9);

                if (value + 2 <= mount)
                {
                    batcher.drawGuiTexture(sprite("heart/vehicle_full"), x, y, 9, 9);
                }
                else if (value + 1 <= mount)
                {
                    batcher.drawGuiTexture(sprite("heart/vehicle_half"), x, y, 9, 9);
                }
            }
        }
        else
        {
            float hunger = (float) Math.max(0D, held(pov.hunger, tick, 20D));

            for (int i = 0; i < 10; i++)
            {
                int x = right - i * 8 - 9;
                int value = i * 2 + 1;

                batcher.drawGuiTexture(sprite("food_empty"), x, top, 9, 9);

                if (value < hunger)
                {
                    batcher.drawGuiTexture(sprite("food_full"), x, top, 9, 9);
                }
                else if (value == hunger)
                {
                    batcher.drawGuiTexture(sprite("food_half"), x, top, 9, 9);
                }
            }
        }

        /* Air over the right column, only while it runs short */
        float air = held(pov.air, tick, (double) MAX_AIR).floatValue();

        if (air < MAX_AIR)
        {
            int y = top - rightRows * 10;
            int bubbles = (int) Math.ceil((air - 2F) * 10F / MAX_AIR);
            int bursting = (int) Math.ceil(air * 10F / MAX_AIR) - bubbles;

            for (int i = 0; i < bubbles + bursting; i++)
            {
                batcher.drawGuiTexture(sprite(i < bubbles ? "air" : "air_bursting"), right - i * 8 - 9, y, 9, 9);
            }
        }
    }

    /** The crosshair, as the game draws it: inverting what is under it */
    private static void renderCrosshair(DrawContext context, Batcher2D batcher, int w, int h)
    {
        /* Flushing the items before it turns blending off; without it the crosshair is plain white */
        context.draw();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GL11.GL_ONE_MINUS_DST_COLOR, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
        batcher.drawGuiTexture(sprite("crosshair"), (w - 15) / 2, (h - 15) / 2, 15, 15);
        context.draw();
        RenderSystem.defaultBlendFunc();
    }

    /**
     * The cursor of an open inventory, where the Hotbar channels put it (from the screen's middle),
     * and what it carries: over the screens' slots and items, so the depth drawn so far is cleared
     * and it goes in front of all of them. Only the tooltip goes over it.
     */
    private static void renderCursor(DrawContext context, int x, int y, ItemStack carried)
    {
        context.draw();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        context.getMatrices().push();
        context.getMatrices().translate(0F, 0F, 500F);

        try
        {
            renderCursorShape(context, x, y, carried);
            context.draw();
        }
        finally
        {
            context.getMatrices().pop();
        }
    }

    private static void renderCursorShape(DrawContext context, int x, int y, ItemStack stack)
    {
        /* The carried item hangs on the cursor as the game hangs it (its middle on the tip), and its
         * depth is cleared so the pointer is drawn over it rather than into it */
        if (stack != null && !stack.isEmpty())
        {
            context.drawItem(stack, x - 8, y - 8);
            context.drawItemInSlot(MinecraftClient.getInstance().textRenderer, stack, x - 8, y - 8);
            context.draw();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);
        }

        /* The pointer, its tip on the cursor: white in a dark outline */
        for (int row = 0; row < 10; row++)
        {
            int width = row < 7 ? row + 1 : 7 - (row - 6) * 2;

            context.fill(x - 1, y + row - 1, x + width + 1, y + row + 1, 0xFF000000);
        }

        for (int row = 0; row < 9; row++)
        {
            int width = row < 7 ? row : 6 - (row - 6) * 2;

            if (width > 0)
            {
                context.fill(x, y + row, x + width, y + row + 1, 0xFFFFFFFF);
            }
        }
    }

    /**
     * A bar's value as the game shows it: what the last keyframe at or before the tick says, held
     * until the next one. Health and food jump, they do not drain from one keyframe to the next, and a
     * take keys them only where they change, so blending would melt a heart over the seconds before
     * the hit that took it. Before the first keyframe, the first one's value.
     */
    private static <T> T held(KeyframeChannel<T> channel, float tick, T fallback)
    {
        if (channel.isEmpty())
        {
            return fallback;
        }

        KeyframeSegment<T> segment = channel.find(tick);

        return segment == null || segment.a == null ? fallback : segment.a.getValue();
    }

    private static double value(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        return channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }

    private static boolean flag(KeyframeChannel<Boolean> channel, float tick)
    {
        return channel.isEmpty() || channel.interpolate(tick, true);
    }
}
