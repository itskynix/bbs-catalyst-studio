package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.catalyst.CatalystComposition;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UIColor;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/** The small window of a marker on the ruler: its name, its colour, Save and Delete */
public class UIMarkerOverlayPanel extends UIOverlayPanel
{
    private final CatalystComposition.CatalystMarker marker;
    private final Runnable beforeEdit;
    private final Runnable edited;
    private final UITextbox name;
    private final UIColor color;
    private int rgb;

    public UIMarkerOverlayPanel(CatalystComposition.CatalystMarker marker, Runnable beforeEdit, Runnable edited, Runnable delete)
    {
        super(UIKeys.CATALYST_NLE_MARKER_SETTINGS);

        this.marker = marker;
        this.beforeEdit = beforeEdit;
        this.edited = edited;
        this.rgb = marker.color & Colors.RGB;

        this.name = new UITextbox(60, (t) -> {});
        this.name.setText(marker.label == null ? "" : marker.label);
        this.name.h(20);

        this.color = new UIColor((c) -> this.rgb = c & Colors.RGB);
        this.color.setColor(this.rgb);

        UIButton save = new UIButton(UIKeys.CATALYST_NLE_MARKER_SAVE, (b) -> this.confirm());
        UIButton remove = new UIButton(UIKeys.CATALYST_NLE_MARKER_DELETE, (b) ->
        {
            this.close.clickItself();
            delete.run();
        });

        save.h(20);
        remove.h(20);

        UIElement container = new UIElement();

        container.relative(this.content).xy(10, 6).w(1F, -20).hTo(this.content.area, 1F, -6);
        container.column(6).vertical().stretch();
        container.add(UI.label(UIKeys.CATALYST_NLE_MARKER_NAME).h(14), this.name, UI.label(UIKeys.CATALYST_NLE_MARKER_COLOR).h(14), this.color, UI.row(save, remove).h(20));
        this.content.add(container);
    }

    public static void open(UIContext context, CatalystComposition.CatalystMarker marker, Runnable beforeEdit, Runnable edited, Runnable delete)
    {
        UIOverlay.addOverlay(context, new UIMarkerOverlayPanel(marker, beforeEdit, edited, delete), 320, 200);
    }

    @Override
    public void confirm()
    {
        this.beforeEdit.run();
        this.marker.label = this.name.getText().trim();
        this.marker.color = Colors.A100 | this.rgb;
        this.close.clickItself();
        this.edited.run();
    }
}
