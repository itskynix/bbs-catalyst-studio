/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.graphics.texture.Texture
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.settings.SettingsBuilder
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 *  mchorse.bbs_mod.settings.values.numeric.ValueFloat
 *  mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  org.joml.Vector4f
 */
package mchorse.bbs_mod.camera.pov.config;

import mchorse.bbs_mod.camera.pov.config.BakeToggleAllValue;
import mchorse.bbs_mod.camera.pov.config.CursorCropValue;
import mchorse.bbs_mod.camera.pov.config.CursorTextureValue;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.graphics.texture.Texture;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.SettingsBuilder;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import org.joml.Vector4f;

public final class PovSettings {
    public static BakeToggleAllValue toggleAll;
    public static ValueBoolean bakeActions;
    public static ValueBoolean bakeCameraShake;
    public static ValueBoolean bakeParticles;
    public static ValueBoolean bakeBossBars;
    public static ValueBoolean bakeScreenEffects;
    public static CursorTextureValue cursorTexture;
    public static CursorCropValue cursorCrop;
    public static ValueFloat cursorDefaultScale;

    private PovSettings() {
    }

    public static void register(SettingsBuilder builder) {
        builder.category("pov", Icons.LOOKING);
        toggleAll = new BakeToggleAllValue("toggle_all");
        builder.register((BaseValue)toggleAll);
        bakeActions = builder.getBoolean("bake_actions", true);
        bakeCameraShake = builder.getBoolean("bake_camera_shake", true);
        bakeParticles = builder.getBoolean("bake_particles", true);
        bakeBossBars = builder.getBoolean("bake_boss_bars", true);
        bakeScreenEffects = builder.getBoolean("bake_screen_effects", true);
        cursorTexture = new CursorTextureValue("cursor_texture");
        builder.register((BaseValue)cursorTexture);
        cursorCrop = new CursorCropValue("cursor_crop");
        builder.register((BaseValue)cursorCrop);
        cursorDefaultScale = builder.getFloat("cursor_default_scale", 1.0f, 0.01f, 10.0f);
    }

    public static void setAllBake(boolean enable) {
        if (bakeActions != null) {
            bakeActions.set(enable);
        }
        if (bakeCameraShake != null) {
            bakeCameraShake.set(enable);
        }
        if (bakeParticles != null) {
            bakeParticles.set(enable);
        }
        if (bakeBossBars != null) {
            bakeBossBars.set(enable);
        }
        if (bakeScreenEffects != null) {
            bakeScreenEffects.set(enable);
        }
    }

    public static boolean areAllBakeEnabled() {
        return PovSettings.isBakeGui() && PovSettings.isBakeCameraShake() && PovSettings.isBakeParticles() && PovSettings.isBakeBossBars() && PovSettings.isBakeScreenEffects();
    }

    public static boolean isBakeGui() {
        return bakeActions != null && (Boolean)bakeActions.get() != false;
    }

    public static boolean isBakeActions() {
        return PovSettings.isBakeGui();
    }

    public static boolean isBakeCameraShake() {
        return bakeCameraShake != null && (Boolean)bakeCameraShake.get() != false;
    }

    public static boolean isBakeMenu() {
        return PovSettings.isBakeGui();
    }

    public static boolean isBakeParticles() {
        return bakeParticles != null && (Boolean)bakeParticles.get() != false;
    }

    public static boolean isBakeBossBars() {
        return bakeBossBars != null && (Boolean)bakeBossBars.get() != false;
    }

    public static boolean isBakeStatusEffects() {
        return PovSettings.isBakeGui();
    }

    public static boolean isBakeToasts() {
        return PovSettings.isBakeGui();
    }

    public static boolean isBakeScreenEffects() {
        return bakeScreenEffects != null && (Boolean)bakeScreenEffects.get() != false;
    }

    public static boolean isBakeAnyActions() {
        return PovSettings.isBakeGui() || PovSettings.isBakeCameraShake() || PovSettings.isBakeParticles() || PovSettings.isBakeBossBars() || PovSettings.isBakeScreenEffects();
    }

    public static float getCursorDefaultScale() {
        return cursorDefaultScale == null ? 1.0f : ((Float)cursorDefaultScale.get()).floatValue();
    }

    public static void renderCursor(Batcher2D batcher) {
        Texture texture;
        Link link;
        Link link2 = link = cursorTexture == null ? null : (Link)cursorTexture.get();
        if (link != null && (texture = BBSModClient.getTextures().getTexture(link)) != null && texture.isValid() && texture.width > 0 && texture.height > 0) {
            Vector4f crop = cursorCrop == null ? null : (Vector4f)cursorCrop.get();
            float left = crop == null ? 0.0f : PovSettings.clampCrop(crop.x, texture.width);
            float top = crop == null ? 0.0f : PovSettings.clampCrop(crop.y, texture.height);
            float right = crop == null ? 0.0f : PovSettings.clampCrop(crop.z, (float)texture.width - left);
            float bottom = crop == null ? 0.0f : PovSettings.clampCrop(crop.w, (float)texture.height - top);
            float width = (float)texture.width - left - right;
            float height = (float)texture.height - top - bottom;
            if (width > 0.0f && height > 0.0f) {
                batcher.texturedBox(texture, -1, 0.0f, 0.0f, width, height, left, top, (float)texture.width - right, (float)texture.height - bottom);
            }
            return;
        }
        batcher.icon(Icons.CURSOR, 0.0f, 0.0f);
    }

    private static float clampCrop(float value, float maximum) {
        return Float.isFinite(value) ? Math.max(0.0f, Math.min(value, maximum)) : 0.0f;
    }
}

