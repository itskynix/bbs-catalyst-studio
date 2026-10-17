package mchorse.bbs_mod.utils.keyframes;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.interps.Interpolations;
import mchorse.bbs_mod.utils.keyframes.factories.PoseKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.TransformKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.PoseTransform;
import mchorse.bbs_mod.utils.pose.Transform;

/** Standalone checks (no test framework): run its main with the main runtime classes on the classpath. */
public class ComponentBezierTest
{
    private static final TransformKeyframeFactory TF = new TransformKeyframeFactory();
    private static final PoseKeyframeFactory PF = new PoseKeyframeFactory();
    private static int failures;
    private static int checks;

    private static void check(boolean ok, String what)
    {
        checks++;

        if (!ok)
        {
            failures++;
            System.out.println("FAIL: " + what);
        }
    }

    private static Keyframe<Transform> key(float tick, float px, float py)
    {
        Transform transform = new Transform();

        transform.translate.set(px, py, 0F);

        Keyframe<Transform> keyframe = new Keyframe<>("k", TF, tick, transform);

        keyframe.getInterpolation().setInterp(Interpolations.BEZIER);

        return keyframe;
    }

    private static Transform at(Keyframe<Transform> a, Keyframe<Transform> b, float x)
    {
        return TF.interpolate(a, a, b, b, Interpolations.BEZIER, x).copy();
    }

    public static void main(String[] args)
    {
        /* 1. No component handles: identical to the plain (Hermite) composite result, and serialises without "handles". */
        Keyframe<Transform> a = key(0, 0, 0);
        Keyframe<Transform> b = key(20, 10, 100);
        Transform plain = at(a, b, 0.3F);

        check(!a.hasComponentHandles(), "fresh key has no component handles");
        check(!a.toData().asMap().has("handles"), "no handles key written for an old-style key");

        /* 2. Y customised, X not: only Y changes. */
        a.setComponentHandle("p.y", 0, 0, 12, 60);
        b.setComponentHandle("p.y", 12, -40, 0, 0);

        Transform custom = at(a, b, 0.3F);

        check(custom.translate.x == plain.translate.x, "X keeps the plain result");
        check(custom.translate.z == plain.translate.z, "Z keeps the plain result");
        check(Math.abs(custom.translate.y - plain.translate.y) > 1F, "Y follows its own handles: " + custom.translate.y + " vs " + plain.translate.y);

        double expected = CubicBezierSolver.value(0, 100, 0, 20, 12, 60, 12, -40, 0.3F);

        check(Math.abs(custom.translate.y - expected) < 1e-4, "Y is the solver's curve with Y's handles");
        check(at(a, b, 0F).translate.y == 0F && at(a, b, 1F).translate.y == 100F, "end points exact");

        /* 3. Serialisation: only the customised components are written, and they round-trip. */
        MapType data = a.toData().asMap();

        check(data.has("handles") && data.get("handles").asMap().has("p.y") && !data.get("handles").asMap().has("p.x"), "only p.y written");

        Keyframe<Transform> back = new Keyframe<>("k", TF);

        /* The value itself is left out: its (de)serialisation needs the game classes, the handles do not. */
        data.remove("value");
        back.fromData(data);

        float[] h = back.getComponentHandle("p.y");

        check(h != null && h[2] == 12F && h[3] == 60F, "handles round-trip");

        Keyframe<Transform> copy = new Keyframe<>("k", TF);

        copy.copy(a);
        copy.setComponentHandle("p.y", 1, 1, 1, 1);
        check(a.getComponentHandle("p.y")[2] == 12F, "copy is deep");

        a.clearComponentHandle("p.y");
        b.clearComponentHandle("p.y");
        check(!a.hasComponentHandles() && !a.toData().asMap().has("handles"), "clearing returns to the old format");

        /* 3b. Graph drag (silent put) and restoring an older state: handles added since must go. */
        Keyframe<Transform> live = key(0, 0, 0);
        Keyframe<Transform> older = key(0, 0, 0);
        MapType olderData = older.toData().asMap();

        olderData.remove("value");
        live.putComponentHandle("r.y", 1, 2, 3, 4);
        check(live.hasComponentHandles() && live.getComponentHandle("r.y")[3] == 4F, "silent put stores the handle");
        live.fromData(olderData);
        check(!live.hasComponentHandles(), "restoring a state without handles clears the live ones");

        check(ComponentBezier.index("arm/r.z") == 5 && ComponentBezier.index("s.x") == 6 && ComponentBezier.index("nope") == -1, "component key index");
        check(ComponentBezier.bone("arm/r.z").equals("arm") && ComponentBezier.bone("p.x").isEmpty(), "component key bone");
        check(Math.abs(ComponentBezier.unit(3) - 180D / Math.PI) < 1e-9 && ComponentBezier.unit(0) == 1D, "rotation is drawn in degrees");

        /* 4. Pose bones: "bone/p.x". */
        Pose poseA = new Pose();
        Pose poseB = new Pose();

        poseA.getOrCreate("arm").translate.set(0, 0, 0);
        poseB.getOrCreate("arm").translate.set(10, 0, 0);
        poseA.getOrCreate("leg").translate.set(0, 0, 0);
        poseB.getOrCreate("leg").translate.set(10, 0, 0);

        Keyframe<Pose> pa = new Keyframe<>("p", PF, 0, poseA);
        Keyframe<Pose> pb = new Keyframe<>("p", PF, 20, poseB);

        pa.getInterpolation().setInterp(Interpolations.BEZIER);

        Pose plainPose = PF.interpolate(pa, pa, pb, pb, Interpolations.BEZIER, 0.3F);
        float plainArm = plainPose.get("arm").translate.x;
        float plainLeg = plainPose.get("leg").translate.x;

        pa.setComponentHandle("arm/p.x", 0, 0, 2, 8);

        Pose customPose = PF.interpolate(pa, pa, pb, pb, Interpolations.BEZIER, 0.3F);

        check(customPose.get("leg").translate.x == plainLeg, "the other bone is untouched");
        check(Math.abs(customPose.get("arm").translate.x - plainArm) > 0.1F, "the bone with handles follows them");

        PoseTransform single = new PoseTransform();

        check(single != null, "pose transform constructs");

        System.out.println(checks + " checks, " + failures + " failures");

        if (failures > 0)
        {
            System.exit(1);
        }
    }
}
