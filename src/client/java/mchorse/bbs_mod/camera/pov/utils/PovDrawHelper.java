package mchorse.bbs_mod.camera.pov.utils;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class PovDrawHelper {
    public static void drawGuiTexture(DrawContext context, Identifier texture, int x, int y, int width, int height) {
        if (context == null || texture == null) {
            return;
        }
        //? if >=1.20.4 {
        context.drawGuiTexture(texture, x, y, width, height);
        //?} else {
        /*Identifier tex = new Identifier(texture.getNamespace(), "textures/gui/sprites/" + texture.getPath() + ".png");
        context.drawTexture(tex, x, y, 0.0f, 0.0f, width, height, width, height);
        *///?}
    }

    public static void drawGuiTexture(DrawContext context, Identifier texture, int i, int j, int k, int l, int x, int y, int width, int height) {
        if (context == null || texture == null) {
            return;
        }
        //? if >=1.20.4 {
        context.drawGuiTexture(texture, i, j, k, l, x, y, width, height);
        //?} else {
        /*Identifier tex = new Identifier(texture.getNamespace(), "textures/gui/sprites/" + texture.getPath() + ".png");
        context.drawTexture(tex, x, y, (float)k, (float)l, width, height, i, j);
        *///?}
    }
}
