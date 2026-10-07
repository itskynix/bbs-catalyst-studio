package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.ui.framework.UIBaseMenu;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;

public class UIWelcomeMenu extends UIBaseMenu
{
    private final Runnable onComplete;
    private boolean finished = false;

    public UIWelcomeMenu(Runnable onComplete)
    {
        super();

        this.onComplete = onComplete;

        UIWelcomeOverlayPanel panel = new UIWelcomeOverlayPanel();
        panel.onClose((e) -> this.finishWelcome());

        UIOverlay.addOverlay(this.context, panel, 1F, 1F).noBackground();
    }

    public void finishWelcome()
    {
        if (this.finished)
        {
            return;
        }

        this.finished = true;
        WelcomeManager.setSeenWelcomeScreen(true);

        if (this.onComplete != null)
        {
            this.onComplete.run();
        }
        else
        {
            MinecraftClient.getInstance().setScreen(new TitleScreen());
        }
    }

    @Override
    protected void closeMenu()
    {
        this.finishWelcome();
    }
}
