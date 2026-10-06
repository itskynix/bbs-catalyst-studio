/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.hud.ClientBossBar
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import java.util.Map;
import java.util.UUID;
import net.minecraft.client.gui.hud.ClientBossBar;

public interface BossBarHudPovAccess {
    public Map<UUID, ClientBossBar> bbsPov$getBossBars();
}

