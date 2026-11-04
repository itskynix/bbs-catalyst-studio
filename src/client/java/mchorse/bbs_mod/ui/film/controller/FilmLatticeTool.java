package mchorse.bbs_mod.ui.film.controller;

import mchorse.bbs_mod.api.client.editor.FilmEditorTool;
import mchorse.bbs_mod.cubic.render.LatticeDeform;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.ILatticeForm;
import mchorse.bbs_mod.forms.renderers.FormRenderer;
import mchorse.bbs_mod.forms.renderers.LatticeGuide;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.ui.film.replays.UIReplaysEditor;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs.IUIKeyframeGraph;
import mchorse.bbs_mod.ui.utils.Area;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * The lattice in the film editor's viewport. While one of a model's lattice tracks (its centre,
 * its points or its bulge sphere) is the dope sheet's selected one, the cage of that model is drawn
 * around it - the yellow wires, a cube on every control point, the light blue centre - and its
 * handles can be dragged right there: a handle follows the cursor across the screen at its own
 * depth, and every move keys the track at the cursor's tick, so the lattice is animated by placing
 * it where it should be, frame after frame.
 *
 * <p>The cage is drawn by {@link mchorse.bbs_mod.film.FilmEntityRenderer} inside the actor's frame,
 * through {@link #renderGuide}, which also hands back the view a click is projected through.</p>
 */
public class FilmLatticeTool extends FilmEditorTool
{
    private static final int NONE = -1;
    private static final int CENTER = -2;
    private static final int BULGE = -3;
    private static final float PICK_RADIUS = 9F;

    /** The tool whose cage is on screen this frame; the renderer asks it */
    private static FilmLatticeTool shown;

    private final UIFilmController controller;

    /* What is being edited this frame: the model as it is drawn, its actor, and the track */
    private ILatticeForm form;
    private IEntity entity;
    private String path = "";
    private UIKeyframeSheet sheet;

    /* Form space to clip space, as the cage was last drawn */
    private final Matrix4f view = new Matrix4f();
    private boolean viewReady;

    /* The handle picked, and the drag on it */
    private int selected = NONE;
    private boolean dragging;
    private float depth;
    private final Vector3f start = new Vector3f();
    private float[] startValue;

    public FilmLatticeTool(UIFilmController controller)
    {
        this.controller = controller;
    }

    private static boolean isLattice(UIKeyframeSheet sheet)
    {
        if (sheet == null || sheet.channel == null)
        {
            return false;
        }

        Object factory = sheet.channel.getFactory();

        return factory == KeyframeFactories.LATTICE || factory == KeyframeFactories.BULGE || factory == KeyframeFactories.CENTER;
    }

    /** Drawn by the film's entity renderer after the actor's form, in the actor's frame */
    public static void renderGuide(IEntity entity, Form root, MatrixStack stack, float transition)
    {
        FilmLatticeTool tool = shown;

        if (tool != null && tool.entity == entity && tool.form != null)
        {
            tool.render(root, stack, transition);
        }
    }

    private void render(Form root, MatrixStack stack, float transition)
    {
        FormRenderer renderer = FormUtilsClient.getRenderer(root);
        Matrix4f frame = null;

        if (renderer != null && !this.path.isEmpty())
        {
            MatrixCache cache = renderer.collectMatrices(this.entity, transition);

            frame = cache.get(this.path) == null ? null : cache.get(this.path).matrix();
        }

        stack.push();

        if (frame != null)
        {
            MatrixStackUtils.multiply(stack, frame);
        }

        this.view.set(this.controller.panel.lastProjection).mul(stack.peek().getPositionMatrix());
        this.viewReady = true;

        LatticeGuide.render(stack, this.form, this.selected >= 0 ? this.selected : -1, true);
        stack.pop();
    }

    /* Which lattice is up */

    @Override
    public void updateTool(UIContext context)
    {
        shown = null;

        if (!this.find())
        {
            this.form = null;
            this.entity = null;
            this.sheet = null;
            this.viewReady = false;
            this.dragging = false;

            return;
        }

        shown = this;

        if (this.dragging)
        {
            this.drag(context.mouseX, context.mouseY);
        }
    }

    /** The lattice track the dope sheet has selected, and the model it belongs to as the actor draws it */
    private boolean find()
    {
        UIReplaysEditor replays = this.controller.panel.replayEditor;

        if (replays == null || replays.keyframeEditor == null || !this.controller.panel.isReplayEditorSelected())
        {
            return false;
        }

        IUIKeyframeGraph graph = replays.keyframeEditor.view.getGraph();
        UIKeyframeSheet pick = null;

        /* A track with keyframes picked wins; otherwise the one last worked on */
        for (UIKeyframeSheet sheet : graph.getSheets())
        {
            if (isLattice(sheet) && sheet.selection.hasAny())
            {
                pick = sheet;

                break;
            }
        }

        if (pick == null && isLattice(graph.getLastSheet()))
        {
            pick = graph.getLastSheet();
        }

        if (pick == null && this.dragging && this.sheet != null)
        {
            pick = this.sheet;
        }

        if (pick == null)
        {
            return false;
        }

        Form owner = pick.descriptor != null ? pick.descriptor.owner() : (pick.property == null ? null : FormUtils.getForm(pick.property));
        IEntity entity = this.controller.getCurrentEntity();

        if (!(owner instanceof ILatticeForm) || entity == null || entity.getForm() == null)
        {
            return false;
        }

        String path = FormUtils.getPath(owner);
        Form live = path == null || path.isEmpty() ? entity.getForm() : FormUtils.getForm(entity.getForm(), path);

        if (!(live instanceof ILatticeForm model))
        {
            return false;
        }

        if (this.sheet != pick)
        {
            this.selected = NONE;
        }

        this.form = model;
        this.entity = entity;
        this.path = path == null ? "" : path;
        this.sheet = pick;

        return true;
    }

    /* Picking and dragging */

    private Area viewport()
    {
        return this.controller.panel.preview.getViewport();
    }

    /** Where a point of the lattice's frame lands on screen; null behind the camera */
    private Vector4f project(Vector3f point)
    {
        Vector4f clip = this.view.transform(new Vector4f(point.x, point.y, point.z, 1F));

        if (clip.w <= 0F)
        {
            return null;
        }

        Area area = this.viewport();

        clip.div(clip.w);

        return new Vector4f(area.x + (clip.x * 0.5F + 0.5F) * area.w, area.y + (1F - (clip.y * 0.5F + 0.5F)) * area.h, clip.z, 1F);
    }

    /** The handle at a place of the lattice's frame: the centre, the bulge sphere's middle or a point */
    private Vector3f handle(int handle)
    {
        if (handle == CENTER)
        {
            Vector3f[] box = LatticeDeform.box(this.form);

            return box == null ? null : new Vector3f(box[0]).add(box[1]).mul(0.5F);
        }

        if (handle == BULGE)
        {
            float[] sphere = this.form.getLattice().bulge.get();

            return new Vector3f(sphere[0], sphere[1], sphere[2]);
        }

        Vector3f[] points = LatticeGuide.points(this.form);

        return points == null || handle < 0 || handle >= points.length ? null : points[handle];
    }

    @Override
    public boolean click(UIContext context)
    {
        if (shown != this || !this.viewReady || context.mouseButton != 0 || !this.viewport().isInside(context))
        {
            return false;
        }

        int best = NONE;
        float distance = PICK_RADIUS * PICK_RADIUS;
        Vector4f bestScreen = null;
        boolean center = this.sheet.channel.getFactory() == KeyframeFactories.CENTER;
        boolean bulge = this.sheet.channel.getFactory() == KeyframeFactories.BULGE;
        int count = this.form.getLattice().enabled.get() && !center && !bulge ? this.form.getLattice().count() : 0;

        /* The track's own handle first, so the centre beats the middle point it sits on */
        int[] order = new int[count + 2];

        order[0] = bulge ? BULGE : CENTER;
        order[1] = bulge ? CENTER : NONE;

        for (int n = 0; n < count; n++)
        {
            order[n + 2] = n;
        }

        for (int handle : order)
        {
            if (handle == NONE || (handle == BULGE && !this.form.getLattice().bulgeActive() && !this.form.getLattice().bulgeEnabled.get()))
            {
                continue;
            }

            Vector3f point = this.handle(handle);
            Vector4f screen = point == null ? null : this.project(point);

            if (screen == null)
            {
                continue;
            }

            float dx = screen.x - context.mouseX;
            float dy = screen.y - context.mouseY;
            float d = dx * dx + dy * dy;

            if (d < distance)
            {
                distance = d;
                best = handle;
                bestScreen = screen;
            }
        }

        if (best == NONE)
        {
            return false;
        }

        /* The handle the drag keys is the one of the track: the centre track moves the centre, and so on */
        if (best == CENTER && !center && !bulge && count > 0)
        {
            return false;
        }

        this.selected = best;
        this.dragging = true;
        this.depth = bestScreen.z;
        this.start.set(this.handle(best));
        this.startValue = this.value().clone();

        return true;
    }

    @Override
    public boolean release(UIContext context)
    {
        if (!this.dragging)
        {
            return false;
        }

        this.dragging = false;

        return true;
    }

    @Override
    public void stopTool()
    {
        this.dragging = false;
    }

    /** What the track holds this frame, as the form shows it */
    private float[] value()
    {
        Object factory = this.sheet.channel.getFactory();

        if (factory == KeyframeFactories.CENTER)
        {
            return this.form.getLattice().center.get();
        }

        if (factory == KeyframeFactories.BULGE)
        {
            return this.form.getLattice().bulge.get();
        }

        return this.form.getLattice().points.get();
    }

    /** The handle follows the cursor at the depth it was picked at; the track is keyed at the cursor's tick */
    @SuppressWarnings("unchecked")
    private void drag(int mouseX, int mouseY)
    {
        Area area = this.viewport();
        Replay replay = this.controller.panel.replayEditor.getReplay();

        if (replay == null || area.w <= 0 || area.h <= 0 || this.startValue == null)
        {
            return;
        }

        float nx = (mouseX - area.x) / (float) area.w * 2F - 1F;
        float ny = 1F - (mouseY - area.y) / (float) area.h * 2F;
        Vector4f world = new Matrix4f(this.view).invert().transform(new Vector4f(nx, ny, this.depth, 1F));

        if (world.w == 0F)
        {
            return;
        }

        world.div(world.w);

        Vector3f delta = new Vector3f(world.x, world.y, world.z).sub(this.start);
        float[] value = this.startValue.clone();
        Object factory = this.sheet.channel.getFactory();

        if (factory == KeyframeFactories.LATTICE)
        {
            if (this.selected < 0)
            {
                return;
            }

            int slot = this.form.getLattice().slot(this.selected) * 3;

            value[slot] += delta.x;
            value[slot + 1] += delta.y;
            value[slot + 2] += delta.z;
        }
        else
        {
            value[0] += delta.x;
            value[1] += delta.y;
            value[2] += delta.z;
        }

        int tick = replay.getTick(this.controller.panel.getCursor());

        ((KeyframeChannel<float[]>) this.sheet.channel).insert(tick, value);

        if (factory == KeyframeFactories.CENTER)
        {
            this.form.getLattice().center.set(value.clone());
        }
        else if (factory == KeyframeFactories.BULGE)
        {
            this.form.getLattice().bulge.set(value.clone());
        }
        else
        {
            this.form.getLattice().points.set(value.clone());
        }
    }
}
