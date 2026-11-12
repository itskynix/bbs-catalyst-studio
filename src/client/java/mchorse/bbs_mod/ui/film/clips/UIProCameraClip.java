package mchorse.bbs_mod.ui.film.clips;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.overwrite.ProCameraClip;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UICirculate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIAnchorKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * UI editor panel for ProCameraClip, extending UIKeyframeClip to inherit the
 * robust keyframe graph, ruler renderer, and timeline synchronization loop.
 */
public class UIProCameraClip extends UIKeyframeClip
{
    /* Lens & Focal Length */
    public UITrackpad focalLength;
    public UITrackpad fov;

    /* Roll / Dutch Angle */
    public UITrackpad roll;

    /* Dolly Zoom / Vertigo */
    public UIToggle lockSubjectSize;
    public UIButton pickSubject;
    public UITrackpad baseSubjectDistance;

    /* Focus Distance */
    public UITrackpad focusDistance;

    /* Depth of field and bokeh */
    public UIButton lockFocus;
    public UITrackpad aperture;
    public UITrackpad bokehIntensity;
    public UITrackpad generalBlur;

    /* Where the focus comes from, and the actor it follows */
    public UICirculate focusMode;
    public UIButton focusActor;
    public UIIcon pickFocusActor;

    /* The focus plane guide in the viewport */
    public UIIcon focusPlaneIcon;

    public ProCameraClip getProClip()
    {
        return (ProCameraClip) this.clip;
    }

    public UIProCameraClip(ProCameraClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        /* Lens & Focal Length trackpads */
        this.focalLength = new UITrackpad((v) -> this.applyFocalLength(v.doubleValue()));
        this.focalLength.limit(1, 1000).tooltip(IKey.raw("35mm Equivalent Focal Length (mm)"));

        this.fov = new UITrackpad((v) -> this.applyFov(v.doubleValue()));
        this.fov.limit(1, 170).tooltip(UIKeys.CAMERA_PANELS_FOV);

        /* Roll trackpad */
        this.roll = new UITrackpad((v) -> this.applyRoll(v.doubleValue()));
        this.roll.limit(-180, 180).tooltip(UIKeys.CAMERA_PANELS_ROLL);

        /* Dolly Zoom / Vertigo */
        this.lockSubjectSize = this.toggle(IKey.raw("Lock Subject Size (Vertigo)"), this.getProClip().lockSubjectSize);
        this.pickSubject = new UIButton(IKey.raw("Pick Subject from Crosshair"), (b) -> this.pickSubjectTarget());
        this.baseSubjectDistance = this.trackpad(this.getProClip().baseSubjectDistance);
        this.baseSubjectDistance.limit(0.1, 100).tooltip(IKey.raw("Subject Distance (Blocks)"));

        /* Focus Distance */
        this.focusDistance = new UITrackpad((v) -> this.applyFocusDistance(v.doubleValue()));
        this.focusDistance.limit(0.1, 500).tooltip(IKey.raw("Focus Distance (Blocks)"));

        /* Depth of field and bokeh */
        this.lockFocus = new UIButton(UIKeys.CAMERA_PANELS_DOF_LOCK_FOCUS, (b) -> this.lockFocusToCrosshair());
        this.lockFocus.tooltip(UIKeys.CAMERA_PANELS_DOF_LOCK_FOCUS_TOOLTIP);

        this.aperture = new UITrackpad((v) -> this.applyAperture(v.doubleValue()));
        this.aperture.limit(ProCameraClip.MIN_APERTURE, ProCameraClip.MAX_APERTURE).tooltip(UIKeys.CAMERA_PANELS_DOF_APERTURE_TOOLTIP);

        this.bokehIntensity = new UITrackpad((v) -> this.applyUnit(this.getProClip().bokehIntensity, this.bokehIntensity, v.doubleValue()));
        this.bokehIntensity.limit(0, 1).tooltip(UIKeys.CAMERA_PANELS_DOF_BOKEH_INTENSITY_TOOLTIP);

        this.generalBlur = new UITrackpad((v) -> this.applyUnit(this.getProClip().generalBlur, this.generalBlur, v.doubleValue()));
        this.generalBlur.limit(0, 1).tooltip(UIKeys.CAMERA_PANELS_DOF_GENERAL_BLUR_TOOLTIP);

        /* Focus source: manual, crosshair, or an actor followed live */
        this.focusMode = new UICirculate((b) -> this.editor.editMultiple(this.getProClip().focusMode, (v) -> v.set(b.getValue())));
        this.focusMode.addLabel(UIKeys.CAMERA_PANELS_DOF_FOCUS_MODE_MANUAL);
        this.focusMode.addLabel(UIKeys.CAMERA_PANELS_DOF_FOCUS_MODE_CROSSHAIR);
        this.focusMode.addLabel(UIKeys.CAMERA_PANELS_DOF_FOCUS_MODE_ACTOR);
        this.focusMode.tooltip(UIKeys.CAMERA_PANELS_DOF_FOCUS_MODE_TOOLTIP);

        this.focusActor = new UIButton(UIKeys.CAMERA_PANELS_DOF_FOCUS_ACTOR_NONE, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                UIAnchorKeyframeFactory.displayActors(this.getContext(), panel.getController().getEntities(), this.getProClip().focusTargetActor.get(), this::setFocusActor);
            }
        });
        this.focusActor.tooltip(UIKeys.CAMERA_PANELS_DOF_FOCUS_ACTOR_TOOLTIP);

        this.pickFocusActor = new UIIcon(Icons.EYEDROPPER, (b) ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            if (panel != null)
            {
                /* The viewport's own pick: the next click on an actor in the preview chooses it */
                panel.getController().picker.toggleTargetPick(this.pickFocusActor, null, (actor, pair) -> this.setFocusActor(actor));
            }
        });
        this.pickFocusActor.wh(20, 20);
        this.pickFocusActor.highlight(() ->
        {
            UIFilmPanel panel = this.getParent(UIFilmPanel.class);

            return panel != null && panel.getController().picker.isPickingTarget(this.pickFocusActor);
        }, Direction.BOTTOM);
        this.pickFocusActor.tooltip(UIKeys.CAMERA_PANELS_DOF_FOCUS_PICK_TOOLTIP);

        /* The focus plane: one icon, in the plane's own purple, that turns it on and off and is highlighted while on */
        ProCameraClip pro = this.getProClip();


        this.focusPlaneIcon = new UIIcon(Icons.PLANE, (b) -> this.editor.editMultiple(pro.showFocusPlane, (v) -> v.set(!v.get())));
        this.focusPlaneIcon.iconColor(0xff9b59b6);
        this.focusPlaneIcon.wh(20, 20);
        this.focusPlaneIcon.highlight(() -> this.getProClip().showFocusPlane.get(), Direction.BOTTOM);
        this.focusPlaneIcon.tooltip(UIKeys.CAMERA_PANELS_DOF_SHOW_FOCUS_PLANE_TOOLTIP);
    }

    /**
     * Follows an actor with the focus, or, given none, goes back to the focus distance channel.
     * Both in one undoable edit.
     */
    private void setFocusActor(String id)
    {
        ProCameraClip clip = this.getProClip();
        String actor = id == null ? "" : id;

        this.editor.editMultiple(clip.focusTargetActor, (v) -> v.set(actor));
        this.editor.editMultiple(clip.focusMode, (v) -> v.set(actor.isEmpty() ? ProCameraClip.FOCUS_MANUAL : ProCameraClip.FOCUS_ACTOR));
        this.fillFocusSource();
    }

    /** The focus source widgets from the clip: the mode and the name of the actor followed. */
    private void fillFocusSource()
    {
        ProCameraClip clip = this.getProClip();
        String id = clip.focusTargetActor.get();
        UIFilmPanel panel = this.getParent(UIFilmPanel.class);
        IKey label = UIKeys.CAMERA_PANELS_DOF_FOCUS_ACTOR_NONE;

        if (!id.isEmpty())
        {
            label = IKey.constant(id);

            if (panel != null)
            {
                for (Replay replay : panel.getData().replays.getList())
                {
                    if (replay.getId().equals(id))
                    {
                        label = IKey.constant(replay.getName());

                        break;
                    }
                }
            }
        }

        this.focusMode.setValue(clip.focusMode.get());
        this.focusActor.label = label;
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        /* Lens Presets */
        UIButton btn24 = new UIButton(IKey.raw("24mm"), (b) -> this.applyFocalLength(24));
        UIButton btn35 = new UIButton(IKey.raw("35mm"), (b) -> this.applyFocalLength(35));
        UIButton btn50 = new UIButton(IKey.raw("50mm"), (b) -> this.applyFocalLength(50));
        UIButton btn85 = new UIButton(IKey.raw("85mm"), (b) -> this.applyFocalLength(85));

        /* Dutch Angle Presets */
        UIButton rollM45 = new UIButton(IKey.raw("-45°"), (b) -> this.applyRoll(-45));
        UIButton rollM15 = new UIButton(IKey.raw("-15°"), (b) -> this.applyRoll(-15));
        UIButton roll0   = new UIButton(IKey.raw("0°"), (b) -> this.applyRoll(0));
        UIButton rollP15 = new UIButton(IKey.raw("+15°"), (b) -> this.applyRoll(15));
        UIButton rollP45 = new UIButton(IKey.raw("+45°"), (b) -> this.applyRoll(45));

        this.panels.add(this.section(IKey.raw("Lens & Focal Length (35mm)"),
            UI.row(UIConstants.MARGIN, 0, 20, btn24, btn35, btn50, btn85),
            UI.row(UIConstants.MARGIN, 0, 20, this.focalLength, this.fov)
        ));
        this.panels.add(this.section(IKey.raw("Roll (Dutch Angle)"),
            UI.row(UIConstants.MARGIN, 0, 20, rollM45, rollM15, roll0, rollP15, rollP45),
            this.roll
        ));
        this.panels.add(this.section(IKey.raw("Dolly Zoom (Vertigo)"),
            this.lockSubjectSize,
            UI.row(UIConstants.MARGIN, 0, 20, this.pickSubject, this.baseSubjectDistance)
        ));
        this.panels.add(this.section(IKey.raw("Focus Distance"), this.focusDistance));

        /* Aperture presets */
        UIButton f07 = new UIButton(IKey.constant("f/0.7"), (b) -> this.applyAperture(0.7));
        UIButton f095 = new UIButton(IKey.constant("f/0.95"), (b) -> this.applyAperture(0.95));
        UIButton f14 = new UIButton(IKey.constant("f/1.4"), (b) -> this.applyAperture(1.4));
        UIButton f28 = new UIButton(IKey.constant("f/2.8"), (b) -> this.applyAperture(2.8));
        UIButton f56 = new UIButton(IKey.constant("f/5.6"), (b) -> this.applyAperture(5.6));
        UIButton f11 = new UIButton(IKey.constant("f/11"), (b) -> this.applyAperture(11));

        this.panels.add(this.section(UIKeys.CAMERA_PANELS_DOF_TITLE,
            UI.labelRow(UIKeys.CAMERA_PANELS_DOF_FOCUS_MODE, this.focusMode),
            UI.row(UIConstants.MARGIN, 0, 20, this.focusActor, this.pickFocusActor, this.focusPlaneIcon),
            this.lockFocus,
            UI.row(UIConstants.MARGIN, 0, 20, f07, f095, f14),
            UI.row(UIConstants.MARGIN, 0, 20, f28, f56, f11),
            UI.labelRow(UIKeys.CAMERA_PANELS_DOF_APERTURE, this.aperture),
            UI.labelRow(UIKeys.CAMERA_PANELS_DOF_BOKEH_INTENSITY, this.bokehIntensity),
            UI.labelRow(UIKeys.CAMERA_PANELS_DOF_GENERAL_BLUR, this.generalBlur)
        ));
    }

    private void applyAperture(double fStop)
    {
        double value = Math.max(ProCameraClip.MIN_APERTURE, Math.min(ProCameraClip.MAX_APERTURE, fStop));

        this.insertKeyframe(this.getCurrentClipTick(), this.getProClip().aperture, value);
        this.aperture.setValue((float) value);
        this.editor.fillData();
    }

    /** A channel that runs from 0 to 1, keyed at the cursor. */
    private void applyUnit(KeyframeChannel<Double> channel, UITrackpad field, double value)
    {
        double clamped = Math.max(0D, Math.min(1D, value));

        this.insertKeyframe(this.getCurrentClipTick(), channel, clamped);
        field.setValue((float) clamped);
        this.editor.fillData();
    }

    /**
     * Focuses on what lies under the crosshair: the distance from the camera to the block or the actor
     * it points at, keyed as the focus distance at the cursor. Looking at nothing leaves the focus as it is.
     */
    private void lockFocusToCrosshair()
    {
        Camera camera = this.editor.getCamera();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (camera == null || mc.world == null)
        {
            return;
        }

        HitResult hit = RayTracing.rayTraceEntity(mc.world, camera, 256);
        Vec3d point = null;

        if (hit instanceof BlockHitResult block && block.getType() != HitResult.Type.MISS)
        {
            point = block.getPos();
        }
        else if (hit instanceof EntityHitResult entity && entity.getType() != HitResult.Type.MISS)
        {
            point = entity.getPos();
        }

        if (point == null)
        {
            this.getContext().notifyInfo(UIKeys.CAMERA_PANELS_DOF_FOCUS_NOTHING);

            return;
        }

        /* Along the camera's own axis, not straight to the point: that is what the lens focuses on */
        Vector3f look = camera.getLookDirection();
        double distance = (point.x - camera.position.x) * look.x + (point.y - camera.position.y) * look.y + (point.z - camera.position.z) * look.z;

        distance = Math.max(0.1D, Math.min(500D, distance));

        this.applyFocusDistance(distance);
        this.getContext().notifyInfo(UIKeys.CAMERA_PANELS_DOF_FOCUS_LOCKED.format(String.format("%.1f", distance)));
    }

    private float getCurrentClipTick()
    {
        UIContext context = this.getContext();
        float cursor = this.editor == null ? 0F : this.editor.getKeyframeCursor(context == null ? 0F : context.getTransition());
        float clipStart = this.clip.tick.get();
        float tick = cursor - clipStart;
        long dur = this.clip.duration.get();

        return Math.max(0F, Math.min(dur > 0 ? (float) dur : Float.MAX_VALUE, tick));
    }

    private void applyFocalLength(double mm)
    {
        double fovVal = ProCameraClip.focalLengthToFov(mm);
        float tick = this.getCurrentClipTick();

        this.insertKeyframe(tick, this.clip.fov, fovVal);
        this.getProClip().focalLength.set((float) mm);
        this.focalLength.setValue((float) mm);
        this.fov.setValue((float) fovVal);
        this.editor.fillData();
    }

    private void applyFov(double fovVal)
    {
        double mm = ProCameraClip.fovToFocalLength(fovVal);
        float tick = this.getCurrentClipTick();

        this.insertKeyframe(tick, this.clip.fov, fovVal);
        this.getProClip().focalLength.set((float) mm);
        this.fov.setValue((float) fovVal);
        this.focalLength.setValue((float) mm);
        this.editor.fillData();
    }

    private void applyRoll(double deg)
    {
        float tick = this.getCurrentClipTick();

        this.insertKeyframe(tick, this.clip.roll, deg);
        this.roll.setValue((float) deg);
        this.editor.fillData();
    }

    private void applyFocusDistance(double dist)
    {
        float tick = this.getCurrentClipTick();

        this.insertKeyframe(tick, this.getProClip().focusDistance, dist);
        this.focusDistance.setValue((float) dist);
        this.editor.fillData();
    }

    private void pickSubjectTarget()
    {
        Camera camera = this.editor.getCamera();
        MinecraftClient mc = MinecraftClient.getInstance();

        if (camera != null && mc.world != null)
        {
            HitResult hit = RayTracing.rayTraceEntity(mc.world, camera, 128);
            Point s = this.getProClip().subject.get();

            if (hit instanceof BlockHitResult bhr && bhr.getType() != HitResult.Type.MISS)
            {
                BlockPos pos = bhr.getBlockPos();
                s.set(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            }
            else if (hit instanceof EntityHitResult ehr && ehr.getType() != HitResult.Type.MISS)
            {
                Vec3d pos = ehr.getPos();
                s.set(pos.x, pos.y, pos.z);
            }
            else
            {
                Vector3f dir = camera.getLookDirection();
                s.set(camera.position.x + dir.x * 5.0, camera.position.y + dir.y * 5.0, camera.position.z + dir.z * 5.0);
            }

            double dist = Math.sqrt(
                Math.pow(camera.position.x - s.x, 2) + Math.pow(camera.position.y - s.y, 2) + Math.pow(camera.position.z - s.z, 2)
            );
            this.getProClip().baseSubjectDistance.set((float) dist);
            this.baseSubjectDistance.setValue((float) dist);

            this.getContext().notifyInfo(IKey.raw("Subject locked at " + String.format("%.1f, %.1f, %.1f (%.1fm)", s.x, s.y, s.z, dist)));
            this.fillData();
        }
    }

    @Override
    public void editClip(Position position)
    {
        super.editClip(position);

        if (this.fov != null) this.fov.setValue(position.angle.fov);
        if (this.focalLength != null) this.focalLength.setValue((float) ProCameraClip.fovToFocalLength(position.angle.fov));
        if (this.roll != null) this.roll.setValue(position.angle.roll);
    }

    private void insertKeyframe(float tick, KeyframeChannel<Double> channel, double x)
    {
        KeyframeSegment<Double> segment = channel.findSegment(tick);
        int insert = channel.insert(tick, x);

        if (segment != null)
        {
            channel.get(insert).copyOverExtra(segment.a);
        }
    }

    @Override
    public void fillData()
    {
        super.fillData();

        float tick = this.getCurrentClipTick();
        double curFov = this.clip.fov.isEmpty() ? 70.0 : this.clip.fov.interpolate(tick);
        this.fov.setValue((float) curFov);
        this.focalLength.setValue((float) ProCameraClip.fovToFocalLength(curFov));

        double curRoll = this.clip.roll.isEmpty() ? 0.0 : this.clip.roll.interpolate(tick);
        this.roll.setValue((float) curRoll);

        double curFocus = this.getProClip().focusDistance.isEmpty() ? 5.0 : this.getProClip().focusDistance.interpolate(tick);
        this.focusDistance.setValue((float) curFocus);

        ProCameraClip pro = this.getProClip();

        this.aperture.setValue((float) (pro.aperture.isEmpty() ? ProCameraClip.MAX_APERTURE : pro.aperture.interpolate(tick)));
        this.bokehIntensity.setValue((float) (pro.bokehIntensity.isEmpty() ? 0.5D : pro.bokehIntensity.interpolate(tick)));
        this.generalBlur.setValue((float) (pro.generalBlur.isEmpty() ? 0D : pro.generalBlur.interpolate(tick)));
        this.fillFocusSource();
        this.baseSubjectDistance.setValue(this.getProClip().baseSubjectDistance.get());
    }

    @Override
    public void applyUndoData(MapType data)
    {
        super.applyUndoData(data);

        if (data.getString("embed").equals("pro_camera"))
        {
            this.editor.embedView(this.keyframes);
            this.keyframes.view.resetView();
        }
    }

    @Override
    public void collectUndoData(MapType data)
    {
        super.collectUndoData(data);

        if (this.keyframes != null && this.keyframes.hasParent())
        {
            data.putString("embed", "pro_camera");
        }
    }
}
