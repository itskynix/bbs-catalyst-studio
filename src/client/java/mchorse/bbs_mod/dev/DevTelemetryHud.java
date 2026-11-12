package mchorse.bbs_mod.dev;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.server.MinecraftServer;

import java.util.Locale;

/**
 * A small, Valorant-style telemetry overlay in the top right corner: per metric a translucent dark
 * box with the live number on top and a line graph of the last frames under it. Each metric is
 * off, text only, graph only or both, set on the developer screen. Draws nothing unless the
 * developer gate is open.
 */
public final class DevTelemetryHud
{
    private static final int SAMPLES = 90;
    private static final int GRAPH_W = SAMPLES;
    private static final int GRAPH_H = 20;
    private static final int PAD = 3;
    private static final int BOX_BG = 0x80000000;
    private static final int BORDER = 0x80ffffff;
    private static final int LINE = 0xff6fb7ff;
    private static final int LINE_BAD = 0xffff6a5a;

    private static final float[][] series = new float[DevConfig.METRICS][SAMPLES];
    private static int head;
    private static int filled;

    private static long lastFrameNanos;
    private static long lastTickNanos;
    private static double tickIntervalMs = 50D;
    private static double lastCursor = Double.NaN;
    private static double cursorDelta;
    private static boolean registered;

    private DevTelemetryHud()
    {}

    public static void init()
    {
        if (registered)
        {
            return;
        }

        registered = true;

        /* Screens (the film editor, the hubs) cover the HUD: they get the overlay drawn after them */
        net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.AFTER_INIT.register((client, screen, w, h) ->
            net.fabricmc.fabric.api.client.screen.v1.ScreenEvents.afterRender(screen).register((s, context, mouseX, mouseY, delta) -> DevOverlay.render(context)));

        ClientTickEvents.END_CLIENT_TICK.register((client) ->
        {
            long now = System.nanoTime();

            if (lastTickNanos != 0L)
            {
                double interval = (now - lastTickNanos) / 1_000_000D;

                tickIntervalMs += (interval - tickIntervalMs) * 0.2D;
            }

            lastTickNanos = now;
        });
    }

    /** The vanilla F3 screen fills the same corners with its own text */
    private static boolean debugScreenOpen()
    {
        try
        {
            return MinecraftClient.getInstance().inGameHud.getDebugHud().shouldShowDebugHud();
        }
        catch (Throwable e)
        {
            return false;
        }
    }

    private static boolean anyEnabled()
    {
        for (int i = 0; i < DevConfig.METRICS; i++)
        {
            if (DevConfig.getMode(i) != DevConfig.OFF)
            {
                return true;
            }
        }

        return false;
    }

    public static void render(DrawContext context)
    {
        if (!DevAccessGuard.hasAccess() || !anyEnabled() || debugScreenOpen())
        {
            lastFrameNanos = 0L;

            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.nanoTime();
        double frameMs = lastFrameNanos == 0L ? 16.7D : (now - lastFrameNanos) / 1_000_000D;

        lastFrameNanos = now;

        double fps = 1000D / Math.max(frameMs, 0.01D);
        double msPt = tickIntervalMs;
        double tps;

        MinecraftServer server = mc.getServer();

        if (server != null)
        {
            msPt = server.getAverageTickTime();
            tps = Math.min(20D, 1000D / Math.max(msPt, 50D));
        }
        else
        {
            tps = Math.min(20D, 1000D / Math.max(tickIntervalMs, 50D));
        }

        double cursor = readCursor();
        double delta = Double.NaN;

        if (!Double.isNaN(cursor) && !Double.isNaN(lastCursor))
        {
            delta = cursor - lastCursor;
        }

        lastCursor = cursor;
        cursorDelta = Double.isNaN(delta) ? 0D : delta;

        Runtime runtime = Runtime.getRuntime();
        double usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1048576D;
        double maxMb = runtime.maxMemory() / 1048576D;

        series[DevConfig.METRIC_FPS][head] = (float) frameMs;
        series[DevConfig.METRIC_TPS][head] = (float) tps;
        series[DevConfig.METRIC_PLAYHEAD][head] = (float) Math.abs(cursorDelta);
        series[DevConfig.METRIC_MEMORY][head] = (float) usedMb;
        head = (head + 1) % SAMPLES;
        filled = Math.min(filled + 1, SAMPLES);

        TextRenderer font = mc.textRenderer;
        int x = context.getScaledWindowWidth() - 4;
        int y = 6;

        y = panel(context, font, DevConfig.METRIC_FPS, x, y, String.format(Locale.ROOT, "Client FPS: %d (%.1f ms)", Math.round(fps), frameMs), false, 0F, 50F);
        y = panel(context, font, DevConfig.METRIC_TPS, x, y, String.format(Locale.ROOT, "TPS: %.1f (%.1f msPT)", tps, msPt), true, 0F, 20F);
        y = panel(context, font, DevConfig.METRIC_PLAYHEAD, x, y, Double.isNaN(cursor)
            ? "Playhead: -"
            : String.format(Locale.ROOT, "Tick: %.2f (%+.2f)", cursor, cursorDelta), false, 0F, 2F);
        panel(context, font, DevConfig.METRIC_MEMORY, x, y, String.format(Locale.ROOT, "Heap: %.1f/%.1f GB%s", usedMb / 1024D, maxMb / 1024D, fboText(mc)), false, 0F, (float) maxMb);
    }

    private static String fboText(MinecraftClient mc)
    {
        Framebuffer fb = mc.getFramebuffer();

        if (fb == null)
        {
            return "";
        }

        double mb = fb.textureWidth * (double) fb.textureHeight * 8D / 1048576D;

        return String.format(Locale.ROOT, " (FBO ~%.0f MB)", mb);
    }

    private static double readCursor()
    {
        try
        {
            UIDashboard dashboard = BBSModClient.getDashboardIfCreated();

            if (dashboard != null && dashboard.getPanels().panel instanceof UIFilmPanel film && film.getRunner() != null)
            {
                return film.getRunner().getCursor(0F);
            }
        }
        catch (Throwable ignored)
        {}

        return Double.NaN;
    }

    /**
     * Draws one metric's box at (x, y) and returns the y of the next one. {@code highIsGood} marks
     * the metrics whose high values are good (TPS), so the line turns red when they fall.
     */
    private static int panel(DrawContext context, TextRenderer font, int metric, int x, int y, String text, boolean highIsGood, float floor, float ceiling)
    {
        int mode = DevConfig.getMode(metric);

        if (mode == DevConfig.OFF)
        {
            return y;
        }

        boolean showText = mode == DevConfig.TEXT || mode == DevConfig.BOTH;
        boolean showGraph = mode == DevConfig.GRAPH || mode == DevConfig.BOTH;
        int inner = showText ? Math.max(font.getWidth(text), showGraph ? GRAPH_W : 0) : GRAPH_W;
        int w = inner + PAD * 2;
        int h = PAD * 2 + (showText ? font.fontHeight : 0) + (showText && showGraph ? 2 : 0) + (showGraph ? GRAPH_H : 0);
        int left = x - w;

        context.fill(left, y, left + w, y + h, BOX_BG);
        context.drawBorder(left, y, w, h, BORDER);

        int cy = y + PAD;

        if (showText)
        {
            context.drawText(font, text, left + PAD, cy, 0xffffffff, true);
            cy += font.fontHeight + 2;
        }

        if (showGraph)
        {
            graph(context, metric, left + w - PAD - GRAPH_W, cy, highIsGood, floor, ceiling);
        }

        return y + h + 4;
    }

    private static void graph(DrawContext context, int metric, int x, int y, boolean highIsGood, float floor, float ceiling)
    {
        float[] data = series[metric];
        float max = ceiling;

        /* Frame time, playhead step and heap scale to what is on the graph; TPS keeps its 0..20 */
        if (metric != DevConfig.METRIC_TPS)
        {
            max = floor + 1F;

            for (int i = 0; i < filled; i++)
            {
                max = Math.max(max, data[i]);
            }

            max = metric == DevConfig.METRIC_MEMORY ? Math.max(max * 1.1F, 64F) : max * 1.1F;
        }

        context.fill(x, y, x + GRAPH_W, y + GRAPH_H, 0x30000000);

        int prevY = -1;

        for (int i = 0; i < filled; i++)
        {
            int index = (head - filled + i + SAMPLES * 2) % SAMPLES;
            float value = data[index];
            float t = Math.max(0F, Math.min(1F, (value - floor) / Math.max(max - floor, 0.0001F)));
            int py = y + GRAPH_H - 1 - Math.round(t * (GRAPH_H - 1));
            boolean bad = highIsGood ? value < ceiling * 0.9F : (metric == DevConfig.METRIC_FPS && value > 33.4F);
            int color = bad ? LINE_BAD : LINE;
            int px = x + (GRAPH_W - filled) + i;

            int from = prevY < 0 ? py : Math.min(prevY, py);
            int to = prevY < 0 ? py : Math.max(prevY, py);

            context.fill(px, from, px + 1, to + 1, color);

            prevY = py;
        }
    }
}
