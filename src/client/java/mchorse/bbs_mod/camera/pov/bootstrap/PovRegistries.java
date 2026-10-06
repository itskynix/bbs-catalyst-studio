/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSMod
 *  mchorse.bbs_mod.camera.clips.ClipFactoryData
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.film.clips.UIClip
 *  mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.bootstrap;

import mchorse.bbs_mod.camera.pov.actions.editor.UIPovActionPanels;
import mchorse.bbs_mod.camera.pov.camera.clip.PovCameraClip;
import mchorse.bbs_mod.camera.pov.camera.clip.UIPovCameraClip;
import mchorse.bbs_mod.camera.pov.config.UICursorCropSetting;
import mchorse.bbs_mod.camera.pov.hand.editor.UIModelKeyframeFactory;
import mchorse.bbs_mod.camera.pov.hud.editor.UIHotbarIntegerKeyframeFactory;
import mchorse.bbs_mod.camera.pov.hud.editor.UIHotbarItemKeyframeFactory;
import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.film.clips.UIClip;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.factories.UIKeyframeFactory;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public final class PovRegistries {
    private PovRegistries() {
    }

    public static void register() {
        UICursorCropSetting.register();
        if (BBSMod.getProvider() != null) {
            BBSMod.getProvider().register(new mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack("bbs_pov", "assets/bbs_pov/assets", mchorse.bbs_mod.camera.pov.PovAddon.class));
            BBSMod.getProvider().register(new mchorse.bbs_mod.resources.packs.InternalAssetsSourcePack("bbs_pov", "assets/bbs_pov", mchorse.bbs_mod.camera.pov.PovAddon.class));
        }
        BBSMod.getFactoryCameraClips().register(new Link("bbs_pov", "pov"), PovCameraClip.class, new ClipFactoryData(Icons.VISIBLE, 0x331050));
        UIClip.register(PovCameraClip.class, UIPovCameraClip::new);
        UIKeyframeFactory.register((IKeyframeFactory)KeyframeFactories.ITEM_STACK, UIHotbarItemKeyframeFactory::new);
        UIKeyframeFactory.register((IKeyframeFactory)KeyframeFactories.INTEGER, UIHotbarIntegerKeyframeFactory::new);
        UIKeyframeFactory.registerProperty((String)"pov_hand_model", UIModelKeyframeFactory::new);
        UIKeyframeFactory.registerProperty((String)"model", UIModelKeyframeFactory::new);
        UIPovActionPanels.register();
    }
}

