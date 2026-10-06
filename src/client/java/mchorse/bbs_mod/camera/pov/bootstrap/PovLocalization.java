package mchorse.bbs_mod.camera.pov.bootstrap;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.l10n.keys.LangKey;
import mchorse.bbs_mod.resources.Link;

public final class PovLocalization {
    private PovLocalization() {
    }

    public static void register() {
        if (BBSModClient.getL10n() == null) {
            return;
        }
        BBSModClient.getL10n().registerOne(lang -> new Link("bbs_pov", "strings/" + lang + ".json"));
        if (BBSModClient.getL10n().getStrings() == null) {
            return;
        }
        putDefault("bbs.config.pov.title", "Point of View");
        putDefault("bbs.config.pov.tooltip", "Options related to First Person POV");
        putDefault("bbs.config.pov.toggle_all", "All");
        putDefault("bbs.config.pov.toggle_all-comment", "Enable or disable all POV baking options at once.");
        putDefault("bbs.config.pov.bake_actions", "Bake GUI");
        putDefault("bbs.config.pov.bake_actions-comment", "Record GUI actions, screens, menus, toasts, and status effects into replay clips.");
        putDefault("bbs.config.pov.bake_camera_shake", "Bake Camera Shake");
        putDefault("bbs.config.pov.bake_camera_shake-comment", "Record vanilla hurt-camera motion into Camera Shake action clips.");
        putDefault("bbs.config.pov.bake_particles", "Bake Particles");
        putDefault("bbs.config.pov.bake_particles-comment", "Record on-screen vanilla particles into Particle Effect clips.");
        putDefault("bbs.config.pov.bake_boss_bars", "Bake Boss Bars");
        putDefault("bbs.config.pov.bake_boss_bars-comment", "Record boss bars into Boss Bar action clips.");
        putDefault("bbs.config.pov.bake_screen_effects", "Bake Screen Effects");
        putDefault("bbs.config.pov.bake_screen_effects-comment", "Record vanilla screen overlays (e.g. portal, freeze, blindness) into Screen Effects action clips.");
        putDefault("bbs.config.pov.cursor_texture", "Cursor Texture");
        putDefault("bbs.config.pov.cursor_texture-comment", "Image used for the GUI cursor.");
        putDefault("bbs.config.pov.cursor_crop", "Cursor Crop");
        putDefault("bbs.config.pov.cursor_crop-comment", "Visible area of the cursor image.");
        putDefault("bbs.config.pov.cursor_default_scale", "Cursor Scale");
        putDefault("bbs.config.pov.cursor_default_scale-comment", "Cursor size multiplier.");
        putDefault("bbs.ui.camera.clips.bbs_pov:pov", "Point of View");
        putDefault("bbs.pov.camera_mode", "Point of View");
        putDefault("bbs.pov.ui.editor.open", "Open Point of View Editor");
    }

    private static void putDefault(String key, String fallback) {
        if (!BBSModClient.getL10n().getStrings().containsKey(key)) {
            BBSModClient.getL10n().getStrings().put(key, new LangKey(null, key, fallback));
        }
    }
}
