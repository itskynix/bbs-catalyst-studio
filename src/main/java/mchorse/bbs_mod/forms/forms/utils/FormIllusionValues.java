package mchorse.bbs_mod.forms.forms.utils;

import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.settings.values.core.ValueTransform;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.settings.values.numeric.ValueInt;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The illusions of a form: ghost copies of it, laid out in a pattern around it (or, with a delay,
 * following the form's own movement a few ticks behind it), each a little fainter than the one
 * before. Every copy is the very same form drawn again, so it carries the form's pose, its effects
 * and its parts.
 *
 * <p>The count, the spread, the opacity, the pattern and the directions are properties saved with the
 * form and animatable like any other (the {@code illusions_*} tracks of the dope sheet); the rest is how
 * the copies are dressed and is set in the form editor. At a count of 0 nothing is drawn and nothing
 * is paid for.</p>
 */
public class FormIllusionValues
{
    public static final int MAX_COUNT = 64;

    /** One line of copies in the picked directions */
    public static final int LINEAR = 0;
    /** A ring around the form, in a plane */
    public static final int CIRCULAR = 1;
    /** Arms out along each picked direction, the same number of copies on each */
    public static final int CROSS = 2;
    /** A grid (or a box) over the picked axes, the form in its middle */
    public static final int GRID = 3;

    /** Planes of the ring: the horizontal one, the one facing front, the one facing the side */
    public static final int PLANE_XZ = 0;
    public static final int PLANE_XY = 1;
    public static final int PLANE_YZ = 2;

    /** A copy on a ring: looking the way it was put, at the form, or away from it */
    public static final int FACE_NONE = 0;
    public static final int FACE_CENTER = 1;
    public static final int FACE_OUTWARD = 2;

    /** How many copies; 0 draws none */
    public final ValueInt count = new ValueInt("illusions_count", 0, 0, MAX_COUNT);
    /** The distance between two neighbouring copies, or the radius of the ring, in the form's units */
    public final ValueFloat spread = new ValueFloat("illusions_spread", 0.5F, 0F, 32F);
    /** How opaque the nearest copy is; the others fade from it */
    public final ValueFloat opacity = new ValueFloat("illusions_opacity", 0.5F, 0F, 1F);

    /** How the copies are laid out: {@link #LINEAR}, {@link #CIRCULAR}, {@link #CROSS} or {@link #GRID} */
    public final ValueInt pattern = new ValueInt("illusions_pattern", LINEAR, 0, 3);
    /** The plane of a ring */
    public final ValueInt plane = new ValueInt("illusions_plane", PLANE_XZ, 0, 2);
    /** Which way the copies of a ring look */
    public final ValueInt face = new ValueInt("illusions_face", FACE_NONE, 0, 2);

    /* The directions the copies are laid out along; several at once make a diagonal (a line) or the arms (a cross) */
    public final ValueBoolean front = new ValueBoolean("illusions_front", false);
    public final ValueBoolean back = new ValueBoolean("illusions_back", true);
    public final ValueBoolean left = new ValueBoolean("illusions_left", false);
    public final ValueBoolean right = new ValueBoolean("illusions_right", false);
    public final ValueBoolean up = new ValueBoolean("illusions_up", false);
    public final ValueBoolean down = new ValueBoolean("illusions_down", false);

    /** The picked directions add up into one diagonal line, instead of each being an arm of its own */
    public final ValueBoolean diagonal = new ValueBoolean("illusions_diagonal", false);

    /** The copies grow fainter the further they are */
    public final ValueBoolean fadeOut = new ValueBoolean("illusions_fade_out", true);
    /** The other way round: the further a copy, the more opaque */
    public final ValueBoolean invertFade = new ValueBoolean("illusions_invert_fade", false);

    /** What each copy gets: a move, a scale and a turn. The same on every copy, or compounding when {@link #cumulative} */
    public final ValueTransform offset = new ValueTransform("illusions_offset", new Transform());

    /** Off: the transform is the same on every copy. On: it grows with the copy's number (spirals, stepped leaning) */
    public final ValueBoolean cumulative = new ValueBoolean("illusions_cumulative", false);

    /** How far, in the form's units, a copy shivers off its place, differently each moment */
    public final ValueFloat jitter = new ValueFloat("illusions_jitter", 0F, 0F, 2F);

    /** Ticks each copy lags behind the one before, in a film: 0 lays them out by pattern, above 0 they trail the actor */
    public final ValueInt delay = new ValueInt("illusions_delay", 0, 0, 40);

    public FormIllusionValues(Form form)
    {
        form.add(this.count);
        form.add(this.spread);
        form.add(this.opacity);
        form.add(this.pattern);
        form.add(this.plane);
        form.add(this.face);
        form.add(this.front);
        form.add(this.back);
        form.add(this.left);
        form.add(this.right);
        form.add(this.up);
        form.add(this.down);
        form.add(this.diagonal);
        form.add(this.fadeOut);
        form.add(this.invertFade);
        form.add(this.offset);
        form.add(this.cumulative);
        form.add(this.jitter);
        form.add(this.delay);
    }

    public boolean isActive()
    {
        return this.count.get() > 0 && this.opacity.get() > 0F;
    }

    /** Where a copy stands, and how it is turned about the normal of the ring's plane (when it is turned) */
    public record Placement(Vector3f position, float angle, int axis, boolean turned)
    {}

    /** The direction of the line, front +Z, left +X, up +Y; behind the form when none is picked, nowhere when the picks cancel */
    public Vector3f direction()
    {
        boolean any = this.front.get() || this.back.get() || this.left.get() || this.right.get() || this.up.get() || this.down.get();

        if (!any)
        {
            return new Vector3f(0F, 0F, -1F);
        }

        Vector3f direction = new Vector3f(
            (this.left.get() ? 1F : 0F) - (this.right.get() ? 1F : 0F),
            (this.up.get() ? 1F : 0F) - (this.down.get() ? 1F : 0F),
            (this.front.get() ? 1F : 0F) - (this.back.get() ? 1F : 0F)
        );

        return direction.lengthSquared() < 1.0E-6F ? direction : direction.normalize();
    }

    /** The picked directions one by one, each its own arm; none when none is picked */
    private List<Vector3f> armsPicked()
    {
        List<Vector3f> arms = new ArrayList<>();

        if (this.front.get()) arms.add(new Vector3f(0F, 0F, 1F));
        if (this.back.get()) arms.add(new Vector3f(0F, 0F, -1F));
        if (this.left.get()) arms.add(new Vector3f(1F, 0F, 0F));
        if (this.right.get()) arms.add(new Vector3f(-1F, 0F, 0F));
        if (this.up.get()) arms.add(new Vector3f(0F, 1F, 0F));
        if (this.down.get()) arms.add(new Vector3f(0F, -1F, 0F));

        return arms;
    }

    /** The picked directions one by one, each its own arm of a cross; the four horizontal ones when none is picked */
    private List<Vector3f> arms()
    {
        List<Vector3f> arms = this.armsPicked();

        if (arms.isEmpty())
        {
            arms.add(new Vector3f(0F, 0F, 1F));
            arms.add(new Vector3f(0F, 0F, -1F));
            arms.add(new Vector3f(1F, 0F, 0F));
            arms.add(new Vector3f(-1F, 0F, 0F));
        }

        return arms;
    }

    /** The places of the copies, nearest first: the n-th copy (1 is the first) is the n-th of the list */
    public List<Placement> layout()
    {
        int count = this.count.get();
        float spread = this.spread.get();
        List<Placement> places = new ArrayList<>(count);

        switch (this.pattern.get())
        {
            case CIRCULAR ->
            {
                int plane = this.plane.get();
                int face = this.face.get();

                for (int n = 0; n < count; n++)
                {
                    double a = Math.PI * 2D * n / count;
                    float c = (float) Math.cos(a) * spread;
                    float s = (float) Math.sin(a) * spread;
                    Vector3f position = plane == PLANE_XY ? new Vector3f(c, s, 0F) : plane == PLANE_YZ ? new Vector3f(0F, c, s) : new Vector3f(c, 0F, s);
                    int axis = plane == PLANE_XY ? 2 : plane == PLANE_YZ ? 0 : 1;
                    float angle = 0F;

                    if (face != FACE_NONE)
                    {
                        /* Looking at the middle from where it stands; the plane's own forward is the +Z of the form */
                        angle = plane == PLANE_XZ ? (float) Math.atan2(-Math.cos(a), -Math.sin(a)) : (float) (a + Math.PI / 2D) * (plane == PLANE_YZ ? -1F : 1F);

                        if (face == FACE_OUTWARD)
                        {
                            angle += (float) Math.PI;
                        }
                    }

                    places.add(new Placement(position, angle, axis, face != FACE_NONE));
                }
            }
            case CROSS ->
            {
                List<Vector3f> arms = this.arms();

                for (int n = 0; n < count; n++)
                {
                    Vector3f arm = arms.get(n % arms.size());
                    int step = n / arms.size() + 1;

                    places.add(new Placement(new Vector3f(arm).mul(spread * step), 0F, 1, false));
                }
            }
            case GRID ->
            {
                boolean x = this.left.get() || this.right.get();
                boolean y = this.up.get() || this.down.get();
                boolean z = this.front.get() || this.back.get();

                if (!x && !y && !z)
                {
                    z = true;
                }

                int axes = (x ? 1 : 0) + (y ? 1 : 0) + (z ? 1 : 0);
                int side = (int) Math.ceil(Math.pow(count + 1, 1D / axes));
                int sx = x ? side : 1;
                int sy = y ? side : 1;
                int sz = z ? side : 1;
                List<Vector3f> cells = new ArrayList<>();

                for (int i = 0; i < sx; i++)
                {
                    for (int j = 0; j < sy; j++)
                    {
                        for (int k = 0; k < sz; k++)
                        {
                            Vector3f cell = new Vector3f((i - (sx - 1) / 2F) * spread, (j - (sy - 1) / 2F) * spread, (k - (sz - 1) / 2F) * spread);

                            /* The form itself stands in the middle cell */
                            if (cell.lengthSquared() > 1.0E-6F)
                            {
                                cells.add(cell);
                            }
                        }
                    }
                }

                cells.sort(Comparator.comparingDouble(Vector3f::lengthSquared));

                for (int n = 0; n < count && n < cells.size(); n++)
                {
                    places.add(new Placement(cells.get(n), 0F, 1, false));
                }
            }
            default ->
            {
                boolean any = this.front.get() || this.back.get() || this.left.get() || this.right.get() || this.up.get() || this.down.get();

                if (any && !this.diagonal.get())
                {
                    /* Every picked direction is an arm of its own, the copies shared out among them */
                    List<Vector3f> arms = this.armsPicked();

                    for (int n = 0; n < count; n++)
                    {
                        Vector3f arm = arms.get(n % arms.size());
                        int step = n / arms.size() + 1;

                        places.add(new Placement(new Vector3f(arm).mul(spread * step), 0F, 1, false));
                    }
                }
                else
                {
                    Vector3f direction = this.direction();

                    for (int n = 1; n <= count; n++)
                    {
                        places.add(new Placement(new Vector3f(direction).mul(spread * n), 0F, 1, false));
                    }
                }
            }
        }

        return places;
    }

    /** The opacity of the n-th copy (1 is the nearest) of the count, 0 to 1 */
    public float opacityOf(int n)
    {
        int count = Math.max(1, this.count.get());
        float fade = 1F;

        if (this.fadeOut.get())
        {
            fade = this.invertFade.get() ? n / (float) count : 1F - (n - 1) / (float) count;
        }

        return Math.max(0F, Math.min(1F, this.opacity.get() * fade));
    }
}
