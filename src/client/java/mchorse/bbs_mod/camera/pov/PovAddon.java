/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.fabricmc.api.ClientModInitializer
 *  org.slf4j.Logger
 *  org.slf4j.LoggerFactory
 */
package mchorse.bbs_mod.camera.pov;

import mchorse.bbs_mod.camera.pov.bootstrap.PovLocalization;
import mchorse.bbs_mod.camera.pov.bootstrap.PovRegistries;
import mchorse.bbs_mod.camera.pov.render.PovBlockOutlineRenderer;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class PovAddon
implements ClientModInitializer {
    public static final String MOD_ID = "bbs_pov";
    public static final Logger LOGGER = LoggerFactory.getLogger((String)"bbs_pov");

    public void onInitializeClient() {
        PovRegistries.register();
        PovLocalization.register();
        PovBlockOutlineRenderer.init();
        LOGGER.info("Enabled POV Editor for BBS 2.5");
    }
}

