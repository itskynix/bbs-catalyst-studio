/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.client.BBSRendering
 *  mchorse.bbs_mod.ui.dashboard.UIDashboard
 *  mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel
 *  mchorse.bbs_mod.ui.film.UIFilmPanel
 *  mchorse.bbs_mod.ui.framework.UIBaseMenu
 *  mchorse.bbs_mod.ui.framework.UIScreen
 *  net.minecraft.client.MinecraftClient
 *  org.lwjgl.glfw.GLFW
 */
package mchorse.bbs_mod.camera.pov.render;

import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

public final class PovViewportMetrics {
    private PovViewportMetrics() {
    }

    public static int getRealWindowWidth() {
        MinecraftClient client = MinecraftClient.getInstance();
        long handle = client.getWindow().getHandle();
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize((long)handle, (int[])w, (int[])h);
        return w[0] > 0 ? w[0] : client.getWindow().getWidth();
    }

    public static int getRealWindowHeight() {
        MinecraftClient client = MinecraftClient.getInstance();
        long handle = client.getWindow().getHandle();
        int[] w = new int[1];
        int[] h = new int[1];
        GLFW.glfwGetFramebufferSize((long)handle, (int[])w, (int[])h);
        return h[0] > 0 ? h[0] : client.getWindow().getHeight();
    }

    public static int getMinecraftGuiScale() {
        int scale;
        MinecraftClient client = MinecraftClient.getInstance();
        int guiScale = (Integer)client.options.getGuiScale().getValue();
        boolean forceUnicode = client.forcesUnicodeFont();
        if (guiScale > 0) {
            int scale2 = guiScale;
            if (forceUnicode && scale2 % 2 != 0) {
                ++scale2;
            }
            return Math.max(1, scale2);
        }
        int winWidth = PovViewportMetrics.getRealWindowWidth();
        int winHeight = PovViewportMetrics.getRealWindowHeight();
        for (scale = 1; scale < winWidth && scale < winHeight && winWidth / (scale + 1) >= 320 && winHeight / (scale + 1) >= 240; ++scale) {
        }
        if (forceUnicode && scale % 2 != 0) {
            ++scale;
        }
        return Math.max(1, scale);
    }

    public static int getFilmGuiScale() {
        int scale;
        if (!BBSRendering.isCustomSize()) {
            return PovViewportMetrics.getMinecraftGuiScale();
        }
        int videoW = BBSRendering.getVideoWidth();
        int videoH = BBSRendering.getVideoHeight();
        if (videoW <= 0 || videoH <= 0) {
            return PovViewportMetrics.getMinecraftGuiScale();
        }
        MinecraftClient client = MinecraftClient.getInstance();
        int guiScale = (Integer)client.options.getGuiScale().getValue();
        boolean forceUnicode = client.forcesUnicodeFont();
        if (guiScale > 0) {
            int winHeight = PovViewportMetrics.getRealWindowHeight();
            if (winHeight > 0 && videoH != winHeight) {
                double factor = (double)videoH / (double)winHeight;
                int scaled = (int)Math.round((double)guiScale * factor);
                if (forceUnicode && scaled % 2 != 0) {
                    ++scaled;
                }
                return Math.max(1, scaled);
            }
            int scale2 = guiScale;
            if (forceUnicode && scale2 % 2 != 0) {
                ++scale2;
            }
            return Math.max(1, scale2);
        }
        for (scale = 1; scale < videoW && scale < videoH && videoW / (scale + 1) >= 320 && videoH / (scale + 1) >= 240; ++scale) {
        }
        if (forceUnicode && scale % 2 != 0) {
            ++scale;
        }
        return Math.max(1, scale);
    }

    public static int getFilmScaledWidth() {
        if (!BBSRendering.isCustomSize()) {
            return PovViewportMetrics.getMinecraftScaledWidth();
        }
        int videoW = BBSRendering.getVideoWidth();
        int videoH = BBSRendering.getVideoHeight();
        if (videoW <= 0 || videoH <= 0) {
            return PovViewportMetrics.getMinecraftScaledWidth();
        }
        int scale = PovViewportMetrics.getFilmGuiScale();
        return Math.max(1, (int)Math.ceil((double)videoW / (double)scale));
    }

    public static int getFilmScaledHeight() {
        if (!BBSRendering.isCustomSize()) {
            return PovViewportMetrics.getMinecraftScaledHeight();
        }
        int videoW = BBSRendering.getVideoWidth();
        int videoH = BBSRendering.getVideoHeight();
        if (videoW <= 0 || videoH <= 0) {
            return PovViewportMetrics.getMinecraftScaledHeight();
        }
        int scale = PovViewportMetrics.getFilmGuiScale();
        return Math.max(1, (int)Math.ceil((double)videoH / (double)scale));
    }

    public static int getMinecraftScaledWidth() {
        return MinecraftClient.getInstance().getWindow().getScaledWidth();
    }

    public static int getMinecraftScaledHeight() {
        return MinecraftClient.getInstance().getWindow().getScaledHeight();
    }

    public static UIFilmPanel resolveFilmPanel() {
        UIBaseMenu currentMenu = UIScreen.getCurrentMenu();
        if (currentMenu instanceof UIDashboard) {
            UIDashboard dashboard = (UIDashboard)currentMenu;
            UIDashboardPanel panel = dashboard.getPanels().panel;
            if (panel instanceof UIFilmPanel) {
                UIFilmPanel filmPanel = (UIFilmPanel)panel;
                return filmPanel;
            }
        }
        return null;
    }
}

