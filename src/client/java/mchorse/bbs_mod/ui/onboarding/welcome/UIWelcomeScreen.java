package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.text.Text;

public class UIWelcomeScreen extends UIScreen
{
    public UIWelcomeScreen(Runnable onComplete)
    {
        super(Text.literal("Welcome to BBS"), new UIWelcomeMenu(onComplete));
    }
}
