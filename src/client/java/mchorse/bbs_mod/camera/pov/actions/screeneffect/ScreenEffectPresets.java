/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.screeneffect;

import mchorse.bbs_mod.camera.pov.actions.screeneffect.ScreenEffectPresetEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScreenEffectPresets {
    private static final Map<String, ScreenEffectPresetEntry> BY_ID = new LinkedHashMap<String, ScreenEffectPresetEntry>();
    private static final List<ScreenEffectPresetEntry> ALL = new ArrayList<ScreenEffectPresetEntry>();
    public static final ScreenEffectPresetEntry SUFFOCATION = ScreenEffectPresets.register(new ScreenEffectPresetEntry("suffocation", "In-Wall Suffocation", "Solid block texture covering the screen", "minecraft:stone", 1.0f, 0));
    public static final ScreenEffectPresetEntry FIRE = ScreenEffectPresets.register(new ScreenEffectPresetEntry("fire", "Fire", "Flames rising from the bottom of the screen", "minecraft:flint_and_steel", 1.0f, 1));
    public static final ScreenEffectPresetEntry VIGNETTE = ScreenEffectPresets.register(new ScreenEffectPresetEntry("vignette", "Vignette", "Radial edge shadow & world border warning", "minecraft:compass", 0.5f, 2));
    public static final ScreenEffectPresetEntry PORTAL = ScreenEffectPresets.register(new ScreenEffectPresetEntry("portal", "Nether Portal", "Animated swirling purple portal overlay", "minecraft:obsidian", 1.0f, 3));
    public static final ScreenEffectPresetEntry PUMPKIN = ScreenEffectPresets.register(new ScreenEffectPresetEntry("pumpkin", "Pumpkin Blur", "Carved pumpkin head eye-hole cutout", "minecraft:carved_pumpkin", 1.0f, 4));
    public static final ScreenEffectPresetEntry FROST = ScreenEffectPresets.register(new ScreenEffectPresetEntry("frost", "Powder Snow Frost", "Ice frost crystals creeping from screen edges", "minecraft:powder_snow_bucket", 1.0f, 5));
    public static final ScreenEffectPresetEntry SPYGLASS = ScreenEffectPresets.register(new ScreenEffectPresetEntry("spyglass", "Spyglass Scope", "Circular zoom scope with black borders", "minecraft:spyglass", 1.0f, 6));
    public static final ScreenEffectPresetEntry TOTEM = ScreenEffectPresets.register(new ScreenEffectPresetEntry("totem", "Totem of Undying", "Animated rising totem popup burst", "minecraft:totem_of_undying", 1.0f, 7));
    public static final ScreenEffectPresetEntry NAUSEA = ScreenEffectPresets.register(new ScreenEffectPresetEntry("nausea", "Nausea Distortion", "Screen wave distortion & wobble shader", "minecraft:pufferfish", 1.0f, 8));
    public static final ScreenEffectPresetEntry NIGHT_VISION = ScreenEffectPresets.register(new ScreenEffectPresetEntry("night_vision", "Night Vision", "Bright ambient light", "minecraft:golden_carrot", 1.0f, 9));
    public static final ScreenEffectPresetEntry BLINDNESS = ScreenEffectPresets.register(new ScreenEffectPresetEntry("blindness", "Blindness", "Deep spherical black fog restricting vision", "minecraft:ink_sac", 1.0f, 10));
    public static final ScreenEffectPresetEntry DARKNESS = ScreenEffectPresets.register(new ScreenEffectPresetEntry("darkness", "Darkness", "Sculk pulsing darkness overlay", "minecraft:sculk_sensor", 1.0f, 11));
    public static final ScreenEffectPresetEntry UNDERWATER = ScreenEffectPresets.register(new ScreenEffectPresetEntry("underwater", "Underwater", "Submerged water texture overlay", "minecraft:water_bucket", 0.1f, 12));

    private static ScreenEffectPresetEntry register(ScreenEffectPresetEntry entry) {
        BY_ID.put(entry.id, entry);
        ALL.add(entry);
        return entry;
    }

    public static List<ScreenEffectPresetEntry> getAll() {
        return Collections.unmodifiableList(ALL);
    }

    public static ScreenEffectPresetEntry getById(String id) {
        if (id == null) {
            return FIRE;
        }
        ScreenEffectPresetEntry entry = BY_ID.get(id.toLowerCase());
        return entry != null ? entry : FIRE;
    }
}

