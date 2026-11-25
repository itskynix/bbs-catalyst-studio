package mchorse.bbs_mod.ui.forms.editors.panels;

import mchorse.bbs_mod.fonts.FontManager;
import mchorse.bbs_mod.forms.forms.LabelForm;
import mchorse.bbs_mod.forms.forms.utils.LabelColorSpans;
import mchorse.bbs_mod.ui.forms.editors.panels.widgets.UILabelTextarea;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.forms.editors.forms.UIForm;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIStringOverlayPanel;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.values.UIValues;

import java.io.File;

public class UILabelFormPanel extends UIFormPanel<LabelForm>
{
    /* The three swatches stay fields: leaving the panel has to dismiss a picker left standing. */
    public UIColor color;
    public UIColor shadowColor;
    public UIColor background;
    public UIColor outlineColor;
    /* The text, and the colour of the part of it that is selected */
    public UILabelTextarea text;
    public UIColor partColor;
    public UICirculate animationMode;
    public UICirculate extrudeMode;

    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.forms.editors.label." + name);
    }

    /** A trackpad for a 0..1-like value, limited to the range its value allows. */
    private UITrackpad ranged(java.util.function.Supplier<mchorse.bbs_mod.settings.values.numeric.ValueFloat> value, double min, double max, IKey tooltip)
    {
        UITrackpad trackpad = UIValues.trackpad(value::get);

        trackpad.limit(min, max).values(0.05F, 0.01F, 0.1F).tooltip(tooltip);

        return trackpad;
    }

    public UILabelFormPanel(UIForm editor)
    {
        super(editor);

        UIButton pickFont = new UIButton(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, (b) ->
        {
            UIStringOverlayPanel panel = UIStringOverlayPanel.links(UIKeys.FORMS_EDITORS_LABEL_FONT_PICK, FontManager.getFontLinks(), (l) -> this.form.font.set(l));

            UIOverlay.addOverlay(this.getContext(), panel.set(this.form.font.get()));
        });
        UIIcon openFontFolder = new UIIcon(Icons.FOLDER, (b) ->
        {
            File folder = FontManager.getFolder();

            folder.mkdirs();
            UIUtils.openFolder(folder);
        });

        UITrackpad fontSize = UIValues.trackpad(() -> this.form.fontSize);
        UITrackpad lineHeight = UIValues.trackpad(() -> this.form.lineHeight);
        UITrackpad max = UIValues.trackpad(() -> this.form.max);
        UITrackpad anchorX = UIValues.trackpad(() -> this.form.anchorX);
        UITrackpad anchorY = UIValues.trackpad(() -> this.form.anchorY);
        UITrackpad shadowX = UIValues.trackpad(() -> this.form.shadowX);
        UITrackpad shadowY = UIValues.trackpad(() -> this.form.shadowY);

        fontSize.limit(FontManager.MIN_SIZE, FontManager.MAX_SIZE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_FONT_SIZE);
        lineHeight.limit(0, Integer.MAX_VALUE, true).tooltip(UIKeys.FORMS_EDITORS_LABEL_LINE_HEIGHT);
        max.limit(-1, Integer.MAX_VALUE, true).increment(10);
        anchorX.values(0.01F);
        anchorY.values(0.01F);
        shadowX.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);
        shadowY.limit(-100, 100).values(0.1F, 0.01F, 0.5F).increment(0.1F);

        this.color = UIValues.color(() -> this.form.color).withAlpha();
        this.shadowColor = UIValues.color(() -> this.form.shadowColor).withAlpha();
        this.background = UIValues.color(() -> this.form.background).withAlpha();

        this.text = new UILabelTextarea((t) ->
        {
            /* The coloured parts move with what was typed around them */
            String before = this.form.text.get();

            if (!before.equals(t))
            {
                this.form.colorSpans.set(LabelColorSpans.write(LabelColorSpans.shift(LabelColorSpans.parse(this.form.colorSpans.get()), before, t)));
                this.form.text.set(t);
            }
        });
        this.text.h(80);
        this.text.valueBinding(() ->
        {
            if (this.form != null && !this.text.isFocused() && !this.text.getText().equals(this.form.text.get()))
            {
                this.text.setText(this.form.text.get());
            }
        });

        this.partColor = new UIColor((c) -> this.colorSelection(c & 0xffffff));
        this.partColor.tooltip(key("part_color_tooltip"));

        UIButton clearPart = new UIButton(key("part_clear"), (b) -> this.colorSelection(null));
        clearPart.tooltip(key("part_clear_tooltip"));

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_LABEL),
            this.text,
            UI.labelRow(key("part_color"), this.partColor),
            clearPart,
            UIValues.toggle(UIKeys.FORMS_EDITORS_BILLBOARD_TITLE, () -> this.form.billboard),
            UIValues.toggle(key("depth_occlusion"), () -> this.form.depthOcclusion).tooltip(key("depth_occlusion_tooltip")),
            this.color,
            max
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_FONT).marginTop(UIConstants.SECTION_GAP),
            UI.row(pickFont, openFontFolder),
            UI.row(fontSize, lineHeight)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_ANCHOR).marginTop(UIConstants.SECTION_GAP),
            UI.row(anchorX, anchorY),
            UIValues.toggle(UIKeys.FORMS_EDITORS_LABEL_ANCHOR_LINES, () -> this.form.anchorLines)
        );

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_SHADOW_OFFSET).marginTop(UIConstants.SECTION_GAP),
            UI.row(shadowX, shadowY)
        );

        this.options.add(UI.labelRow(UIKeys.FORMS_EDITORS_LABEL_SHADOW_COLOR, this.shadowColor).marginTop(UIConstants.SECTION_GAP));

        this.options.add(
            UI.label(UIKeys.FORMS_EDITORS_LABEL_BACKGROUND).marginTop(UIConstants.SECTION_GAP),
            this.background,
            UIValues.trackpad(() -> this.form.offset)
        );

        /* Reveal animation */
        this.animationMode = new UICirculate((b) -> this.form.animationMode.set(b.getValue()));
        this.animationMode.addLabel(key("animation.none"));
        this.animationMode.addLabel(key("animation.character"));
        this.animationMode.addLabel(key("animation.word"));
        this.animationMode.addLabel(key("animation.line"));
        this.animationMode.tooltip(key("animation_mode"));
        UIValues.resettable(this.animationMode, () -> this.form.animationMode, () -> this.animationMode.setValue(this.form.animationMode.get()));
        /* Shows what the form holds, not its first label: the circulate starts at 0 whatever the form says */
        this.animationMode.valueBinding(() -> this.readCirculate(this.animationMode, this.form == null ? null : this.form.animationMode));

        UISection animation = new UISection(key("section.animation"));

        animation.fields.add(
            UI.labelRow(key("animation_mode"), this.animationMode),
            UI.label(key("reveal_progress")), this.ranged(() -> this.form.revealProgress, 0, 10, key("reveal_progress_tooltip")),
            UI.label(key("stagger")), this.ranged(() -> this.form.stagger, 0, 1, key("stagger_tooltip")),
            UI.label(key("reveal_offset")), this.ranged(() -> this.form.revealOffset, -100, 100, key("reveal_offset_tooltip")),
            UI.label(key("reveal_overshoot")), this.ranged(() -> this.form.revealOvershoot, 0, 2, key("reveal_overshoot_tooltip"))
        );
        animation.setExpanded(false);

        /* Shape */
        this.extrudeMode = new UICirculate((b) -> this.form.extrudeMode.set(b.getValue()));
        this.extrudeMode.addLabel(key("extrude_mode.layers"));
        this.extrudeMode.addLabel(key("extrude_mode.depth"));
        this.extrudeMode.tooltip(key("extrude_mode_tooltip"));
        UIValues.resettable(this.extrudeMode, () -> this.form.extrudeMode, () -> this.extrudeMode.setValue(this.form.extrudeMode.get()));
        /* Shows what the form holds, not its first label: the circulate starts at 0 whatever the form says */
        this.extrudeMode.valueBinding(() -> this.readCirculate(this.extrudeMode, this.form == null ? null : this.form.extrudeMode));

        /* Depth and bend, each its own section */
        UISection depth = new UISection(key("section.depth"));

        depth.fields.add(
            UI.labelRow(key("extrude_mode"), this.extrudeMode),
            UI.label(key("extrude")), this.ranged(() -> this.form.extrude, 0, 5, key("extrude_tooltip"))
        );
        depth.setExpanded(false);

        UISection arc = new UISection(key("section.arc"));

        arc.fields.add(
            UI.label(key("arc")), this.ranged(() -> this.form.arc, -180, 180, key("arc_tooltip"))
        );
        arc.setExpanded(false);

        /* Fill and outline */
        this.outlineColor = UIValues.color(() -> this.form.outlineColor);

        UISection outline = new UISection(key("section.outline"));

        outline.fields.add(
            UI.label(key("fill_opacity")), this.ranged(() -> this.form.fillOpacity, 0, 1, key("fill_opacity_tooltip")),
            UI.label(key("outline_width")), this.ranged(() -> this.form.outlineWidth, 0, 5, key("outline_width_tooltip")),
            UI.labelRow(key("outline_color"), this.outlineColor),
            UI.label(key("outline_opacity")), this.ranged(() -> this.form.outlineOpacity, 0, 1, key("outline_opacity_tooltip"))
        );
        outline.setExpanded(false);

        /* Dissolve and shatter are in the Effects tab (UILabelEffectsPanel) */
        this.options.add(animation, depth, arc, outline);
    }

    /**
     * Puts the form's value on a circulate. Without it the button stayed on its first label (the
     * mode with index 0) while the form held another one - its default, or a keyframed value - so
     * the panel said "Linear" while the perlin dissolve ran.
     */
    private void readCirculate(UICirculate circulate, ValueInt value)
    {
        if (value != null && circulate.getValue() != value.get())
        {
            circulate.setValue(value.get());
        }
    }

    /** Colours the selected part of the text; with nothing selected, a clear takes every part's colour away. */
    private void colorSelection(Integer rgb)
    {
        int start = this.text.getSelectionStart();
        int end = this.text.getSelectionEnd();

        if (end <= start)
        {
            if (rgb == null)
            {
                this.form.colorSpans.set("");
            }
            else
            {
                this.getContext().notifyInfo(key("part_none_selected"));
            }

            return;
        }

        this.form.colorSpans.set(LabelColorSpans.write(LabelColorSpans.apply(LabelColorSpans.parse(this.form.colorSpans.get()), start, end, rgb)));
    }

    @Override
    public void startEdit(LabelForm form)
    {
        super.startEdit(form);

        this.text.setText(form.text.get());
    }

    @Override
    public void finishEdit()
    {
        super.finishEdit();

        this.color.picker.removeFromParent();
        this.shadowColor.picker.removeFromParent();
        this.background.picker.removeFromParent();
        this.outlineColor.picker.removeFromParent();
        this.partColor.picker.removeFromParent();
    }
}
