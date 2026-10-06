/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.DeathScreen
 *  net.minecraft.client.gui.screen.GameMenuScreen
 *  net.minecraft.client.gui.screen.Screen
 *  net.minecraft.client.gui.screen.SleepingChatScreen
 *  net.minecraft.client.network.ClientPlayerEntity
 */
package mchorse.bbs_mod.camera.pov.actions.menu.schema;

import net.minecraft.client.gui.screen.DeathScreen;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.SleepingChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;

public final class MenuTypeResolver {
    private MenuTypeResolver() {
    }

    public static String resolve(Screen screen) {
        GameMenuScreen menu;
        if (screen instanceof GameMenuScreen && (menu = (GameMenuScreen)screen).shouldShowMenu()) {
            return "game_menu";
        }
        if (screen instanceof DeathScreen) {
            return "death";
        }
        if (screen instanceof SleepingChatScreen) {
            return "sleep";
        }
        return null;
    }

    public static String resolveLive(Screen screen, ClientPlayerEntity player) {
        String type = MenuTypeResolver.resolve(screen);
        if (type != null) {
            return type;
        }
        if (player != null && player.getSleepTimer() > 0) {
            return "sleep";
        }
        return null;
    }
}

