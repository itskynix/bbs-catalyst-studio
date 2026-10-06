/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.DeathScreen
 *  net.minecraft.text.Text
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.DeathScreenPovAccess;
import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={DeathScreen.class})
public interface DeathScreenPovAccessor
extends DeathScreenPovAccess {
    @Override
    @Accessor(value="message")
    public Text bbsPov$getDeathMessage();

    @Override
    @Accessor(value="scoreText")
    public Text bbsPov$getScoreText();

    @Override
    @Accessor(value="ticksSinceDeath")
    public int bbsPov$getTicksSinceDeath();
}

