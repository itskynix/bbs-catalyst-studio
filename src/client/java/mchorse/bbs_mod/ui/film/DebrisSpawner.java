package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.camera.clips.fx.DebrisClip;
import mchorse.bbs_mod.ui.framework.elements.utils.Batcher2D;
import mchorse.bbs_mod.utils.clips.ClipContext;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import mchorse.bbs_mod.mixin.client.ParticleManagerInvoker;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.random.Random;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns the bursts of the debris clips into particles. A burst fires once, when playback runs into
 * the clip's first ticks; scrubbing into the middle of a clip fires nothing, and leaving the clip
 * arms it again.
 */
public class DebrisSpawner
{
    /** How far into the clip a burst may still fire (a frame can land a tick late) */
    private static final int FIRE_WINDOW = 2;

    private static final Set<DebrisClip> FIRED = new HashSet<>();

    public static void render(MatrixStack stack, Batcher2D batcher, ClipContext context)
    {
        List<DebrisClip.Burst> bursts = DebrisClip.getBursts(context);

        FIRED.retainAll(bursts.stream().map((b) -> b.clip).toList());

        ClientWorld world = MinecraftClient.getInstance().world;

        if (world == null || !context.playing)
        {
            return;
        }

        for (DebrisClip.Burst burst : bursts)
        {
            if (burst.age <= FIRE_WINDOW && FIRED.add(burst.clip))
            {
                spawn(world, burst.clip);
            }
        }
    }

    private static void spawn(ClientWorld world, DebrisClip clip)
    {
        MinecraftClient mc = MinecraftClient.getInstance();

        /* Same clip, same scatter: a re-rendered take throws its debris the same way */
        Random random = Random.create(Double.doubleToLongBits(clip.x.get()) * 31L + Double.doubleToLongBits(clip.z.get()) + clip.tick.get());
        int count = clip.count.get();
        double x = clip.x.get();
        double y = clip.y.get();
        double z = clip.z.get();

        for (int i = 0; i < count; i++)
        {
            /* A sphere of directions leaning upward: shrapnel goes out in every direction, but nothing digs into the ground */
            double vx = random.nextGaussian();
            double vy = random.nextGaussian() * 0.6D + 0.45D;
            double vz = random.nextGaussian();
            double length = Math.max(Math.sqrt(vx * vx + vy * vy + vz * vz), 1.0E-4D);
            double base = clip.speed.get() * (0.4D + random.nextDouble() * 0.9D) / length;

            Kind kind = pick(clip, random);

            if (kind == null)
            {
                return;
            }

            double speed = base * kind.speed;
            Particle particle = ((ParticleManagerInvoker) mc.particleManager).bbs$createParticle(kind.effect,
                x + random.nextGaussian() * 0.1D, y + random.nextGaussian() * 0.1D, z + random.nextGaussian() * 0.1D,
                vx * speed, vy * speed, vz * speed);

            if (particle != null)
            {
                /* The particle constructors flatten whatever velocity they are given into a slow drift of their own; the throw is set after. */
                particle.setVelocity(vx * speed, vy * speed, vz * speed);
                mc.particleManager.addParticle(particle);
            }
        }
    }

    private record Kind(ParticleEffect effect, double speed, int weight)
    {}

    /** Weighted: sparks and shards up front, smoke only as a minor accent */
    private static Kind pick(DebrisClip clip, Random random)
    {
        List<Kind> kinds = new ArrayList<>();

        if (clip.sparks.get())
        {
            kinds.add(new Kind(ParticleTypes.CRIT, 1.5D, 14));
            kinds.add(new Kind(ParticleTypes.LAVA, 1.1D, 7));
            kinds.add(new Kind(ParticleTypes.ELECTRIC_SPARK, 1.6D, 9));
        }

        if (clip.glass.get())
        {
            kinds.add(new Kind(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.GLASS.getDefaultState()), 1D, 28));
        }

        if (clip.metal.get())
        {
            kinds.add(new Kind(new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.IRON_BLOCK.getDefaultState()), 0.9D, 32));
        }

        if (clip.smoke.get())
        {
            kinds.add(new Kind(ParticleTypes.SMOKE, 0.3D, 10));
        }

        int total = 0;

        for (Kind kind : kinds)
        {
            total += kind.weight;
        }

        if (total == 0)
        {
            return null;
        }

        int roll = random.nextInt(total);

        for (Kind kind : kinds)
        {
            roll -= kind.weight;

            if (roll < 0)
            {
                return kind;
            }
        }

        return kinds.get(0);
    }
}
