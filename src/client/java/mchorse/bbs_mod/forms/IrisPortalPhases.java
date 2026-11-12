package mchorse.bbs_mod.forms;

import net.irisshaders.iris.Iris;
import net.irisshaders.iris.fantastic.ParticleRenderingPhase;
import net.irisshaders.iris.fantastic.PhasedParticleEngine;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.MinecraftClient;

import java.lang.reflect.Field;

/**
 * Keeps Iris's idea of what is being drawn in step with a portal pass, and hands it back afterwards.
 * Loaded by {@link FormPortals} only while Iris is loaded.
 *
 * <p>The pass runs after the world has drawn, when the pipeline's phase is none and the particle
 * engine is left at the translucent half of its split. Entities drawn then would not be told apart
 * as entities by a shader pack, and the opaque particle sheets would be left out. Both are set for
 * the part of the pass that needs them and put back as they were found, so nothing the pass did is
 * seen by what the frame still has to draw.</p>
 */
final class IrisPortalPhases
{
    private static Field particlePhaseField;
    private static boolean particlePhaseSearched;
    private static boolean failed;

    private IrisPortalPhases()
    {}

    private static WorldRenderingPipeline pipeline()
    {
        return Iris.getPipelineManager().getPipelineNullable();
    }

    /** Sets the pipeline's phase to entities; returns what it was, for {@link #endPhase}, or null. */
    static Object beginEntities()
    {
        if (failed)
        {
            return null;
        }

        try
        {
            WorldRenderingPipeline pipeline = pipeline();

            if (pipeline == null)
            {
                return null;
            }

            WorldRenderingPhase previous = pipeline.getPhase();

            pipeline.setPhase(WorldRenderingPhase.ENTITIES);

            return previous;
        }
        catch (Throwable e)
        {
            fail(e);

            return null;
        }
    }

    static void endPhase(Object previous)
    {
        if (!(previous instanceof WorldRenderingPhase phase))
        {
            return;
        }

        try
        {
            WorldRenderingPipeline pipeline = pipeline();

            if (pipeline != null)
            {
                pipeline.setPhase(phase);
            }
        }
        catch (Throwable e)
        {
            fail(e);
        }
    }

    /**
     * Lets the particle engine draw every sheet, opaque ones too; returns the half it was left at,
     * for {@link #endParticles}, or null when there is nothing to put back.
     */
    static Object beginParticles()
    {
        if (failed)
        {
            return null;
        }

        try
        {
            Object engine = MinecraftClient.getInstance().particleManager;

            if (!(engine instanceof PhasedParticleEngine phased))
            {
                return null;
            }

            Object previous = particlePhase(engine);

            phased.setParticleRenderingPhase(ParticleRenderingPhase.EVERYTHING);

            /* Unreadable: the world leaves it at the translucent half once its own particles are drawn */
            return previous == null ? ParticleRenderingPhase.TRANSLUCENT : previous;
        }
        catch (Throwable e)
        {
            fail(e);

            return null;
        }
    }

    static void endParticles(Object previous)
    {
        if (!(previous instanceof ParticleRenderingPhase phase))
        {
            return;
        }

        try
        {
            if (MinecraftClient.getInstance().particleManager instanceof PhasedParticleEngine phased)
            {
                phased.setParticleRenderingPhase(phase);
            }

            /* The engine sets the pipeline to particles as it starts and to none as it ends; the pass
             * was found at whatever the caller saved, which it puts back itself */
        }
        catch (Throwable e)
        {
            fail(e);
        }
    }

    /** The pipeline's phase as it is, for a part of the pass that changes it on its own (particles). */
    static Object currentPhase()
    {
        if (failed)
        {
            return null;
        }

        try
        {
            WorldRenderingPipeline pipeline = pipeline();

            return pipeline == null ? null : pipeline.getPhase();
        }
        catch (Throwable e)
        {
            fail(e);

            return null;
        }
    }

    private static Object particlePhase(Object engine)
    {
        try
        {
            if (!particlePhaseSearched)
            {
                particlePhaseSearched = true;

                for (Class<?> c = engine.getClass(); c != null && particlePhaseField == null; c = c.getSuperclass())
                {
                    for (Field field : c.getDeclaredFields())
                    {
                        if (field.getType() == ParticleRenderingPhase.class && !java.lang.reflect.Modifier.isStatic(field.getModifiers()))
                        {
                            field.setAccessible(true);
                            particlePhaseField = field;

                            break;
                        }
                    }
                }
            }

            return particlePhaseField == null ? null : particlePhaseField.get(engine);
        }
        catch (Throwable e)
        {
            return null;
        }
    }

    private static void fail(Throwable e)
    {
        failed = true;

        System.err.println("[BBS] Keeping Iris's rendering phase through a portal failed, and is left alone from now on: " + e);
    }
}
