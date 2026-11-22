package mchorse.bbs_mod.forms.renderers;

import mchorse.bbs_mod.forms.forms.DecayForm;

/**
 * The maths of the decay: for a point in space, how much of it is decayed and in what colour.
 *
 * <p>Everything is a function of the point, the seed and the progress, so the same point always
 * answers the same and the pattern only grows as the progress climbs. It never allocates: the answer
 * goes into the caller's array.</p>
 */
public class DecayPattern
{
    /** The values one sample needs, read from the form once per frame */
    public static class Params
    {
        public int preset;
        public int seed;
        public float progress;
        public float softness;
        public float scale;
        public float depthInset;
        public float opacity;
        public float[] primary = new float[3];
        public float[] edge = new float[3];

        public Params read(DecayForm form)
        {
            this.preset = form.preset.get();
            this.seed = form.seed.get();
            this.progress = form.decayProgress.get();
            this.softness = Math.max(0.01F, form.edgeSoftness.get());
            this.scale = Math.max(0.2F, form.noiseScale.get());
            this.depthInset = form.depthInset.get();
            this.opacity = form.opacity.get();

            set(this.primary, form.primaryColor.get());
            set(this.edge, form.edgeColor.get());

            return this;
        }

        private static void set(float[] out, mchorse.bbs_mod.utils.colors.Color color)
        {
            out[0] = color.r;
            out[1] = color.g;
            out[2] = color.b;
        }

        /** A signature, so a cached build knows when the values it was made from changed */
        public int signature()
        {
            int h = this.preset * 31 + this.seed;

            h = h * 31 + Float.floatToIntBits(Math.round(this.progress * 400F) / 400F);
            h = h * 31 + Float.floatToIntBits(this.softness);
            h = h * 31 + Float.floatToIntBits(this.scale);
            h = h * 31 + Float.floatToIntBits(this.depthInset);
            h = h * 31 + Float.floatToIntBits(this.opacity);

            for (int i = 0; i < 3; i++)
            {
                h = h * 31 + Float.floatToIntBits(this.primary[i]);
                h = h * 31 + Float.floatToIntBits(this.edge[i]);
            }

            return h;
        }
    }

    private static int hash(int x, int y, int z, int seed)
    {
        int h = seed * 374761393 + x * 668265263 + y * 1274126177 + z * 1911520717;

        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;

        return h;
    }

    private static float rnd(int hash)
    {
        return (hash & 0xFFFFFF) / 16777216F;
    }

    private static float smooth(float t)
    {
        t = t < 0F ? 0F : (t > 1F ? 1F : t);

        return t * t * (3F - 2F * t);
    }

    private static float smoothstep(float a, float b, float x)
    {
        return smooth((x - a) / (b - a));
    }

    /** Smooth value noise, 0 to 1 */
    public static float noise(double x, double y, double z, int seed)
    {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float fx = smooth((float) (x - xi));
        float fy = smooth((float) (y - yi));
        float fz = smooth((float) (z - zi));

        float c000 = rnd(hash(xi, yi, zi, seed));
        float c100 = rnd(hash(xi + 1, yi, zi, seed));
        float c010 = rnd(hash(xi, yi + 1, zi, seed));
        float c110 = rnd(hash(xi + 1, yi + 1, zi, seed));
        float c001 = rnd(hash(xi, yi, zi + 1, seed));
        float c101 = rnd(hash(xi + 1, yi, zi + 1, seed));
        float c011 = rnd(hash(xi, yi + 1, zi + 1, seed));
        float c111 = rnd(hash(xi + 1, yi + 1, zi + 1, seed));

        float x00 = c000 + (c100 - c000) * fx;
        float x10 = c010 + (c110 - c010) * fx;
        float x01 = c001 + (c101 - c001) * fx;
        float x11 = c011 + (c111 - c011) * fx;
        float y0 = x00 + (x10 - x00) * fy;
        float y1 = x01 + (x11 - x01) * fy;

        return y0 + (y1 - y0) * fz;
    }

    /** Three octaves of the noise, with the contrast pulled up so the blotches have shape: 0 to 1 */
    public static float fbm(double x, double y, double z, int seed)
    {
        float v = noise(x, y, z, seed) * 0.5F + noise(x * 2.03, y * 2.03, z * 2.03, seed + 17) * 0.3F + noise(x * 4.1, y * 4.1, z * 4.1, seed + 41) * 0.2F;

        v = (v - 0.5F) * 1.7F + 0.5F;

        return v < 0F ? 0F : (v > 1F ? 1F : v);
    }

    /** The two nearest feature distances of a 3D cellular noise, written into out[0] and out[1] */
    public static void voronoi(double x, double y, double z, int seed, float[] out)
    {
        int xi = (int) Math.floor(x);
        int yi = (int) Math.floor(y);
        int zi = (int) Math.floor(z);
        float f1 = 8F;
        float f2 = 8F;

        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dy = -1; dy <= 1; dy++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    int cx = xi + dx;
                    int cy = yi + dy;
                    int cz = zi + dz;
                    double px = cx + rnd(hash(cx, cy, cz, seed));
                    double py = cy + rnd(hash(cx, cy, cz, seed + 101));
                    double pz = cz + rnd(hash(cx, cy, cz, seed + 202));
                    double ex = px - x;
                    double ey = py - y;
                    double ez = pz - z;
                    float d = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);

                    if (d < f1)
                    {
                        f2 = f1;
                        f1 = d;
                    }
                    else if (d < f2)
                    {
                        f2 = d;
                    }
                }
            }
        }

        out[0] = f1;
        out[1] = f2;
    }

    /**
     * One point's decay. {@code x, y, z} is where the pattern is read (world blocks on a wall, the
     * form's own space on an object, so it travels with the object); {@code y01} is how high up the
     * box the point is, 0 to 1; {@code radial} how far from the centre, 0 at the middle and 1 at a
     * corner. {@code out} gets the colour in 0 to 2 and the opacity, not yet times the form's own, in 3.
     */
    public static void sample(Params p, double x, double y, double z, float y01, float radial, float[] out, float[] scratch)
    {
        out[3] = 0F;

        float progress = p.progress;

        if (progress <= 0.0005F)
        {
            return;
        }

        if (p.preset == DecayForm.PRESET_CRACKS)
        {
            sampleCracks(p, x, y, z, radial, out, scratch);

            return;
        }

        double s = p.scale;
        float value;
        float threshold;
        float shade;

        switch (p.preset)
        {
            case DecayForm.PRESET_RUST:
            {
                value = fbm(x * s * 1.1, y * s * 1.1, z * s * 1.1, p.seed) * 0.78F + noise(x * s * 6.0, y * s * 6.0, z * s * 6.0, p.seed + 5) * 0.22F;
                threshold = 1.02F - progress * 1.25F + radial * 0.25F;
                shade = noise(x * s * 9.0, y * s * 9.0, z * s * 9.0, p.seed + 9);

                break;
            }
            case DecayForm.PRESET_MOSS:
            {
                value = fbm(x * s * 0.9, y * s * 0.9, z * s * 0.9, p.seed) * 0.65F + noise(x * s * 3.5, y * s * 3.5, z * s * 3.5, p.seed + 5) * 0.35F;
                value += (rnd(hash((int) Math.floor(x * 16.0), (int) Math.floor(y * 16.0), (int) Math.floor(z * 16.0), p.seed + 3)) - 0.5F) * 0.14F;
                threshold = 1F - progress * 1.25F + radial * 0.2F + y01 * 0.25F;
                shade = noise(x * s * 7.0, y * s * 7.0, z * s * 7.0, p.seed + 9);

                break;
            }
            case DecayForm.PRESET_STAIN:
            {
                /* Stretched downwards, so it reads as something that ran */
                value = fbm(x * s * 1.5, y * s * 0.32, z * s * 1.5, p.seed);
                threshold = 1F - progress * 1.3F + radial * 0.15F + (1F - y01) * 0.2F;
                shade = noise(x * s * 4.0, y * s * 1.2, z * s * 4.0, p.seed + 9);

                break;
            }
            default:
            {
                value = fbm(x * s, y * s, z * s, p.seed);
                threshold = 1.02F - progress * 1.25F + radial * 0.25F;
                shade = 0.5F;

                break;
            }
        }

        float u = (value - threshold) / p.softness;
        float alpha = smoothstep(0F, 0.5F, u);

        if (alpha <= 0.002F)
        {
            return;
        }

        /* The rim wears the edge colour, the body the main one, a little mottled */
        float body = smoothstep(0.2F, 1.2F, u);
        float mottle = 0.8F + shade * 0.4F;

        if (p.preset == DecayForm.PRESET_CUSTOM)
        {
            out[0] = 0.8F + body * 0.2F;
            out[1] = out[0];
            out[2] = out[0];
        }
        else
        {
            for (int i = 0; i < 3; i++)
            {
                out[i] = Math.min(2F, (p.edge[i] + (p.primary[i] - p.edge[i]) * body) * mottle);
            }
        }

        out[3] = alpha;
    }

    private static void sampleCracks(Params p, double x, double y, double z, float radial, float[] out, float[] scratch)
    {
        double s = p.scale;
        float progress = p.progress;

        /* Warp the point, so the cell borders wander like a break and do not run straight */
        double wx = x + (noise(x * s * 1.3, y * s * 1.3, z * s * 1.3, p.seed + 61) - 0.5) * 0.35 / s * 2.0;
        double wy = y + (noise(x * s * 1.3, y * s * 1.3, z * s * 1.3, p.seed + 62) - 0.5) * 0.35 / s * 2.0;
        double wz = z + (noise(x * s * 1.3, y * s * 1.3, z * s * 1.3, p.seed + 63) - 0.5) * 0.35 / s * 2.0;

        voronoi(wx * s, wy * s, wz * s, p.seed, scratch);

        /* Breaks reach outwards from the middle, later and thinner at the rim of the spread */
        float arrival = radial * 0.7F + fbm(x * s * 0.8, y * s * 0.8, z * s * 0.8, p.seed + 7) * 0.3F;
        float gate = smoothstep(0F, p.softness + 0.02F, progress * 1.15F - arrival);

        if (gate <= 0.002F)
        {
            return;
        }

        float edgeBlocks = (scratch[1] - scratch[0]) / (float) s;
        float width = 0.03F + 0.03F * progress;
        float core = (1F - smoothstep(width * 0.5F, width, edgeBlocks)) * gate;

        /* Fine branches: a tighter net that only turns up once the decay is well along */
        voronoi(wx * s * 2.6, wy * s * 2.6, wz * s * 2.6, p.seed + 313, scratch);

        float branchBlocks = (scratch[1] - scratch[0]) / (float) (s * 2.6);
        float branchGate = smoothstep(0.35F, 0.9F, progress * 1.1F - arrival * 0.6F);
        float branch = (1F - smoothstep(width * 0.25F, width * 0.55F, branchBlocks)) * branchGate * gate;

        core = Math.max(core, branch * 0.85F);

        /* The worn rim of a break, and with the depth inset the dark that pools round it */
        float haloWidth = width * (2.4F + p.depthInset * 18F);
        float halo = (1F - smoothstep(width, haloWidth, edgeBlocks)) * gate * (0.32F + p.depthInset * 2F);
        float alpha = core + halo * (1F - core);

        if (alpha <= 0.002F)
        {
            return;
        }

        float dark = Math.max(0F, 1F - p.depthInset * 4F);
        float haloDark = Math.max(0.2F, 1F - p.depthInset * 2.5F);

        for (int i = 0; i < 3; i++)
        {
            float coreColor = p.primary[i] * dark;
            float haloColor = p.edge[i] * haloDark;

            out[i] = (coreColor * core + haloColor * halo * (1F - core)) / alpha;
        }

        out[3] = Math.min(1F, alpha);
    }
}
