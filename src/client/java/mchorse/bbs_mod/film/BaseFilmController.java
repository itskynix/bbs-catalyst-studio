package mchorse.bbs_mod.film;

import com.mojang.blaze3d.systems.RenderSystem;
import mchorse.bbs.compat.mtr.MtrCompatHelper;
import mchorse.bbs.compat.mtr.MtrConfig;
import mchorse.bbs.compat.mtr.MtrGovernor;
import mchorse.bbs_mod.BBSModClient;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mchorse.bbs_mod.BBSSettings;
import mchorse.bbs_mod.client.BBSRendering;
import mchorse.bbs_mod.client.renderer.ItemUseEffects;
import mchorse.bbs_mod.client.renderer.LivePlayerItemUse;
import mchorse.bbs_mod.client.renderer.ThirdPersonItemUse;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.entity.ActorEntity;
import mchorse.bbs_mod.film.replays.RecordFrame;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayItemUse;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.tracks.AnchorResolver;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviour;
import mchorse.bbs_mod.film.replays.tracks.TrackBehaviours;
import mchorse.bbs_mod.film.replays.tracks.TrackContext;
import mchorse.bbs_mod.forms.FormPortals;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.entities.StubEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.utils.FormIllusionValues;
import mchorse.bbs_mod.forms.renderers.utils.FormIllusions;
import mchorse.bbs_mod.forms.renderers.utils.RenderFrame;
import mchorse.bbs_mod.utils.colors.Colors;
import mchorse.bbs_mod.forms.forms.utils.Anchor;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.mixin.EntityInvoker;
import mchorse.bbs_mod.mixin.LivingEntityRollAccessor;
import mchorse.bbs_mod.mixin.client.ClientPlayerEntityAccessor;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.utils.Pair;
import mchorse.bbs_mod.utils.profiler.BBSProfiler;
import mchorse.bbs_mod.api.client.events.FilmEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public abstract class BaseFilmController
{
    public final Film film;

    /** The film's entities keyed by their replay's stable id, in replay-list order. */
    public final Map<String, IEntity> entities = new LinkedHashMap<>();

    public boolean paused;
    public int exception = -1;

    private final AnchorResolver anchors = this::resolveAnchor;

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vector3f TEMP_VECTOR = new Vector3f();

    /* Rendering helpers */

    public BaseFilmController(Film film)
    {
        this.film = film;
    }

    /**
     * The film's replays, or nothing at all when no film is open — the editor keeps its controller
     * around while the panel shows no film (see {@link #createEntities()}, which returns early for
     * the same reason). These loops used to walk the entity map, which is simply empty then; walking
     * the replay list instead means the absent film has to be answered for here.
     */
    private List<Replay> replays()
    {
        return this.film == null ? Collections.emptyList() : this.film.replays.getList();
    }

    public Map<String, IEntity> getEntities()
    {
        return this.entities;
    }

    public void togglePause()
    {
        this.paused = !this.paused;
    }

    public void createEntities()
    {
        this.entities.clear();

        if (this.film == null)
        {
            return;
        }

        int i = 0;

        for (Replay replay : this.film.replays.getList())
        {
            if (replay.enabled.get())
            {
                World world = MinecraftClient.getInstance().world;
                IEntity entity = new StubEntity(world);
                int ticks = replay.getTick(this.getTick());

                entity.setForm(FormUtils.copy(replay.form.get()));
                replay.keyframes.apply(ticks, entity);
                this.applyMtrSync(replay, ticks, entity, true);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());

                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevPitch(entity.getPitch());
                entity.setPrevBodyYaw(entity.getBodyYaw());

                this.entities.put(replay.getId(), entity);
            }

            i += 1;
        }

        FilmEvents.CREATED.invoker().onFilmCreated(this);
    }

    public abstract Map<String, Integer> getActors();

    public abstract int getTick();

    public boolean hasFinished()
    {
        return false;
    }

    public void update()
    {
        this.updateEntities(this.getTick());
    }

    protected void updateEntities(int ticks)
    {
        FilmEvents.TICK_BEFORE.invoker().onFilmTick(this, ticks);

        if (this.film != null && this.film.worldTimeline != null && MtrCompatHelper.isMtrLoaded())
        {
            MtrCompatHelper.registerWorldTimelineBridge();
            this.film.worldTimeline.apply(ticks);
        }

        List<Replay> replays = this.replays();

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            IEntity entity = this.entities.get(replay.getId());

            /* Before the pause check: a switch flipped in the editor shows on a paused film too */
            VehicleRide.syncSettings(this.film, replay, entity);

            if (entity == null || !this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                /* Replay-local: a looping replay wraps the film tick to its own window, and that must not
                 * carry over to the next replay in the loop (which would then wrap an already wrapped tick). */
                int replayTicks = replay.getTick(ticks);

                this.prepareMtrLimbDelta(replay, replayTicks, entity);
                this.updateEntityAndForm(entity, replayTicks);
                this.applyReplay(replay, replayTicks, entity);

                /* Vanilla's eating and drinking effects: the actor never ticks
                 * an item use, so the crumbs and chewing come from the clip.
                 * The take the real player acts is no exception - the film's use
                 * is only answered while drawing, so vanilla's own tick spits
                 * nothing for them either (see LivePlayerItemUse). */
                ItemUseEffects.tick(replay, entity, replayTicks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof ActorEntity actor)
                        {
                            boolean mtrSynced = this.isMtrSyncedAt(replay, replayTicks);

                            /* Force synchronize entity angles */
                            float yaw = mtrSynced ? entity.getYaw() : replay.keyframes.yaw.interpolate(replayTicks).floatValue();
                            float pitch = mtrSynced ? entity.getPitch() : replay.keyframes.pitch.interpolate(replayTicks).floatValue();

                            actor.setYaw(yaw);
                            actor.setHeadYaw(mtrSynced ? entity.getHeadYaw() : replay.keyframes.headYaw.interpolate(replayTicks).floatValue());
                            actor.setBodyYaw(mtrSynced ? entity.getBodyYaw() : replay.keyframes.bodyYaw.interpolate(replayTicks).floatValue());
                            actor.setPitch(pitch);

                            /* And its position, for the same reason the angles are forced: the body
                             * is drawn from these keyframes, while the entity's own position comes
                             * over the network and vanilla eases it in over three ticks. Vanilla
                             * settles a blow against the hitbox the client can see, so a hitbox
                             * trailing the body means aiming at the body and missing. Zero
                             * interpolation steps is what stops that easing from dragging it back. */
                            double x = mtrSynced ? entity.getX() : replay.keyframes.x.interpolate(replayTicks);
                            double y = mtrSynced ? entity.getY() : replay.keyframes.y.interpolate(replayTicks);
                            double z = mtrSynced ? entity.getZ() : replay.keyframes.z.interpolate(replayTicks);

                            //? if >=1.20.4 {
                            actor.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, 0);
                            //?} else {
                            /*actor.updateTrackedPositionAndAngles(x, y, z, yaw, pitch, 0, false);
                            *///?}
                            actor.setPosition(x, y, z);

                            /* The blow itself lands on the entity, but the body that shows it is the
                             * replay's, so the flash has to be carried across. */
                            entity.setHurtTimer(actor.hurtTime);

                            replay.applyClientActions(replayTicks, new MCEntity(anEntity), this.film);
                        }
                        else if (anEntity instanceof PlayerEntity player)
                        {
                            double x = replay.keyframes.x.interpolate(replayTicks);
                            double y = replay.keyframes.y.interpolate(replayTicks);
                            double z = replay.keyframes.z.interpolate(replayTicks);
                            double prevX = replay.keyframes.x.interpolate(replayTicks - 1);
                            double prevY = replay.keyframes.y.interpolate(replayTicks - 1);
                            double prevZ = replay.keyframes.z.interpolate(replayTicks - 1);

                            player.setVelocity(x - prevX, y - prevY, z - prevZ);
                        }
                    }
                }
            }
        }

        FilmEvents.TICK_AFTER.invoker().onFilmTick(this, ticks);
    }

    public void updateEndWorld()
    {
        int ticks = this.getTick();

        List<Replay> replays = this.replays();

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            IEntity entity = this.entities.get(replay.getId());

            if (entity == null || !this.canUpdate(i, replay, entity, UpdateMode.UPDATE))
            {
                continue;
            }

            if (replay != null)
            {
                /* Replay-local, like in updateEntities: a looping replay wraps the film tick to its own
                 * window, and writing that back into the loop variable handed the wrapped tick to every
                 * replay after it — which then wrapped an already wrapped tick. */
                int replayTicks = replay.getTick(ticks);

                Map<String, Integer> actors = this.getActors();

                if (actors != null)
                {
                    Integer entityId = actors.get(replay.getId());

                    if (entityId != null)
                    {
                        Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                        if (anEntity instanceof PlayerEntity player)
                        {
                            boolean mtrSynced = this.applyMtrSync(replay, replayTicks, entity, false);
                            double x = mtrSynced ? entity.getX() : replay.keyframes.x.interpolate(replayTicks);
                            double y = mtrSynced ? entity.getY() : replay.keyframes.y.interpolate(replayTicks);
                            double z = mtrSynced ? entity.getZ() : replay.keyframes.z.interpolate(replayTicks);
                            boolean sneaking = EntityState.isOn(replay.keyframes.state(EntityState.SNEAKING).interpolate(replayTicks));
                            boolean grounded = EntityState.isOn(replay.keyframes.state(EntityState.GROUNDED).interpolate(replayTicks));
                            boolean swimming = EntityState.isOn(replay.keyframes.state(EntityState.SWIMMING).interpolate(replayTicks));
                            boolean gliding = EntityState.isOn(replay.keyframes.state(EntityState.GLIDING).interpolate(replayTicks));

                            Vec3d pos = player.getPos();

                            /* Probe downwards so vanilla's collision registers the floor - see
                             * ReplayKeyframes#GRAVITY_PROBE. */
                            double dY = y - pos.y - (grounded ? ReplayKeyframes.GRAVITY_PROBE : 0D);

                            player.move(MovementType.SELF, new Vec3d(x - pos.x, dY, z - pos.z));
                            player.setPosition(x, y, z);

                            player.setSneaking(sneaking);
                            player.setOnGround(grounded);

                            /* The player's own tick overwrites this from the input every tick, but
                             * baseTick (which spawns the sprinting particles) runs before it, so a
                             * value written at the end of the world tick is the one vanilla sees. */
                            player.setSprinting(EntityState.isOn(replay.keyframes.state(EntityState.SPRINTING).interpolate(replayTicks)));

                            /* Same window, same reason: written at the end of the world tick so
                             * the player's own tick doesn't get to overwrite them first. */
                            player.setSwimming(swimming);
                            ((EntityInvoker) player).bbs$setFlag(EntityState.FALL_FLYING_FLAG, gliding);
                            player.setPose(EntityState.pose(gliding, swimming, sneaking));
                            ((LivingEntityRollAccessor) player).bbs$setRoll(replay.keyframes.roll.interpolate(replayTicks).intValue());

                            /* First person teleports the player from keyframes instead of walking it, so vanilla's
                             * stride distance (the view-bobbing amplitude) is computed from a zero velocity and stays
                             * flat. Re-derive it from the actual per-tick displacement (the same source as the limb
                             * animation) with vanilla's own easing. prevStrideDistance already holds last tick's value
                             * (snapshotted by the player tick), so only the current one is advanced — keeping the bob
                             * smooth between frames. */
                            float dx = (float) (player.getX() - player.prevX);
                            float dz = (float) (player.getZ() - player.prevZ);
                            float stride = grounded ? Math.min(0.1F, (float) Math.sqrt(dx * dx + dz * dz)) : 0F;

                            player.strideDistance = player.prevStrideDistance + (stride - player.prevStrideDistance) * 0.4F;

                            if (player instanceof ClientPlayerEntityAccessor accessor)
                            {
                                accessor.bbs$setIsSneakingPose(sneaking);
                            }

                            if (player instanceof ClientPlayerEntity playerEntity)
                            {
                                playerEntity.input.sneaking = sneaking;
                            }

                            player.fallDistance = replay.keyframes.fall.interpolate(replayTicks).floatValue();
                        }
                    }
                }
            }
        }
    }

    protected void updateEntityAndForm(IEntity entity, int tick)
    {
        entity.update();

        if (entity.getForm() != null)
        {
            entity.getForm().update(entity);
        }
    }

    protected void applyReplay(Replay replay, int ticks, IEntity entity)
    {
        replay.keyframes.apply(ticks, entity);
        this.applyMtrSync(replay, ticks, entity, false);
        ReplayLookAt.apply(replay, this.film, ticks, entity);
        replay.applyClientActions(ticks, entity, this.film);
    }

    protected boolean isMtrSyncedAt(Replay replay, float tick)
    {
        if (replay == null || !MtrConfig.isPlaybackSync() || !MtrCompatHelper.isMtrLoaded())
        {
            return false;
        }

        RecordFrame frame = replay.keyframes.getInterpolatedMtrFrame(tick);

        if (frame == null || !frame.isMtrRiding)
        {
            return false;
        }

        return MtrCompatHelper.getVehicleCarPose(MtrGovernor.resolveLiveId(frame.mtrVehicleId), frame.mtrCarNumber) != null;
    }

    protected void prepareMtrLimbDelta(Replay replay, int replayTicks, IEntity entity)
    {
        if (replay == null || entity == null || !MtrConfig.isPlaybackSync() || !MtrCompatHelper.isMtrLoaded())
        {
            return;
        }

        RecordFrame curr = replay.keyframes.getMtrFrame(replayTicks);

        if (curr == null || !curr.isMtrRiding)
        {
            return;
        }

        if (MtrCompatHelper.getVehicleCarPose(MtrGovernor.resolveLiveId(curr.mtrVehicleId), curr.mtrCarNumber) == null)
        {
            return;
        }

        RecordFrame prev = replay.keyframes.getMtrFrame(replayTicks - 1);

        if (prev != null && prev.isMtrRiding && prev.mtrVehicleId == curr.mtrVehicleId && prev.mtrCarNumber == curr.mtrCarNumber)
        {
            entity.setPrevX(entity.getX() - (curr.mtrLocalX - prev.mtrLocalX));
            entity.setPrevY(entity.getY() - (curr.mtrLocalY - prev.mtrLocalY));
            entity.setPrevZ(entity.getZ() - (curr.mtrLocalZ - prev.mtrLocalZ));
        }
        else
        {
            entity.setPrevX(entity.getX());
            entity.setPrevY(entity.getY());
            entity.setPrevZ(entity.getZ());
        }
    }

    protected boolean applyMtrSync(Replay replay, float tick, IEntity entity, boolean syncPrev)
    {
        if (replay == null || entity == null || !MtrConfig.isPlaybackSync() || !MtrCompatHelper.isMtrLoaded())
        {
            return false;
        }

        RecordFrame frame = replay.keyframes.getInterpolatedMtrFrame(tick);

        if (frame == null || !frame.isMtrRiding)
        {
            return false;
        }

        /* Read-only: the vehicle itself is driven solely by MtrGovernor (through the world timeline).
         * The recorded vehicle may be played by another live vehicle of the same siding. */
        MtrCompatHelper.PositionAndRotation carPose = MtrCompatHelper.getVehicleCarPose(MtrGovernor.resolveLiveId(frame.mtrVehicleId), frame.mtrCarNumber);

        if (carPose == null)
        {
            return false;
        }

        MtrCompatHelper.TransformedPose worldPose = MtrCompatHelper.transformToWorld(
            carPose,
            frame.mtrLocalX,
            frame.mtrLocalY,
            frame.mtrLocalZ,
            frame.mtrLocalYaw,
            frame.mtrLocalPitch
        );

        if (worldPose == null)
        {
            return false;
        }

        float recordedYaw = replay.keyframes.yaw.interpolate(tick).floatValue();
        float recordedHeadYaw = replay.keyframes.headYaw.interpolate(tick).floatValue();
        float recordedBodyYaw = replay.keyframes.bodyYaw.interpolate(tick).floatValue();

        float headOffset = MathHelper.wrapDegrees(recordedHeadYaw - recordedYaw);
        float bodyOffset = MathHelper.wrapDegrees(recordedBodyYaw - recordedYaw);

        float worldHeadYaw = worldPose.yaw + headOffset;
        float worldBodyYaw = worldPose.yaw + bodyOffset;

        entity.setPosition(worldPose.x, worldPose.y, worldPose.z);
        entity.setYaw(worldPose.yaw);
        entity.setPitch(worldPose.pitch);
        entity.setHeadYaw(worldHeadYaw);
        entity.setBodyYaw(worldBodyYaw);

        if (syncPrev)
        {
            entity.setPrevX(worldPose.x);
            entity.setPrevY(worldPose.y);
            entity.setPrevZ(worldPose.z);
            entity.setPrevYaw(worldPose.yaw);
            entity.setPrevPitch(worldPose.pitch);
            entity.setPrevHeadYaw(worldHeadYaw);
            entity.setPrevBodyYaw(worldBodyYaw);
        }

        return true;
    }

    public void startRenderFrame(float transition)
    {
        if (this.film != null && this.film.worldTimeline != null && MtrCompatHelper.isMtrLoaded())
        {
            MtrCompatHelper.registerWorldTimelineBridge();
            this.film.worldTimeline.apply(this.getTick() + (this.paused ? 0F : transition));
        }

        List<Replay> replays = this.replays();

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            IEntity entity = this.entities.get(replay.getId());

            if (entity == null || !this.canUpdate(i, replay, entity, UpdateMode.PROPERTIES))
            {
                continue;
            }

            float delta = this.getTransition(entity, transition);
            int tick = replay.getTick(this.getTick());
            float exactTick = tick + delta;
            boolean mtrSynced = this.applyMtrSync(replay, exactTick, entity, true);

            /* Apply property */
            Form form1 = entity.getForm();

            this.applyTracks(replay, form1, exactTick, delta);

            /* The item use of this take, published for everything that draws
             * its body: the procedural animator poses the arms with it, and the
             * model form renderer makes the vanilla item predicates fire on the
             * held items (a drawn bow bends and shows its arrow) */
            ItemUsePose.Use use = ReplayItemUse.compute(replay, exactTick, true);
            ItemUsePose.Use offUse = ReplayItemUse.compute(replay, exactTick, false);

            ThirdPersonItemUse.set(ThirdPersonItemUse.keyOf(entity), use, offUse);

            Map<String, Integer> actors = this.getActors();

            if (actors != null)
            {
                Integer entityId = actors.get(replay.getId());

                if (entityId != null)
                {
                    Entity anEntity = MinecraftClient.getInstance().world.getEntityById(entityId);

                    ThirdPersonItemUse.set(anEntity, use, offUse);

                    if (anEntity instanceof ActorEntity actor)
                    {
                        this.applyTracks(replay, actor.getForm(), exactTick, delta);

                        if (mtrSynced)
                        {
                            actor.setPosition(entity.getX(), entity.getY(), entity.getZ());
                            actor.prevX = entity.getX();
                            actor.prevY = entity.getY();
                            actor.prevZ = entity.getZ();
                            actor.lastRenderX = entity.getX();
                            actor.lastRenderY = entity.getY();
                            actor.lastRenderZ = entity.getZ();
                            actor.setYaw(entity.getYaw());
                            actor.setHeadYaw(entity.getHeadYaw());
                            actor.setBodyYaw(entity.getBodyYaw());
                            actor.setPitch(entity.getPitch());
                            actor.prevYaw = entity.getYaw();
                            actor.prevHeadYaw = entity.getHeadYaw();
                            actor.prevBodyYaw = entity.getBodyYaw();
                            actor.prevPitch = entity.getPitch();
                        }
                    }
                    else if (anEntity instanceof PlayerEntity player)
                    {
                        /* The first person hand is vanilla's, and it asks the
                         * live player what it is using - the film has to say */
                        LivePlayerItemUse.apply(player, use, offUse);

                        Morph morph = Morph.getMorph(player);

                        if (morph != null)
                        {
                            this.applyTracks(replay, morph.getForm(), exactTick, delta);
                        }

                        float yawHead = mtrSynced ? entity.getHeadYaw() : replay.keyframes.headYaw.interpolate(exactTick).floatValue();
                        float yawBody = mtrSynced ? entity.getBodyYaw() : replay.keyframes.bodyYaw.interpolate(exactTick).floatValue();
                        float pitch = mtrSynced ? entity.getPitch() : replay.keyframes.pitch.interpolate(exactTick).floatValue();

                        player.setYaw(yawHead);
                        player.setHeadYaw(yawHead);
                        player.setPitch(pitch);
                        player.setBodyYaw(yawBody);
                        player.prevYaw = yawHead;
                        player.prevHeadYaw = yawHead;
                        player.prevPitch = pitch;
                        player.prevBodyYaw = yawBody;
                    }
                }
            }
        }
    }

    /**
     * Lay one replay's tracks over a form for this frame: the per-frame overrides the track kinds
     * leave behind are dropped first, so a track that was deleted (or whose keyframes ran out) stops
     * driving the form, and then every track applies itself.
     *
     * <p>This used to be two passes written side by side — {@code FormProperties.applyProperties}
     * for properties, bones and materials, and a second dispatcher here for the IK, pole, physics
     * and wind tracks. Both walked the same map and matched the same ids; the kinds now say what
     * they do themselves (see {@link TrackBehaviour}).</p>
     */
    protected void applyTracks(Replay replay, Form root, float tick, float transition)
    {
        if (replay == null || root == null)
        {
            return;
        }

        TrackBehaviours.clearOverrides(root);

        replay.properties.apply(TrackContext.frame(root, transition, this.anchors), tick, 1F);
    }

    /**
     * Resolving an anchor is the one thing a track cannot do on its own: it means composing the bone
     * matrices of another replay's live entity, which only the controller has.
     */
    private Vector3f resolveAnchor(Anchor anchor, float transition)
    {
        return resolveAnchor(this.entities, anchor, transition);
    }

    /**
     * The same resolution over any set of entities — the live ones above, or a headless sample's
     * (see {@link IKBake}). The position comes back in a shared holder, good until the next call.
     */
    public static Vector3f resolveAnchor(Map<String, IEntity> entities, Anchor anchor, float transition)
    {
        if (entities.get(anchor.replay) == null)
        {
            return null;
        }

        Pair<Matrix4f, Float> matrix = FilmMatrices.getTotalMatrix(entities, anchor, IDENTITY, 0D, 0D, 0D, transition, 0, true);

        return (matrix.a != null ? matrix.a : IDENTITY).getTranslation(TEMP_VECTOR);
    }

    /**
     * The matrix-cache key of a bone path: the {@code pose.bones.} namespace drops out, leaving the
     * owning form's path and the bone ({@code 0/1/pose.bones.head} &rarr; {@code 0/1/head}), which is
     * how {@link MatrixCache} keys its entries. A path that is not a bone track passes through.
     */
    protected float getTransition(IEntity entity, float transition)
    {
        /* A ridden vehicle moves on in a paused film: it is the drive that moves it, not the film */
        if (VehicleRide.isRidden(entity))
        {
            return transition;
        }

        return this.paused ? 0F : transition;
    }

    protected boolean canUpdate(int i, Replay replay, IEntity entity, UpdateMode updateMode)
    {
        /* Ridden: drawn and ticked whatever the film is doing - paused, or recording this very replay */
        if (VehicleRide.isRidden(this.film, replay))
        {
            return true;
        }

        if (this.paused && (updateMode == UpdateMode.UPDATE))
        {
            return false;
        }

        return i != this.exception;
    }

    /**
     * Half-extent of the box a replay is culled by, around its entity. Deliberately generous:
     * a form reaches past its hitbox (trails, particles, scaled models), and a box this large
     * still culls everything a big set keeps far outside the shot.
     */
    private static final double CULL_RADIUS = 32D;

    public void render(WorldRenderContext context)
    {
        RenderSystem.enableDepthTest();

        BBSProfiler.begin(BBSProfiler.Timer.WORLD_FORMS);

        List<Replay> replays = this.replays();
        Frustum frustum = BBSSettings.frustumCulling.get() && !BBSRendering.isIrisShadowPass() && !FormPortals.isPassActive() ? context.frustum() : null;

        for (int i = 0; i < replays.size(); i++)
        {
            Replay replay = replays.get(i);
            IEntity entity = this.entities.get(replay.getId());

            if (entity == null || !this.canUpdate(i, replay, entity, UpdateMode.RENDER))
            {
                continue;
            }

            /* Claimed before culling, not inside the draw: the film and the world cull by
             * different boxes, and an actor this film skipped would otherwise be picked back up
             * by the vanilla renderer and drawn at its networked position. */
            if (!this.claimActor(replay, entity))
            {
                continue;
            }

            float renderTick = replay.getTick(this.getTick()) + this.getTransition(entity, context./*$ wtd*/tickDelta()/**/);

            this.applyMtrSync(replay, renderTick, entity, true);

            if (frustum != null && this.isCulled(frustum, replay, entity))
            {
                continue;
            }

            this.renderEntity(context, replay, entity);
        }

        BBSProfiler.end(BBSProfiler.Timer.WORLD_FORMS);

        /* Outside the timer: what an addon draws is the addon's cost, not the forms'. */
        FilmEvents.RENDER_AFTER.invoker().onFilmRender(this, context);
    }

    /**
     * Whether the replay's generous surroundings are entirely off screen. An anchored form
     * stands wherever its target does, not at its entity, so it is never culled by the entity's
     * position; culling a replay others hang off is fine — anchors read its matrices through
     * the pose pipeline, not through its draw.
     */
    private boolean isCulled(Frustum frustum, Replay replay, IEntity entity)
    {
        Form form = entity.getForm();

        /* A portal is as large as it is set to be, and its window may be on screen with its entity far off it */
        if (FormPortals.hasPortal(form))
        {
            return false;
        }

        if (form == null || form.anchor.get().hasTarget())
        {
            return false;
        }

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        return !frustum.isVisible(new Box(
            x - CULL_RADIUS, y - CULL_RADIUS, z - CULL_RADIUS,
            x + CULL_RADIUS, y + CULL_RADIUS, z + CULL_RADIUS
        ));
    }

    /**
     * Take responsibility for drawing this replay's actor body, so the entity renderer leaves it
     * alone. The actor flag gives a replay a body in the world &mdash; collisions, blows, pressure
     * plates &mdash; it does not change how the replay is drawn. Drawn from the keyframes like
     * every other replay, it moves without riding the network, and it keeps what belongs to a
     * replay rather than to an entity: its shadow, its relative origin, its onion skin, its tag.
     *
     * <p>What the shell is being put through travels the other way, because the shell is the thing
     * blows land on and the body drawn over it is the thing anyone looks at: the flash of a hit and
     * the 20 ticks of falling over went to a body nobody was watching, so hitting an actor did
     * nothing visible and killing one left the keyframed body standing.</p>
     *
     * @return whether there is still a body to draw. A shell that finished falling is taken out of
     *         the world, and the film lets its body go with it - the way a death looked when vanilla
     *         drew actors. A shell simply not here (never spawned, out of tracking range) is not a
     *         death, and the keyframes are the whole reason the film draws the body itself.
     */
    private boolean claimActor(Replay replay, IEntity entity)
    {
        if (!replay.actor.get())
        {
            return true;
        }

        Map<String, Integer> actors = this.getActors();
        Integer entityId = actors == null ? null : actors.get(replay.getId());

        if (entityId != null)
        {
            BBSModClient.getFilms().markActorDrawn(entityId);

            if (MinecraftClient.getInstance().world.getEntityById(entityId) instanceof ActorEntity actor)
            {
                /* The higher of the recorded flash and the one being taken right now, so a replay
                 * that carries a damage track keeps it while its shell can still be hit. Read from
                 * the track rather than from the body, which holds the answer of the frame before:
                 * a paused editor stops refreshing it, and comparing against it would have latched
                 * the first blow on forever. */
                int recorded = replay.keyframes.damage.interpolate(replay.getTick(this.getTick())).intValue();

                entity.setHurtTimer(Math.max(recorded, actor.hurtTime));

                /* Taken as it stands, which is also what puts a body back on its feet: the actor
                 * spawned by the next restart is alive and counts zero. */
                entity.setDeathTime(actor.deathTime);

                return true;
            }
        }

        return entity.getDeathTime() <= 0;
    }

    protected void renderEntity(WorldRenderContext context, Replay replay, IEntity entity)
    {
        FilmControllerContext filmContext = getFilmControllerContext(context, replay, entity);

        filmContext.transition = getTransition(entity, context./*$ wtd*/tickDelta()/**/);

        FilmEntityRenderer.renderEntity(filmContext);

        this.renderIllusionTrail(context, replay, entity);
    }

    /**
     * The illusions of a form with a delay: the actor drawn again as it stood some ticks ago, once
     * for each copy, each lagging further behind and fainter. The actor is put back to a past tick
     * the way the onion skin does it, drawn, and returned to where it was, position and all.
     */
    private void renderIllusionTrail(WorldRenderContext context, Replay replay, IEntity entity)
    {
        Form form = entity.getForm();

        if (form == null || !form.illusions.isActive() || form.illusions.delay.get() <= 0 || FormIllusions.isDrawing())
        {
            return;
        }

        FormIllusionValues illusions = form.illusions;
        int ticks = replay.getTick(this.getTick());
        float transition = this.getTransition(entity, context./*$ wtd*/tickDelta()/**/);

        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();
        double px = entity.getPrevX();
        double py = entity.getPrevY();
        double pz = entity.getPrevZ();
        float yaw = entity.getYaw();
        float headYaw = entity.getHeadYaw();
        float bodyYaw = entity.getBodyYaw();
        float pitch = entity.getPitch();
        float pyaw = entity.getPrevYaw();
        float pHeadYaw = entity.getPrevHeadYaw();
        float pBodyYaw = entity.getPrevBodyYaw();
        float pPitch = entity.getPrevPitch();
        boolean drew = false;

        FormIllusions.begin();

        try
        {
            for (int n = 1; n <= illusions.count.get(); n++)
            {
                float opacity = illusions.opacityOf(n);
                float tick = ticks + transition - illusions.delay.get() * n;

                if (tick < 0F)
                {
                    break;
                }

                if (opacity <= 0.003F)
                {
                    continue;
                }

                replay.keyframes.apply(tick, entity);
                this.applyMtrSync(replay, tick, entity, true);
                replay.properties.applyProperties(form, tick);
                entity.setPrevX(entity.getX());
                entity.setPrevY(entity.getY());
                entity.setPrevZ(entity.getZ());
                entity.setPrevYaw(entity.getYaw());
                entity.setPrevHeadYaw(entity.getHeadYaw());
                entity.setPrevBodyYaw(entity.getBodyYaw());
                entity.setPrevPitch(entity.getPitch());
                RenderFrame.invalidate();

                FilmControllerContext ghost = this.getFilmControllerContext(context, replay, entity)
                    .color(Colors.setA(Colors.WHITE, opacity))
                    .transition(0F);

                FilmEntityRenderer.renderEntity(ghost);
                drew = true;
            }
        }
        finally
        {
            FormIllusions.end();

            if (drew)
            {
                /* Back to now: the keyframes and the form's properties, then the place the actor stood at */
                replay.keyframes.apply(ticks + transition, entity);
                this.applyMtrSync(replay, ticks + transition, entity, true);
                replay.properties.applyProperties(form, ticks + transition);
                RenderFrame.invalidate();

                entity.setPosition(x, y, z);
                entity.setPrevX(px);
                entity.setPrevY(py);
                entity.setPrevZ(pz);
                entity.setYaw(yaw);
                entity.setHeadYaw(headYaw);
                entity.setBodyYaw(bodyYaw);
                entity.setPitch(pitch);
                entity.setPrevYaw(pyaw);
                entity.setPrevHeadYaw(pHeadYaw);
                entity.setPrevBodyYaw(pBodyYaw);
                entity.setPrevPitch(pPitch);
            }
        }
    }

    protected FilmControllerContext getFilmControllerContext(WorldRenderContext context, Replay replay, IEntity entity)
    {
        return FilmControllerContext.instance
            .setup(this.entities, entity, replay, context)
            .shadow(replay.shadow.get(), replay.shadowSize.get())
            .nameTag(replay.nameTag.get())
            .relative(replay.relative.get());
    }

    public void shutdown()
    {
        FilmEvents.SHUTDOWN.invoker().onFilmShutdown(this);

        if (this.film != null && this.film.worldTimeline != null)
        {
            this.film.worldTimeline.resetAll();
        }

        this.entities.clear();

        /* A live morphed player outlives the film - without this its bow would
         * stay drawn forever after the playback stops */
        ThirdPersonItemUse.clear();
        ItemUseEffects.clear();
        LivePlayerItemUse.clear();
    }

    public static enum UpdateMode
    {
        UPDATE, RENDER, PROPERTIES;
    }
}
