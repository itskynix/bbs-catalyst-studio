package mchorse.bbs_mod.ui.film.viewport;

import com.mojang.blaze3d.platform.GlStateManager;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.camera.Camera;
import mchorse.bbs_mod.camera.clips.CameraClip;
import mchorse.bbs_mod.camera.controller.RunnerCameraController;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.film.UIFilmPreview;
import mchorse.bbs_mod.ui.film.controller.UIFilmController;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.UIScreen;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.ui.utils.context.DisabledContextAction;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.colors.Colors;
import net.minecraft.client.gl.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.List;

/**
 * The film preview cut into one to four panes, each with a camera of its own.
 *
 * <p>The world is drawn once per frame, from one camera. With several panes it is drawn from a
 * different pane's camera on different frames: the focused pane on two frames of three, the others
 * taking the third in turn, and each frame is copied into the buffer of the pane it was drawn for.
 * Nothing about the single pane changes: while the layout is {@link ViewportLayout#SINGLE}, or one pane
 * is maximized, none of this runs and the preview shows the world's frame as it always has.</p>
 *
 * <p>The focused pane is the live one: the controller's camera mode and the orbit hold its state, so
 * every input, flight and gizmo works on it as before. The other panes' state is swapped in for the
 * frame drawn for them and swapped out again as that frame ends.</p>
 */
public final class ViewportSet
{
    public static final int MAX = 4;

    private static final ViewportPane[] PANES = {new ViewportPane(0), new ViewportPane(1), new ViewportPane(2), new ViewportPane(3)};

    /** Stands for the film's own camera while a video is written, whatever the panes show */
    private static final ViewportPane RECORD = new ViewportPane(-1);

    private static final long DOUBLE_CLICK_MS = 350L;

    private static ViewportLayout layout = ViewportLayout.SINGLE;
    private static int focused;
    private static int maximized = -1;
    private static boolean initialized;

    /* The frame schedule and the frame being drawn */
    private static int frame;
    private static int turn;
    private static ViewportPane slice;
    private static boolean sliceFocused;
    private static boolean sliceAuxiliary;
    private static int savedPov;
    private static boolean savedEnabled;
    private static mchorse.bbs_mod.ui.utils.camera.OrbitViewportController.OrbitState savedOrbit;
    private static ViewportPane lastSlice;

    /** The focused pane's camera as the world saw it; the world camera itself is whoever was drawn last */
    private static final Camera focusedCamera = new Camera();

    /** The top edge of the preview, which the first row of panes sits on */
    private static int top;

    private static ViewportPane clickedTitle;
    private static long clickedAt;

    private ViewportSet()
    {}

    /* State */

    public static ViewportLayout getLayout()
    {
        return layout;
    }

    public static ViewportPane getFocused()
    {
        return PANES[focused];
    }

    /** The camera of the focused pane as the world last drew it */
    public static Camera getFocusedCamera()
    {
        return focusedCamera;
    }

    private static boolean recording()
    {
        return BBSModClient.getVideoRecorder().isRecording();
    }

    /** The panes cut the preview up right now: more than one is showing and no video is being written */
    public static boolean isMulti()
    {
        return layout != ViewportLayout.SINGLE && maximized < 0 && !recording();
    }

    /** Whether the panes' titles are up, which a maximized pane keeps so that it can be put back */
    public static boolean showHeaders()
    {
        return layout != ViewportLayout.SINGLE && !recording();
    }

    private static int visibleCount()
    {
        return maximized >= 0 ? 1 : layout.count;
    }

    private static ViewportPane visible(int k)
    {
        return maximized >= 0 ? PANES[maximized] : PANES[k];
    }

    /** Where the focused pane is, inside the preview's own bounds */
    public static Area focusedRect(Area fallback)
    {
        Area rect = PANES[focused].rect;

        return isMulti() && rect.w > 0 && rect.h > 0 ? rect : fallback;
    }

    /** Whether the pane the world is being drawn for is the one the editor works on, or the frame is a plain one */
    public static boolean tracksFocusedView()
    {
        return slice == null || sliceFocused;
    }

    /** The post effects belong to the film's camera pane; helper panes are drawn plain and fast */
    public static boolean skipPostEffects()
    {
        return slice != null && sliceAuxiliary;
    }

    /* Layout and focus */

    /** A film editor is opened anew: it starts with one pane, and the panes of the last one are let go */
    public static void reset()
    {
        slice = null;
        lastSlice = null;
        layout = ViewportLayout.SINGLE;
        focused = 0;
        maximized = -1;
        initialized = false;

        for (ViewportPane pane : PANES)
        {
            pane.release();
        }
    }

    public static void setLayout(ViewportLayout next, UIFilmPreview preview)
    {
        if (next == layout)
        {
            return;
        }

        UIFilmController controller = preview.getFilmPanel().getController();

        if (layout == ViewportLayout.SINGLE)
        {
            /* The live view becomes pane 0, and the others start from where it stands */
            ViewportPane first = PANES[0];

            first.pov = controller.getPovMode();
            first.clip = null;
            first.orbit = controller.orbit.captureState();
            preview.exportView(first);

            if (!initialized)
            {
                int[] povs = {0, first.pov == UIFilmController.CAMERA_MODE_CAMERA ? UIFilmController.CAMERA_MODE_ORBIT : UIFilmController.CAMERA_MODE_CAMERA, UIFilmController.CAMERA_MODE_ORBIT, UIFilmController.CAMERA_MODE_FIRST_PERSON};

                for (int i = 1; i < MAX; i++)
                {
                    PANES[i].pov = povs[i];
                    PANES[i].orbit = first.orbit.copy();
                }

                initialized = true;
            }

            focused = 0;
        }

        layout = next;
        maximized = -1;

        if (next == ViewportLayout.SINGLE)
        {
            /* Whatever pane was focused is the preview's view now, as it stands */
            focused = 0;

            for (ViewportPane pane : PANES)
            {
                pane.release();
            }
        }
        else if (focused >= next.count)
        {
            focus(0, preview);
        }
    }

    /** Makes a pane the live one: the editor's camera mode, orbit and zoom become its own */
    public static void focus(int index, UIFilmPreview preview)
    {
        if (index == focused || index < 0 || index >= MAX)
        {
            return;
        }

        UIFilmPanel panel = preview.getFilmPanel();
        UIFilmController controller = panel.getController();
        ViewportPane old = PANES[focused];
        ViewportPane next = PANES[index];

        old.pov = controller.getPovMode();
        old.orbit = controller.orbit.captureState();

        if (old.pov != UIFilmController.CAMERA_MODE_CAMERA)
        {
            old.clip = null;
        }

        preview.exportView(old);

        focused = index;

        boolean enabled = controller.orbit.enabled;

        controller.applyTransientPov(next.pov);
        controller.orbit.enabled = panel.isFlying() ? enabled : controller.orbit.enabled;
        controller.orbit.restoreState(next.orbit);
        preview.importView(next);
    }

    public static void toggleMaximize(ViewportPane pane, UIFilmPreview preview)
    {
        if (maximized == pane.index)
        {
            maximized = -1;

            return;
        }

        focus(pane.index, preview);
        maximized = pane.index;
    }

    /** A pane's camera is picked: the live pane takes it as the editor's camera mode, the others keep it for their frames */
    public static void select(ViewportPane pane, ViewportCameraSource source, CameraClip clip, UIFilmPreview preview)
    {
        pane.pov = source.pov;
        pane.clip = source == ViewportCameraSource.SPECIFIC_CLIP ? clip : null;

        if (pane.index == focused)
        {
            preview.getFilmPanel().getController().setPov(source.pov);
        }
    }

    /* Layout of the panes on the screen */

    /** Where the panes go inside the preview, to be called whenever the preview is drawn or clicked */
    public static void place(Area bounds)
    {
        top = bounds.y;

        if (layout == ViewportLayout.SINGLE)
        {
            return;
        }

        if (maximized >= 0)
        {
            PANES[maximized].rect.copy(bounds);

            return;
        }

        Area[] rects = new Area[MAX];

        for (int i = 0; i < MAX; i++)
        {
            rects[i] = PANES[i].rect;
        }

        layout.place(bounds, rects);
    }

    public static ViewportPane paneAt(int x, int y)
    {
        for (int i = 0; i < visibleCount(); i++)
        {
            ViewportPane pane = visible(i);

            if (pane.rect.isInside(x, y))
            {
                return pane;
            }
        }

        return null;
    }

    public static ViewportPane visiblePane(int k)
    {
        return visible(k);
    }

    public static int getVisibleCount()
    {
        return layout == ViewportLayout.SINGLE ? 1 : visibleCount();
    }

    /* Titles */

    private static int povOf(ViewportPane pane, UIFilmPreview preview)
    {
        return pane.index == focused ? preview.getFilmPanel().getController().getPovMode() : pane.pov;
    }

    private static ViewportCameraSource sourceOf(ViewportPane pane, UIFilmPreview preview)
    {
        return ViewportCameraSource.of(povOf(pane, preview), pane.clip != null);
    }

    /** What a clip is called on the timeline: its title, or its kind when it has none */
    private static String baseName(UIFilmPanel panel, CameraClip clip)
    {
        String title = clip.title.get();

        if (!title.isEmpty())
        {
            return title;
        }

        return UIKeys.C_CLIP.get(panel.cameraEditor.clips.getFactory().getType(clip)).get();
    }

    /** The clip's name, with its layer and tick added when another clip has the same one, so that the two can be told apart */
    public static String clipName(UIFilmPanel panel, List<CameraClip> clips, CameraClip clip)
    {
        String name = baseName(panel, clip);
        int same = 0;

        for (CameraClip other : clips)
        {
            if (baseName(panel, other).equals(name))
            {
                same += 1;
            }
        }

        if (same > 1)
        {
            name = UIKeys.FILM_VIEWPORT_SOURCE_CLIP_DETAIL.format(name, clip.layer.get() + 1, clip.tick.get()).get();
        }

        return name;
    }

    private static String titleOf(ViewportPane pane, UIFilmPreview preview)
    {
        ViewportCameraSource source = sourceOf(pane, preview);

        if (source == ViewportCameraSource.SPECIFIC_CLIP && pane.clip != null)
        {
            UIFilmPanel panel = preview.getFilmPanel();

            return clipName(panel, cameraClips(panel), pane.clip);
        }

        return source.label.get();
    }

    /** Whether another showing pane already has this camera: the film's, an actor's eyes or the clip */
    private static boolean taken(ViewportPane self, ViewportCameraSource kind, CameraClip clip, UIFilmPreview preview)
    {
        if (kind == ViewportCameraSource.FREE_ORBIT)
        {
            return false;
        }

        for (int i = 0; i < visibleCount(); i++)
        {
            ViewportPane other = visible(i);

            if (other == self)
            {
                continue;
            }

            ViewportCameraSource has = sourceOf(other, preview);

            if (has == kind && (kind != ViewportCameraSource.SPECIFIC_CLIP || other.clip == clip))
            {
                return true;
            }
        }

        return false;
    }

    /** A pane falls back to its own free camera */
    private static void demote(ViewportPane pane, UIFilmPreview preview)
    {
        pane.pov = UIFilmController.CAMERA_MODE_ORBIT;
        pane.clip = null;

        if (pane.index == focused)
        {
            preview.getFilmPanel().getController().setPov(UIFilmController.CAMERA_MODE_ORBIT);
        }
    }

    /**
     * Two panes may not show the same camera. A clip that is gone from the film, or a camera that a
     * second showing pane has too, sends that pane to its free camera; the live pane keeps its own.
     */
    private static void validate(UIFilmPreview preview)
    {
        List<CameraClip> clips = cameraClips(preview.getFilmPanel());
        List<Object> claimed = new java.util.ArrayList<>();
        int n = visibleCount();

        for (int step = 0; step < n; step++)
        {
            ViewportPane pane = null;

            if (step == 0)
            {
                for (int i = 0; i < n; i++)
                {
                    if (visible(i).index == focused)
                    {
                        pane = visible(i);
                    }
                }

                if (pane == null)
                {
                    continue;
                }
            }
            else
            {
                int seen = 0;

                for (int i = 0; i < n; i++)
                {
                    if (visible(i).index != focused && ++seen == step)
                    {
                        pane = visible(i);
                    }
                }

                if (pane == null)
                {
                    continue;
                }
            }

            if (pane.clip != null)
            {
                boolean exists = false;

                for (CameraClip clip : clips)
                {
                    exists |= clip == pane.clip;
                }

                if (!exists)
                {
                    demote(pane, preview);
                }
            }

            ViewportCameraSource source = sourceOf(pane, preview);

            if (source == ViewportCameraSource.FREE_ORBIT)
            {
                continue;
            }

            Object key = source == ViewportCameraSource.SPECIFIC_CLIP ? pane.clip : source;
            boolean clash = false;

            for (Object other : claimed)
            {
                clash |= other == key;
            }

            if (clash)
            {
                demote(pane, preview);
            }
            else
            {
                claimed.add(key);
            }
        }
    }

    /**
     * Where the titles go. The top row's sit under the floating toolbar when that is in their way, all
     * of them at the same height; each is its label and the arrow of its camera menu.
     */
    public static void layoutHeaders(UIContext context, UIFilmPreview preview, Area toolbar)
    {
        validate(preview);

        int h = 14;
        int offset = 0;

        for (int i = 0; i < visibleCount(); i++)
        {
            ViewportPane pane = visible(i);
            String label = titleOf(pane, preview);
            int w = Math.min(context.batcher.getFont().getWidth(label) + 10, Math.max(0, pane.rect.w - 26));

            pane.title.set(pane.rect.x + 4, pane.rect.y + 4, w, h);

            if (pane.rect.y <= top + 1 && pane.title.x < toolbar.ex() + 4 && pane.title.x + w + 14 > toolbar.x - 4)
            {
                offset = Math.max(offset, toolbar.ey() + 4 - (pane.rect.y + 4));
            }
        }

        for (int i = 0; i < visibleCount(); i++)
        {
            ViewportPane pane = visible(i);

            if (pane.rect.y <= top + 1)
            {
                pane.title.y += offset;
            }

            pane.chevron.set(pane.title.ex(), pane.title.y, 14, h);
        }
    }

    /** The title in the pane's top left corner with the arrow of its camera menu, and the frame of the live pane */
    public static void renderHeader(UIContext context, ViewportPane pane, UIFilmPreview preview)
    {
        boolean live = pane.index == focused;
        Area rect = pane.rect;
        String label = titleOf(pane, preview);
        int h = pane.title.h;

        context.batcher.box(pane.title.x, pane.title.y, pane.chevron.ex(), pane.chevron.ey(), Colors.A75 | 0x0B0F19);

        int color = live ? Colors.WHITE : 0xFFB8BCC6;

        context.batcher.text(context.batcher.getFont().limitToWidth(label, Math.max(0, pane.title.w - 8)), pane.title.x + 5, pane.title.y + (h - context.batcher.getFont().getHeight()) / 2, color);
        context.batcher.icon(Icons.ARROW_DOWN, color, pane.chevron.mx() - 8, pane.chevron.my() - 8);

        if (pane.chevron.isInside(context))
        {
            context.batcher.box(pane.chevron.x, pane.chevron.y, pane.chevron.ex(), pane.chevron.ey(), Colors.A12);
        }

        if (live && layout != ViewportLayout.SINGLE)
        {
            /* A frame of one and a half pixels: the full colour and, inside it, half of it */
            int accent = BBSSettings.primaryColor(Colors.A100);

            context.batcher.outline(rect.x, rect.y, rect.ex(), rect.ey(), accent);
            context.batcher.outline(rect.x + 1, rect.y + 1, rect.ex() - 1, rect.ey() - 1, Colors.setA(accent, 0.5F));
        }
    }

    /**
     * A click on a pane. A click on its title twice in a row maximizes it, or puts it back; the arrow opens its
     * camera menu; anywhere else it only becomes the live pane, and the click is spent on that.
     */
    public static boolean click(UIContext context, UIFilmPreview preview)
    {
        if (!showHeaders() || context.mouseButton > 1)
        {
            return false;
        }

        ViewportPane pane = paneAt(context.mouseX, context.mouseY);

        if (pane == null)
        {
            return false;
        }

        if (context.mouseButton == 0 && pane.chevron.isInside(context))
        {
            focus(pane.index, preview);
            openMenu(context, pane, preview);

            return true;
        }

        if (context.mouseButton == 0 && pane.title.isInside(context))
        {
            long now = System.currentTimeMillis();

            if (clickedTitle == pane && now - clickedAt <= DOUBLE_CLICK_MS)
            {
                clickedTitle = null;
                toggleMaximize(pane, preview);
            }
            else
            {
                clickedTitle = pane;
                clickedAt = now;
                focus(pane.index, preview);
            }

            return true;
        }

        if (pane.index != focused)
        {
            focus(pane.index, preview);

            return true;
        }

        return false;
    }

    /** The pane under the mouse becomes the live one before the wheel or a drag goes to it */
    public static void focusUnder(UIContext context, UIFilmPreview preview)
    {
        if (!isMulti())
        {
            return;
        }

        ViewportPane pane = paneAt(context.mouseX, context.mouseY);

        if (pane != null && pane.index != focused)
        {
            focus(pane.index, preview);
        }
    }

    private static List<CameraClip> cameraClips(UIFilmPanel panel)
    {
        List<CameraClip> list = new java.util.ArrayList<>();

        if (panel.getData() != null && panel.getData().camera != null)
        {
            for (Clip clip : panel.getData().camera.get())
            {
                if (clip instanceof CameraClip camera)
                {
                    list.add(camera);
                }
            }
        }

        return list;
    }

    private static void openMenu(UIContext context, ViewportPane pane, UIFilmPreview preview)
    {
        ViewportCameraSource current = sourceOf(pane, preview);
        UIFilmPanel panel = preview.getFilmPanel();
        List<CameraClip> clips = cameraClips(panel);

        context.replaceContextMenu((menu) ->
        {
            for (ViewportCameraSource kind : new ViewportCameraSource[] {ViewportCameraSource.RENDER_CAMERA, ViewportCameraSource.FREE_ORBIT, ViewportCameraSource.ACTOR_POV})
            {
                if (taken(pane, kind, null, preview))
                {
                    menu.action(new DisabledContextAction(kind.icon, kind.label));
                }
                else
                {
                    menu.action(kind.icon, kind.label, current == kind, () -> select(pane, kind, null, preview));
                }
            }

            for (int i = 0; i < clips.size() && i < 24; i++)
            {
                CameraClip clip = clips.get(i);
                IKey label = IKey.constant(clipName(panel, clips, clip));

                if (taken(pane, ViewportCameraSource.SPECIFIC_CLIP, clip, preview))
                {
                    menu.action(new DisabledContextAction(ViewportCameraSource.SPECIFIC_CLIP.icon, label));
                }
                else
                {
                    menu.action(ViewportCameraSource.SPECIFIC_CLIP.icon, label, current == ViewportCameraSource.SPECIFIC_CLIP && pane.clip == clip, () -> select(pane, ViewportCameraSource.SPECIFIC_CLIP, clip, preview));
                }
            }
        });
    }

    /** The layout button's menu */
    public static void openLayoutMenu(UIContext context, UIFilmPreview preview)
    {
        context.replaceContextMenu((menu) ->
        {
            for (ViewportLayout option : ViewportLayout.values())
            {
                menu.action(Icons.LAYOUT, option.label, layout == option, () -> setLayout(option, preview));
            }
        });
    }

    /* The world's frames */

    private static UIFilmPanel panel()
    {
        if (UIScreen.getCurrentMenu() instanceof UIDashboard dashboard && dashboard.getPanels().panel instanceof UIFilmPanel film && film.preview != null && film.getController() != null)
        {
            return film;
        }

        return null;
    }

    /** The world is about to be drawn: which pane's camera is it drawn from this time */
    public static void worldBegin()
    {
        worldEnd();

        if (layout == ViewportLayout.SINGLE)
        {
            return;
        }

        UIFilmPanel panel = panel();

        if (panel == null || panel.getData() == null)
        {
            return;
        }

        UIFilmController controller = panel.getController();
        boolean multi = visibleCount() > 1 && !panel.isFlying();
        ViewportPane target;

        if (recording())
        {
            /* A video is the film's camera, whatever the panes show */
            target = RECORD;
        }
        else if (!multi)
        {
            target = PANES[focused];
        }
        else
        {
            target = schedule();
        }

        slice = target;
        sliceFocused = target == PANES[focused];
        savedPov = controller.getPovMode();
        savedEnabled = controller.orbit.enabled;
        savedOrbit = null;

        int pov;
        CameraClip clip;

        if (target == RECORD)
        {
            controller.applyTransientPov(UIFilmController.CAMERA_MODE_CAMERA);

            pov = UIFilmController.CAMERA_MODE_CAMERA;
            clip = null;
            sliceAuxiliary = false;
        }
        else if (sliceFocused)
        {
            pov = savedPov;
            clip = target.clip;
            sliceAuxiliary = multi && (pov != UIFilmController.CAMERA_MODE_CAMERA || clip != null);
        }
        else
        {
            savedOrbit = controller.orbit.captureState();

            controller.orbit.restoreState(target.orbit);
            controller.applyTransientPov(target.pov);

            pov = target.pov;
            clip = target.clip;
            sliceAuxiliary = pov != UIFilmController.CAMERA_MODE_CAMERA || clip != null;
        }

        RunnerCameraController runner = panel.getRunner();

        runner.ticksOverride = pov == UIFilmController.CAMERA_MODE_CAMERA && clip != null ? clipTick(panel, clip) : -1;
    }

    /** The focused pane on two frames of three, the others in turn on the third */
    private static ViewportPane schedule()
    {
        int n = visibleCount();
        int f = frame++;

        if (f % 3 != 2 || n < 2)
        {
            return PANES[focused];
        }

        int others = n - 1;

        turn = (turn + 1) % others;

        int seen = 0;

        for (int i = 0; i < n; i++)
        {
            ViewportPane pane = visible(i);

            if (pane.index == focused)
            {
                continue;
            }

            if (seen++ == turn)
            {
                return pane;
            }
        }

        return PANES[focused];
    }

    private static int clipTick(UIFilmPanel panel, CameraClip clip)
    {
        int start = clip.tick.get();
        int end = start + Math.max(1, clip.duration.get()) - 1;

        return Math.max(start, Math.min(end, panel.getCursor()));
    }

    /** The frame was drawn: the editor's own camera state comes back, and the frame waits to be copied */
    public static void worldEnd()
    {
        if (slice == null)
        {
            return;
        }

        UIFilmPanel panel = panel();

        if (panel != null)
        {
            UIFilmController controller = panel.getController();

            if (savedOrbit != null)
            {
                slice.orbit = controller.orbit.captureState();

                controller.orbit.restoreState(savedOrbit);
            }

            controller.applyTransientPov(savedPov);
            controller.orbit.enabled = savedEnabled;
            panel.getRunner().ticksOverride = -1;

            if (sliceFocused && slice != RECORD)
            {
                focusedCamera.copy(panel.getWorldCamera());
            }
        }

        lastSlice = slice == RECORD ? null : slice;
        slice = null;
        sliceAuxiliary = false;
        savedOrbit = null;
    }

    /** At the interface's turn: the frame the world just drew goes into the buffer of the pane it was drawn for */
    public static void capture(Framebuffer source)
    {
        worldEnd();

        ViewportPane pane = lastSlice;

        lastSlice = null;

        if (pane == null || source == null || !isMulti() || source.fbo <= 0)
        {
            return;
        }

        Framebuffer target = pane.ensure(source.textureWidth, source.textureHeight);
        int previous = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);

        if (scissor)
        {
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
        }

        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, source.fbo);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.fbo);
        GL30.glBlitFramebuffer(0, 0, source.textureWidth, source.textureHeight, 0, 0, target.textureWidth, target.textureHeight, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, previous);

        if (scissor)
        {
            GL11.glEnable(GL11.GL_SCISSOR_TEST);
        }

        pane.markContent();
    }
}
