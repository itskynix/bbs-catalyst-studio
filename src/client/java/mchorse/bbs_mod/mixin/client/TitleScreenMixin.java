package mchorse.bbs_mod.mixin.client;

import mchorse.bbs_mod.ui.onboarding.welcome.UIWelcomeScreen;
import mchorse.bbs_mod.ui.onboarding.welcome.WelcomeManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen
{
    @Unique
    private boolean bbs$checkedWelcome = false;

    protected TitleScreenMixin(Text title)
    {
        super(title);
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void bbs$onTitleScreenInit(CallbackInfo ci)
    {
        if (this.bbs$checkedWelcome)
        {
            return;
        }

        if (this.client != null && this.client.getOverlay() == null)
        {
            this.bbs$checkedWelcome = true;

            if (!WelcomeManager.hasSeenWelcomeScreen())
            {
                this.client.setScreen(new UIWelcomeScreen(() ->
                {
                    WelcomeManager.setSeenWelcomeScreen(true);
                    this.client.setScreen(new TitleScreen());
                }));
            }
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void bbs$onTitleScreenRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci)
    {
        if (this.bbs$checkedWelcome)
        {
            return;
        }

        if (this.client != null && this.client.getOverlay() == null)
        {
            this.bbs$checkedWelcome = true;

            if (!WelcomeManager.hasSeenWelcomeScreen())
            {
                ci.cancel();

                this.client.setScreen(new UIWelcomeScreen(() ->
                {
                    WelcomeManager.setSeenWelcomeScreen(true);
                    this.client.setScreen(new TitleScreen());
                }));
            }
        }
    }
}
