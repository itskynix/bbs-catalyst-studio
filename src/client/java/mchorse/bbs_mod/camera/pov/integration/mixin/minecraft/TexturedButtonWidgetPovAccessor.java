/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.gui.screen.ButtonTextures
 *  net.minecraft.client.gui.widget.TexturedButtonWidget
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.gen.Accessor
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.minecraft;

//? if >=1.20.4 {
import net.minecraft.client.gui.screen.ButtonTextures;
import net.minecraft.client.gui.widget.TexturedButtonWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={TexturedButtonWidget.class})
public interface TexturedButtonWidgetPovAccessor {
    @Accessor(value="textures")
    public ButtonTextures bbsPov$getTextures();
}
//?} else {
/*import net.minecraft.client.gui.widget.TexturedButtonWidget;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value={TexturedButtonWidget.class})
public interface TexturedButtonWidgetPovAccessor {
    @Accessor(value="texture")
    public Identifier bbsPov$getTexture();
}
*///?}

