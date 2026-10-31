package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.settings.values.core.ValueColor;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.colors.Color;

public class LabelForm extends Form
{
    /** Also what its main tab in the form editor wears — see {@link Form#getIcon()}. */
    public static final Icon ICON = Icons.FONT;

    public final ValueString text = new ValueString("text", "Hello, World!");

    /* Font: a TrueType file in the assets, empty for Minecraft's own one */
    public final ValueLink font = new ValueLink("font", null);
    public final ValueInt fontSize = new ValueInt("fontSize", 9);
    /** 0 hands the spacing over to the font itself. */
    public final ValueInt lineHeight = new ValueInt("lineHeight", 0);

    public final ValueBoolean billboard = new ValueBoolean("billboard", false);
    /** Whether the world can stand in front of the text: on, blocks and actors hide it; off, it is always drawn on top. */
    public final ValueBoolean depthOcclusion = new ValueBoolean("depthOcclusion", true);
    public final ValueColor color = new ValueColor("color", Color.white());

    public final ValueInt max = new ValueInt("max", -1);
    public final ValueFloat anchorX = new ValueFloat("anchorX", 0.5F);
    public final ValueFloat anchorY = new ValueFloat("anchorY", 0.5F);
    public final ValueBoolean anchorLines = new ValueBoolean("anchorLines", false);

    /* Shadow properties */
    public final ValueFloat shadowX = new ValueFloat("shadowX", 1F);
    public final ValueFloat shadowY = new ValueFloat("shadowY", 1F);
    public final ValueColor shadowColor = new ValueColor("shadowColor", new Color(0, 0, 0, 0));

    /* Background */
    public final ValueColor background = new ValueColor("background", new Color(0, 0, 0, 0));
    public final ValueFloat offset = new ValueFloat("offset", 3F);

    /* Reveal: which unit of the text appears on its own, and how far along the appearing is */
    public static final int ANIMATION_NONE = 0;
    public static final int ANIMATION_CHARACTER = 1;
    public static final int ANIMATION_WORD = 2;
    public static final int ANIMATION_LINE = 3;

    public final ValueInt animationMode = new ValueInt("animationMode", ANIMATION_NONE, 0, 3);
    /** 1 shows everything, 0 nothing; each unit follows it a little later than the one before (stagger). Above 1 is allowed: the units move on past their place by the excess (an overshooting curve). */
    public final ValueFloat revealProgress = new ValueFloat("revealProgress", 1F, 0F, Float.POSITIVE_INFINITY);
    /** How much of the progress the units are spread over: 0 is all at once, 1 is one after the other. */
    public final ValueFloat stagger = new ValueFloat("stagger", 0.5F, 0F, 1F);
    /** How far below its place (in text pixels) a unit starts before it slides in. */
    public final ValueFloat revealOffset = new ValueFloat("revealOffset", 6F, -100F, 100F);
    /** Spring of the arrival: 0 slides in straight, above 0 each letter overshoots its place and settles back (back-out ease). */
    public final ValueFloat revealOvershoot = new ValueFloat("revealOvershoot", 0F, 0F, 2F);

    /* Shape */
    /** Depth of the text toward its back, in quarters of the text height. 0 is flat. */
    public static final int EXTRUDE_LAYERS = 0;
    public static final int EXTRUDE_DEPTH = 1;

    /** How the depth is made: stacked slices (retro), or one solid mesh grown from the letters' pixels. */
    public final ValueInt extrudeMode = new ValueInt("extrudeMode", EXTRUDE_LAYERS, 0, 1);
    public final ValueFloat extrude = new ValueFloat("extrude", 0F, 0F, 5F);
    /** The angle in degrees the whole line is bent through: positive arches it up, negative sags it. */
    public final ValueFloat arc = new ValueFloat("arc", 0F, -180F, 180F);

    /* Fill and outline, independent of each other */
    public final ValueFloat fillOpacity = new ValueFloat("fillOpacity", 1F, 0F, 1F);
    public final ValueFloat outlineWidth = new ValueFloat("outlineWidth", 0F, 0F, 5F);
    public final ValueColor outlineColor = new ValueColor("outlineColor", Color.white());
    public final ValueFloat outlineOpacity = new ValueFloat("outlineOpacity", 1F, 0F, 1F);

    /* Dissolve: the letters burn away pixel by pixel */
    public static final int DISSOLVE_LINEAR = 0;
    public static final int DISSOLVE_PERLIN = 1;
    public static final int DISSOLVE_LUMA = 2;

    public final ValueFloat dissolveProgress = new ValueFloat("dissolveProgress", 0F, 0F, 1F);
    public final ValueInt dissolveMode = new ValueInt("dissolveMode", DISSOLVE_PERLIN, 0, 2);
    /** Width of the glowing edge where the burning has just reached, as a share of the whole run. 0 has none. */
    public final ValueFloat dissolveEdge = new ValueFloat("dissolveEdge", 0.1F, 0F, 0.5F);
    public final ValueColor dissolveBurnColor = new ValueColor("dissolveBurnColor", new Color(1F, 0.48F, 0.1F));

    /* Shatter: the letters break into cubes that fly apart */
    public final ValueFloat shatterProgress = new ValueFloat("shatterProgress", 0F, 0F, 1F);
    /** How far, in text pixels, the cubes fly by the end. */
    public final ValueFloat shatterSpread = new ValueFloat("shatterSpread", 4F, 0F, 20F);

    public LabelForm()
    {
        super();

        this.add(this.text);
        this.add(this.font);
        this.add(this.fontSize);
        this.add(this.lineHeight);
        this.add(this.billboard);
        this.add(this.depthOcclusion);
        this.add(this.color);
        this.add(this.max);
        this.add(this.anchorX);
        this.add(this.anchorY);
        this.add(this.anchorLines);
        this.add(this.shadowX);
        this.add(this.shadowY);
        this.add(this.shadowColor);
        this.add(this.background);
        this.add(this.offset);
        this.add(this.animationMode);
        this.add(this.revealProgress);
        this.add(this.stagger);
        this.add(this.revealOffset);
        this.add(this.revealOvershoot);
        this.add(this.extrudeMode);
        this.add(this.extrude);
        this.add(this.arc);
        this.add(this.fillOpacity);
        this.add(this.outlineWidth);
        this.add(this.outlineColor);
        this.add(this.outlineOpacity);
        this.add(this.dissolveProgress);
        this.add(this.dissolveMode);
        this.add(this.dissolveEdge);
        this.add(this.dissolveBurnColor);
        this.add(this.shatterProgress);
        this.add(this.shatterSpread);
    }

    @Override
    public String getDefaultDisplayName()
    {
        return this.text.get();
    }

    @Override
    public Icon getIcon()
    {
        return ICON;
    }

}