package mchorse.bbs_mod.ui.onboarding.welcome;

import mchorse.bbs_mod.l10n.L10n;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.PanelNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.SplitterNode;
import mchorse.bbs_mod.settings.values.ui.EditorLayoutNode.StackNode;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.util.List;

/**
 * The seven film editor layouts the setup wizard and the editor's "Window and Layout" menu offer,
 * modelled on the editors people come from, and a small drawing of any layout tree: each panel a
 * block with what it holds sketched in it (a monitor, a timeline with clips, property rows, a list).
 */
public final class NleLayouts
{
    public static final String[] IDS = {"premiere", "davinci", "capcut", "legacy", "reels", "bbscs", "custom"};
    public static final int REELS = 4;
    public static final int CUSTOM = 6;

    private static final int[] PALETTE = {0xFF4C9CFF, 0xFF6CE08A, 0xFFFFB347, 0xFFFF7AA8, 0xFFB48CFF, 0xFF4CD7E0, 0xFFFFE066};

    private NleLayouts()
    {}

    public static IKey name(int index)
    {
        return L10n.lang("bbs.ui.oobe.layout." + IDS[index]);
    }

    public static IKey description(int index)
    {
        return L10n.lang("bbs.ui.oobe.layout." + IDS[index] + "_desc");
    }

    private static EditorLayoutNode lists()
    {
        return new StackNode(List.of("replaysList", "replayProps"), "replaysList");
    }

    /**
     * A layout, as the film editor's panels: the preview (the 3D monitor), the main panel (the
     * timeline), the edit area (properties and effects), and the replays list with their properties.
     * A splitter that is horizontal stacks its halves, one that is not puts them side by side. The
     * last one is the user's own, handed in.
     */
    public static EditorLayoutNode tree(int index, EditorLayoutNode custom)
    {
        return switch (index)
        {
            /* Premiere: the project and the timeline on the left, the monitor and the effects on the right */
            case 0 -> new SplitterNode(false, 0.45F,
                new SplitterNode(true, 0.38F, lists(), new PanelNode("main")),
                new SplitterNode(true, 0.62F, new PanelNode("preview"), new PanelNode("editArea")));
            /* DaVinci: monitors over a wide timeline, the colour and properties on the right */
            case 1 -> new SplitterNode(true, 0.6F,
                new SplitterNode(false, 0.25F, lists(), new SplitterNode(false, 0.72F, new PanelNode("preview"), new PanelNode("editArea"))),
                new PanelNode("main"));
            /* CapCut: the media pool top left, one timeline along the bottom, the preview standing on the right */
            case 2 -> new SplitterNode(true, 0.62F,
                new SplitterNode(false, 0.3F, lists(), new SplitterNode(false, 0.55F, new PanelNode("editArea"), new PanelNode("preview"))),
                new PanelNode("main"));
            /* The old BBS panels */
            case 3 -> preset("bbs_old");
            /* Reels and shorts: a tall preview in the middle */
            case REELS -> new SplitterNode(false, 0.28F, lists(),
                new SplitterNode(false, 0.4F, new PanelNode("preview"), new SplitterNode(true, 0.55F, new PanelNode("main"), new PanelNode("editArea"))));
            /* BBS Catalyst Studio, as it ships */
            case 5 -> EditorLayoutNode.defaultFilmLayout();
            default -> custom == null ? EditorLayoutNode.defaultFilmLayout() : custom;
        };
    }

    private static EditorLayoutNode preset(String id)
    {
        for (LayoutPresets.Preset preset : LayoutPresets.ALL)
        {
            if (preset.id.equals(id))
            {
                return preset.getTree();
            }
        }

        return EditorLayoutNode.defaultFilmLayout();
    }

    private static int fade(int color, float alpha)
    {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);

        return (a << 24) | (color & 0xFFFFFF);
    }

    private static void rect(UIContext c, float x, float y, float w, float h, int color, float alpha)
    {
        int fill = fade(color, alpha);

        if ((fill >>> 24) > 0 && w > 0F && h > 0F)
        {
            c.batcher.box(Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), fill);
        }
    }

    /** A layout tree drawn small; the preview stands nine by sixteen when tall, the clock moves the playheads */
    public static void schematic(UIContext c, EditorLayoutNode node, float x, float y, float w, float h, boolean tall, float clock, float alpha, int accent)
    {
        float seam = 2F;

        if (node == null || w <= 2F || h <= 2F)
        {
            return;
        }

        if (node instanceof SplitterNode splitter)
        {
            if (splitter.isHorizontal())
            {
                float h1 = h * splitter.getRatio();

                schematic(c, splitter.getFirst(), x, y, w, h1 - seam / 2F, tall, clock, alpha, accent);
                schematic(c, splitter.getSecond(), x, y + h1 + seam / 2F, w, h - h1 - seam / 2F, tall, clock, alpha, accent);
            }
            else
            {
                float w1 = w * splitter.getRatio();

                schematic(c, splitter.getFirst(), x, y, w1 - seam / 2F, h, tall, clock, alpha, accent);
                schematic(c, splitter.getSecond(), x + w1 + seam / 2F, y, w - w1 - seam / 2F, h, tall, clock, alpha, accent);
            }

            return;
        }

        String id = node instanceof PanelNode panel ? panel.getPanelId() : node instanceof StackNode stack ? stack.getActivePanelId() : "";

        rect(c, x, y, w, h, 0xFF26252F, alpha);

        /* A stack of panels has its tabs on top */
        if (node instanceof StackNode)
        {
            rect(c, x, y, Math.min(w, 26F), 5F, accent, alpha);
            rect(c, x + 28F, y, Math.min(Math.max(0F, w - 28F), 22F), 5F, 0xFF3A3948, alpha);
            y += 6F;
            h -= 6F;
        }

        switch (id == null ? "" : id)
        {
            case "preview" ->
            {
                /* The monitor: a frame, a play mark and a scrub bar */
                float fw = tall ? Math.min(w - 8F, (h - 14F) * 9F / 16F) : w - 8F;
                float fh = tall ? fw * 16F / 9F : h - 14F;
                float fx = x + (w - fw) / 2F;
                float fy = y + 4F;

                rect(c, fx, fy, fw, fh, 0xFF1B3260, alpha);
                rect(c, fx + fw / 2F - 3F, fy + fh / 2F - 4F, 2F, 8F, 0xFFFFFFFF, alpha);
                rect(c, fx + fw / 2F - 1F, fy + fh / 2F - 3F, 2F, 6F, 0xFFFFFFFF, alpha);
                rect(c, fx + fw / 2F + 1F, fy + fh / 2F - 1.5F, 2F, 3F, 0xFFFFFFFF, alpha);
                rect(c, x + 4F, y + h - 7F, w - 8F, 2F, 0x40FFFFFF, alpha);
                rect(c, x + 4F, y + h - 7F, (w - 8F) * (0.5F + 0.5F * (float) Math.sin(clock * 0.7F)), 2F, accent, alpha);
            }
            case "main" ->
            {
                /* The timeline: tracks with clips on them and the playhead */
                int tracks = Math.max(1, Math.min(4, (int) (h / 9F)));
                float trackH = (h - 6F) / tracks;

                for (int t = 0; t < tracks; t++)
                {
                    float ty = y + 3F + t * trackH;

                    rect(c, x + 3F, ty, w - 6F, trackH - 2F, 0xFF1E1D26, alpha);

                    for (int k = 0; k < 3; k++)
                    {
                        float start = x + 4F + ((t * 0.21F + k * 0.33F) % 1F) * (w - 10F);
                        float length = Math.min((w - 10F) * 0.18F, x + w - 6F - start);

                        rect(c, start, ty + 1F, length, trackH - 4F, PALETTE[(t + k) % PALETTE.length] & 0xC0FFFFFF, alpha);
                    }
                }

                rect(c, x + 3F + (w - 6F) * ((clock * 0.12F) % 1F), y + 1F, 1F, h - 2F, 0xFFFF5050, alpha);
            }
            case "editArea", "replayProps" ->
            {
                /* Properties: a label and a value, row under row */
                for (float ry = y + 4F; ry + 4F < y + h; ry += 8F)
                {
                    rect(c, x + 4F, ry, (w - 8F) * 0.35F, 3F, 0x50FFFFFF, alpha);
                    rect(c, x + 6F + (w - 8F) * 0.4F, ry, (w - 8F) * 0.55F, 3F, 0x30FFFFFF, alpha);
                }
            }
            default ->
            {
                /* A list: an item a row, each with its swatch */
                int n = 0;

                for (float ry = y + 4F; ry + 5F < y + h; ry += 8F)
                {
                    rect(c, x + 4F, ry, 5F, 5F, PALETTE[n++ % PALETTE.length], alpha);
                    rect(c, x + 12F, ry + 1F, Math.max(0F, w - 18F), 3F, 0x40FFFFFF, alpha);
                }
            }
        }
    }
}
