/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.text.Text
 */
package mchorse.bbs_mod.camera.pov.integration.access.minecraft;

import net.minecraft.text.Text;

public interface DeathScreenPovAccess {
    public Text bbsPov$getDeathMessage();

    public Text bbsPov$getScoreText();

    public int bbsPov$getTicksSinceDeath();
}

