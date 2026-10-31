package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.forms.structure.StructureWand;
import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.camera.WorldFreeCam;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin
{
    @Shadow
    private double cursorDeltaX;

    @Shadow
    private double cursorDeltaY;

    @Shadow
    public boolean isCursorLocked()
    {
        return false;
    }

    /** The world free camera turns with the mouse instead of the player. */
    @Inject(method = "updateMouse", at = @At("HEAD"), cancellable = true)
    public void freeCamLook(CallbackInfo ci)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (!WorldFreeCam.isActive() || mc.currentScreen != null)
        {
            return;
        }

        if (this.isCursorLocked() && mc.isWindowFocused())
        {
            double sensitivity = mc.options.getMouseSensitivity().getValue() * 0.6D + 0.2D;
            double scale = sensitivity * sensitivity * sensitivity * 8D;

            WorldFreeCam.INSTANCE.look(this.cursorDeltaX * scale, this.cursorDeltaY * scale);
        }

        this.cursorDeltaX = 0D;
        this.cursorDeltaY = 0D;

        ci.cancel();
    }

    /** The wheel sets the free camera's speed, not the hotbar. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    public void freeCamScroll(long window, double horizontal, double vertical, CallbackInfo ci)
    {
        if (window == Window.getWindow() && WorldFreeCam.isActive() && MinecraftClient.getInstance().currentScreen == null)
        {
            WorldFreeCam.INSTANCE.scroll(vertical);

            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"))
    public void mouseScroll(long window, double horizontal, double vertical, CallbackInfo ci)
    {
        if (window == Window.getWindow())
        {
            Window.setVerticalScroll((int) vertical);
        }
    }

    /** A notch the structure wand spends on its box must not reach the hotbar. */
    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    public void wandScroll(long window, double horizontal, double vertical, CallbackInfo ci)
    {
        if (window == Window.getWindow() && StructureWand.onScroll(vertical))
        {
            ci.cancel();
        }
    }
}
