/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions;

import java.util.Locale;

public enum PovActionType {
    GUI("gui", "GUI Screen", 0x9a68d4),
    MENU("menu", "GUI Menu", 0xb82c33),
    CAMERA_SHAKE("camera_shake", "Camera Shake", 0xf78f39),
    PARTICLE_EFFECT("particle_effect", "Particle Effect", 0xf8ff33),
    BOSS_BARS("boss_bars", "Boss Bars", 0x18032b),
    STATUS_EFFECTS("status_effects", "Status Effects", 0xeb98d7),
    TOASTS("toasts", "Toasts", 0x889677),
    CHAT("chat", "Chat", 0xcfe8e4),
    SCREEN_EFFECT("screen_effect", "Screen Effect", 0xe8cfe3);

    public final String id;
    public final String title;
    public final int color;

    private PovActionType(String id, String title, int color) {
        this.id = id;
        this.title = title;
        this.color = color;
    }

    public int seedLayer() {
        return switch (this.ordinal()) {
            default -> throw new IncompatibleClassChangeError();
            case 0 -> 0;
            case 1 -> 1;
            case 2 -> 2;
            case 7 -> 3;
            case 5 -> 4;
            case 8 -> 5;
            case 3 -> 6;
            case 6 -> 7;
            case 4 -> 12;
        };
    }

    public static PovActionType fromId(String id) {
        if (id != null) {
            String lower = id.toLowerCase(Locale.ROOT);
            if ("gui_screen".equals(lower)) {
                return GUI;
            }
            if ("gui_menu".equals(lower)) {
                return MENU;
            }
            if ("particle".equals(lower) || "eating_effect".equals(lower)) {
                return PARTICLE_EFFECT;
            }
            for (PovActionType type : PovActionType.values()) {
                if (!type.id.equals(lower)) continue;
                return type;
            }
        }
        return GUI;
    }
}

