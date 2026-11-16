package mchorse.bbs_mod.ui.dashboard.panels.catalyst;

import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.PanelNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.SplitterNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.StackNode;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.util.ArrayList;
import java.util.List;

/**
 * The panels of the edit page's dock and the layouts it ships with. The monitor and the timeline are
 * the core of the page: they stay on screen. The library and the inspector can be moved, closed or
 * shrunk into a drawer at an edge.
 */
public class CatalystLayouts
{
    public static final String MEDIA = "cat_media";
    public static final String MONITOR = "cat_monitor";
    public static final String INSPECTOR = "cat_inspector";
    public static final String TIMELINE = "cat_timeline";

    public static final int DEFAULT = 0;
    public static final int WIDE = 1;
    public static final int COLOR = 2;
    public static final int VERTICAL = 3;
    public static final int COUNT = 4;

    public static IKey name(int index)
    {
        switch (index)
        {
            case WIDE: return UIKeys.CATALYST_NLE_LAYOUT_WIDE;
            case COLOR: return UIKeys.CATALYST_NLE_LAYOUT_COLOR;
            case VERTICAL: return UIKeys.CATALYST_NLE_LAYOUT_VERTICAL;
            default: return UIKeys.CATALYST_NLE_LAYOUT_DEFAULT;
        }
    }

    public static IKey description(int index)
    {
        switch (index)
        {
            case WIDE: return UIKeys.CATALYST_NLE_LAYOUT_WIDE_DESC;
            case COLOR: return UIKeys.CATALYST_NLE_LAYOUT_COLOR_DESC;
            case VERTICAL: return UIKeys.CATALYST_NLE_LAYOUT_VERTICAL_DESC;
            default: return UIKeys.CATALYST_NLE_LAYOUT_DEFAULT_DESC;
        }
    }

    public static EditorLayoutNode tree(int index)
    {
        switch (index)
        {
            case WIDE:
            {
                /* The monitor takes the width; the library and the inspector share a tabbed column */
                List<String> tabs = new ArrayList<>();

                tabs.add(MEDIA);
                tabs.add(INSPECTOR);

                return new SplitterNode(true, 0.62F,
                    new SplitterNode(false, 0.78F, new PanelNode(MONITOR), new StackNode(tabs, MEDIA)),
                    new PanelNode(TIMELINE));
            }

            case COLOR:
            {
                /* A broad inspector for grading and effects */
                return new SplitterNode(true, 0.60F,
                    new SplitterNode(false, 0.16F, new PanelNode(MEDIA),
                        new SplitterNode(false, 0.50F, new PanelNode(MONITOR), new PanelNode(INSPECTOR))),
                    new PanelNode(TIMELINE));
            }

            case VERTICAL:
            {
                /* A tall monitor on the left for vertical video, the rest beside it */
                return new SplitterNode(false, 0.34F, new PanelNode(MONITOR),
                    new SplitterNode(true, 0.58F,
                        new SplitterNode(false, 0.50F, new PanelNode(MEDIA), new PanelNode(INSPECTOR)),
                        new PanelNode(TIMELINE)));
            }

            default:
            {
                /* Library, monitor and inspector in a row, the timeline under all of them */
                return new SplitterNode(true, 0.56F,
                    new SplitterNode(false, 0.22F, new PanelNode(MEDIA),
                        new SplitterNode(false, 0.72F, new PanelNode(MONITOR), new PanelNode(INSPECTOR))),
                    new PanelNode(TIMELINE));
            }
        }
    }

    /** A layout tree drawn small: each panel as what it holds, in a few rectangles */
    public static void schematic(UIContext c, EditorLayoutNode node, int x, int y, int w, int h, int accent)
    {
        if (node == null || w <= 2 || h <= 2)
        {
            return;
        }

        if (node instanceof SplitterNode splitter)
        {
            if (splitter.isHorizontal())
            {
                int h1 = Math.round(h * splitter.getRatio());

                schematic(c, splitter.getFirst(), x, y, w, h1 - 1, accent);
                schematic(c, splitter.getSecond(), x, y + h1 + 1, w, h - h1 - 1, accent);
            }
            else
            {
                int w1 = Math.round(w * splitter.getRatio());

                schematic(c, splitter.getFirst(), x, y, w1 - 1, h, accent);
                schematic(c, splitter.getSecond(), x + w1 + 1, y, w - w1 - 1, h, accent);
            }

            return;
        }

        String id = node instanceof PanelNode panel ? panel.getPanelId() : node instanceof StackNode stack ? stack.getActivePanelId() : "";

        c.batcher.box(x, y, x + w, y + h, 0xFF26252F);

        /* A stack of panels has its tabs on top */
        if (node instanceof StackNode)
        {
            c.batcher.box(x, y, x + Math.min(w, 22), y + 4, accent);
            c.batcher.box(x + 24, y, x + Math.min(w, 44), y + 4, 0xFF3A3948);
            y += 5;
            h -= 5;
        }

        switch (id == null ? "" : id)
        {
            case MONITOR ->
            {
                c.batcher.box(x + 3, y + 3, x + w - 3, y + h - 7, 0xFF1B3260);
                c.batcher.box(x + w / 2 - 1, y + (h - 7) / 2 - 2, x + w / 2 + 2, y + (h - 7) / 2 + 3, 0xFFFFFFFF);
                c.batcher.box(x + 3, y + h - 4, x + w - 3, y + h - 3, 0x40FFFFFF);
                c.batcher.box(x + 3, y + h - 4, x + 3 + (w - 6) / 2, y + h - 3, accent);
            }
            case TIMELINE ->
            {
                int tracks = Math.max(1, Math.min(4, h / 8));
                int trackH = Math.max(3, (h - 4) / tracks);

                for (int t = 0; t < tracks; t++)
                {
                    int ty = y + 2 + t * trackH;

                    c.batcher.box(x + 2, ty, x + w - 2, ty + trackH - 1, 0xFF1E1D26);
                    c.batcher.box(x + 4 + t * 5, ty + 1, x + 4 + t * 5 + Math.max(4, w / 4), ty + trackH - 2, t % 2 == 0 ? 0xC03F7FD0 : 0xC03FA07A);
                }

                c.batcher.box(x + w / 3, y + 1, x + w / 3 + 1, y + h - 1, 0xFFFF5050);
            }
            case MEDIA ->
            {
                int cell = Math.max(3, Math.min(w, h) / 3 - 1);

                for (int i = 0; i < 6; i++)
                {
                    int cx = x + 3 + (i % 2) * (cell + 2);
                    int cy = y + 3 + (i / 2) * (cell + 2);

                    if (cx + cell < x + w && cy + cell < y + h)
                    {
                        c.batcher.box(cx, cy, cx + cell, cy + cell, 0xFF3A3948);
                    }
                }
            }
            case INSPECTOR ->
            {
                for (int i = 0; i < 5; i++)
                {
                    int ly = y + 4 + i * 5;

                    if (ly + 2 < y + h)
                    {
                        c.batcher.box(x + 3, ly, x + w - 3 - (i % 2) * 6, ly + 2, i == 0 ? accent : 0xFF3A3948);
                    }
                }
            }
            default ->
            {}
        }
    }
}
