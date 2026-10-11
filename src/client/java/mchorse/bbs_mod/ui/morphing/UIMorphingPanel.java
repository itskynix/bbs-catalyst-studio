package mchorse.bbs_mod.ui.morphing;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.IMorphProvider;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.forms.UIFormPalette;
import mchorse.bbs_mod.ui.forms.UIToggleEditorEvent;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.morphing.camera.ImmersiveMorphingCameraController;
import mchorse.bbs_mod.ui.onboarding.TourAnchors;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.Direction;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;

public class UIMorphingPanel extends UIDashboardPanel
{
    public UIMorphStudioScreen studioScreen;
    public UIFormPalette palette;
    public UIIcon backToStudioBtn;
    public UIIcon demorph;
    public UIIcon fromMob;

    private boolean showingStudio = true;
    private ImmersiveMorphingCameraController controller;

    public UIMorphingPanel(UIDashboard dashboard)
    {
        super(dashboard);

        /* Klasik Palet & Editör Mekanizması */
        this.palette = new UIFormPalette(this::setForm);
        this.palette.updatable().cantExit();
        this.palette.immersive();
        this.palette.full(this);
        this.palette.editor.renderer.full(dashboard.getRoot());
        this.palette.noBackground();
        this.palette.canModify();

        this.backToStudioBtn = new UIIcon(Icons.ARROW_LEFT, (b) -> this.showStudioView());
        this.backToStudioBtn.tooltip(UIKeys.MORPH_STUDIO_RETURN, Direction.TOP);

        this.demorph = new UIIcon(Icons.POSE, (b) -> this.demorph());
        this.demorph.tooltip(UIKeys.MORPHING_DEMORPH, Direction.TOP);

        this.fromMob = new UIIcon(Icons.MORPH, (b) -> this.morphFromMob());
        this.fromMob.tooltip(UIKeys.MORPHING_FROM_MOB, Direction.TOP);

        this.palette.list.bar.add(this.backToStudioBtn, this.fromMob, this.demorph);

        this.palette.getEvents().register(UIToggleEditorEvent.class, (e) ->
        {
            if (!e.editing)
            {
                this.showStudioView();
            }
        });

        /* Modern Morphing Studio & Canlı 3D Podyum Ekranı */
        this.studioScreen = new UIMorphStudioScreen(this);
        this.studioScreen.full(this);

        this.add(this.palette, this.studioScreen);

        this.syncViews();

        this.controller = new ImmersiveMorphingCameraController(() -> this.palette.editor.isEditing() ? this.palette.editor.renderer : null);

        this.onAppear(this::enterMorphing);
        this.onDisappear(this::leaveMorphing);

        /* What the tour of this panel points at */
        TourAnchors.register("morphing.forms", () -> this.palette.list.forms);
        TourAnchors.register("morphing.edit", () -> this.palette.list.edit);
        TourAnchors.register("morphing.demorph", () -> this.demorph);
    }

    public void showStudioView()
    {
        this.showingStudio = true;
        this.syncViews();
        this.studioScreen.refresh();
    }

    public void showClassicView()
    {
        this.showingStudio = false;
        this.syncViews();
        this.palette.edit(false);
    }

    public void openEditor(Form form)
    {
        this.showingStudio = false;
        this.syncViews();
        this.palette.setSelected(form);
        this.palette.edit(true);
    }

    private void syncViews()
    {
        this.studioScreen.setVisible(this.showingStudio);
        this.palette.setVisible(!this.showingStudio);
    }

    private void enterMorphing()
    {
        this.palette.list.forms.scroll.scrollSpeed = 40;

        Morph morph = ((IMorphProvider) MinecraftClient.getInstance().player).getMorph();
        Form playerForm = morph != null ? morph.getForm() : null;

        this.palette.list.setupForms(BBSModClient.getFormCategories());
        this.palette.setSelected(playerForm);

        this.studioScreen.setupForms(BBSModClient.getFormCategories());
        this.studioScreen.setSelectedForm(playerForm);

        BBSModClient.getCameraController().add(this.controller);
        MinecraftClient.getInstance().options.setPerspective(Perspective.THIRD_PERSON_BACK);

        this.syncViews();

        if (!this.showingStudio && BBSSettings.morphingFocusSearch.get())
        {
            MinecraftClient.getInstance().execute(() -> this.palette.list.focusSearchInput());
        }
    }

    private void leaveMorphing()
    {
        BBSModClient.getCameraController().remove(this.controller);
        MinecraftClient.getInstance().options.setPerspective(Perspective.FIRST_PERSON);
    }

    public void setForm(Form form)
    {
        ClientNetwork.sendPlayerForm(form);

        if (this.studioScreen != null)
        {
            this.studioScreen.setSelectedForm(form);
        }

        if (this.palette != null)
        {
            this.palette.setSelected(form);
        }
    }

    public void demorph()
    {
        this.setForm(null);
    }

    public void morphFromMob()
    {
        Form form = Morph.getMobForm(MinecraftClient.getInstance().player);

        if (form != null)
        {
            this.setForm(form);
        }
    }

    @Override
    public boolean needsBackground()
    {
        if (this.showingStudio)
        {
            return true;
        }

        return !this.palette.editor.isEditing();
    }
}