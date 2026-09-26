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
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
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
