package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.fx.DebrisClip;
import mchorse.bbs_mod.camera.clips.fx.CinematicEffectsClip;
import mchorse.bbs_mod.camera.clips.fx.ColorGradeClip;
import mchorse.bbs_mod.camera.clips.fx.EyeClip;
import mchorse.bbs_mod.camera.clips.fx.GrainClip;
import mchorse.bbs_mod.camera.clips.fx.LensDistortionClip;
import mchorse.bbs_mod.camera.clips.fx.LetterboxClip;
import mchorse.bbs_mod.camera.clips.fx.ScreenShakeClip;
import mchorse.bbs_mod.camera.clips.fx.WorldWobbleClip;
import mchorse.bbs_mod.camera.clips.fx.MotionBlurClip;
import mchorse.bbs_mod.camera.clips.fx.ScreenShaderGraphClip;
import mchorse.bbs_mod.camera.clips.fx.VignetteClip;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import org.joml.Vector3f;

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

    public static class UILensDistortionClip extends UIFxClip<LensDistortionClip>
    {
        public UILensDistortionClip(LensDistortionClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }
    }

    public static class UIScreenShakeClip extends UIFxClip<ScreenShakeClip>
    {
        public UIScreenShakeClip(ScreenShakeClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }
    }

    public static class UIWorldWobbleClip extends UIFxClip<WorldWobbleClip>
    {
        public UICirculate mode;
        public UITrackpad centerX;
        public UITrackpad centerY;

        public UIWorldWobbleClip(WorldWobbleClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.mode = new UICirculate((b) -> this.editor.editMultiple(this.clip.mode, (v) -> v.set(b.getValue())));
            this.mode.addLabel(key("wobble_wave"));
            this.mode.addLabel(key("wobble_twirl"));
            this.mode = this.bind(this.mode, () -> this.mode.setValue(this.clip.mode.get()));

            this.centerX = this.trackpad(this.clip.centerX);
            this.centerX.limit(0D, 1D);
            this.centerY = this.trackpad(this.clip.centerY);
            this.centerY.limit(0D, 1D);
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("wobble_mode"), this.mode));
            this.panels.add(this.section(key("wobble_center"), this.centerX, this.centerY));
        }
    }

    public static class UIDebrisClip extends UIFxClip<DebrisClip>
    {
        public UITrackpad x;
        public UITrackpad y;
        public UITrackpad z;
        public UIButton fromCamera;
        public UITrackpad count;
        public UITrackpad speed;
        public UIToggle sparks;
        public UIToggle glass;
        public UIToggle metal;
        public UIToggle smoke;

        public UIDebrisClip(DebrisClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.x = this.trackpad(this.clip.x);
            this.y = this.trackpad(this.clip.y);
            this.z = this.trackpad(this.clip.z);
            this.fromCamera = new UIButton(key("debris_from_camera"), (b) ->
            {
                Camera camera = this.editor.getCamera();
                Vector3f look = camera.getLookDirection();

                /* Four blocks in front of the camera: where the eye is looking at the crash */
                this.editor.editMultiple(this.clip.x, (v) -> v.set((float) (camera.position.x + look.x * 4F)));
                this.editor.editMultiple(this.clip.y, (v) -> v.set((float) (camera.position.y + look.y * 4F)));
                this.editor.editMultiple(this.clip.z, (v) -> v.set((float) (camera.position.z + look.z * 4F)));
            });
            this.count = this.trackpad(this.clip.count);
            this.count.limit(1, 512);
            this.speed = this.trackpad(this.clip.speed);
            this.speed.limit(this.clip.speed);
            this.sparks = this.toggle(key("debris_sparks"), this.clip.sparks);
            this.glass = this.toggle(key("debris_glass"), this.clip.glass);
            this.metal = this.toggle(key("debris_metal"), this.clip.metal);
            this.smoke = this.toggle(key("debris_smoke"), this.clip.smoke);
        }

        @Override
        protected boolean hasKeyframesSection()
        {
            return false;
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("debris_position"), this.x, this.y, this.z, this.fromCamera));
            this.panels.add(this.section(key("debris_burst"), this.count, this.speed, this.sparks, this.glass, this.metal, this.smoke));
        }
    }

    public static class UIMotionBlurClip extends UIFxClip<MotionBlurClip>
    {
        public UITrackpad shutter;
        public UITrackpad rotationalMultiplier;
        public UITrackpad baseBlur;
        public UITrackpad whipPanMultiplier;
        public UITrackpad snapThreshold;

        public UIMotionBlurClip(MotionBlurClip clip, IUIClipsDelegate editor)
        {
            super(clip, editor);
        }

        @Override
        protected void registerUI()
        {
            super.registerUI();

            this.shutter = this.trackpad(this.clip.shutter);
            this.shutter.limit(this.clip.shutter).tooltip(key("shutter"));
            this.rotationalMultiplier = this.trackpad(this.clip.rotationalMultiplier);
            this.rotationalMultiplier.limit(this.clip.rotationalMultiplier).tooltip(key("rotational_multiplier"));
            this.baseBlur = this.trackpad(this.clip.baseBlur);
            this.baseBlur.limit(this.clip.baseBlur).tooltip(key("base_blur"));
            this.whipPanMultiplier = this.trackpad(this.clip.whipPanMultiplier);
            this.whipPanMultiplier.limit(this.clip.whipPanMultiplier).tooltip(key("whip_pan_multiplier"));
            this.snapThreshold = this.trackpad(this.clip.snapThreshold);
            this.snapThreshold.limit(this.clip.snapThreshold).tooltip(key("snap_threshold"));
        }

        @Override
        protected void addFxPanels()
        {
            this.panels.add(this.section(key("motion_blur"), this.shutter, this.rotationalMultiplier, this.baseBlur, this.whipPanMultiplier, this.snapThreshold));
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
