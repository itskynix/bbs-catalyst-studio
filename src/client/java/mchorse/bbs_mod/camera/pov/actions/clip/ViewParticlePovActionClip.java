/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.numeric.ValueFloat
 *  mchorse.bbs_mod.settings.values.numeric.ValueInt
 *  org.joml.Vector3f
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import org.joml.Vector3f;

public abstract class ViewParticlePovActionClip
extends PovActionClip {
    public final ValueInt count = new ValueInt("count", Integer.valueOf(1), Integer.valueOf(0), Integer.valueOf(4096));
    public final ValueFloat spread = new ValueFloat("spread", Float.valueOf(0.0f), Float.valueOf(0.0f), Float.valueOf(64.0f));
    public final ValueFloat velX = new ValueFloat("vel_x", Float.valueOf(0.0f));
    public final ValueFloat velY = new ValueFloat("vel_y", Float.valueOf(0.0f));
    public final ValueFloat velZ = new ValueFloat("vel_z", Float.valueOf(0.0f));
    public final ValueInt seed = new ValueInt("seed", Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(Integer.MAX_VALUE));
    public final ValueFloat tx = new ValueFloat("tx", Float.valueOf(0.0f));
    public final ValueFloat ty = new ValueFloat("ty", Float.valueOf(0.0f));
    public final ValueFloat tz = new ValueFloat("tz", Float.valueOf(0.0f));
    public final ValueFloat sx = new ValueFloat("sx", Float.valueOf(1.0f));
    public final ValueFloat sy = new ValueFloat("sy", Float.valueOf(1.0f));
    public final ValueFloat sz = new ValueFloat("sz", Float.valueOf(1.0f));
    public final ValueFloat rx = new ValueFloat("rx", Float.valueOf(0.0f));
    public final ValueFloat ry = new ValueFloat("ry", Float.valueOf(0.0f));
    public final ValueFloat rz = new ValueFloat("rz", Float.valueOf(0.0f));

    protected ViewParticlePovActionClip() {
        this.add((BaseValue)this.count);
        this.add((BaseValue)this.spread);
        this.add((BaseValue)this.velX);
        this.add((BaseValue)this.velY);
        this.add((BaseValue)this.velZ);
        this.add((BaseValue)this.seed);
        this.add((BaseValue)this.tx);
        this.add((BaseValue)this.ty);
        this.add((BaseValue)this.tz);
        this.add((BaseValue)this.sx);
        this.add((BaseValue)this.sy);
        this.add((BaseValue)this.sz);
        this.add((BaseValue)this.rx);
        this.add((BaseValue)this.ry);
        this.add((BaseValue)this.rz);
    }

    public void transformPoint(Vector3f point) {
        point.mul(((Float)this.sx.get()).floatValue(), ((Float)this.sy.get()).floatValue(), ((Float)this.sz.get()).floatValue());
        point.rotateX((float)Math.toRadians(((Float)this.rx.get()).floatValue()));
        point.rotateY((float)Math.toRadians(((Float)this.ry.get()).floatValue()));
        point.rotateZ((float)Math.toRadians(((Float)this.rz.get()).floatValue()));
        point.add(((Float)this.tx.get()).floatValue(), ((Float)this.ty.get()).floatValue(), ((Float)this.tz.get()).floatValue());
    }

    public void transformVector(Vector3f vector) {
        vector.mul(((Float)this.sx.get()).floatValue(), ((Float)this.sy.get()).floatValue(), ((Float)this.sz.get()).floatValue());
        vector.rotateX((float)Math.toRadians(((Float)this.rx.get()).floatValue()));
        vector.rotateY((float)Math.toRadians(((Float)this.ry.get()).floatValue()));
        vector.rotateZ((float)Math.toRadians(((Float)this.rz.get()).floatValue()));
    }

    public float uniformScale() {
        return (((Float)this.sx.get()).floatValue() + ((Float)this.sy.get()).floatValue() + ((Float)this.sz.get()).floatValue()) / 3.0f;
    }
}

