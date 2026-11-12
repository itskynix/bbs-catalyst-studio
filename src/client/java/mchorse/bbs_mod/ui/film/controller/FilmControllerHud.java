package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.api.client.editor.TrackCategory;

import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.film.FilmTarget;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.PreviewHud;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import net.minecraft.client.util.math.MatrixStack;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeEditor;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.StringUtils;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;

/**
 * The editor's overlay on the film preview. Split out of {@link UIFilmController} because it
 * only READS — every number on screen belongs to something else (the mouse control, the take,
 * the selection), and drawing them was the longest single stretch of the controller.
 */
public class FilmControllerHud
{
    /** The side of the selected replay's form thumbnail. */
    private static final int FORM_SIZE = 40;

    private final UIFilmController controller;

    /** Lays the selection card out against the fitted frame while the preview is zoomed. */
    private final PreviewHud cardHud = new PreviewHud();

    public FilmControllerHud(UIFilmController controller)
    {
        this.controller = controller;
    }

    /**
     * Everything the editor draws over the preview: the stick guide while an actor is driven by
     * hand, the recording tally, the loop and flight-speed marks, the selected replay's name and
     * form thumbnail — then the gizmo, the pick highlight and the orbit widget on top.
     *
     * <p>Which corner each of them lands in is the point of the split: the top left is what the
     * editor is <em>doing</em> (recording, looping, flying), the top right is what is
     * <em>selected</em> (the replay, its form, what the gizmo is on). Everything is stacked
     * through {@link PreviewHud}, so no line moves because another one happens to be showing.
     */
    public void render(UIContext context, PreviewHud hud, Area navBlock)
    {
        Area area = hud.getFrame();
        int mode = this.controller.mouse.getMode();

        /* Render recording overlay */
        if (this.controller.isRecording())
        {
            String tally = this.controller.getRecordingCountdown() <= 0
                ? UIKeys.FILM_CONTROLLER_TICKS.format(this.controller.getTick()).get()
                : String.valueOf(this.controller.getRecordingCountdown() / 20F);
            int iconW = Icons.SPHERE.w;
            int textY = context.batcher.getFont().getHeight() / 2;
            Area block = hud.push(PreviewHud.Anchor.TOP_LEFT, iconW + PreviewHud.GAP + context.batcher.getFont().getWidth(tally), Icons.SPHERE.h);

            hud.include(PreviewHud.Anchor.TOP_LEFT, block, (c) ->
            {
                c.batcher.icon(Icons.SPHERE, Colors.RED | Colors.A100, block.x, block.y);
                c.batcher.text(tally, block.x + iconW + PreviewHud.GAP, block.my() - textY, Colors.WHITE, true);
            });
        }

        if (BBSSettings.editorLoop.get())
        {
            hud.icon(context, PreviewHud.Anchor.TOP_LEFT, Icons.REFRESH, Colors.WHITE | Colors.A100);
        }

        if (this.controller.panel.isFlying())
        {
            String label = UIKeys.FILM_CONTROLLER_SPEED.format(this.controller.panel.dashboard.orbit.speed.getValue()).get();

            hud.text(context, PreviewHud.Anchor.TOP_LEFT, label, Colors.WHITE);
        }

        Replay replay = this.controller.panel.replayEditor.getReplay();
        UIFilmPreview preview = this.controller.panel.preview;
        Area image = preview.getViewport();
        boolean zoomed = image.x != area.x || image.y != area.y || image.w != area.w || image.h != area.h;

        if (replay != null && BBSSettings.editorPreviewSelectionHud.get())
        {
            if (zoomed)
            {
                this.renderSelectionZoomed(context, area, image, preview.area, replay);
            }
            else
            {
                this.renderSelection(context, hud, area, replay, null);
            }
        }

        if (this.controller.getControlled() != null && mode > 0)
        {
            /* Render helpful guides for sticks and triggers controls */
            String label = UIKeys.FILM_GROUPS_LEFT_STICK.get();

            if (mode == 2)
            {
                label = UIKeys.FILM_GROUPS_RIGHT_STICK.get();
            }
            else if (mode == 3)
            {
                label = UIKeys.FILM_GROUPS_TRIGGERS.get();
            }
            else if (mode == 4)
            {
                label = UIKeys.FILM_GROUPS_EXTRA_1.get();
            }
            else if (mode == 5)
            {
                label = UIKeys.FILM_GROUPS_EXTRA_2.get();
            }

            hud.label(context, PreviewHud.Anchor.BOTTOM_LEFT, label, Colors.WHITE, BBSSettings.primaryColor(Colors.A100));

            int ww = (int) (Math.min(area.w, area.h) * 0.75F);
            int hh = ww;
            int x = area.x + (area.w - ww) / 2;
            int y = area.y + (area.h - hh) / 2;
            int color = Colors.setA(Colors.WHITE, 0.5F);

            context.batcher.outline(x, y, x + ww, y + hh, color);

            int bx = area.x + area.w / 2 + (int) (this.controller.mouse.getStick().y * ww / 2);
            int by = area.y + area.h / 2 + (int) (this.controller.mouse.getStick().x * hh / 2);

            context.batcher.box(bx - 4, by - 4, bx + 4, by + 4, color);
        }

        if (BBSSettings.profilerOverlay.get())
        {
            this.renderProfiler(context, hud);
        }

        /* The visual gizmo draws here, before the picking preview, so the bone /
         * sphere hover highlights composite on top of it. It moved out of the
         * world pass into the UI pipeline so its translucent parts blend
         * correctly (see Gizmo#renderInterface). */
        if (this.controller.canShowGizmo())
        {
            BBSProfiler.begin(BBSProfiler.Timer.GIZMO);
            this.controller.gizmo().renderGizmo(context);
            BBSProfiler.end(BBSProfiler.Timer.GIZMO);
        }

        BBSProfiler.begin(BBSProfiler.Timer.PICK_PREVIEW);
        /* The highlight is the pick buffer laid over the picture: over the zoomed and panned one,
         * which is also what the pick read the cursor against, cut to the preview */
        context.batcher.clip(preview.area, context);
        this.controller.picker.renderPreview(context, image);
        context.batcher.unclip(context);
        BBSProfiler.end(BBSProfiler.Timer.PICK_PREVIEW);

        this.controller.orbitGizmo.render(context, navBlock);

        this.controller.orbit.handleOrbiting(context);
    }

    /**
     * What the gizmo is on, for the line under the actor's name, or {@code null} when no gizmo
     * is shown. The replay's own placement is named by its timeline category — that is where
     * its keys land, and no single track owns it; everything else is named by
     * {@link UIKeyframeEditor#getTargetLabel}, which knows whether the useful name is the
     * track's or a bone picked inside it.
     */
    /**
     * The per-frame call counters of the hot paths, bottom left. Numbers come from the
     * previous finished frame's snapshot, so they don't flicker mid-frame.
     */
    private void renderProfiler(UIContext context, PreviewHud hud)
    {
        /* Milliseconds first: they rank the subsystems. Counts below verify the caches. */
        for (BBSProfiler.Timer timer : BBSProfiler.Timer.VALUES)
        {
            String label = String.format("%s: %.2f ms", timer.name().toLowerCase(), BBSProfiler.getMs(timer));

            hud.text(context, PreviewHud.Anchor.BOTTOM_LEFT, label, Colors.WHITE);
        }

        for (BBSProfiler.Section section : BBSProfiler.Section.VALUES)
        {
            hud.text(context, PreviewHud.Anchor.BOTTOM_LEFT, section.name().toLowerCase() + ": " + BBSProfiler.get(section), Colors.LIGHTEST_GRAY);
        }
    }

    /**
     * The selected replay's name, what the gizmo is on, and its form's thumbnail, in the top right
     * of {@code area}. With {@code thumbOut} the thumbnail is not drawn but handed back, for a caller
     * that draws the rest under a transform the 3D thumbnail cannot follow.
     */
    private void renderSelection(UIContext context, PreviewHud hud, Area area, Replay replay, Area[] thumbOut)
    {
        Form form = replay.form.get();

        /* The thumbnail stands to the right of the whole stack, so the lines are laid out
         * in what is left of the corner rather than running under it. */
        if (form != null)
        {
            hud.inset(PreviewHud.Anchor.TOP_RIGHT, FORM_SIZE + PreviewHud.GAP);
        }

        Area block = hud.text(context, PreviewHud.Anchor.TOP_RIGHT, replay.getName(), Colors.WHITE);
        Area last = block;

        /* What the gizmo is on, under the actor's name — the gizmo's position in the scene
         * is the only other clue, and a root gizmo standing at a bone's height is not one.
         * Only while a gizmo is actually shown: with nothing selected the line would be
         * answering a question nobody asked. */
        String targetLabel = this.editTargetLabel();

        if (targetLabel != null)
        {
            last = hud.text(context, PreviewHud.Anchor.TOP_RIGHT, targetLabel, Colors.LIGHTEST_GRAY);
        }

        if (form != null)
        {
            /* Centred on the lines it belongs to, but never pushed out through the top. */
            Area thumbnail = new Area();

            thumbnail.setSize(FORM_SIZE, FORM_SIZE);
            thumbnail.setPos(
                area.ex() - PreviewHud.MARGIN - FORM_SIZE,
                Math.max(area.y, (block.y + last.ey()) / 2 - FORM_SIZE / 2)
            );

            hud.backdrop(thumbnail);

            if (thumbOut != null)
            {
                thumbOut[0] = thumbnail;

                return;
            }

            hud.include(PreviewHud.Anchor.TOP_RIGHT, thumbnail, (c) ->
            {
                c.batcher.clip(thumbnail.x, thumbnail.y, thumbnail.w, thumbnail.h, c);
                /* renderPreview, not renderUI: the form's own name card would repeat the
                 * replay's name standing right beside it. */
                FormUtilsClient.renderPreview(form, c, thumbnail.x, thumbnail.y, thumbnail.ex(), thumbnail.ey());
                c.batcher.unclip(c);
            });
        }
    }

    /**
     * The selection card while the preview is zoomed or panned: it belongs to the picture, so it is
     * laid out against the fitted frame as usual and then drawn through the same scale and shift
     * that put the picture where it is - text, wash and thumbnail alike - cut to the preview.
     */
    private void renderSelectionZoomed(UIContext context, Area frame, Area image, Area preview, Replay replay)
    {
        this.cardHud.begin(frame);

        Area[] thumb = new Area[1];

        this.renderSelection(context, this.cardHud, frame, replay, thumb);

        float scale = image.w / (float) Math.max(1, frame.w);
        float dx = image.x - frame.x * scale;
        float dy = image.y - frame.y * scale;
        MatrixStack matrices = context.batcher.getContext().getMatrices();

        context.batcher.clip(preview, context);
        context.batcher.flush();
        matrices.push();
        matrices.translate(dx, dy, 0F);
        matrices.scale(scale, scale, 1F);

        try
        {
            this.cardHud.flush(context);
            context.batcher.flush();
        }
        finally
        {
            matrices.pop();
        }

        Form form = replay.form.get();

        if (thumb[0] != null && form != null)
        {
            /* The 3D thumbnail is drawn into a rectangle of the screen, so it gets the mapped one */
            int x1 = Math.round(dx + thumb[0].x * scale);
            int y1 = Math.round(dy + thumb[0].y * scale);
            int x2 = Math.round(dx + thumb[0].ex() * scale);
            int y2 = Math.round(dy + thumb[0].ey() * scale);

            context.batcher.clip(x1, y1, x2 - x1, y2 - y1, context);
            FormUtilsClient.renderPreview(form, context, x1, y1, x2, y2);
            context.batcher.unclip(context);
        }

        context.batcher.unclip(context);
    }

    private String editTargetLabel()
    {
        if (!this.controller.canShowGizmo())
        {
            return null;
        }

        FilmTarget target = this.controller.getEditTarget();

        if (target.is(FilmTarget.Kind.ROOT))
        {
            return TrackCategory.REPLAY.label.get();
        }

        if (target.isNone())
        {
            return null;
        }

        UIKeyframeEditor editor = this.controller.panel.replayEditor.keyframeEditor;
        String label = editor == null ? null : editor.getTargetLabel();

        /* Nothing to ask (the track was rebuilt under us) — the path is still better than nothing. */
        return label != null ? label : StringUtils.fileName(target.bone());
    }
}
