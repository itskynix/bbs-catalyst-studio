/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.data.types.ListType
 *  mchorse.bbs_mod.data.types.MapType
 *  mchorse.bbs_mod.utils.clips.Clip
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.statuseffect.StatusEffectEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.clips.Clip;

public final class StatusEffectsPovActionClip
extends PovActionClip {
    private final List<StatusEffectEntry> effects = new ArrayList<StatusEffectEntry>();

    @Override
    public PovActionType getActionType() {
        return PovActionType.STATUS_EFFECTS;
    }

    public List<StatusEffectEntry> getEffects() {
        return this.effects;
    }

    public void addEffect(StatusEffectEntry entry) {
        if (entry != null && !this.hasEffect(entry.getEffectId())) {
            this.effects.add(entry);
        }
    }

    public void removeEffect(StatusEffectEntry entry) {
        this.effects.remove(entry);
    }

    public boolean hasEffect(String effectId) {
        if (effectId == null) {
            return false;
        }
        for (StatusEffectEntry entry : this.effects) {
            if (!effectId.equalsIgnoreCase(entry.getEffectId())) continue;
            return true;
        }
        return false;
    }

    public void moveUp(int index) {
        if (index > 0 && index < this.effects.size()) {
            Collections.swap(this.effects, index, index - 1);
        }
    }

    public void moveDown(int index) {
        if (index >= 0 && index < this.effects.size() - 1) {
            Collections.swap(this.effects, index, index + 1);
        }
    }

    public BaseType toData() {
        BaseType baseData = super.toData();
        MapType data = baseData instanceof MapType ? (MapType)baseData : new MapType();
        ListType list = new ListType();
        for (StatusEffectEntry entry : this.effects) {
            MapType entryData = new MapType();
            entry.toData(entryData);
            list.add((BaseType)entryData);
        }
        data.put("status_effects", (BaseType)list);
        return data;
    }

    public void fromData(BaseType data) {
        Object object;
        MapType map;
        super.fromData(data);
        this.effects.clear();
        if (data instanceof MapType && (map = (MapType)data).has("status_effects") && (object = map.get("status_effects")) instanceof ListType) {
            ListType list = (ListType)object;
            for (BaseType element : list) {
                if (!(element instanceof MapType)) continue;
                MapType entryMap = (MapType)element;
                StatusEffectEntry entry = new StatusEffectEntry();
                entry.fromData(entryMap);
                this.effects.add(entry);
            }
        }
    }

    protected Clip create() {
        StatusEffectsPovActionClip clip = new StatusEffectsPovActionClip();
        for (StatusEffectEntry entry : this.effects) {
            clip.addEffect(entry.copy());
        }
        return clip;
    }
}

