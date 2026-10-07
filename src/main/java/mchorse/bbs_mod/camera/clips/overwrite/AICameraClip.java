package mchorse.bbs_mod.camera.clips.overwrite;

import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.data.Angle;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.camera.values.ValueAngle;
import mchorse.bbs_mod.camera.values.ValuePoint;
import mchorse.bbs_mod.utils.clips.Clip;

public class AICameraClip extends KeyframeClip
{
    public final ValuePoint origin = new ValuePoint("origin", new Point(0, 0, 0));
    public final ValueAngle angle = new ValueAngle("angle", new Angle(0, 0, 0, 70));

    public AICameraClip()
    {
        super();

        this.add(this.origin);
        this.add(this.angle);
    }

    @Override
    public void fromCamera(Camera camera)
    {
        super.fromCamera(camera);

        if (camera != null)
        {
            this.origin.set(new Point(camera.position.x, camera.position.y, camera.position.z));
            Angle a = new Angle(0, 0);
            a.set(camera);
            this.angle.set(a);
        }
    }

    @Override
    public Clip create()
    {
        return new AICameraClip();
    }
}
