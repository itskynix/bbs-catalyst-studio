package mchorse.bbs_mod.ui.film.export;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.export.HardwareEncoder;
import mchorse.bbs_mod.camera.export.VideoExportProfile;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.utils.FontRenderer;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;

/**
 * DaVinci Resolve "Deliver" style Live Render Monitor HUD.
 * Renders at the bottom of the screen during export, showing real-time progress,
 * SMPTE timecode, rendering speed (FPS), ETA, and active hardware encoder badge.
 */
public class UIRenderMonitorHud extends UIElement
{
    private String movieName = "Video";
    private VideoExportProfile profile;
    private int totalFrames = 1;
    private int currentFrame = 0;
    private int currentTick = 0;
    private int totalTicks = 1;
    private double fps = 60;

    private long startTimeMs = 0;
    private double renderFps = 0;
    private long lastFrameTimeMs = 0;
    private int lastFrameCount = 0;

    private final UIButton abortButton;
    private Runnable onAbort;

    private final Area barArea = new Area();

    public UIRenderMonitorHud()
    {
        super();

        this.abortButton = new UIButton(IKey.raw("Abort (ESC)"), (b) ->
        {
            if (this.onAbort != null)
            {
                this.onAbort.run();
            }
        });

        this.abortButton.h(22);
        this.add(this.abortButton);
    }

    public void start(String movieName, VideoExportProfile profile, int totalTicks, int totalFrames, double fps, Runnable onAbort)
    {
        this.movieName = movieName != null ? movieName : "Video";
        this.profile = profile;
        this.totalTicks = Math.max(1, totalTicks);
        this.totalFrames = Math.max(1, totalFrames);
        this.fps = fps > 0 ? fps : 60;
        this.currentFrame = 0;
        this.currentTick = 0;
        this.startTimeMs = System.currentTimeMillis();
        this.lastFrameTimeMs = this.startTimeMs;
        this.lastFrameCount = 0;
        this.renderFps = 0;
        this.onAbort = onAbort;
    }

    public void updateProgress(int currentFrame, int currentTick)
    {
        this.currentFrame = currentFrame;
        this.currentTick = currentTick;

        long now = System.currentTimeMillis();
        long elapsedSinceLast = now - this.lastFrameTimeMs;

        if (elapsedSinceLast >= 500)
        {
            int framesDelta = currentFrame - this.lastFrameCount;
            this.renderFps = (framesDelta * 1000.0) / (double) elapsedSinceLast;
            this.lastFrameTimeMs = now;
            this.lastFrameCount = currentFrame;
        }
    }

    @Override
    public void render(UIContext context)
    {
        FontRenderer font = context.batcher.getFont();
        int screenW = this.area.w;
        int screenH = this.area.h;

        if (screenW <= 0 || screenH <= 0)
        {
            if (context.menu != null && context.menu.width > 0 && context.menu.height > 0)
            {
                screenW = context.menu.width;
                screenH = context.menu.height;
            }
            else
            {
                screenW = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
                screenH = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledHeight();
            }
        }

        // HUD dimensions (bottom bar)
        int barH = 50;
        int barY = screenH - barH;
        this.barArea.set(0, barY, screenW, barH);

        // Position the abort button inside the bottom bar on the far right
        int abortW = 90;
        this.abortButton.w(abortW).xy(screenW - abortW - 12, barY + (barH - 22) / 2);

        // 1. Frosted dark bar background
        context.batcher.box(0, barY, screenW, screenH, Colors.A90 | 0x111317);

        // 2. Top hairline divider
        int primary = BBSSettings.primaryColor.get();
        context.batcher.box(0, barY, screenW, barY + 1, Colors.A50 | primary);

        // 3. Progress calculation
        float progress = Math.min(1F, (float) this.currentFrame / (float) this.totalFrames);
        int progressW = (int) (screenW * progress);

        // 4. Progress bar fill
        context.batcher.box(0, barY + 1, progressW, barY + 4, Colors.A100 | primary);
        context.batcher.box(progressW, barY + 1, screenW, barY + 4, Colors.A25 | 0x22252A);

        // 5. Left Section: Icon, Job Name, Hardware Badge
        int leftX = 14;
        int contentY = barY + 12;

        context.batcher.icon(Icons.FILM, Colors.ACTIVE, leftX, contentY + 2);
        leftX += 20;

        String title = (this.currentFrame > 0 ? "RENDERING: " : "WARMING UP: ") + this.movieName;
        context.batcher.textShadow(title, leftX, contentY + 5, Colors.WHITE);
        leftX += font.getWidth(title) + 10;

        // Hardware Encoder badge
        String hwName;
        if (this.profile != null)
        {
            HardwareEncoder resolved = this.profile.getHardwareEncoder().resolve();
            hwName = resolved.getLabel().get() + " • " + this.profile.getCodec().getId().toUpperCase();
        }
        else
        {
            hwName = HardwareEncoder.detect().getLabel().get();
        }

        int badgeW = font.getWidth(hwName) + 10;
        int badgeH = 14;
        int badgeY = contentY + 3;

        context.batcher.box(leftX, badgeY, leftX + badgeW, badgeY + badgeH, Colors.A50 | 0x203545);
        context.batcher.outline(leftX, badgeY, leftX + badgeW, badgeY + badgeH, Colors.A75 | 0x3882B5);
        context.batcher.text(hwName, leftX + 5, badgeY + 3, Colors.WHITE);

        // 6. Middle Section: Percentage, Frame/Tick Counter, SMPTE Timecode
        int midX = screenW / 2 - 120;

        String pctText = String.format("%.1f%%", progress * 100F);
        context.batcher.textShadow(pctText, midX, contentY + 1, Colors.ACTIVE);

        String smpte = this.calculateSMPTE(this.currentFrame, this.fps);
        String counters = String.format("Frame: %d/%d  |  Tick: %d/%d  |  TC: %s",
            this.currentFrame, this.totalFrames,
            this.currentTick, this.totalTicks,
            smpte
        );
        context.batcher.text(counters, midX, contentY + 16, Colors.LIGHTEST_GRAY);

        // 7. Right Stats Section: Render Speed & ETA
        long elapsedSec = (System.currentTimeMillis() - this.startTimeMs) / 1000;
        long remainingFrames = Math.max(0, this.totalFrames - this.currentFrame);
        long etaSec = this.renderFps > 0 ? (long) (remainingFrames / this.renderFps) : 0;

        String speedText = String.format("Speed: %.1f fps (%.2fx)", this.renderFps, this.renderFps / Math.max(1.0, this.fps));
        String etaText = String.format("ETA: %02d:%02d  |  Elapsed: %02d:%02d",
            etaSec / 60, etaSec % 60,
            elapsedSec / 60, elapsedSec % 60
        );

        int rightX = screenW - abortW - 25 - font.getWidth(speedText);
        int etaW = font.getWidth(etaText);
        int statsX = Math.min(rightX, screenW - abortW - 25 - etaW);

        context.batcher.textShadow(speedText, statsX, contentY + 1, Colors.WHITE);
        context.batcher.text(etaText, statsX, contentY + 16, Colors.LIGHTEST_GRAY);

        super.render(context);
    }

    /**
     * Calculates SMPTE standard timecode format (HH:MM:SS:FF) from frame count and framerate.
     */
    private String calculateSMPTE(int frame, double fps)
    {
        double safeFps = fps > 0 ? fps : 60;
        int totalSeconds = (int) (frame / safeFps);
        int frameRem = (int) (frame % safeFps);
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;

        return String.format("%02d:%02d:%02d:%02d", hours, minutes, seconds, frameRem);
    }
}
