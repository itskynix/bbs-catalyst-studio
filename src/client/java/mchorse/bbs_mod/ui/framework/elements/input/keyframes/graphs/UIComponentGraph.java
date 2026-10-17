package mchorse.bbs_mod.ui.framework.elements.input.keyframes.graphs;

import mchorse.bbs_mod.graphics.window.Window;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframeSheet;
import mchorse.bbs_mod.ui.framework.elements.input.keyframes.UIKeyframes;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.utils.keyframes.ComponentBezier;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.factories.IKeyframeFactory;
import mchorse.bbs_mod.utils.keyframes.factories.PoseKeyframeFactory;
import mchorse.bbs_mod.utils.pose.Pose;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The graph of a composite track (a transform, or a pose of many bones): not one curve but one curve
 * per component - position, rotation and scale, X/Y/Z, per bone for a pose - picked in a channel tree
 * beside the canvas. The picked ("active") channel is the one whose points and Bezier handles can be
 * grabbed; the other visible channels are drawn as plain curves.
 *
 * <p>A channel is addressed by its {@link ComponentBezier} key ({@code "p.x"}, or {@code "arm/r.y"}
 * inside a pose). Handles are edited per component through {@link Keyframe#putComponentHandle}, so the
 * keyframe's own handles - and with them every older scene - are never touched. Rotation is stored in
 * radians and drawn and edited in degrees.</p>
 */
public class UIComponentGraph extends UIKeyframeGraph
{
    public static final int TREE_WIDTH = 196;

    /** X, Y, Z in the order every group of the tree lists them. */
    public static final int[] AXIS_COLORS = {Colors.RED, Colors.GREEN, Colors.BLUE};

    private final boolean pose;
    private final UIComponentChannelTree tree;
    private final Set<String> visible = new LinkedHashSet<>();

    private String active;

    /** The channel the shared drawing and picking code currently reads; the active one outside a render pass. */
    private String current;

    private List<String> bones = Collections.emptyList();
    private long bonesStamp = Long.MIN_VALUE;

    public UIComponentGraph(UIKeyframes keyframes, UIKeyframeSheet sheet)
    {
        super(keyframes, sheet);

        this.pose = sheet.channel.getFactory() instanceof PoseKeyframeFactory;
        this.tree = new UIComponentChannelTree(this);

        keyframes.add(this.tree);

        List<String> bones = this.getBones();
        String prefix = this.pose ? (bones.isEmpty() ? null : bones.get(0) + "/") : "";

        if (prefix != null)
        {
            for (int i = 0; i < 3; i++)
            {
                this.visible.add(prefix + ComponentBezier.KEYS[i]);
            }

            this.active = prefix + ComponentBezier.KEYS[0];
        }

        this.current = this.active;
    }

    /** Draws the channel tree on top of the canvas. */
    public void renderTree(UIContext context)
    {
        this.tree.renderLate(context);
    }

    /** Takes the channel tree off the timeline again. */
    public void dispose()
    {
        this.tree.removeFromParent();
    }

    /* Channel state, driven by the tree */

    public boolean isPose()
    {
        return this.pose;
    }

    /** The bones some keyframe of the track has a transform for, sorted. Empty for a lone transform. */
    public List<String> getBones()
    {
        long now = System.currentTimeMillis();

        if (!this.pose)
        {
            return this.bones;
        }

        if (now - this.bonesStamp > 400L)
        {
            Set<String> names = new TreeSet<>();

            for (Object object : this.sheet.channel.getKeyframes())
            {
                if (((Keyframe<?>) object).getValue() instanceof Pose value)
                {
                    names.addAll(value.transforms.keySet());
                }
            }

            this.bones = new ArrayList<>(names);
            this.bonesStamp = now;
        }

        return this.bones;
    }

    public String getActive()
    {
        return this.active;
    }

    public boolean isChannelVisible(String key)
    {
        return this.visible.contains(key);
    }

    public void setChannelVisible(String key, boolean show)
    {
        if (show)
        {
            this.visible.add(key);
        }
        else
        {
            this.visible.remove(key);
        }
    }

    /** Makes the channel the editable one, showing it. The view re-fits when it moves to another kind of value. */
    public void activate(String key)
    {
        String previous = this.active;

        this.visible.add(key);
        this.active = key;
        this.current = key;

        if (previous == null || group(previous) != group(key) || !ComponentBezier.bone(previous).equals(ComponentBezier.bone(key)))
        {
            this.resetViewY(this.sheet);
        }
    }

    private static int group(String key)
    {
        return Math.max(0, ComponentBezier.index(key)) / 3;
    }

    public static int colorOf(String key)
    {
        return AXIS_COLORS[Math.max(0, ComponentBezier.index(key)) % 3];
    }

    /* Component access */

    private Transform transformOf(Object value, String key, boolean create)
    {
        if (value instanceof Pose pose)
        {
            String bone = ComponentBezier.bone(key);

            return create ? pose.getOrCreate(bone) : pose.transforms.get(bone);
        }

        return value instanceof Transform transform ? transform : null;
    }

    /** The channel's value in drawn units (degrees for a rotation). */
    private double read(Object value, String key)
    {
        int index = key == null ? -1 : ComponentBezier.index(key);

        if (index < 0)
        {
            return 0D;
        }

        Transform transform = this.transformOf(value, key, false);
        double stored = transform == null ? (index >= 6 ? 1D : 0D) : ComponentBezier.get(transform, index);

        return stored * ComponentBezier.unit(index);
    }

    /** Writes a delta in drawn units onto the channel of a value. */
    private void shift(Object value, String key, double delta)
    {
        int index = ComponentBezier.index(key);
        Transform transform = this.transformOf(value, key, true);

        if (index >= 0 && transform != null)
        {
            ComponentBezier.set(transform, index, (float) (ComponentBezier.get(transform, index) + delta / ComponentBezier.unit(index)));
        }
    }

    private void shiftKeyframe(Keyframe keyframe, String key, double delta)
    {
        IKeyframeFactory factory = this.sheet.channel.getFactory();
        Object copy = factory.copy(keyframe.getValue());

        this.shift(copy, key, delta);
        keyframe.setValue(copy, false);
    }

    /* Hooks of the shared graph */

    @Override
    protected double yOf(Keyframe frame)
    {
        return this.read(frame.getValue(), this.current);
    }

    @Override
    protected double yOfValue(Object value)
    {
        return this.read(value, this.current);
    }

    @Override
    protected float[] handlesOf(Keyframe frame)
    {
        float[] handle = this.current == null ? null : frame.getComponentHandle(this.current);

        if (handle == null)
        {
            /* Not customised: the keyframe's own time handles, no value offset. */
            return new float[] {frame.lx, 0F, frame.rx, 0F};
        }

        float unit = (float) ComponentBezier.unit(ComponentBezier.index(this.current));

        return new float[] {handle[0], handle[1] * unit, handle[2], handle[3] * unit};
    }

    @Override
    protected boolean showHandles()
    {
        return this.current != null && this.current.equals(this.active);
    }

    @Override
    public int getLeftInset()
    {
        return TREE_WIDTH;
    }

    @Override
    public void resetViewY(UIKeyframeSheet sheet)
    {
        String saved = this.current;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        this.yAxis.set(0, 2);

        for (String key : this.visible)
        {
            this.current = key;

            for (Object object : sheet.channel.getKeyframes())
            {
                double y = this.yOf((Keyframe) object);

                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }

        this.current = saved;

        if (minY > maxY)
        {
            minY = -10;
            maxY = 10;
        }

        if (Math.abs(maxY - minY) < 0.01D)
        {
            this.yAxis.setShift(minY);
            this.yAxis.anchor(0.5F);
        }
        else
        {
            this.yAxis.viewOffset(minY, maxY, this.keyframes.area.h, 30);
        }
    }

    /* Picking and editing */

    @Override
    public Pair<Keyframe, KeyframeType> findKeyframe(int mouseX, int mouseY)
    {
        if (this.active == null || !this.visible.contains(this.active))
        {
            return null;
        }

        this.current = this.active;

        return super.findKeyframe(mouseX, mouseY);
    }

    @Override
    public boolean addKeyframeAt(float tick, int mouseY)
    {
        /* The new keyframe is the track's interpolated value, with the active channel moved to the cursor. */
        Keyframe keyframe = this.addKeyframeManually(this.sheet, tick, null);

        if (keyframe != null && this.active != null)
        {
            this.current = this.active;
            this.shiftKeyframe(keyframe, this.active, this.fromGraphY(mouseY) - this.yOf(keyframe));
        }

        return true;
    }

    @Override
    public void dragKeyframes(UIContext context, Pair<Keyframe, KeyframeType> type, int originalX, int originalY, float originalT, Object originalV)
    {
        if (type == null || this.active == null)
        {
            return;
        }

        this.current = this.active;

        Keyframe keyframe = type.a;
        double unit = ComponentBezier.unit(ComponentBezier.index(this.active));

        if (type.b == KeyframeType.REGULAR)
        {
            float offsetX = (float) this.keyframes.fromGraphX(originalX) - originalT;
            double offsetY = this.fromGraphY(originalY) - this.yOfValue(originalV);
            float fx = (float) this.keyframes.fromGraphX(context.mouseX) - offsetX;

            if (this.keyframes.isSnappingToTicks())
            {
                fx = Math.round(this.keyframes.fromGraphX(context.mouseX) - offsetX);
            }

            /* Every selected key moves by the same amount, each from its own value of the channel. */
            double delta = this.fromGraphY(context.mouseY) - offsetY - this.yOf(keyframe);
            List<Keyframe> moved = new ArrayList<>(this.sheet.selection.getSelected());

            if (!moved.contains(keyframe))
            {
                moved.add(keyframe);
            }

            this.setTick(fx, false);

            for (Keyframe other : moved)
            {
                this.shiftKeyframe(other, this.active, delta);
            }
        }
        else
        {
            float[] handle = keyframe.getComponentHandle(this.active);
            float lx = handle == null ? keyframe.lx : handle[0];
            float ly = handle == null ? 0F : handle[1];
            float rx = handle == null ? keyframe.rx : handle[2];
            float ry = handle == null ? 0F : handle[3];
            float dx = (float) (this.keyframes.fromGraphX(context.mouseX) - keyframe.getTick());
            float dy = (float) ((this.fromGraphY(context.mouseY) - this.yOf(keyframe)) / unit);
            boolean mirror = !Window.isShiftPressed();

            if (type.b == KeyframeType.LEFT_HANDLE)
            {
                lx = -dx;
                ly = dy;

                if (mirror)
                {
                    rx = lx;
                    ry = -ly;
                }
            }
            else
            {
                rx = dx;
                ry = dy;

                if (mirror)
                {
                    lx = rx;
                    ly = -ry;
                }
            }

            keyframe.putComponentHandle(this.active, lx, ly, rx, ry);
        }

        this.keyframes.triggerChange();
    }

    /* Rendering: every visible channel in its colour, the active one last and on top */

    private List<String> drawOrder()
    {
        List<String> order = new ArrayList<>(this.visible);

        if (this.active != null && order.remove(this.active))
        {
            order.add(this.active);
        }

        return order;
    }

    @Override
    protected void renderGraph(UIContext context)
    {
        int color = this.sheet.color;

        try
        {
            for (String key : this.drawOrder())
            {
                this.current = key;
                this.sheet.color = key.equals(this.active) ? colorOf(key) : Colors.mulRGB(colorOf(key), 0.6F);

                super.renderGraph(context);
            }
        }
        finally
        {
            this.sheet.color = color;
            this.current = this.active;
        }
    }

    @Override
    public void renderTopmostKeyframes(UIContext context)
    {
        int color = this.sheet.color;

        try
        {
            for (String key : this.drawOrder())
            {
                this.current = key;
                this.sheet.color = key.equals(this.active) ? colorOf(key) : Colors.mulRGB(colorOf(key), 0.6F);

                super.renderTopmostKeyframes(context);
            }
        }
        finally
        {
            this.sheet.color = color;
            this.current = this.active;
        }
    }
}
