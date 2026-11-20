package mchorse.bbs_mod.ui.forms.editors.panels;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs_mod.forms.forms.FakeSpaceForm;
import mchorse.bbs_mod.forms.renderers.FakeSpaceFormRenderer;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.base.BaseValueNumber;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIModelRenderer;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.values.UIValues;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Vector3f;

import java.util.function.Supplier;

/**
 * The planet editor: the planet turning on the left, under the mouse, and on the right everything about
 * it - its texture and how it is laid on (UV offset and scale), where it is and how big, how its axis
 * leans and how fast it turns, its atmosphere and its ring. Every field writes the planet at once, so
 * the preview and the scene follow.
 */
public class UIPlanetEditorOverlayPanel extends UIOverlayPanel
{
    private final FakeSpaceForm.Planet planet;
    private final UIButton textureButton;
    private final UIButton ringTextureButton;
    private final mchorse.bbs_mod.ui.framework.elements.utils.UILabel textureState;

    public UIPlanetEditorOverlayPanel(FakeSpaceForm.Planet planet)
    {
        super(UIKeys.FAKE_SPACE_PLANET_EDITOR);

        this.planet = planet;

        UIPlanetPreview preview = new UIPlanetPreview(planet);

        preview.relative(this.content).x(0).y(0).w(0.5F).h(1F);

        this.textureButton = new UIButton(IKey.EMPTY, (b) -> this.pick(planet.texture));
        this.textureButton.tooltip(UIKeys.FAKE_SPACE_PLANET_TEXTURE_TOOLTIP);
        this.ringTextureButton = new UIButton(IKey.EMPTY, (b) -> this.pick(planet.ringTexture));
        this.ringTextureButton.tooltip(UIKeys.FAKE_SPACE_PLANET_RING_TEXTURE_TOOLTIP);

        this.textureState = UI.label(IKey.EMPTY).color(0x88ffffff);

        UIButton clearTexture = new UIButton(UIKeys.FAKE_SPACE_PLANET_CLEAR_TEXTURE, (b) ->
        {
            planet.texture.set(null);
            this.updateTextureLabels();
        });
        UIButton clearRingTexture = new UIButton(UIKeys.FAKE_SPACE_PLANET_CLEAR_TEXTURE, (b) ->
        {
            planet.ringTexture.set(null);
            this.updateTextureLabels();
        });

        UIScrollView fields = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING,
            UI.label(UIKeys.FAKE_SPACE_PLANET_SURFACE).background(),
            UI.labelRow(key("name"), UIValues.textbox(60, () -> planet.name)),
            UI.labelRow(key("texture"), this.textureButton),
            this.textureState,
            clearTexture,
            UI.labelRow(key("uv_offset"), UI.row(this.trackpad(() -> planet.uvOffsetX, -2D, 2D, "uv_offset"), this.trackpad(() -> planet.uvOffsetY, -2D, 2D, "uv_offset"))),
            UI.labelRow(key("uv_scale"), UI.row(this.trackpad(() -> planet.uvScaleX, 0.1D, 8D, "uv_scale"), this.trackpad(() -> planet.uvScaleY, 0.1D, 8D, "uv_scale"))),
            this.color("color_a", () -> planet.colorA),
            this.color("color_b", () -> planet.colorB),

            UI.label(UIKeys.FAKE_SPACE_PLANET_PLACE).background().marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(key("position"), UI.row(this.trackpad(() -> planet.x, -4096D, 4096D, "position"), this.trackpad(() -> planet.y, -4096D, 4096D, "position"), this.trackpad(() -> planet.z, -4096D, 4096D, "position"))),
            UI.labelRow(key("radius"), this.trackpad(() -> planet.radius, 0.5D, 1024D, "radius")),
            UI.labelRow(key("tilt"), this.trackpad(() -> planet.tilt, -90D, 90D, "tilt")),
            UI.labelRow(key("rotation_speed"), this.trackpad(() -> planet.rotationSpeed, -360D, 360D, "rotation_speed")),

            UI.label(UIKeys.FAKE_SPACE_PLANET_SKY).background().marginTop(UIConstants.SECTION_GAP),
            UI.labelRow(key("atmosphere"), this.trackpad(() -> planet.atmosphere, 0D, 1D, "atmosphere")),
            this.color("atmosphere_color", () -> planet.atmosphereColor),
            this.toggle("ring", () -> planet.ring),
            UI.labelRow(key("ring_inner"), this.trackpad(() -> planet.ringInner, 1.01D, 6D, "ring_inner")),
            UI.labelRow(key("ring_outer"), this.trackpad(() -> planet.ringOuter, 1.05D, 10D, "ring_outer")),
            this.color("ring_color", () -> planet.ringColor),
            UI.labelRow(key("ring_texture"), this.ringTextureButton),
            clearRingTexture
        );

        fields.relative(this.content).x(0.5F).y(0).w(0.5F).h(1F);

        this.content.add(preview, fields);
        this.updateTextureLabels();
    }

    private static IKey key(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.fake_space.planet." + id);
    }

    private static IKey tooltip(String id)
    {
        return L10n.lang("bbs.ui.forms.editors.fake_space.planet." + id + "-tooltip");
    }

    private UITrackpad trackpad(Supplier<? extends BaseValueNumber<?>> value, double min, double max, String id)
    {
        UITrackpad trackpad = UIValues.trackpad(value);

        trackpad.limit(min, max);
        trackpad.tooltip(tooltip(id));

        return trackpad;
    }

    private UIElement toggle(String id, Supplier<ValueBoolean> value)
    {
        UIElement toggle = UIValues.toggle(key(id), value);

        toggle.tooltip(tooltip(id));

        return toggle;
    }

    private UIElement color(String id, Supplier<ValueColor> value)
    {
        UIElement color = UIValues.color(value);

        color.tooltip(tooltip(id));

        return UI.labelRow(key(id), color);
    }

    private void pick(ValueLink value)
    {
        /* Inside this window, filling it: opened over the studio it would sit under the window, out of reach */
        UITexturePicker.open(this, value.get(), (link) ->
        {
            value.set(link);
            this.updateTextureLabels();
        });
    }

    private void updateTextureLabels()
    {
        this.textureButton.label = this.planet.texture.get() == null ? UIKeys.FAKE_SPACE_PLANET_PICK_TEXTURE : IKey.constant(this.planet.texture.get().toString());
        this.ringTextureButton.label = this.planet.ringTexture.get() == null ? UIKeys.FAKE_SPACE_PLANET_PICK_TEXTURE : IKey.constant(this.planet.ringTexture.get().toString());
        this.textureState.label = this.planet.texture.get() == null ? UIKeys.FAKE_SPACE_PLANET_PROCEDURAL : UIKeys.FAKE_SPACE_PLANET_TEXTURED;
    }

    /** The planet alone at the middle, sized to fit, turned by the mouse */
    public static class UIPlanetPreview extends UIModelRenderer
    {
        private final FakeSpaceForm.Planet planet;

        public UIPlanetPreview(FakeSpaceForm.Planet planet)
        {
            this.planet = planet;
        }

        @Override
        public void reset()
        {
            super.reset();

            this.setPosition(0F, 0F, 0F);
            this.setDistance(4.5F);
            this.setRotation(30F, 20F);
        }

        @Override
        protected void renderGrid(UIContext context)
        {}

        @Override
        protected void renderUserModel(UIContext context)
        {
            if (this.planet == null)
            {
                return;
            }

            MatrixStack stack = context.batcher.getContext().getMatrices();
            MinecraftClient mc = MinecraftClient.getInstance();
            float seconds = mc.world == null ? (System.currentTimeMillis() % 3600000L) / 1000F : (mc.world.getTime() % 72000L + context.getTransition()) / 20F;
            float radius = this.planet.radius.get();
            /* The planet is drawn a block wide whatever its size, so the preview always shows all of it */
            float fit = 1F / Math.max(radius, 0.01F);
            Vector3f light = FakeSpaceFormRenderer.direction(-50F, 25F, new Vector3f());

            stack.push();
            stack.scale(fit, fit, fit);

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);

            FakeSpaceFormRenderer.drawGlobe(stack, this.planet, 0F, 0F, 0F, radius, light, seconds, null);

            RenderSystem.depthMask(false);
            FakeSpaceFormRenderer.drawHaloAndRing(stack, this.planet, 0F, 0F, 0F, radius, light);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();

            stack.pop();
        }
    }
}
