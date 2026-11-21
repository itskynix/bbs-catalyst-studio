package mchorse.bbs_mod.nav;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.blocks.entities.ModelBlockEntity;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.pose.Transform;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * AI for a form placed in the world as a model block. A model block is not part of any film, so its
 * walk is not worked out ahead: it is lived, one step every tick of the block, with the same steps an
 * actor of a film takes (see {@link AiSimulation}) - a place picked on the mesh of the nearest
 * navigation volume, found with A*, walked, waited at, with the others of its kind kept out of the way
 * and the head turning before the body.
 *
 * <p>The block's stand-in entity is put where the walk is before it updates, so its legs swing by how
 * far it went; the renderer moves the form there and turns it, between the place of the tick before
 * and this one so it glides rather than steps.</p>
 */
public final class ModelBlockAi
{
    private static final Map<ModelBlockEntity, Live> LIVE = new WeakHashMap<>();

    /** One walking block: its agent and where it was on the tick before */
    private static final class Live
    {
        AiSimulation.Agent agent;
        double prevX;
        double prevY;
        double prevZ;
        float prevYaw;
        float prevHead;
        float prevPitch;
    }

    /** What the renderer needs for a frame: the offset from the block's own place, and the turns */
    public record Shown(double dx, double dy, double dz, float bodyYaw, float prevBodyYaw, float headYaw, float prevHeadYaw, float pitch, float prevPitch, float delta) {}

    /** The camera of the game, at the feet of someone standing under it */
    private static final AiSimulation.Target CAMERA = (tick, out) ->
    {
        net.minecraft.util.math.Vec3d eye = net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();

        out[0] = eye.x;
        out[1] = eye.y - 1.62D;
        out[2] = eye.z;
    };

    private ModelBlockAi()
    {}

    public static void register()
    {
        ModelBlockEntity.CLIENT_AI = ModelBlockAi::tick;
    }

    private static Film film()
    {
        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);

        return panel == null ? null : panel.getData();
    }

    /** One step of a block's walk, called from its tick before its stand-in updates */
    private static void tick(ModelBlockEntity block, IEntity entity)
    {
        Form form = block.getProperties().getForm();

        if (form == null || !form.hasAi.get())
        {
            LIVE.remove(block);

            return;
        }

        AiSimulation.Volume volume = AiSimulation.findVolume(film());

        if (volume == null)
        {
            LIVE.remove(block);

            return;
        }

        NavGrid grid = NavGrids.get(volume.form(), volume.x(), volume.y(), volume.z());

        if (grid == null || grid.getNodes().isEmpty())
        {
            return;
        }

        Live live = LIVE.get(block);

        if (live == null)
        {
            NavGrid.Node node = grid.nearest(entity.getX(), entity.getY(), entity.getZ());

            if (node == null)
            {
                return;
            }

            AiSimulation.Agent agent = new AiSimulation.Agent();

            agent.id = block.getPos().toShortString();
            agent.settings = form;
            agent.random = new Random(block.getPos().asLong() * 31L + form.aiSeed.get() * 7919L);
            agent.x = node.centerX();
            agent.y = node.y;
            agent.z = node.centerZ();
            agent.idle = 1 + agent.random.nextInt(20);

            live = new Live();
            live.agent = agent;
            live.prevX = agent.x;
            live.prevY = agent.y;
            live.prevZ = agent.z;
            LIVE.put(block, live);
        }

        AiSimulation.Agent agent = live.agent;

        /* The form may be a new object after an edit: the walk goes on with its settings */
        agent.settings = form;

        /* A block has no film to find an actor in: the camera is what it can follow */
        String reference = form.aiFollowTarget.get().trim();

        agent.target = reference.equalsIgnoreCase("camera") || reference.equalsIgnoreCase("kamera") ? CAMERA : null;
        agent.watching = agent.target != null && agent.watching;
        live.prevX = agent.x;
        live.prevY = agent.y;
        live.prevZ = agent.z;
        live.prevYaw = agent.yaw;
        live.prevHead = agent.head;
        live.prevPitch = agent.pitch;

        List<AiSimulation.Agent> agents = new ArrayList<>();

        for (Live other : LIVE.values())
        {
            agents.add(other.agent);
        }

        AiSimulation.step(agent, agents, grid);

        entity.setPrevX(live.prevX);
        entity.setPrevY(live.prevY);
        entity.setPrevZ(live.prevZ);
        entity.setPosition(agent.x, agent.y, agent.z);
    }

    /** Where to draw a block's form on this frame, or null when the block is not walking */
    public static Shown shown(ModelBlockEntity block, float delta)
    {
        Live live = LIVE.get(block);

        if (live == null)
        {
            return null;
        }

        AiSimulation.Agent agent = live.agent;
        Transform transform = block.getProperties().getTransform();
        double anchorX = block.getPos().getX() + 0.5D + transform.translate.x;
        double anchorY = block.getPos().getY() + transform.translate.y;
        double anchorZ = block.getPos().getZ() + 0.5D + transform.translate.z;
        double x = live.prevX + (agent.x - live.prevX) * delta;
        double y = live.prevY + (agent.y - live.prevY) * delta;
        double z = live.prevZ + (agent.z - live.prevZ) * delta;

        return new Shown(x - anchorX, y - anchorY, z - anchorZ, agent.yaw, near(live.prevYaw, agent.yaw), agent.head, near(live.prevHead, agent.head), agent.pitch, live.prevPitch, delta);
    }

    private static float near(float previous, float current)
    {
        while (previous - current > 180F)
        {
            previous -= 360F;
        }

        while (previous - current < -180F)
        {
            previous += 360F;
        }

        return previous;
    }
}
