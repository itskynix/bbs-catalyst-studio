/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.ui.film.clips.UIClip
 */
package mchorse.bbs_mod.camera.pov.actions.editor;

import mchorse.bbs_mod.camera.pov.actions.clip.BossBarPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.MenuPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ParticleEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.SemanticHudPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.StatusEffectsPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIBossBarActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UICameraShakeActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIChatActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIGuiActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIMenuActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIParticleEffectActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIScreenEffectActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UISemanticHudActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIStatusEffectsActionClip;
import mchorse.bbs_mod.camera.pov.actions.editor.UIToastActionClip;
import mchorse.bbs_mod.ui.film.clips.UIClip;

public final class UIPovActionPanels {
    private static boolean registered;

    private UIPovActionPanels() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UIClip.register(GuiPovActionClip.class, UIGuiActionClip::new);
        UIClip.register(MenuPovActionClip.class, UIMenuActionClip::new);
        UIClip.register(CameraShakePovActionClip.class, UICameraShakeActionClip::new);
        UIClip.register(ParticleEffectPovActionClip.class, UIParticleEffectActionClip::new);
        UIClip.register(BossBarPovActionClip.class, UIBossBarActionClip::new);
        UIClip.register(ScreenEffectPovActionClip.class, UIScreenEffectActionClip::new);
        UIClip.register(StatusEffectsPovActionClip.class, UIStatusEffectsActionClip::new);
        UIClip.register(ToastPovActionClip.class, UIToastActionClip::new);
        UIClip.register(ChatPovActionClip.class, UIChatActionClip::new);
        UIClip.register(SemanticHudPovActionClip.class, UISemanticHudActionClip::new);
    }
}

