/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 */
package mchorse.bbs_mod.camera.pov.config;

import mchorse.bbs_mod.camera.pov.config.PovSettings;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;

public class BakeToggleAllValue
extends ValueBoolean {
    public BakeToggleAllValue(String id) {
        super(id, true);
    }

    public Boolean get() {
        return PovSettings.areAllBakeEnabled();
    }

    public void set(Boolean value) {
        super.set(value);
        PovSettings.setAllBake(value != null && value != false);
    }
}

