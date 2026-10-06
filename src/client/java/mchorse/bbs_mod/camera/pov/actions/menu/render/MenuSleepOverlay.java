/*
 * Decompiled with CFR 0.152.
 */
package mchorse.bbs_mod.camera.pov.actions.menu.render;

public final class MenuSleepOverlay {
    public static final int RGB = 0x101020;
    public static final float MAX_ALPHA = 220.0f;

    private MenuSleepOverlay() {
    }

    public static float progress(int sleepTimer) {
        if (sleepTimer <= 0) {
            return 0.0f;
        }
        float timer = sleepTimer;
        float progress = timer / 100.0f;
        if (progress > 1.0f) {
            progress = 1.0f - (timer - 100.0f) / 10.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, progress));
    }

    public static int color(float progress) {
        float clamped = Math.max(0.0f, Math.min(1.0f, progress));
        int alpha = (int)(220.0f * clamped);
        return alpha << 24 | 0x101020;
    }
}

