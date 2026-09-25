package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.MultiTrackRecorder;
import mchorse.bbs_mod.film.replays.RecordedTrack;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.MinecraftClient;

/**
 * Real-time HUD overlay displayed during multi-track replay recording in the world.
 * Renders status indicator, current active track, actor name, switch hotkeys, and tick counter.
 */
public class UIMultiTrackHud
{
    public static void render(Batcher2D batcher2D, MultiTrackRecorder recorder, float tickDelta)
    {
        if (recorder == null || batcher2D == null)
        {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getWindow() == null)
        {
            return;
        }

        int sw = mc.getWindow().getScaledWidth();
        int tick = recorder.getTick();
        int activeIdx = recorder.getActiveTrackIndex();
        int totalTracks = recorder.getTracks().size();
        RecordedTrack activeTrack = recorder.getActiveTrack();
        String actorName = activeTrack != null ? activeTrack.getName() : "Actor";

        // Construct labels
        String recTitle = "REC - Track " + (activeIdx + 1) + "/" + totalTracks + " (" + actorName + ")";
        String hintInfo = "[<- / -> Switch Track] [Tick: " + Math.max(0, tick) + "]";

        int fontH = batcher2D.getFont().getHeight();
        int w1 = batcher2D.getFont().getWidth(recTitle);
        int w2 = batcher2D.getFont().getWidth(hintInfo);
        int contentW = Math.max(w1 + 22, w2);
        int boxW = contentW + 24;
        int boxH = fontH * 2 + 16;

        int boxX = (sw - boxW) / 2;
        int boxY = 8;

        // Background styling: sleek dark panel with subtle border
        batcher2D.box(boxX, boxY, boxX + boxW, boxY + boxH, Colors.A75 | 0x121418);
        batcher2D.outline(boxX, boxY, boxX + boxW, boxY + boxH, Colors.A50 | 0x4a4f5c);

        // Blinking red recording dot
        boolean blink = (System.currentTimeMillis() / 450) % 2 == 0;
        int dotColor = blink ? (Colors.RED | Colors.A100) : (0x770000 | Colors.A100);
        batcher2D.icon(Icons.SPHERE, dotColor, boxX + 6, boxY + 5);

        // Header line: REC - Track 2/3 (Actor: Bob)
        batcher2D.textShadow(recTitle, boxX + 24, boxY + 6, 0xffffff);

        // Subline: [<- / -> Switch Track] [Tick: 340]
        batcher2D.textShadow(hintInfo, boxX + 24, boxY + fontH + 9, 0xaaaaaa);
    }
}
