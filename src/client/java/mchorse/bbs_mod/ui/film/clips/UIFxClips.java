package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.clips.fx.CinematicEffectsClip;
import mchorse.bbs_mod.camera.clips.fx.ColorGradeClip;
import mchorse.bbs_mod.camera.clips.fx.EyeClip;
import mchorse.bbs_mod.camera.clips.fx.GrainClip;
import mchorse.bbs_mod.camera.clips.fx.LetterboxClip;
import mchorse.bbs_mod.camera.clips.fx.ScreenShaderGraphClip;
import mchorse.bbs_mod.camera.clips.fx.VignetteClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;

/**
 * The panels of the post-production clips. They differ only in the few fields each keeps besides
 * its keyframe channels, so they live together.
 */
public class UIFxClips
{
    private static IKey key(String name)
    {
        return L10n.lang("bbs.ui.camera.fx." + name);
    }

    public static class UIColorGradeClip extends UIFxClip<ColorGradeClip>
    {
        public UIColor overlayColor;

        public UIColorGradeClip(ColorGradeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.overlayColor = this.color(this.clip.overlayColor);
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("overlay_color"), this.overlayColor));
        }
    }

    public static class UICinematicEffectsClip extends UIFxClip<CinematicEffectsClip>
    {
        public UICinematicEffectsClip(CinematicEffectsClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }
    }

    public static class UIGrainClip extends UIFxClip<GrainClip>
    {
        public UIGrainClip(GrainClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }
    }

    public static class UIVignetteClip extends UIFxClip<VignetteClip>
    {
        public UIColor color;

        public UIVignetteClip(VignetteClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.color = this.color(this.clip.color);
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("vignette_color"), this.color));
        }
    }

    public static class UILetterboxClip extends UIFxClip<LetterboxClip>
    {
        public UIColor color;
        public UIToggle useKeyframes;
        public UITrackpad angle;
        public UITrackpad aspect;
        public UITrackpad thickness;
        public UITrackpad feather;
        public UITrackpad offsetX;
        public UITrackpad offsetY;

        public UILetterboxClip(LetterboxClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.color = this.color(this.clip.color);
            this.useKeyframes = this.toggle(key("use_keyframes"), this.clip.useKeyframes);
            this.angle = this.trackpad(this.clip.tiltAngle);
            this.angle.tooltip(key("tilt_angle"));
            this.aspect = this.trackpad(this.clip.tiltAspect);
            this.aspect.tooltip(key("tilt_aspect"));
            this.thickness = this.trackpad(this.clip.tiltThickness);
            this.thickness.tooltip(key("tilt_thickness"));
            this.feather = this.trackpad(this.clip.tiltFeather);
            this.feather.tooltip(key("tilt_feather"));
            this.offsetX = this.trackpad(this.clip.offsetX);
            this.offsetX.tooltip(key("offset_x"));
            this.offsetY = this.trackpad(this.clip.offsetY);
            this.offsetY.tooltip(key("offset_y"));
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("letterbox_color"), this.color));
            this.panels.add(this.section(key("tilt"), UI.row(this.angle, this.aspect), UI.row(this.thickness, this.feather)));
            this.panels.add(this.section(key("offset"), UI.row(this.offsetX, this.offsetY)));
            this.panels.add(this.useKeyframes);
        }
    }

    public static class UIEyeClip extends UIFxClip<EyeClip>
    {
        public UIColor color;
        public UITrackpad opacity;
        public UITrackpad angle;
        public UITrackpad aspect;
        public UITrackpad thickness;
        public UITrackpad feather;
        public UITrackpad offsetX;
        public UITrackpad offsetY;

        public UIEyeClip(EyeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.color = this.color(this.clip.color);
            this.opacity = this.trackpad(this.clip.opacity);
            this.opacity.limit(0, 100).tooltip(key("opacity"));
            this.angle = this.trackpad(this.clip.tiltAngle);
            this.angle.tooltip(key("tilt_angle"));
            this.aspect = this.trackpad(this.clip.tiltAspect);
            this.aspect.tooltip(key("tilt_aspect"));
            this.thickness = this.trackpad(this.clip.tiltThickness);
            this.thickness.tooltip(key("tilt_thickness"));
            this.feather = this.trackpad(this.clip.tiltFeather);
            this.feather.tooltip(key("tilt_feather"));
            this.offsetX = this.trackpad(this.clip.offsetX);
            this.offsetX.tooltip(key("offset_x"));
            this.offsetY = this.trackpad(this.clip.offsetY);
            this.offsetY.tooltip(key("offset_y"));
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("eye_color"), this.color, this.opacity));
            this.panels.add(this.section(key("tilt"), UI.row(this.angle, this.aspect), UI.row(this.thickness, this.feather)));
            this.panels.add(this.section(key("offset"), UI.row(this.offsetX, this.offsetY)));
        }
    }

    /** The graph editor is a later stage; the clip only holds its place and says so. */
    public static class UIScreenShaderGraphClip extends UIClip<ScreenShaderGraphClip>
    {
        public UIScreenShaderGraphClip(ScreenShaderGraphClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerPanels()
        {
            super.registerPanels();

            this.panels.add(UI.label(key("graph_placeholder")));
        }
    }
}
