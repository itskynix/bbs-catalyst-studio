package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.clips.fx.PostEffect;
import mchorse.bbs_mod.camera.clips.fx.PostEffectStack;
import mchorse.bbs_mod.catalyst.CatalystLayer;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.panels.UICatalystPanel;
import mchorse.bbs_mod.ui.film.clips.UIPostEffectPicker;
import mchorse.bbs_mod.ui.film.clips.pov.UIPovEntryList;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UIScrollView;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextarea;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.windows.RoundedDraw;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.Function;

/**
 * The inspector, the full height of the page on the right: the picked clip's name, and under the
 * Video, Audio, Effects and Transition tabs its settings in folding sections - transform (zoom with
 * a lock for both axes, position, rotation, anchor, flips), crop, composite (opacity, blend mode),
 * the clip's post-process effect stack, and how it comes in.
 *
 * <p>Transform and opacity go through the panel's keyframe-aware setter: a property with keyframes
 * gets one at the playhead, one without simply takes the value.</p>
 */
public class UINleInspector extends UIElement
{
    private static final int TABS_H = 20;
    private static final int HEADER_H = 22;

    private static final String[] TRANSITIONS = {"", "cross_dissolve", "dip_to_black", "slide"};
    private static final String[] BLEND_MODES = {"NORMAL", "MULTIPLY", "SCREEN", "ADD", "OVERLAY"};

    private static final ValueFloat NOTHING = new ValueFloat("nothing", 0F);

    private final UICatalystPanel panel;

    private int tab;
    private final UIScrollView[] pages = new UIScrollView[4];
    private final IKey[] tabTitles = {UIKeys.CATALYST_NLE_TAB_VIDEO, UIKeys.CATALYST_NLE_TAB_AUDIO, UIKeys.CATALYST_NLE_TAB_EFFECTS, UIKeys.CATALYST_NLE_TAB_TRANSITION};

    private final UILabel empty;
    private final UITextbox name;

    /* Video */
    private final UISection textSection;
    private final UITextarea text;
    private final UISection sourceSection;
    private final UILabel source;
    private final UIButton extend;
    private final UITrackpad filmFps;
    private final UIElement filmFpsRow;
    private final UISection transformSection;
    private final UISection cropSection;

    /* Effects */
    private final UIPovEntryList<PostEffectStack.Layer> effects;
    private final UIElement effectFields;
    private final UILabel[] paramLabels = new UILabel[PostEffectStack.PARAMS];
    private final UITrackpad[] params = new UITrackpad[PostEffectStack.PARAMS];
    private final UIElement[] paramRows = new UIElement[PostEffectStack.PARAMS];
    private final UILabel effectColorLabel;
    private final UIColor effectColor;

    private CatalystLayer shown;

    public UINleInspector(UICatalystPanel panel)
    {
        this.panel = panel;

        for (int i = 0; i < this.pages.length; i++)
        {
            this.pages[i] = UI.scrollView(4, 5);
            this.pages[i].relative(this).y(TABS_H + HEADER_H).w(1F).h(1F, -TABS_H - HEADER_H);
            this.add(this.pages[i]);
        }

        this.name = new UITextbox(200, (t) -> this.edit("name", (layer) -> layer.name = t));
        this.name.relative(this).x(5).y(TABS_H + 2).w(1F, -10).h(18);

        this.empty = UI.label(UIKeys.CATALYST_NLE_INSPECTOR_EMPTY).color(0x88ffffff);
        this.empty.labelAnchor(0.5F, 0.5F);
        this.empty.relative(this).y(TABS_H).w(1F).h(60);

        this.add(this.name, this.empty);

        /* ── Video ── */
        this.text = new UITextarea((Consumer<String>) (t) -> this.edit("text", (layer) -> layer.resourcePath = t));
        this.text.background().wrap(true).h(64);

        UITrackpad fontSize = this.trackpad("font", (layer) -> (double) layer.fontSize, (layer, v) -> layer.fontSize = (int) Math.round(v));
        fontSize.limit(4, 400, true);

        UIColor textColor = new UIColor((c) -> this.edit("text_color", (layer) -> layer.textColor = 0xff000000 | c));
        textColor.valueBinding(() -> this.withShown((layer) -> textColor.setColor(layer.textColor)));

        this.textSection = this.section(UIKeys.CATALYST_NLE_SECTION_TEXT, this.text, UI.labelRow(UIKeys.CATALYST_NLE_FONT_SIZE, fontSize), textColor);

        this.source = UI.label(IKey.EMPTY).color(0xaaffffff);
        this.extend = new UIButton(IKey.EMPTY, (b) -> this.edit("extend", (layer) -> layer.duration = Math.max(1, layer.mediaDuration - layer.mediaOffset)));
        this.filmFps = this.trackpad("film_fps", (layer) -> (double) layer.filmFps, (layer, v) -> layer.filmFps = Math.max(1, (int) Math.round(v)));
        this.filmFps.limit(1, 240, true);
        this.filmFpsRow = UI.labelRow(UIKeys.CATALYST_NLE_FILM_FPS, this.filmFps);
        this.sourceSection = this.section(UIKeys.CATALYST_NLE_SECTION_SOURCE, this.source, this.extend, this.filmFpsRow);

        UITrackpad zoomX = this.effective(UICatalystTimeline.PROP_SCALE, 0, 2);
        UITrackpad zoomY = this.effective(UICatalystTimeline.PROP_SCALE, 1, 3);
        UIIcon lock = new UIIcon(() -> this.shown != null && this.shown.scaleLocked ? Icons.LOCKED : Icons.UNLOCKED, (b) -> this.edit("lock", (layer) -> layer.scaleLocked = !layer.scaleLocked));
        lock.tooltip(UIKeys.CATALYST_NLE_LOCK_RATIO);
        lock.wh(18, 18);

        UITrackpad posX = this.effective(UICatalystTimeline.PROP_POSITION, 0, 0);
        UITrackpad posY = this.effective(UICatalystTimeline.PROP_POSITION, 1, 1);
        UITrackpad rotation = this.effective(UICatalystTimeline.PROP_ROTATION, 0, 4);
        UITrackpad anchorX = this.effective(UICatalystTimeline.PROP_ANCHOR, 0, 6);
        UITrackpad anchorY = this.effective(UICatalystTimeline.PROP_ANCHOR, 1, 7);

        anchorX.limit(0, 1);
        anchorY.limit(0, 1);

        UIToggle flipH = this.toggle(UIKeys.CATALYST_NLE_FLIP_H, (layer) -> layer.flipH, (layer, v) -> layer.flipH = v);
        UIToggle flipV = this.toggle(UIKeys.CATALYST_NLE_FLIP_V, (layer) -> layer.flipV, (layer, v) -> layer.flipV = v);

        /* Both axes get the same room; the lock sits at the right end of the group's title */
        UIElement zoomXRow = UI.labelRow(IKey.raw("X"), this.layerKeyed(zoomX, CatalystLayer.SUB_PROP_SCALE_X));
        UIElement zoomYRow = UI.labelRow(IKey.raw("Y"), this.layerKeyed(zoomY, CatalystLayer.SUB_PROP_SCALE_Y));
        UILabel zoomTitle = UI.label(UIKeys.CATALYST_NLE_ZOOM_XY);

        zoomTitle.w(1F, -22);
        lock.w(18);

        UIElement zoomHeader = UI.row(4, 0, 18, zoomTitle, lock);

        /* The nine anchors: the clip's own corner, edge or centre onto the same one of the frame */
        mchorse.bbs_mod.ui.film.clips.widgets.UIAnchorGrid placement = new mchorse.bbs_mod.ui.film.clips.widgets.UIAnchorGrid((x, y) -> this.edit("placement", (layer) ->
        {
            mchorse.bbs_mod.catalyst.CatalystComposition comp = this.panel.getActiveComposition();
            int w = comp != null && comp.width > 0 ? comp.width : 1920;
            int h = comp != null && comp.height > 0 ? comp.height : 1080;

            this.panel.applyPropValue(layer, UICatalystTimeline.PROP_ANCHOR, 0, x);
            this.panel.applyPropValue(layer, UICatalystTimeline.PROP_ANCHOR, 1, y);
            this.panel.applyPropValue(layer, UICatalystTimeline.PROP_POSITION, 0, (x - 0.5F) * w);
            this.panel.applyPropValue(layer, UICatalystTimeline.PROP_POSITION, 1, (y - 0.5F) * h);
        }));

        placement.valueBinding(() -> this.withShown((layer) ->
        {
            mchorse.bbs_mod.catalyst.CatalystComposition comp = this.panel.getActiveComposition();
            int w = comp != null && comp.width > 0 ? comp.width : 1920;
            int h = comp != null && comp.height > 0 ? comp.height : 1080;
            float[] eff = layer.computeEffective(this.panel.getCurrentFrame());

            /* Lit only while the clip really sits on one of the nine */
            boolean placed = Math.abs(eff[0] - (eff[6] - 0.5F) * w) < 0.5F && Math.abs(eff[1] - (eff[7] - 0.5F) * h) < 0.5F;

            placement.setValue(placed ? eff[6] : -1F, placed ? eff[7] : -1F);
        }));

        this.transformSection = this.section(UIKeys.CATALYST_NLE_SECTION_TRANSFORM,
            UI.label(UIKeys.CATALYST_NLE_PLACEMENT).marginTop(6), placement,
            zoomHeader, zoomXRow, zoomYRow,
            UI.label(UIKeys.CATALYST_NLE_POSITION),
            UI.labelRow(IKey.raw("X"), this.layerKeyed(posX, CatalystLayer.SUB_PROP_POS_X)),
            UI.labelRow(IKey.raw("Y"), this.layerKeyed(posY, CatalystLayer.SUB_PROP_POS_Y)),
            UI.labelRow(UIKeys.CATALYST_NLE_ROTATION, this.layerKeyed(rotation, CatalystLayer.SUB_PROP_ROTATION)),
            UI.label(UIKeys.CATALYST_NLE_ANCHOR),
            UI.labelRow(IKey.raw("X"), this.layerKeyed(anchorX, CatalystLayer.SUB_PROP_ANCHOR_X)),
            UI.labelRow(IKey.raw("Y"), this.layerKeyed(anchorY, CatalystLayer.SUB_PROP_ANCHOR_Y)),
            flipH, flipV);

        UITrackpad cropL = this.trackpad("crop_l", (layer) -> layer.cropLeft * 100D, (layer, v) -> layer.cropLeft = crop(v));
        UITrackpad cropR = this.trackpad("crop_r", (layer) -> layer.cropRight * 100D, (layer, v) -> layer.cropRight = crop(v));
        UITrackpad cropT = this.trackpad("crop_t", (layer) -> layer.cropTop * 100D, (layer, v) -> layer.cropTop = crop(v));
        UITrackpad cropB = this.trackpad("crop_b", (layer) -> layer.cropBottom * 100D, (layer, v) -> layer.cropBottom = crop(v));

        for (UITrackpad c : new UITrackpad[] {cropL, cropR, cropT, cropB})
        {
            c.limit(0, 100);
        }

        this.cropSection = this.section(UIKeys.CATALYST_NLE_SECTION_CROP,
            UI.labelRow(UIKeys.CATALYST_NLE_CROP_LEFT, cropL), UI.labelRow(UIKeys.CATALYST_NLE_CROP_RIGHT, cropR),
            UI.labelRow(UIKeys.CATALYST_NLE_CROP_TOP, cropT), UI.labelRow(UIKeys.CATALYST_NLE_CROP_BOTTOM, cropB));

        UITrackpad opacity = this.effective(UICatalystTimeline.PROP_OPACITY, 0, 5);
        opacity.limit(0, 100);

        UICirculate blend = new UICirculate((b) -> this.edit("blend", (layer) -> layer.blendMode = BLEND_MODES[b.getValue()]));

        for (String mode : BLEND_MODES)
        {
            blend.addLabel(mchorse.bbs_mod.l10n.L10n.lang("bbs.ui.catalyst.nle.blend." + mode.toLowerCase()));
        }

        blend.valueBinding(() -> this.withShown((layer) -> blend.setValue(indexOf(BLEND_MODES, layer.blendMode.toUpperCase()))));

        UISection composite = this.section(UIKeys.CATALYST_NLE_SECTION_COMPOSITE,
            UI.labelRow(UIKeys.CATALYST_NLE_OPACITY, this.layerKeyed(opacity, CatalystLayer.SUB_PROP_OPACITY)), UI.labelRow(UIKeys.CATALYST_NLE_BLEND_MODE, blend));

        this.pages[0].add(this.textSection, this.sourceSection, this.transformSection, this.cropSection, composite);

        /* ── Audio ── */
        UITrackpad volume = this.effective(UICatalystTimeline.PROP_VOLUME, 0, -1);
        volume.limit(0, 2);
        UITrackpad pan = this.effective(UICatalystTimeline.PROP_PAN, 0, -2);
        pan.limit(-1, 1);
        UITrackpad offset = this.trackpad("audio_offset", (layer) -> (double) layer.audioOffset, (layer, v) -> layer.audioOffset = (int) Math.round(v));
        offset.integer();

        this.pages[1].add(this.section(UIKeys.CATALYST_NLE_SECTION_AUDIO,
            UI.labelRow(UIKeys.CATALYST_NLE_VOLUME, this.layerKeyed(volume, CatalystLayer.SUB_PROP_VOLUME)), UI.labelRow(UIKeys.CATALYST_NLE_PAN, this.layerKeyed(pan, CatalystLayer.SUB_PROP_PAN)), UI.labelRow(UIKeys.CATALYST_NLE_AUDIO_OFFSET, offset)));

        /* ── Effects ── */
        this.effects = new UIPovEntryList<>(UINleInspector::effectLabel, (l) -> this.pickEffect(l.isEmpty() ? null : l.get(0)));
        this.effects.background();
        this.effects.h(16 * 5);

        UIIcon add = new UIIcon(Icons.ADD, (b) -> this.openPicker());
        UIIcon remove = new UIIcon(Icons.REMOVE, (b) -> this.withEffect((e) -> this.shown.effects.remove(e)));
        UIIcon up = new UIIcon(Icons.MOVE_UP, (b) -> this.withEffect((e) -> this.shown.effects.move(e, -1)));
        UIIcon down = new UIIcon(Icons.MOVE_DOWN, (b) -> this.withEffect((e) -> this.shown.effects.move(e, 1)));

        add.tooltip(UIKeys.POST_FX_ADD);
        remove.tooltip(UIKeys.POST_FX_REMOVE);
        up.tooltip(UIKeys.POST_FX_MOVE_UP);
        down.tooltip(UIKeys.POST_FX_MOVE_DOWN);

        UIToggle enabled = new UIToggle(UIKeys.POST_FX_ENABLED, (b) -> this.withPickedEffect((e) -> e.enabled.set(b.getValue())));
        enabled.valueBinding(() -> this.withPickedEffect((e) -> enabled.setValue(e.enabled.get())));

        UITrackpad intensity = this.effectValue(() -> this.pickedEffect() == null ? NOTHING : this.pickedEffect().intensity, -1);
        intensity.limit(0, 1);

        UIElement column = UI.column(enabled, UI.label(UIKeys.POST_FX_INTENSITY), this.effectKeyed(intensity, -1));

        for (int i = 0; i < PostEffectStack.PARAMS; i++)
        {
            int index = i;

            this.paramLabels[i] = UI.label(IKey.EMPTY);
            this.params[i] = this.effectValue(() -> this.pickedEffect() == null ? NOTHING : this.pickedEffect().values[index], index);
            this.paramRows[i] = this.effectKeyed(this.params[i], index);
            column.add(this.paramLabels[i], this.paramRows[i]);
        }

        this.effectColorLabel = UI.label(UIKeys.POST_FX_COLOR);
        this.effectColor = new UIColor((c) -> this.withPickedEffect((e) -> e.color.set(c & Colors.RGB)));
        this.effectColor.valueBinding(() -> this.withPickedEffect((e) -> this.effectColor.setColor(e.color.get())));
        column.add(this.effectColorLabel, this.effectColor);
        this.effectFields = column;

        this.pages[2].add(this.section(UIKeys.POST_FX_STACK, UI.row(add, remove, up, down), this.effects, this.effectFields));

        /* ── Transition ── */
        UICirculate transition = new UICirculate((b) -> this.edit("transition", (layer) -> layer.transitionIn = TRANSITIONS[b.getValue()]));

        transition.addLabel(UIKeys.CATALYST_NLE_TRANSITION_NONE);
        transition.addLabel(UIKeys.CATALYST_NLE_TRANSITION_CROSS_DISSOLVE);
        transition.addLabel(UIKeys.CATALYST_NLE_TRANSITION_DIP_TO_BLACK);
        transition.addLabel(UIKeys.CATALYST_NLE_TRANSITION_SLIDE);
        transition.valueBinding(() -> this.withShown((layer) -> transition.setValue(indexOf(TRANSITIONS, layer.transitionIn))));

        UITrackpad length = this.trackpad("transition_frames", (layer) -> (double) layer.transitionFrames, (layer, v) -> layer.transitionFrames = Math.max(1, (int) Math.round(v)));
        length.limit(1, 600, true);

        this.pages[3].add(this.section(UIKeys.CATALYST_NLE_SECTION_TRANSITION,
            UI.labelRow(UIKeys.CATALYST_NLE_TRANSITION_TYPE, transition), UI.labelRow(UIKeys.CATALYST_NLE_TRANSITION_LENGTH, length)));

        this.refresh();
    }

    /* ── Helpers ── */

    private static float crop(double percent)
    {
        return (float) Math.max(0D, Math.min(0.99D, percent / 100D));
    }

    private static int indexOf(String[] values, String value)
    {
        for (int i = 0; i < values.length; i++)
        {
            if (values[i].equals(value))
            {
                return i;
            }
        }

        return 0;
    }

    private static String effectLabel(PostEffectStack.Layer layer)
    {
        PostEffect effect = PostEffect.get(layer.effect.get());
        String name = effect == null ? layer.effect.get() : UIPostEffectPicker.effectName(effect).get();

        return layer.enabled.get() ? name : "- " + name;
    }

    private UISection section(IKey title, UIElement... fields)
    {
        UISection section = new UISection(title);

        section.fields.add(fields);

        return section;
    }

    private void withShown(Consumer<CatalystLayer> action)
    {
        if (this.shown != null)
        {
            action.accept(this.shown);
        }
    }

    /** An edit of the shown clip: one undo step per run of edits to the same thing, and a save soon after */
    private void edit(String what, Consumer<CatalystLayer> change)
    {
        CatalystLayer layer = this.shown;

        if (layer == null)
        {
            return;
        }

        this.panel.pushUndoFor(layer.id + ":" + what);
        change.accept(layer);
        this.panel.markDirty();
    }

    private UITrackpad trackpad(String what, Function<CatalystLayer, Double> get, java.util.function.BiConsumer<CatalystLayer, Double> set)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.edit(what, (layer) -> set.accept(layer, v)));

        trackpad.valueBinding(() -> this.withShown((layer) -> trackpad.setValue(get.apply(layer))));

        return trackpad;
    }

    private UIToggle toggle(IKey label, Function<CatalystLayer, Boolean> get, java.util.function.BiConsumer<CatalystLayer, Boolean> set)
    {
        UIToggle toggle = new UIToggle(label, (b) -> this.edit(label.get(), (layer) -> set.accept(layer, b.getValue())));

        toggle.valueBinding(() -> this.withShown((layer) -> toggle.setValue(get.apply(layer))));

        return toggle;
    }

    /**
     * A keyframe-aware field: shows the value at the playhead ({@code effective} indexes the clip's
     * computed transform; -1 is the volume, -2 the pan) and writes through the panel's setter.
     */
    private UITrackpad effective(int prop, int component, int effective)
    {
        UITrackpad trackpad = new UITrackpad(null);

        trackpad.callback = (v) -> this.edit("prop" + prop + ":" + component, (layer) ->
        {
            float value = v.floatValue();

            /* Locked zoom: both axes take the change of the one edited */
            if (prop == UICatalystTimeline.PROP_SCALE && layer.scaleLocked)
            {
                float[] eff = layer.computeEffective(this.panel.getCurrentFrame());
                float old = eff[2 + component];
                float other = eff[3 - component];
                float ratio = Math.abs(old) < 1.0E-4F ? 1F : value / old;

                this.panel.applyPropValue(layer, prop, 1 - component, other * ratio);
            }

            this.panel.applyPropValue(layer, prop, component, value);
        });

        trackpad.valueBinding(() -> this.withShown((layer) ->
        {
            int frame = this.panel.getCurrentFrame();
            double value = effective == -1 ? layer.computeEffectiveVolume(frame)
                : effective == -2 ? layer.computeEffectivePan(frame)
                : layer.computeEffective(frame)[effective];

            trackpad.setValue(value);
        }));

        return trackpad;
    }

    /** An effect's intensity (-1) or parameter: it shows the value at the playhead and edits the key there once it has keys */
    private UITrackpad effectValue(java.util.function.Supplier<ValueFloat> value, int index)
    {
        UITrackpad trackpad = new UITrackpad((v) ->
        {
            PostEffectStack.Layer effect = this.pickedEffect();

            if (effect != null && this.shown != null)
            {
                this.panel.pushUndoFor(this.shown.id + ":fx");

                if (effect.channel(index).isEmpty())
                {
                    value.get().set(v.floatValue());
                }
                else
                {
                    effect.channel(index).insert(this.panel.getCurrentFrame(), v);
                }

                this.panel.markDirty();
            }
        });

        trackpad.valueBinding(() ->
        {
            PostEffectStack.Layer effect = this.pickedEffect();

            trackpad.setValue(effect == null ? 0D : effect.value(index, this.panel.getCurrentFrame()));
        });

        return trackpad;
    }

    /* ── Keyframes ── */

    /**
     * A field with its keyframe controls on the right: an arrow to the key before the playhead, the
     * diamond that puts a key down at the playhead or takes the one there away (filled when there is
     * one, amber when the field is animated at all), and an arrow to the key after it.
     */
    private UIElement keyedRow(UITrackpad pad, java.util.function.BooleanSupplier has, java.util.function.BooleanSupplier animated, Runnable toggle, java.util.function.IntUnaryOperator neighbour)
    {
        UIIcon prev = this.keyArrow(true, () -> this.jump(neighbour.applyAsInt(-1)));
        UIIcon next = this.keyArrow(false, () -> this.jump(neighbour.applyAsInt(1)));
        UIIcon diamond = new UIIcon(Icons.ADD, (b) -> toggle.run())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 2, RoundedDraw.ALL, this.hover ? 0x44ffffff : 0x22ffffff);

                boolean on = has.getAsBoolean();
                int color = on ? 0xffffc83d : (animated.getAsBoolean() ? 0xffd9a62e : (this.hover ? 0xffffffff : 0x99ffffff));
                int cx = this.area.mx();
                int cy = this.area.my();

                for (int i = -5; i <= 5; i++)
                {
                    int half = 5 - Math.abs(i);

                    if (on)
                    {
                        context.batcher.box(cx - half, cy + i, cx + half + 1, cy + i + 1, color);
                    }
                    else
                    {
                        context.batcher.box(cx - half, cy + i, cx - half + 1, cy + i + 1, color);
                        context.batcher.box(cx + half, cy + i, cx + half + 1, cy + i + 1, color);
                    }
                }
            }
        };

        diamond.w(12).h(16);
        prev.tooltip(UIKeys.CATALYST_NLE_KEY_PREV);
        next.tooltip(UIKeys.CATALYST_NLE_KEY_NEXT);
        diamond.tooltip(UIKeys.CATALYST_NLE_KEY_TOGGLE);

        /* One small capsule: three 12 by 16 buttons a pixel apart */
        UIElement keys = UI.row(1, 0, 16, prev, diamond, next);

        keys.w(38);
        keys.marginTop(2);
        pad.w(1F, -42);

        return UI.row(4, 0, 20, pad, keys);
    }

    /** A small triangle button, pointing left for the key before the playhead and right for the one after it */
    private UIIcon keyArrow(boolean left, Runnable action)
    {
        UIIcon arrow = new UIIcon(Icons.ADD, (b) -> action.run())
        {
            @Override
            protected void renderSkin(UIContext context)
            {
                RoundedDraw.fill(context.batcher, this.area.x, this.area.y, this.area.ex(), this.area.ey(), 2, RoundedDraw.ALL, this.hover ? 0x44ffffff : 0x22ffffff);

                int cx = this.area.mx();
                int cy = this.area.my();
                int color = this.hover ? 0xffffffff : 0xb0ffffff;

                for (int k = 0; k < 4; k++)
                {
                    int x = left ? cx - 2 + k : cx + 1 - k;

                    context.batcher.box(x, cy - k, x + 1, cy + k + 1, color);
                }
            }
        };

        arrow.w(12).h(16);

        return arrow;
    }

    private void jump(int frame)
    {
        if (frame >= 0)
        {
            this.panel.jumpToFrame(frame);
        }
    }

    /** A field of the clip's own transform, keyed on one of its axes ({@code prop} is a SUB_PROP_ constant) */
    private UIElement layerKeyed(UITrackpad pad, int prop)
    {
        return this.keyedRow(pad,
            () -> this.shown != null && this.shown.hasKeyframeAtSingle(prop, this.panel.getCurrentFrame()),
            () -> this.shown != null && this.shown.channelFor(prop) != null && !this.shown.channelFor(prop).isEmpty(),
            () -> this.edit("key" + prop, (layer) -> layer.toggleKeyframeSingle(prop, this.panel.getCurrentFrame())),
            (dir) -> this.shown == null ? -1 : CatalystLayer.neighbourKey(this.shown.channelFor(prop), this.panel.getCurrentFrame(), dir));
    }

    /** An effect's intensity (-1) or parameter, keyed in the frames of the timeline like everything else on the clip */
    private UIElement effectKeyed(UITrackpad pad, int index)
    {
        return this.keyedRow(pad,
            () ->
            {
                PostEffectStack.Layer effect = this.pickedEffect();

                return effect != null && effectKeyAt(effect.channel(index), this.panel.getCurrentFrame()) >= 0;
            },
            () ->
            {
                PostEffectStack.Layer effect = this.pickedEffect();

                return effect != null && !effect.channel(index).isEmpty();
            },
            () -> this.toggleEffectKey(index),
            (dir) ->
            {
                PostEffectStack.Layer effect = this.pickedEffect();

                return effect == null ? -1 : CatalystLayer.neighbourKey(effect.channel(index), this.panel.getCurrentFrame(), dir);
            });
    }

    private static int effectKeyAt(mchorse.bbs_mod.utils.keyframes.KeyframeChannel<Double> channel, int frame)
    {
        List<mchorse.bbs_mod.utils.keyframes.Keyframe<Double>> keys = channel.getKeyframes();

        for (int i = 0; i < keys.size(); i++)
        {
            if (Math.round(keys.get(i).getTick()) == frame)
            {
                return i;
            }
        }

        return -1;
    }

    private void toggleEffectKey(int index)
    {
        PostEffectStack.Layer effect = this.pickedEffect();

        if (effect == null || this.shown == null)
        {
            return;
        }

        this.panel.pushUndoFor(this.shown.id + ":fxkey" + index);

        int frame = this.panel.getCurrentFrame();
        mchorse.bbs_mod.utils.keyframes.KeyframeChannel<Double> channel = effect.channel(index);
        int at = effectKeyAt(channel, frame);

        if (at >= 0)
        {
            channel.remove(at);
        }
        else
        {
            channel.insert(frame, effect.value(index, frame));
        }

        this.panel.markDirty();
    }

    /* ── Effects ── */

    private PostEffectStack.Layer pickedEffect()
    {
        return this.effects.getCurrentFirst();
    }

    private void withPickedEffect(Consumer<PostEffectStack.Layer> action)
    {
        PostEffectStack.Layer effect = this.pickedEffect();

        if (effect != null && this.shown != null)
        {
            action.accept(effect);
            this.panel.markDirty();
        }
    }

    private void withEffect(Consumer<PostEffectStack.Layer> action)
    {
        PostEffectStack.Layer effect = this.pickedEffect();

        if (effect == null || this.shown == null)
        {
            return;
        }

        this.panel.pushUndo();
        action.accept(effect);
        this.panel.markDirty();
        this.effects.setList(this.shown.effects.getAllTyped());

        boolean kept = this.shown.effects.getAllTyped().contains(effect);

        if (kept)
        {
            this.effects.setCurrentScroll(effect);
        }

        this.pickEffect(kept ? effect : null);
    }

    private void openPicker()
    {
        if (this.shown == null)
        {
            return;
        }

        UIOverlay.addOverlay(this.getContext(), new UIPostEffectPicker(this::addEffect), 560, 380);
    }

    /** Adds an effect to the shown clip's stack, from the picker or from the library tab */
    public void addEffect(PostEffect effect)
    {
        if (this.shown == null || effect == null)
        {
            return;
        }

        this.panel.pushUndo();

        PostEffectStack.Layer layer = this.shown.effects.add(effect);

        this.panel.markDirty();
        this.effects.setList(this.shown.effects.getAllTyped());
        this.effects.setCurrentScroll(layer);
        this.pickEffect(layer);
        this.tab = 2;
        this.updateTabs();
    }

    private void pickEffect(PostEffectStack.Layer layer)
    {
        PostEffect effect = layer == null ? null : PostEffect.get(layer.effect.get());

        this.effectFields.setVisible(effect != null);

        if (effect != null)
        {
            for (int i = 0; i < PostEffectStack.PARAMS; i++)
            {
                boolean has = i < effect.params.length;

                this.paramLabels[i].setVisible(has);
                this.params[i].setVisible(has);
                this.paramRows[i].setVisible(has);

                if (has)
                {
                    this.paramLabels[i].label = UIPostEffectPicker.paramName(effect.params[i].name());
                    this.params[i].limit(effect.params[i].min(), effect.params[i].max());
                }
            }

            this.effectColorLabel.setVisible(effect.hasColor());
            this.effectColor.setVisible(effect.hasColor());
        }

        if (this.pages[2].hasParent())
        {
            this.pages[2].resize();
        }
    }

    /* ── State ── */

    /** Shows the panel's picked clip; called whenever the selection or the clip changes */
    public void refresh()
    {
        CatalystLayer layer = this.panel.getSelectedLayer();
        boolean changed = layer != this.shown;

        this.shown = layer;
        this.empty.setVisible(layer == null);
        this.name.setVisible(layer != null);

        if (layer == null)
        {
            for (UIScrollView page : this.pages)
            {
                page.setVisible(false);
            }

            return;
        }

        if (changed || !this.name.isFocused())
        {
            this.name.setText(layer.name);
        }

        boolean text = layer.layerType == CatalystLayer.LayerType.TEXT;
        boolean visual = !layer.isAudioClip();
        boolean media = layer.layerType == CatalystLayer.LayerType.VIDEO || layer.layerType == CatalystLayer.LayerType.AUDIO
            || layer.layerType == CatalystLayer.LayerType.IMAGE || layer.layerType == CatalystLayer.LayerType.SCENE;

        this.textSection.setVisible(text);

        if (text && (changed || !this.text.isFocused()))
        {
            this.text.setText(layer.resourcePath == null ? "" : layer.resourcePath);
        }

        this.sourceSection.setVisible(media);

        if (media)
        {
            String path = layer.resourcePath == null ? "" : layer.resourcePath.trim();
            String file = path.isEmpty() ? "-" : new File(path).getName();

            this.source.label = IKey.raw(UIKeys.CATALYST_NLE_SOURCE.get() + ": " + (file.isEmpty() ? path : file));
            this.extend.setVisible(layer.mediaDuration > 0);
            this.extend.label = IKey.raw(UIKeys.CATALYST_NLE_EXTEND.get() + " " + Math.max(1, layer.mediaDuration - layer.mediaOffset));
            this.filmFpsRow.setVisible(layer.layerType == CatalystLayer.LayerType.SCENE);
        }

        this.transformSection.setVisible(visual);
        this.cropSection.setVisible(visual);

        if (changed)
        {
            this.effects.setList(layer.effects.getAllTyped());
            this.effects.setIndex(-1);
            this.pickEffect(null);

            /* An audio clip opens on its audio, everything else on its picture */
            if (this.tab == 0 && !visual)
            {
                this.tab = 1;
            }
            else if (this.tab == 1 && visual && layer.layerType != CatalystLayer.LayerType.VIDEO)
            {
                this.tab = 0;
            }
        }

        this.updateTabs();
    }

    /** Opens a tab: 0 video, 1 audio, 2 effects, 3 transition */
    public void setTab(int tab)
    {
        this.tab = Math.max(0, Math.min(this.pages.length - 1, tab));
        this.updateTabs();
    }

    private void updateTabs()
    {
        for (int i = 0; i < this.pages.length; i++)
        {
            this.pages[i].setVisible(this.shown != null && i == this.tab);
        }

        if (this.pages[this.tab].hasParent())
        {
            this.pages[this.tab].resize();
        }
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        if (context.mouseButton == 0 && context.mouseY >= this.area.y && context.mouseY < this.area.y + TABS_H && this.area.isInside(context))
        {
            int width = this.area.w / this.tabTitles.length;
            int index = Math.min(this.tabTitles.length - 1, (context.mouseX - this.area.x) / Math.max(1, width));

            this.tab = index;
            this.updateTabs();

            return true;
        }

        return super.subMouseClicked(context);
    }

    @Override
    public void render(UIContext context)
    {
        if (this.panel.getSelectedLayer() != this.shown)
        {
            this.refresh();
        }

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.ey(), 0xff151a23);
        context.batcher.box(this.area.x, this.area.y, this.area.x + 1, this.area.ey(), 0xff262d3a);

        /* The tabs: rounded pills, the open one lit */
        FontRenderer font = context.batcher.getFont();
        int width = this.area.w / this.tabTitles.length;
        int accent = BBSSettings.primaryColor.get();

        context.batcher.box(this.area.x, this.area.y, this.area.ex(), this.area.y + TABS_H, 0xff1a1f2a);

        for (int i = 0; i < this.tabTitles.length; i++)
        {
            int x1 = this.area.x + i * width + 2;
            int x2 = x1 + width - 4;
            boolean active = i == this.tab;
            boolean hover = context.mouseX >= x1 && context.mouseX < x2 && context.mouseY >= this.area.y && context.mouseY < this.area.y + TABS_H;
            String label = font.limitToWidth(this.tabTitles[i].get(), width - 8);

            if (active || hover)
            {
                RoundedDraw.fill(context.batcher, x1, this.area.y + 2, x2, this.area.y + TABS_H - 2, 4, RoundedDraw.ALL, active ? Colors.setA(accent, 0.5F) : 0x18ffffff);
            }

            context.batcher.text(label, (x1 + x2) / 2 - font.getWidth(label) / 2, this.area.y + 6, active ? Colors.WHITE : 0xffaab4c4, false);
        }

        context.batcher.box(this.area.x, this.area.y + TABS_H + HEADER_H - 1, this.area.ex(), this.area.y + TABS_H + HEADER_H, 0xff262d3a);

        super.render(context);
    }
}
