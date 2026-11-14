package mchorse.bbs_mod.ui.dashboard.panels.sandbox;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.dashboard.panels.UIDashboardPanel;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.context.UIContextMenu;
import mchorse.bbs_mod.ui.framework.elements.context.UIWin11ContextMenu;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UISliderTrackpad;
import mchorse.bbs_mod.ui.framework.elements.input.text.UITextbox;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.framework.elements.utils.UIText;
import mchorse.bbs_mod.ui.framework.elements.windows.UIDesktopWindow;
import mchorse.bbs_mod.ui.framework.elements.windows.UIGlassSlider;
import mchorse.bbs_mod.ui.framework.elements.windows.UIGlassTextbox;
import mchorse.bbs_mod.ui.framework.elements.windows.UIGlassToggle;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * A bench for the desktop window: one button per kind of window, so how it looks, drags, stacks and
 * closes can be tried without touching any of the real dialogs.
 */
public class UISandboxPanel extends UIDashboardPanel
{
    private final UIElement desktop = new UIElement();
    private final UILabel status;
    private int opened;

    public UISandboxPanel(UIDashboard dashboard)
    {
        super(dashboard);

        UIElement bench = new UIElement();

        bench.relative(this).x(20).y(20).w(300).h(250);
        bench.column(6).vertical().stretch();

        UILabel header = UI.label(UIKeys.SANDBOX_HEADER).color(Colors.setA(Colors.WHITE, 0.7F));

        header.h(20);

        this.status = UI.label(UIKeys.SANDBOX_STATUS_IDLE).color(Colors.setA(Colors.WHITE, 0.55F));
        this.status.h(20);

        bench.add(header);
        bench.add(this.button(UIKeys.SANDBOX_INFO_BUTTON, this::openInfo));
        bench.add(this.button(UIKeys.SANDBOX_CONFIRM_BUTTON, this::openConfirm));
        bench.add(this.button(UIKeys.SANDBOX_INPUT_BUTTON, this::openInput));
        bench.add(this.button(UIKeys.SANDBOX_TOOL_BUTTON, this::openTool));
        bench.add(this.button(UIKeys.SANDBOX_MULTI_BUTTON, this::openMulti));
        bench.add(this.status);

        UILabel hint = UI.label(UIKeys.SANDBOX_MENU_HINT).color(Colors.setA(Colors.WHITE, 0.4F));

        hint.h(20);
        bench.add(hint);

        /* The windows live in a layer over the whole panel; it passes every click that misses a window */
        this.desktop.full(this);

        this.add(bench, this.desktop);

        /* An empty place of the bench opens the menu; the windows have their own, below */
        this.context(this::menu);
    }

    private UIContextMenu menu()
    {
        return new UIWin11ContextMenu()
            .quick(Icons.CUT, UIKeys.SANDBOX_MENU_CUT, () -> this.picked(UIKeys.SANDBOX_MENU_CUT))
            .quick(Icons.COPY, UIKeys.SANDBOX_MENU_COPY, () -> this.picked(UIKeys.SANDBOX_MENU_COPY))
            .quick(Icons.PASTE, UIKeys.SANDBOX_MENU_PASTE, () -> this.picked(UIKeys.SANDBOX_MENU_PASTE))
            .quick(Icons.REMOVE, UIKeys.SANDBOX_MENU_DELETE, () -> this.picked(UIKeys.SANDBOX_MENU_DELETE))
            .item(Icons.UNDO, UIKeys.SANDBOX_MENU_UNDO, "Ctrl+Z", () -> this.picked(UIKeys.SANDBOX_MENU_UNDO))
            .item(Icons.REDO, UIKeys.SANDBOX_MENU_REDO, "Ctrl+Y", () -> this.picked(UIKeys.SANDBOX_MENU_REDO))
            .separator()
            .item(Icons.ALL_DIRECTIONS, UIKeys.SANDBOX_MENU_SELECT_ALL, "Ctrl+A", () -> this.picked(UIKeys.SANDBOX_MENU_SELECT_ALL))
            .item(Icons.DUPE, UIKeys.SANDBOX_MENU_DUPLICATE, "Ctrl+D", () -> this.picked(UIKeys.SANDBOX_MENU_DUPLICATE))
            .item(Icons.EDIT, UIKeys.SANDBOX_MENU_RENAME, "F2", () -> this.picked(UIKeys.SANDBOX_MENU_RENAME))
            .separator()
            .submenu(Icons.FILE, UIKeys.SANDBOX_MENU_NEW, () -> this.picked(UIKeys.SANDBOX_MENU_NEW))
            .submenu(Icons.UPLOAD, UIKeys.SANDBOX_MENU_SEND, () -> this.picked(UIKeys.SANDBOX_MENU_SEND))
            .separator()
            .item(Icons.GEAR, UIKeys.SANDBOX_MENU_PROPERTIES, "Alt+Enter", () -> this.picked(UIKeys.SANDBOX_MENU_PROPERTIES));
    }

    private void picked(IKey command)
    {
        this.status.label = UIKeys.SANDBOX_STATUS_MENU.format(command.get());
    }

    private UIButton button(IKey label, Runnable action)
    {
        UIButton button = new UIButton(label, (b) -> action.run());

        button.h(24);

        return button;
    }

    private UIDesktopWindow window(IKey title, int w, int h)
    {
        UIDesktopWindow window = new UIDesktopWindow(title).size(w, h);

        window.content.column(8).vertical().stretch().padding(10);
        window.context(this::menu);

        return window;
    }

    /** Each new window lands a little further down and right, so they never hide each other whole. */
    private void show(UIDesktopWindow window)
    {
        int step = this.opened % 6;

        this.opened += 1;
        window.open(this.desktop, 320 + step * 28, 30 + step * 28);
    }

    private void openInfo()
    {
        UIDesktopWindow window = this.window(UIKeys.SANDBOX_INFO_TITLE, 360, 170);

        window.content.add(new UIText(UIKeys.SANDBOX_INFO_TEXT).padding(0, 0).lineHeight(10));
        window.footer(UIKeys.SANDBOX_OK, null, null);

        this.show(window);
    }

    private void openConfirm()
    {
        UIDesktopWindow window = this.window(UIKeys.SANDBOX_CONFIRM_TITLE, 380, 170);

        window.content.add(new UIText(UIKeys.SANDBOX_CONFIRM_TEXT).padding(0, 0).lineHeight(10));
        window.footer(UIKeys.SANDBOX_CONFIRM, () -> this.status.label = UIKeys.SANDBOX_STATUS_CONFIRMED, UIKeys.SANDBOX_CANCEL);

        this.show(window);
    }

    private void openInput()
    {
        UIDesktopWindow window = this.window(UIKeys.SANDBOX_INPUT_TITLE, 380, 170);
        UITextbox box = new UIGlassTextbox(null);

        box.placeholder(UIKeys.SANDBOX_INPUT_PLACEHOLDER);
        box.h(20);

        UILabel label = UI.label(UIKeys.SANDBOX_INPUT_LABEL).color(Colors.setA(Colors.WHITE, 0.7F));

        label.h(16);

        window.content.add(label, box);
        window.footer(UIKeys.SANDBOX_CONFIRM, () -> this.status.label = UIKeys.SANDBOX_STATUS_INPUT.format(box.getText()), UIKeys.SANDBOX_CANCEL);

        this.show(window);
    }

    private void openTool()
    {
        UIDesktopWindow window = this.window(UIKeys.SANDBOX_TOOL_TITLE, 400, 220);
        double[] value = {60D};
        UIGlassSlider slider = new UIGlassSlider((v) -> value[0] = v);

        slider.limit(0, 100, true);
        slider.setValue(value[0]);

        UILabel label = UI.label(UIKeys.SANDBOX_TOOL_SLIDER).color(Colors.setA(Colors.WHITE, 0.7F));

        label.h(16);

        UIGlassToggle toggle = new UIGlassToggle(UIKeys.SANDBOX_TOOL_TOGGLE, true, (t) -> {});

        window.content.add(label, slider, toggle);
        window.footer(UIKeys.SANDBOX_APPLY, () -> this.status.label = UIKeys.SANDBOX_STATUS_TOOL.format(String.valueOf((int) value[0])), UIKeys.SANDBOX_CANCEL);

        this.show(window);
    }

    private void openMulti()
    {
        for (int i = 1; i <= 3; i++)
        {
            UIDesktopWindow window = this.window(UIKeys.SANDBOX_MULTI_TITLE.format(String.valueOf(i)), 320, 160);

            window.content.add(new UIText(UIKeys.SANDBOX_MULTI_TEXT).padding(0, 0).lineHeight(10));
            window.footer(UIKeys.SANDBOX_OK, null, UIKeys.SANDBOX_CANCEL);

            this.show(window);
        }
    }
}
