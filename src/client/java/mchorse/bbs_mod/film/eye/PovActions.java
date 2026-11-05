package mchorse.bbs_mod.film.eye;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.film.pov.PovBossBarClip;
import mchorse.bbs_mod.film.pov.PovChatClip;
import mchorse.bbs_mod.film.pov.PovClip;
import mchorse.bbs_mod.film.pov.PovGuiClip;
import mchorse.bbs_mod.film.pov.PovParticleClip;
import mchorse.bbs_mod.film.pov.PovScreenEffectsClip;
import mchorse.bbs_mod.film.pov.PovShakeClip;
import mchorse.bbs_mod.film.pov.PovStatusEffectsClip;
import mchorse.bbs_mod.film.pov.PovToastClip;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.Ids;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.texture.Sprite;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.Identifier;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * The POV action clips of the replay a Point of View clip looks out of, as they are at a tick:
 * the screen effects under the interface, the boss bars, status effects, chat, interfaces, toasts
 * and the totem over it, the particles put in front of the eyes, and what the shake and the nausea
 * do to the view. What reaches the picture is what the camera clip's output flags let through.
 */
public final class PovActions
{
    private static final Identifier VIGNETTE = Ids.of("minecraft", "textures/misc/vignette.png");
    private static final Identifier PUMPKIN = Ids.of("minecraft", "textures/misc/pumpkinblur.png");
    private static final Identifier FREEZE = Ids.of("minecraft", "textures/misc/powder_snow_outline.png");

    /** How long the game's totem takes to spring up and fade, in ticks */
    private static final float TOTEM_TICKS = 40F;
    /** How long a toast takes to slide in and out, in ticks */
    private static final float TOAST_SLIDE = 10F;

    /** The last puff each particle clip made (its emission number), so each is made once */
    private static final Map<Object, Integer> PUFFS = new WeakHashMap<>();
    private static final Random RANDOM = new Random();

    private PovActions()
    {}

    /** A clip that covers the tick: the clip, the tick in its own time, and its envelope there */
    public record Active(PovClip clip, float tick, float factor)
    {}

    public static List<Active> active(Replay replay, float tick)
    {
        List<Active> list = new ArrayList<>();

        if (replay == null)
        {
            return list;
        }

        for (Clip clip : replay.pov.actions.getClips((int) tick))
        {
            if (clip instanceof PovClip pov && pov.enabled.get())
            {
                float rel = tick - pov.tick.get();

                list.add(new Active(pov, rel, pov.envelope.factorEnabled(pov.duration.get(), rel)));
            }
        }

        return list;
    }

    /* The view: what the shake clips, the freeze and the nausea do to the camera */

    /** How long the game's hurt lasts, in ticks, and how far its tilt rolls the view at most */
    public static final float HURT_TICKS = 10F;
    public static final float HURT_ROLL = 14F;

    /** The game's hurt tilt at a point of a hurt (1 just hit, 0 over), as a roll in degrees */
    public static float hurtRoll(float hurt, float max)
    {
        if (hurt <= 0F)
        {
            return 0F;
        }

        float f = Math.min(hurt / Math.max(max, 1F), 1F);

        return -HURT_ROLL * (float) Math.sin(f * f * f * f * Math.PI);
    }

    /**
     * What the active clips do to the camera, on top of its own turn: {pitch, roll, fov added,
     * fov multiplied}, the angles in degrees. The shake clips are the game's hurt and death tilt
     * (a hurt played from the clip's start when nothing is keyed), turned by where the hit came
     * from, with a trembling shake on top while the hurt lasts; the freeze narrows the lens as it
     * sets in.
     */
    public static float[] viewTilt(Replay replay, float tick, boolean shakes, boolean effects)
    {
        float[] tilt = {0F, 0F, 0F, 1F};

        for (Active active : active(replay, tick))
        {
            if (shakes && active.clip instanceof PovShakeClip shake)
            {
                float max = (float) Math.max(1D, PovClip.value(shake.maxHurtTime, active.tick, HURT_TICKS));
                float hurt = (float) PovClip.value(shake.hurtTime, active.tick, Math.max(0D, max - active.tick));
                float death = (float) PovClip.value(shake.deathTime, active.tick, 0D);
                float intensity = (float) PovClip.value(shake.intensity, active.tick, 1D) * active.factor;
                double yaw = Math.toRadians(PovClip.value(shake.damageTiltYaw, active.tick, 0D));

                if (hurt > 0F)
                {
                    float roll = hurtRoll(hurt, max) * intensity;

                    /* The tilt leans away from where the hit came from, as the game turns its tilt by the hit's yaw */
                    tilt[1] += roll * (float) Math.cos(yaw);
                    tilt[0] += roll * (float) Math.sin(yaw);

                    /* The shake: a tremble dying out with the hurt */
                    float amplitude = (float) PovClip.value(shake.shakeAmplitude, active.tick, 3D) * intensity;
                    float frequency = (float) PovClip.value(shake.shakeFrequency, active.tick, 12D);
                    float fade = Math.min(hurt / max, 1F);
                    double phase = active.tick / 20D * frequency * Math.PI * 2D;

                    tilt[0] += amplitude * fade * (float) Math.sin(phase);
                    tilt[1] += amplitude * fade * 0.6F * (float) Math.sin(phase * 1.37D + 1.1D);
                }

                if (death > 0F)
                {
                    tilt[1] += (40F - 8000F / (Math.min(death, 20F) + 200F)) * intensity;
                }
            }
            else if (effects && active.clip instanceof PovScreenEffectsClip screen)
            {
                for (PovScreenEffectsClip.Effect effect : screen.effects.getAllTyped())
                {
                    if (effect.type.get().equals("freeze"))
                    {
                        float progress = (float) Math.max(0D, Math.min(1D, PovClip.value(effect.progress, active.tick, 1D))) * active.factor;

                        tilt[3] *= 1F - progress * 0.15F;
                    }
                }
            }
        }

        return tilt;
    }

    /**
     * The game's nausea, the way the game does it: the projection of the world stretched along an
     * axis turning round the view, as strong as the effect's strength, as fast as its speed (7 the
     * effect's, 20 a nether portal's). Applied to the projection the world is drawn with.
     */
    /** The nausea of the replay a Point of View clip looks out of, when the clip lets screen effects through */
    public static void applyNausea(MatrixStack matrices)
    {
        ActorEyeState.Report report = ActorEyeState.beforeCamera();

        if (report != null && report.replay() != null && report.clip().outScreenEffects.get())
        {
            applyNausea(matrices, report.replay(), report.tick());
        }
    }

    public static void applyNausea(MatrixStack matrices, Replay replay, float tick)
    {
        float strength = 0F;
        float speed = 7F;

        for (Active active : active(replay, tick))
        {
            if (active.clip instanceof PovScreenEffectsClip screen)
            {
                for (PovScreenEffectsClip.Effect effect : screen.effects.getAllTyped())
                {
                    if (effect.type.get().equals("nausea"))
                    {
                        strength = Math.max(strength, (float) PovClip.value(effect.strength, active.tick, 1D) * active.factor);
                        speed = (float) PovClip.value(effect.speed, active.tick, 7D);
                    }
                }
            }
        }

        if (strength <= 0F)
        {
            return;
        }

        float g = 5F / (strength * strength + 5F) - strength * 0.04F;

        g *= g;

        RotationAxis axis = RotationAxis.of(new Vector3f(0F, (float) Math.sqrt(2D) / 2F, (float) Math.sqrt(2D) / 2F));
        float angle = tick * speed;

        matrices.multiply(axis.rotationDegrees(angle));
        matrices.scale(1F / Math.max(g, 0.0001F), 1F, 1F);
        matrices.multiply(axis.rotationDegrees(-angle));
    }

    /* Under the interface */

    public static void renderScreenEffects(DrawContext context, List<Active> actives, int w, int h)
    {
        for (Active active : actives)
        {
            if (!(active.clip instanceof PovScreenEffectsClip clip))
            {
                continue;
            }

            for (PovScreenEffectsClip.Effect effect : clip.effects.getAllTyped())
            {
                float opacity = (float) PovClip.value(effect.opacity, active.tick, 1D) * active.factor;

                switch (effect.type.get())
                {
                    case "vignette" ->
                    {
                        Color color = PovClip.keyed(effect.color, active.tick, null);
                        float r = color == null ? 0F : color.r;
                        float g = color == null ? 0F : color.g;
                        float b = color == null ? 0F : color.b;

                        /* Darkens what is under it by the texture, tinted: what is let through is the colour */
                        RenderSystem.enableBlend();
                        RenderSystem.blendFuncSeparate(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
                        RenderSystem.setShaderColor((1F - r) * opacity, (1F - g) * opacity, (1F - b) * opacity, 1F);
                        context.drawTexture(VIGNETTE, 0, 0, -90, 0F, 0F, w, h, w, h);
                        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
                        RenderSystem.defaultBlendFunc();
                    }
                    case "pumpkin" -> overlay(context, PUMPKIN, opacity, w, h);
                    case "freeze" -> overlay(context, FREEZE, effect.opacity.isEmpty() ? (float) PovClip.value(effect.progress, active.tick, 1D) * active.factor : opacity, w, h);
                    default -> {}
                }
            }
        }
    }

    private static void overlay(DrawContext context, Identifier texture, float opacity, int w, int h)
    {
        if (opacity <= 0F)
        {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1F, 1F, 1F, Math.min(opacity, 1F));
        context.drawTexture(texture, 0, 0, -90, 0F, 0F, w, h, w, h);
        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
    }

    /* Over the interface */

    /**
     * @param hud whether the camera lets the replay's interface through (boss bars, effects, chat
     *            are a part of it); the interfaces, toasts and the totem need only the POV actions
     */
    public static void renderOverHud(DrawContext context, Batcher2D batcher, List<Active> actives, Replay replay, float tick, int w, int h, boolean hud, boolean effects, int mouseX, int mouseY)
    {
        MinecraftClient mc = MinecraftClient.getInstance();
        int bars = 0;
        int toasts = 0;

        if (hud)
        {
            for (Active active : actives)
            {
                if (active.clip instanceof PovBossBarClip bar)
                {
                    renderBossBar(context, batcher, mc, bar, active, w, 12 + bars * 19);
                    bars += 1;
                }
                else if (active.clip instanceof PovStatusEffectsClip status)
                {
                    renderStatusEffects(context, batcher, mc, status, active, w);
                }
                else if (active.clip instanceof PovChatClip chat)
                {
                    renderChat(context, mc, chat, active, tick, w, h);
                }
            }
        }

        for (Active active : actives)
        {
            if (active.clip instanceof PovGuiClip gui)
            {
                context.draw();
                PovGuiRenderer.render(context, batcher, gui, active.tick, active.factor, replay, tick, w, h, mouseX, mouseY);
            }
        }

        /* The toasts and the totem over everything before them, the screens included */
        context.draw();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        for (Active active : actives)
        {
            if (active.clip instanceof PovToastClip toast)
            {
                renderToast(context, batcher, mc, toast, active, w, toasts * 32);
                toasts += 1;
            }
            else if (effects && active.clip instanceof PovScreenEffectsClip screen)
            {
                for (PovScreenEffectsClip.Effect effect : screen.effects.getAllTyped())
                {
                    if (effect.type.get().equals("totem"))
                    {
                        renderTotem(context, effect, active, w, h);
                    }
                }
            }
        }
    }

    private static void renderBossBar(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovBossBarClip bar, Active active, int w, int y)
    {
        String preset = bar.preset.get();
        int color = PovClip.keyed(bar.color, active.tick, switch (preset) { case "wither" -> 5; case "raid" -> 2; default -> 0; });
        int style = PovClip.keyed(bar.style, active.tick, preset.equals("raid") ? 2 : 0);
        float health = (float) Math.max(0D, Math.min(1D, PovClip.value(bar.health, active.tick, 1D)));
        String colorName = PovBossBarClip.COLORS.get(Math.floorMod(color, PovBossBarClip.COLORS.size()));
        String styleName = PovBossBarClip.STYLES.get(Math.floorMod(style, PovBossBarClip.STYLES.size()));
        int x = w / 2 - 91;
        int fill = (int) (health * 183F);

        RenderSystem.setShaderColor(1F, 1F, 1F, active.factor);
        batcher.drawGuiTexture(Ids.of("minecraft", "boss_bar/" + colorName + "_background"), x, y, 182, 5);

        if (style > 0)
        {
            batcher.drawGuiTexture(Ids.of("minecraft", "boss_bar/" + styleName + "_background"), x, y, 182, 5);
        }

        if (fill > 0)
        {
            batcher.drawGuiTexture(Ids.of("minecraft", "boss_bar/" + colorName + "_progress"), 182, 5, 0, 0, x, y, fill, 5);

            if (style > 0)
            {
                batcher.drawGuiTexture(Ids.of("minecraft", "boss_bar/" + styleName + "_progress"), 182, 5, 0, 0, x, y, fill, 5);
            }
        }

        RenderSystem.setShaderColor(1F, 1F, 1F, 1F);

        String name = PovClip.keyed(bar.name, active.tick, "");
        Text text = !name.isEmpty() ? Text.literal(name) : switch (preset)
        {
            case "ender_dragon" -> Text.translatable("entity.minecraft.ender_dragon");
            case "wither" -> Text.translatable("entity.minecraft.wither");
            case "raid" -> Text.translatable("event.minecraft.raid");
            default -> Text.literal("Boss");
        };

        context.drawTextWithShadow(mc.textRenderer, text, (w - mc.textRenderer.getWidth(text)) / 2, y - 9, 0xFFFFFF);
    }

    /** The game's places: the beneficial effects in a row at the top right, the others in a row under them */
    private static void renderStatusEffects(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovStatusEffectsClip clip, Active active, int w)
    {
        int good = 0;
        int bad = 0;

        for (PovStatusEffectsClip.Effect entry : clip.effects.getAllTyped())
        {
            Identifier id = Identifier.tryParse(entry.effect.get());
            StatusEffect effect = id == null ? null : Registries.STATUS_EFFECT.get(id);

            float alpha = (float) PovClip.value(entry.alpha, active.tick, 1D) * active.factor;

            /* Worn off: it gives its place in the row up, as the game's icons do */
            if (effect == null || alpha <= 0F)
            {
                continue;
            }

            boolean beneficial = effect.isBeneficial();
            int x;
            int y;

            if (beneficial)
            {
                good += 1;
                x = w - 25 * good;
                y = 1;
            }
            else
            {
                bad += 1;
                x = w - 25 * bad;
                y = 27;
            }

            float duration = (float) PovClip.value(entry.duration, active.tick, 1200D);

            /* Running out: it blinks, quicker the less is left */
            if (!entry.ambient.get() && duration < 200F)
            {
                alpha *= 0.55F + 0.45F * (float) Math.cos(duration * Math.PI / 5F);
            }

            RenderSystem.setShaderColor(1F, 1F, 1F, active.factor);
            batcher.drawGuiTexture(Ids.of("minecraft", entry.ambient.get() ? "hud/effect_background_ambient" : "hud/effect_background"), x, y, 24, 24);

            Sprite sprite = mc.getStatusEffectSpriteManager().getSprite(effect);

            RenderSystem.setShaderColor(1F, 1F, 1F, Math.max(0F, Math.min(1F, alpha)));
            context.drawSprite(x + 3, y + 3, 0, 18, 18, sprite);
            RenderSystem.setShaderColor(1F, 1F, 1F, 1F);
        }
    }

    /**
     * The chat as the game draws it: the sent lines above (the latest lowest), the line being typed
     * on its bar at the bottom with the cursor blinking where it stands, and, for a command, the
     * names that would complete it.
     */
    private static void renderChat(DrawContext context, MinecraftClient mc, PovChatClip chat, Active active, float tick, int w, int h)
    {
        TextRenderer font = mc.textRenderer;
        String executed = PovClip.keyed(chat.executed, active.tick, "");
        int scroll = (int) PovClip.value(chat.scroll, active.tick, 0D);
        int alpha = (int) (0x80 * active.factor);

        if (!executed.isEmpty())
        {
            String[] lines = executed.split("\n");
            int y = h - 40;

            for (int i = lines.length - 1 - scroll; i >= 0 && y > 0; i--, y -= 9)
            {
                if (i >= lines.length)
                {
                    continue;
                }

                context.fill(0, y - 1, 320, y + 8, alpha << 24);
                context.drawTextWithShadow(font, lines[i], 2, y, 0xFFFFFF);
            }
        }

        String text = PovClip.keyed(chat.chatText, active.tick, "");
        boolean bar = PovClip.flag(chat.background, active.tick, !chat.chatText.isEmpty());

        if (!bar && text.isEmpty())
        {
            return;
        }

        int typed = (int) Math.max(0D, Math.min(text.length(), PovClip.value(chat.typed, active.tick, text.length())));
        String shown = text.substring(0, typed);
        int cursor = Math.max(0, Math.min(shown.length(), PovClip.keyed(chat.cursor, active.tick, shown.length())));

        if (bar)
        {
            context.fill(2, h - 14, w - 2, h - 2, alpha << 24);
        }

        context.drawTextWithShadow(font, shown, 4, h - 12, 0xE0E0E0);

        /* The suggestions of a command: what is typed so far, completed by the names it could be */
        if (chat.suggestions.get() && shown.startsWith("/"))
        {
            List<String> names = commandNames(shown.substring(1));

            if (!names.isEmpty() && !shown.contains(" "))
            {
                int x = 4 + font.getWidth("/");
                int width = 0;

                for (String name : names)
                {
                    width = Math.max(width, font.getWidth(name));
                }

                int top = h - 14 - names.size() * 12;

                context.fill(x - 1, top, x + width + 1, h - 14, 0xD0000000);

                for (int i = 0; i < names.size(); i++)
                {
                    context.drawTextWithShadow(font, names.get(i), x, top + 2 + i * 12, i == 0 ? 0xFFFF00 : 0xAAAAAA);
                }

                String first = names.get(0);

                if (first.length() > shown.length() - 1)
                {
                    context.drawTextWithShadow(font, first.substring(shown.length() - 1), 4 + font.getWidth(shown), h - 12, 0x808080);
                }
            }
        }

        /* The cursor: an underscore at the end of the line, a bar inside it, blinking as the game's does */
        if (((int) (tick / 6F)) % 2 == 0)
        {
            int x = 4 + font.getWidth(shown.substring(0, cursor));

            if (cursor >= shown.length())
            {
                context.drawTextWithShadow(font, "_", x, h - 12, 0xE0E0E0);
            }
            else
            {
                context.fill(x, h - 13, x + 1, h - 3, 0xFFD0D0D0);
            }
        }
    }

    private static List<String> commandNames(String prefix)
    {
        List<String> names = new ArrayList<>();
        ClientPlayNetworkHandler network = MinecraftClient.getInstance().getNetworkHandler();

        if (network == null)
        {
            return names;
        }

        network.getCommandDispatcher().getRoot().getChildren().forEach((node) ->
        {
            if (node.getName().startsWith(prefix))
            {
                names.add(node.getName());
            }
        });

        names.sort(String::compareTo);

        return names.size() > 10 ? names.subList(0, 10) : names;
    }

    /** A toast, sliding in from the right at the start and out at the end of its clip */
    private static void renderToast(DrawContext context, Batcher2D batcher, MinecraftClient mc, PovToastClip toast, Active active, int w, int y)
    {
        float duration = toast.duration.get();
        float slide = toast.slide.isEmpty()
            ? Math.min(Math.min(active.tick / TOAST_SLIDE, 1F), Math.min((duration - active.tick) / TOAST_SLIDE, 1F))
            : (float) PovClip.value(toast.slide, active.tick, 1D);

        slide = Math.max(0F, Math.min(1F, slide));
        slide = 1F - (1F - slide) * (1F - slide);

        if (slide <= 0F)
        {
            return;
        }

        int preset = toast.preset.get();
        int x = w - (int) (160 * slide);
        TextRenderer font = mc.textRenderer;

        batcher.drawGuiTexture(Ids.of("minecraft", preset == PovToastClip.NOTICE ? "toast/system" : "toast/advancement"), x, y, 160, 32);

        Text title = !toast.titleText.get().isEmpty() ? Text.literal(toast.titleText.get()) : switch (preset)
        {
            case PovToastClip.GOAL -> Text.translatable("advancements.toast.goal");
            case PovToastClip.CHALLENGE -> Text.translatable("advancements.toast.challenge");
            case PovToastClip.NOTICE -> Text.literal(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.film.replays.pov.toast_notice").get());
            default -> Text.translatable("advancements.toast.task");
        };
        int titleColor = preset == PovToastClip.CHALLENGE ? 0xFF88FF : 0xFFFF00;
        Link link = toast.texture.get();
        ItemStack item = toast.item.get();
        boolean icon = link != null || (item != null && !item.isEmpty());
        int textX = icon || preset != PovToastClip.NOTICE ? x + 30 : x + 18;

        context.drawText(font, title, textX, y + 7, titleColor, false);

        List<OrderedText> lines = font.wrapLines(Text.literal(toast.description.get()), 160 - (textX - x) - 6);

        if (!lines.isEmpty())
        {
            context.drawText(font, lines.get(0), textX, y + 18, 0xFFFFFF, false);
        }

        if (link != null)
        {
            Texture texture = BBSModClient.getTextures().getTexture(link);

            context.draw();
            batcher.fullTexturedBox(texture, x + 8, y + 8, 16, 16);
        }
        else if (item != null && !item.isEmpty())
        {
            context.drawItem(item, x + 8, y + 8);
        }
    }

    /**
     * The totem of undying springing up in front of the eyes, the way the game shows it: in the
     * middle of the screen, swelling up and spinning round, then shrinking away, over two seconds
     * (or as its progress is keyed). The item is the effect's own, the totem when it has none.
     */
    private static void renderTotem(DrawContext context, PovScreenEffectsClip.Effect effect, Active active, int w, int h)
    {
        float p = (float) PovClip.value(effect.progress, active.tick, active.tick / TOTEM_TICKS);

        if (p <= 0F || p >= 1F || active.factor <= 0F)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        ItemStack stack = effect.item.get();

        if (stack == null || stack.isEmpty())
        {
            stack = new ItemStack(Items.TOTEM_OF_UNDYING);
        }

        /* The game's curve of the pop: up fast, a wobble, gone */
        float g = p * p;
        float c = p * g;
        float curve = 10.25F * c * g - 24.95F * g * g + 25.5F * c - 13.8F * g + 4F * p;
        float k = curve * (float) Math.PI;
        float size = (50F + 175F * (float) Math.sin(k)) * active.factor;
        MatrixStack matrices = context.getMatrices();

        context.draw();
        RenderSystem.enableDepthTest();
        RenderSystem.disableCull();
        matrices.push();
        matrices.translate(w / 2F, h / 2F, 100F);
        matrices.scale(size, -size, size);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(900F * Math.abs((float) Math.sin(k))));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(6F * (float) Math.cos(p * 8F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(6F * (float) Math.cos(p * 8F)));
        mc.getItemRenderer().renderItem(stack, ModelTransformationMode.FIXED, 0xF000F0, OverlayTexture.DEFAULT_UV, matrices, context.getVertexConsumers(), mc.world, 0);
        context.draw();
        matrices.pop();
        RenderSystem.enableCull();
    }

    /* In front of the eyes */

    /**
     * Make the particles the clips ask for at this tick, each emission once: the particle clips at
     * their start (and every interval after), the totem's burst at its start.
     */
    public static void spawnParticles(List<Active> actives, boolean actions, boolean effects)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc.world == null || mc.particleManager == null)
        {
            return;
        }

        Camera camera = mc.gameRenderer.getCamera();

        for (Active active : actives)
        {
            if (actions && active.clip instanceof PovParticleClip clip)
            {
                int interval = clip.interval.get();
                int emission = interval > 0 ? (int) (active.tick / interval) : 0;

                if (once(clip, emission))
                {
                    Identifier id = Identifier.tryParse(clip.particle.get());
                    Object type = id == null ? null : Registries.PARTICLE_TYPE.get(id);

                    if (type instanceof ParticleEffect particle)
                    {
                        burst(mc, camera, particle, clip.count.get(), (float) clip.offset.get().x, (float) clip.offset.get().y, (float) clip.offset.get().z, clip.spread.get(), clip.speed.get());
                    }
                }
            }
            else if (effects && active.clip instanceof PovScreenEffectsClip screen)
            {
                for (PovScreenEffectsClip.Effect effect : screen.effects.getAllTyped())
                {
                    /* The totem's burst: out from the middle of the view, a puff every tick for a second and a half, as the game's lasts */
                    int emission = (int) active.tick;

                    if (effect.type.get().equals("totem") && PovClip.flag(effect.particles, active.tick, true) && emission < 30 && once(effect, emission))
                    {
                        burst(mc, camera, ParticleTypes.TOTEM_OF_UNDYING, 16, 0F, 0F, 0.8F, 0.1F, 0.9F);
                    }
                }
            }
        }
    }

    /** Whether an emission is new; going back before it (a scrub, a loop) arms it again */
    private static boolean once(Object key, int emission)
    {
        Integer last = PUFFS.get(key);

        if (last != null && last >= emission)
        {
            if (last > emission)
            {
                PUFFS.put(key, emission);
            }

            return false;
        }

        PUFFS.put(key, emission);

        return true;
    }

    /** A puff around a point in the view's axes (x right, y up, z ahead), off in random directions */
    private static void burst(MinecraftClient mc, Camera camera, ParticleEffect particle, int count, float ox, float oy, float oz, float spread, float speed)
    {
        Vector3f forward = camera.getHorizontalPlane();
        Vector3f up = camera.getVerticalPlane();
        Vector3f right = new Vector3f(forward).cross(up);
        double x = camera.getPos().x + right.x * ox + up.x * oy + forward.x * oz;
        double y = camera.getPos().y + right.y * ox + up.y * oy + forward.y * oz;
        double z = camera.getPos().z + right.z * ox + up.z * oy + forward.z * oz;

        for (int i = 0; i < count; i++)
        {
            double px = x + (RANDOM.nextDouble() - 0.5D) * spread;
            double py = y + (RANDOM.nextDouble() - 0.5D) * spread;
            double pz = z + (RANDOM.nextDouble() - 0.5D) * spread;

            mc.particleManager.addParticle(particle, px, py, pz, (RANDOM.nextDouble() - 0.5D) * speed, (RANDOM.nextDouble() - 0.5D) * speed, (RANDOM.nextDouble() - 0.5D) * speed);
        }
    }
}
