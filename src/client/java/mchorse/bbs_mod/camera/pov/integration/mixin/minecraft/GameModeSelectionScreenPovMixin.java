/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.GameModeSelectionScreen
 *  org.spongepowered.asm.mixin.Mixin
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

import mchorse.bbs_mod.camera.pov.integration.access.minecraft.GameModeSelectionScreenPovAccess;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.client.gui.screen.GameModeSelectionScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value={GameModeSelectionScreen.class})
public class GameModeSelectionScreenPovMixin
implements GameModeSelectionScreenPovAccess {
    private static Field FIELD;

    @Override
    public Object bbsPov$getGameMode() {
        try {
            if (FIELD == null) {
                for (Field f : GameModeSelectionScreen.class.getDeclaredFields()) {
                    if (!f.getType().isEnum() || Modifier.isFinal(f.getModifiers())) continue;
                    f.setAccessible(true);
                    FIELD = f;
                    break;
                }
            }
            if (FIELD != null) {
                return FIELD.get(this);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }
}

