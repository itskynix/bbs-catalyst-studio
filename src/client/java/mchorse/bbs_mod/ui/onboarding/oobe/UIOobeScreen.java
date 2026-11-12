package mchorse.bbs_mod.ui.onboarding.oobe;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UICentralHubPanel;
import mchorse.bbs_mod.ui.framework.UIScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.text.Text;

/** The first-run wizard as a screen of its own; {@code onComplete} runs once its card has melted away. */
public class UIOobeScreen extends UIScreen
{
    public UIOobeScreen(Runnable onComplete)
    {
        super(Text.literal("Blockbuster Catalyst Studio"), new UIOobeMenu(onComplete));
    }

    /** The wizard is over: on to the studio's central hub, or back to the title if the studio cannot come up */
    public static void openHub(MinecraftClient mc)
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboard();

            dashboard.setPanel(dashboard.getPanel(UICentralHubPanel.class));
            UIScreen.open(dashboard);
        }
        catch (Throwable e)
        {
            /* Without a world nothing of the studio may assume one; if something still does, the whole
             * stack is in the log and the title screen stays the way back */
            BBSMod.LOGGER.error("The Central Hub could not be opened", e);
            mc.setScreen(new TitleScreen());
        }
    }

    /** From the settings: the wizard once more, and back to where it was left when it ends */
    public static void rerun(MinecraftClient mc)
    {
        Screen previous = mc.currentScreen;

        mc.setScreen(new UIOobeScreen(() -> mc.setScreen(previous)));
    }
}
