package mchorse.bbs_mod.ui.dashboard.textures;

import mchorse.bbs_mod.BBSMod;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.dashboard.panels.landing.ILandingHost;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.input.UITexturePicker;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.textures.TextureFiles;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.PNGEncoder;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.resources.Pixels;

import java.io.File;
import java.io.IOException;

/**
 * Texture manager panel: hosts a modern Creative Hub screen ({@link UITexturesHubScreen})
 * with quick access to recent textures, search, category filtering, and direct file import.
 * Seamlessly integrates with the unified {@link UITexturePicker} for detailed texture
 * inspection and editing.
 */
public class UITextureManagerPanel extends UIDashboardPanel implements ILandingHost
{
    public UITexturePicker picker;
    public UITexturesHubScreen hub;
    private boolean showingHub = true;
    public UIIcon backToHubBtn;

    public static void extractTexture(Link link, Pixels pixels, int frames, int w, int h, int x, int y)
    {
        if (pixels == null)
        {
            /* TODO: throw error */

            return;
        }

        int endX = w + x * (frames - 1);
        int endY = h + y * (frames - 1);

        if (endX > pixels.width || endY > pixels.height)
        {
            /* TODO: throw error */

            return;
        }

        for (int i = 0; i < frames; i++)
        {
            Link texture = new Link(link.source, StringUtils.removeExtension(link.path) + "_" + (i + 1) + ".png");
            File file = BBSMod.getProvider().getFile(texture);

            if (file != null)
            {
                Pixels newPixels = Pixels.fromSize(w, h);
                int sx1 = x * i;
                int sy1 = y * i;

                newPixels.drawPixels(pixels, 0, 0, w, h, sx1, sy1, sx1 + w, sy1 + h);

                try
                {
                    PNGEncoder.writeToFile(newPixels, file);
                }
                catch (IOException e)
                {
                    e.printStackTrace();
                }

                newPixels.delete();
            }
        }
    }

    public UITextureManagerPanel(UIDashboard dashboard)
    {
        super(dashboard);

        this.picker = new UITexturePicker(null).cantBeClosed();
        this.picker.full(this);
        this.picker.fill(null);
        this.add(this.picker);

        this.hub = new UITexturesHubScreen(this);
        this.hub.full(this);
        this.add(this.hub);

        this.backToHubBtn = new UIIcon(Icons.ARROW_LEFT, (b) -> this.showHub());
        this.backToHubBtn.tooltip(UIKeys.TEXTURES_HUB_RETURN);
        this.picker.topBar.actions.action(this.backToHubBtn);

        this.syncViews();

        this.onAppear(() ->
        {
            if (this.showingHub)
            {
                this.hub.refresh();
            }
            else
            {
                this.picker.syncToSharedTabs();
            }
        });

        /* What the tour of this panel points at */
        TourAnchors.register("textures.browser", () -> this.picker.browser);
        TourAnchors.register("textures.edit", () -> this.picker.edit);
        TourAnchors.register("textures.tabs", () -> this.picker.topBar);
    }

    public void showHub()
    {
        this.showingHub = true;
        this.syncViews();
        this.hub.refresh();
    }

    public void showPicker()
    {
        this.showingHub = false;
        this.syncViews();
        this.picker.syncToSharedTabs();
    }

    public void openTexture(Link link)
    {
        if (link != null)
        {
            BBSSettings.recentData.touch("textures", link.toString());
        }

        this.showPicker();
        this.picker.openTexture(link);
    }

    private void syncViews()
    {
        this.hub.setVisible(this.showingHub);
        this.picker.setVisible(!this.showingHub);
    }

    public Link getLink()
    {
        return this.picker.current;
    }

    /* ILandingHost implementation */

    @Override
    public IKey getTitle()
    {
        return UIKeys.TEXTURES_HUB_BADGE;
    }

    @Override
    public Icon getTabIcon(String id)
    {
        return Icons.MATERIAL;
    }

    @Override
    public String getRecentType()
    {
        return "textures";
    }

    @Override
    public void pickData(String id)
    {
        this.openTexture(Link.create(id));
    }

    @Override
    public void openDataManager()
    {
        this.showPicker();
    }

    @Override
    public void requestNames()
    {
        this.hub.fillNames(this.hub.collectAllTextures());
    }

    @Override
    public File getDataFolder()
    {
        return TextureFiles.file(Link.assets(""));
    }
}
