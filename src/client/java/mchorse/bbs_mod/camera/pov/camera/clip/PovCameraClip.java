/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.camera.clips.CameraClip
 *  mchorse.bbs_mod.camera.clips.CameraClipContext
 *  mchorse.bbs_mod.camera.clips.misc.TrackerFrame
 *  mchorse.bbs_mod.camera.data.Angle
 *  mchorse.bbs_mod.camera.data.Point
 *  mchorse.bbs_mod.camera.data.Position
 *  mchorse.bbs_mod.film.Film
 *  mchorse.bbs_mod.film.replays.Replay
 *  mchorse.bbs_mod.forms.FormUtilsClient
 *  mchorse.bbs_mod.forms.entities.IEntity
 *  mchorse.bbs_mod.forms.forms.Form
 *  mchorse.bbs_mod.forms.renderers.FormRenderer
 *  mchorse.bbs_mod.forms.renderers.ModelFormRenderer
 *  mchorse.bbs_mod.forms.renderers.utils.MatrixCache
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 *  mchorse.bbs_mod.settings.values.numeric.ValueFloat
 *  mchorse.bbs_mod.settings.values.numeric.ValueInt
 *  mchorse.bbs_mod.utils.RayTracing
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.clips.ClipContext
 *  mchorse.bbs_mod.utils.interps.Lerps
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.client.MinecraftClient
 *  net.minecraft.util.hit.BlockHitResult
 *  net.minecraft.util.hit.HitResult$Type
 *  net.minecraft.util.math.Vec3d
 *  net.minecraft.world.World
 *  org.joml.Vector3d
 */
package mchorse.bbs_mod.camera.pov.camera.clip;

import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClips;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.clips.CameraClipContext;
import mchorse.bbs_mod.camera.clips.misc.TrackerFrame;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.data.Position;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.RayTracing;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.ClipContext;
import mchorse.bbs_mod.utils.interps.Lerps;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;

public class PovCameraClip
extends CameraClip {
    public static final int VIEW_FIRST_PERSON = 0;
    public static final int VIEW_THIRD_PERSON_BACK = 1;
    public static final int VIEW_THIRD_PERSON_FRONT = 2;
    public static final String[] PERSPECTIVE_LABELS = new String[]{"First Person View", "Third Person (Back)", "Third Person (Front)"};
    public final ValueInt selector = new ValueInt("selector", Integer.valueOf(-1));
    public final ValueBoolean hands = new ValueBoolean("hands", true);
    public final ValueBoolean hud = new ValueBoolean("hud", true);
    public final ValueBoolean crosshair = new ValueBoolean("crosshair", true);
    public final ValueBoolean actions = new ValueBoolean("actions", true);
    public final ValueBoolean screenEffects = new ValueBoolean("screen_effects", true);
    public final ValueBoolean cursor = new ValueBoolean("cursor", true);
    public final ValueBoolean cameraShake = new ValueBoolean("camera_shake", true);
    public final ValueBoolean headLook = new ValueBoolean("head_look", true);
    public final ValueBoolean hardcoreLook = new ValueBoolean("hardcore_look", false);
    public final ValueBoolean blockOutline = new ValueBoolean("block_outline", true);
    public final ValueInt perspective = new ValueInt("perspective", Integer.valueOf(0));
    public final ValueFloat fov = new ValueFloat("fov", Float.valueOf(70.0f), Float.valueOf(1.0f), Float.valueOf(180.0f));

    public PovCameraClip() {
        this.add((BaseValue)this.selector);
        this.add((BaseValue)this.hands);
        this.add((BaseValue)this.hud);
        this.add((BaseValue)this.crosshair);
        this.add((BaseValue)this.actions);
        this.add((BaseValue)this.screenEffects);
        this.add((BaseValue)this.cursor);
        this.add((BaseValue)this.cameraShake);
        this.add((BaseValue)this.headLook);
        this.add((BaseValue)this.hardcoreLook);
        this.add((BaseValue)this.blockOutline);
        this.add((BaseValue)this.perspective);
        this.add((BaseValue)this.fov);
    }

    public static String getPerspectiveLabel(int mode) {
        if (mode < 0 || mode >= PERSPECTIVE_LABELS.length) {
            return PERSPECTIVE_LABELS[0];
        }
        return PERSPECTIVE_LABELS[mode];
    }

    public static IKey getPerspectiveKey(int mode) {
        return switch (mode) {
            case VIEW_THIRD_PERSON_BACK -> L10n.lang("bbs.pov.clip.perspective.third_back", "Third Person (Back)");
            case VIEW_THIRD_PERSON_FRONT -> L10n.lang("bbs.pov.clip.perspective.third_front", "Third Person (Front)");
            default -> L10n.lang("bbs.pov.clip.perspective.first_person", "First Person View");
        };
    }

    public static int nextPerspective(int mode) {
        return (mode + 1) % PERSPECTIVE_LABELS.length;
    }

    public boolean isFirstPerson() {
        return (Integer)this.perspective.get() == 0;
    }

    protected void applyClip(ClipContext context, Position position) {
        if (!this.isTopPovClip(context)) {
            return;
        }
        if (((Boolean)this.headLook.get()).booleanValue() && context instanceof CameraClipContext) {
            CameraClipContext cameraContext = (CameraClipContext)context;
            int index = (Integer)this.selector.get();
            IEntity entity = null;
            Replay replay = null;
            if (index >= 0 && cameraContext.entities != null) {
                BaseValue baseValue;
                if (context.clips != null && (baseValue = context.clips.getParent()) instanceof Film) {
                    Film film = (Film)baseValue;
                    List replays = film.replays.getList();
                    if (index < replays.size()) {
                        replay = (Replay)replays.get(index);
                        entity = (IEntity)cameraContext.entities.get(replay.getId());
                    }
                }
                if (entity == null) {
                    int i = 0;
                    for (IEntity candidate : cameraContext.entities.values()) {
                        if (i == index) {
                            entity = candidate;
                            break;
                        }
                        ++i;
                    }
                }
            }
            if (entity != null) {
                int view;
                TrackerFrame frame;
                float transition = context.transition;
                Form form = entity.getForm();
                float filmTick = (float)context.ticks + transition;
                double eyeHeight = PovCameraClips.getSmoothPovEyeHeight(replay, entity, form, filmTick);
                double x = Lerps.lerp((double)entity.getPrevX(), (double)entity.getX(), (double)transition);
                double y = Lerps.lerp((double)entity.getPrevY(), (double)entity.getY(), (double)transition) + eyeHeight;
                double z = Lerps.lerp((double)entity.getPrevZ(), (double)entity.getZ(), (double)transition);
                float headYaw = (float)Lerps.lerpYaw((double)entity.getPrevHeadYaw(), (double)entity.getHeadYaw(), (double)transition);
                float pitch = Lerps.lerp((float)entity.getPrevPitch(), (float)entity.getPitch(), (float)transition);
                float yaw = headYaw + 180.0f;
                float roll = 0.0f;
                if (((Boolean)this.hardcoreLook.get()).booleanValue() && (frame = this.resolveHardcoreHeadFrame(cameraContext, entity, position, transition)) != null) {
                    double scaleY = 1.0;
                    if (form != null && form.transform != null && form.transform.get() != null) {
                        scaleY = Math.max(0.001, (double)((Transform)form.transform.get()).scale.y);
                    }
                    double eyeDiff = (eyeHeight - 1.5) / scaleY;
                    Point eyeOffset = new Point(0.0, eyeDiff, 0.0);
                    Vector3d headPos = frame.position(eyeOffset);
                    Angle headAngle = frame.angles(new Point(0.0, 0.0, 0.0));
                    x = headPos.x;
                    y = headPos.y;
                    z = headPos.z;
                    yaw = headAngle.yaw;
                    pitch = headAngle.pitch;
                    roll = headAngle.roll;
                    headYaw = yaw - 180.0f;
                }
                if ((view = ((Integer)this.perspective.get()).intValue()) == 1 || view == 2) {
                    float radYaw = headYaw * ((float)Math.PI / 180);
                    float radPitch = pitch * ((float)Math.PI / 180);
                    float cosPitch = (float)Math.cos(radPitch);
                    float sinPitch = (float)Math.sin(radPitch);
                    float cosYaw = (float)Math.cos(radYaw);
                    float sinYaw = (float)Math.sin(radYaw);
                    double fx = -sinYaw * cosPitch;
                    double fy = -sinPitch;
                    double fz = cosYaw * cosPitch;
                    double maxDistance = 4.0;
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (view == 1) {
                        Vec3d dir;
                        Vec3d eye;
                        BlockHitResult hit;
                        double dist = maxDistance;
                        if (mc.world != null && (hit = RayTracing.rayTrace((World)mc.world, (Vec3d)(eye = new Vec3d(x, y, z)), (Vec3d)(dir = new Vec3d(-fx, -fy, -fz)), (double)maxDistance)) != null && hit.getType() == HitResult.Type.BLOCK) {
                            dist = Math.max(0.0, eye.distanceTo(hit.getPos()) - 0.15);
                        }
                        position.point.set(x - dist * fx, y - dist * fy, z - dist * fz);
                        position.angle.yaw = yaw;
                        position.angle.pitch = pitch;
                    } else {
                        Vec3d dir;
                        Vec3d eye;
                        BlockHitResult hit;
                        double dist = maxDistance;
                        if (mc.world != null && (hit = RayTracing.rayTrace((World)mc.world, (Vec3d)(eye = new Vec3d(x, y, z)), (Vec3d)(dir = new Vec3d(fx, fy, fz)), (double)maxDistance)) != null && hit.getType() == HitResult.Type.BLOCK) {
                            dist = Math.max(0.0, eye.distanceTo(hit.getPos()) - 0.15);
                        }
                        double camX = x + dist * fx;
                        double camY = y + dist * fy;
                        double camZ = z + dist * fz;
                        position.point.set(camX, camY, camZ);
                        if (dist > 0.05) {
                            Angle look = Angle.angle((double)(x - camX), (double)(y - camY), (double)(z - camZ));
                            position.angle.yaw = look.yaw;
                            position.angle.pitch = look.pitch;
                        } else {
                            position.angle.yaw = headYaw;
                            position.angle.pitch = -pitch;
                        }
                    }
                } else {
                    position.point.set(x, y, z);
                    position.angle.yaw = yaw;
                    position.angle.pitch = pitch;
                }
                position.angle.roll = roll;
            }
        }
        if (((Boolean)this.headLook.get()).booleanValue()) {
            position.angle.fov = ((Float)this.fov.get()).floatValue();
        }
    }

    private TrackerFrame resolveHardcoreHeadFrame(CameraClipContext cameraContext, IEntity entity, Position position, float transition) {
        Form form = entity.getForm();
        if (form == null) {
            return null;
        }
        FormRenderer formRenderer = FormUtilsClient.getRenderer((Form)form);
        if (formRenderer instanceof ModelFormRenderer) {
            ModelFormRenderer modelFormRenderer = (ModelFormRenderer)formRenderer;
            modelFormRenderer.ensureAnimator(transition);
        }
        String headBone = "head";
        if (formRenderer != null) {
            MatrixCache map = formRenderer.collectMatrices(entity, transition);
            if (!map.has(headBone)) {
                for (String key : map.keySet()) {
                    if (!key.equalsIgnoreCase("head") && !key.toLowerCase().endsWith("/head") && !key.toLowerCase().endsWith(".head")) continue;
                    headBone = key;
                    break;
                }
            }
            if (!map.has(headBone)) {
                List<String> bones = formRenderer.getBones();
                for (String bone : bones) {
                    if (!bone.equalsIgnoreCase("head") && !bone.toLowerCase().endsWith("head")) continue;
                    headBone = bone;
                    break;
                }
            }
        }
        return TrackerFrame.resolve((Map)cameraContext.entities, (IEntity)entity, (String)headBone, (double)position.point.x, (double)position.point.y, (double)position.point.z, (float)transition);
    }

    private boolean isTopPovClip(ClipContext context) {
        if (context.clips == null) {
            return true;
        }
        PovCameraClip top = null;
        int topLayer = Integer.MIN_VALUE;
        for (Clip candidate : context.clips.getClips(context.ticks)) {
            if (!(candidate instanceof PovCameraClip)) continue;
            PovCameraClip pov = (PovCameraClip)candidate;
            if (!((Boolean)pov.enabled.get()).booleanValue() || (Integer)pov.layer.get() < topLayer) continue;
            top = pov;
            topLayer = (Integer)pov.layer.get();
        }
        return top == this;
    }

    protected Clip create() {
        return new PovCameraClip();
    }
}

