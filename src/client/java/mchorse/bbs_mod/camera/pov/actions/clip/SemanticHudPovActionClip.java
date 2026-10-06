/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.core.ValueString
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.core.ValueString;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public final class SemanticHudPovActionClip
extends PovActionClip {
    public final ValueString hudType;
    public final KeyframeChannel<String> state;
    public final KeyframeChannel<Float> opacity;

    public SemanticHudPovActionClip() {
        this(PovActionType.TOASTS);
    }

    public SemanticHudPovActionClip(PovActionType type) {
        this.hudType = new ValueString("hud_type", PovActionType.TOASTS.id);
        this.state = this.channel("state", KeyframeFactories.STRING);
        this.opacity = this.channel("opacity", KeyframeFactories.FLOAT);
        this.hudType.set((type == null ? PovActionType.TOASTS.id : type.id));
        this.add((BaseValue)this.hudType);
    }

    @Override
    public PovActionType getActionType() {
        return this.hudType == null ? PovActionType.TOASTS : PovActionType.fromId((String)this.hudType.get());
    }

    @Override
    public void normalize() {
        super.normalize();
        SemanticHudPovActionClip.constant(this.state);
        SemanticHudPovActionClip.clamp(this.opacity, 0.0f, 1.0f);
    }

    protected Clip create() {
        return new SemanticHudPovActionClip(this.getActionType());
    }
}

