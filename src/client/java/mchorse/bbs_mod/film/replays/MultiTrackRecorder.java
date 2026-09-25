package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.film.*;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.morphing.Morph;
import mchorse.bbs_mod.network.ClientNetwork;
import mchorse.bbs_mod.ui.utils.UIUtils;
import mchorse.bbs_mod.utils.PlayerUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Multi-Track Replay Recorder allowing live on-the-fly actor track switching
 * during in-world recording. Previously recorded tracks continue playing back
 * simultaneously as interactive ghost puppets while recording new tracks on the same timeline.
 */
public class MultiTrackRecorder extends Recorder
{
    private final List<RecordedTrack> tracks = new ArrayList<>();
    private int activeTrackIndex = 0;
    private String switchNotification = "";
    private long switchNotificationTime = 0;

    public MultiTrackRecorder(Film film, Form form, int replayId, int tick)
    {
        super(film, form, replayId, tick);

        this.setupInitialTracks(film, form, replayId);
    }

    private void setupInitialTracks(Film film, Form form, int replayId)
    {
        this.tracks.clear();

        if (film != null && !film.replays.getList().isEmpty())
        {
            List<Replay> filmReplays = film.replays.getList();
            for (int i = 0; i < filmReplays.size(); i++)
            {
                Replay r = filmReplays.get(i);
                String name = r.label.get();
                if (name == null || name.trim().isEmpty())
                {
                    name = r.form.get() != null ? r.form.get().getId() : "Actor " + (i + 1);
                }
                RecordedTrack track = new RecordedTrack(i, r.getId(), name, r.form.get());

                if (!r.keyframes.x.isEmpty())
                {
                    track.getKeyframes().copyOver(r.keyframes, 0);
                    track.setRecorded(true);
                    track.setEndTick(film.calculateDuration());

                    double rx = r.keyframes.x.interpolate(0);
                    double ry = r.keyframes.y.interpolate(0);
                    double rz = r.keyframes.z.interpolate(0);
                    float ryaw = (float) (double) r.keyframes.yaw.interpolate(0);
                    float rpitch = (float) (double) r.keyframes.pitch.interpolate(0);
                    track.setSpawnPos(new Vector3d(rx, ry, rz), new Vector4f(ryaw, rpitch, ryaw, ryaw));
                    track.setLastTransform(rx, ry, rz, ryaw, rpitch, ryaw, ryaw);
                }

                this.tracks.add(track);
            }

            this.activeTrackIndex = Math.max(0, Math.min(replayId, this.tracks.size() - 1));
        }
        else
        {
            // Fallback if film has no replays yet
            String defaultName = form != null ? form.getId() : "Actor 1";
            RecordedTrack track1 = new RecordedTrack(0, "track_0", defaultName, form);
            this.tracks.add(track1);
            this.activeTrackIndex = 0;
        }

        this.exception = this.activeTrackIndex;

        // Snapshot current player position for active track if not set
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null && !this.tracks.isEmpty())
        {
            RecordedTrack active = this.getActiveTrack();
            if (active != null && !active.hasRecordedTransform())
            {
                active.setSpawnPos(
                    new Vector3d(player.getX(), player.getY(), player.getZ()),
                    new Vector4f(player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw())
                );
                active.setLastTransform(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw()
                );
            }
        }
    }

    public List<RecordedTrack> getTracks()
    {
        return Collections.unmodifiableList(this.tracks);
    }

    public int getActiveTrackIndex()
    {
        return this.activeTrackIndex;
    }

    public RecordedTrack getActiveTrack()
    {
        if (this.activeTrackIndex >= 0 && this.activeTrackIndex < this.tracks.size())
        {
            return this.tracks.get(this.activeTrackIndex);
        }

        return !this.tracks.isEmpty() ? this.tracks.get(0) : null;
    }

    public String getSwitchNotification()
    {
        if (System.currentTimeMillis() - this.switchNotificationTime < 2500)
        {
            return this.switchNotification;
        }

        return "";
    }

    /**
     * Switch to the next actor track circularly ((current + 1) % total).
     */
    public boolean nextTrack()
    {
        if (this.tracks.size() <= 1)
        {
            return false;
        }

        int nextIndex = (this.activeTrackIndex + 1) % this.tracks.size();
        return this.switchToTrack(nextIndex);
    }

    /**
     * Switch to the previous actor track circularly ((current - 1 + total) % total).
     */
    public boolean previousTrack()
    {
        if (this.tracks.size() <= 1)
        {
            return false;
        }

        int prevIndex = (this.activeTrackIndex - 1 + this.tracks.size()) % this.tracks.size();
        return this.switchToTrack(prevIndex);
    }

    /**
     * Switches the active recording channel to targetIndex.
     * Freezes and starts live ghost playback for the previous channel,
     * and transfers player control to the new channel without pausing the time clock.
     */
    public boolean switchToTrack(int targetIndex)
    {
        if (targetIndex < 0 || targetIndex >= this.tracks.size() || targetIndex == this.activeTrackIndex)
        {
            return false;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;

        // 1. Finalize and freeze the departing track
        int departingIndex = this.activeTrackIndex;
        RecordedTrack departing = this.getActiveTrack();
        if (departing != null && player != null)
        {
            departing.setEndTick(this.tick);
            departing.setRecorded(true);
            departing.setLastTransform(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw()
            );

            Morph morph = Morph.getMorph(player);
            if (morph != null)
            {
                departing.getKeyframes().record(this.tick, morph.entity, null);
                if (morph.getForm() != null)
                {
                    departing.setForm(morph.getForm());
                }
            }

            // Sync recorded keyframes back to the film's replay model
            // so BaseFilmController natively plays the single original actor
            if (this.film != null && departingIndex >= 0 && departingIndex < this.film.replays.getList().size())
            {
                Replay filmReplay = this.film.replays.getList().get(departingIndex);
                filmReplay.keyframes.copyOver(departing.getKeyframes(), 0);
                if (departing.getForm() != null)
                {
                    filmReplay.form.set(FormUtils.copy(departing.getForm()));
                }
            }
        }

        // 2. Activate the incoming track
        this.activeTrackIndex = targetIndex;
        this.exception = targetIndex;
        RecordedTrack incoming = this.getActiveTrack();

        if (incoming != null && player != null)
        {
            incoming.setStartTick(Math.min(incoming.getStartTick(), this.tick));

            if (incoming.hasRecordedTransform())
            {
                // Has previous recording on this track: instantly teleport player to its exact last pose
                double tx = incoming.getLastX();
                double ty = incoming.getLastY();
                double tz = incoming.getLastZ();
                float tyaw = incoming.getLastYaw();
                float tpitch = incoming.getLastPitch();
                float theadYaw = incoming.getLastHeadYaw();
                float tbodyYaw = incoming.getLastBodyYaw();

                player.setPosition(tx, ty, tz);
                player.setYaw(tyaw);
                player.setPitch(tpitch);
                player.setHeadYaw(theadYaw);
                player.setBodyYaw(tbodyYaw);
                player.setVelocity(Vec3d.ZERO);
                player.prevX = tx;
                player.prevY = ty;
                player.prevZ = tz;
                player.prevYaw = tyaw;
                player.prevPitch = tpitch;
                player.prevHeadYaw = theadYaw;
                player.prevBodyYaw = tbodyYaw;

                PlayerUtils.teleport(tx, ty, tz, tyaw, tbodyYaw, tpitch);
            }
            else if (incoming.getSpawnPos() != null)
            {
                // New track with pre-defined spawn marker
                Vector3d pos = incoming.getSpawnPos();
                Vector4f rot = incoming.getSpawnRot();
                double tx = pos.x;
                double ty = pos.y;
                double tz = pos.z;
                float tyaw = rot != null ? rot.z : player.getYaw();
                float tpitch = rot != null ? rot.y : player.getPitch();

                player.setPosition(tx, ty, tz);
                player.setYaw(tyaw);
                player.setPitch(tpitch);
                player.setVelocity(Vec3d.ZERO);

                PlayerUtils.teleport(tx, ty, tz, tyaw, tpitch);
                incoming.setLastTransform(tx, ty, tz, tyaw, tpitch, tyaw, tyaw);
            }
            else
            {
                // Brand new track started at current player position
                incoming.setSpawnPos(
                    new Vector3d(player.getX(), player.getY(), player.getZ()),
                    new Vector4f(player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw())
                );
                incoming.setLastTransform(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw()
                );
            }

            // Sync visual morph
            if (incoming.getForm() != null)
            {
                ClientNetwork.sendPlayerForm(incoming.getForm());
            }

            this.switchNotification = "Switched to " + incoming.getName();
            this.switchNotificationTime = System.currentTimeMillis();
            UIUtils.playClick(1.5F);
        }

        return true;
    }

    @Override
    public void update()
    {
        // 1. Advance tick, countdown and landmark handling via super
        super.update();

        if (this.hasNotStarted() || this.tick < 0)
        {
            return;
        }

        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null)
        {
            return;
        }

        // 2. Record the live player into the ACTIVE track
        RecordedTrack active = this.getActiveTrack();
        if (active != null)
        {
            Morph morph = Morph.getMorph(player);
            active.getKeyframes().record(this.tick, morph.entity, null);
            active.setEndTick(this.tick);
            active.setRecorded(true);
            active.setLastTransform(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(), player.getHeadYaw(), player.getBodyYaw()
            );

            if (morph != null && morph.getForm() != null)
            {
                active.setForm(morph.getForm());
            }
        }
    }

    @Override
    public void render(WorldRenderContext context)
    {
        // Render camera preview and scene overlays via super.
        // Ghost entities and duplicated puppets have been completely removed.
        // Inactive tracks are already played as single original actors by BaseFilmController.
        super.render(context);
    }

    /**
     * Commits all recorded tracks into the given Film.
     */
    public void commitToFilm(Film film)
    {
        if (film == null)
        {
            return;
        }

        for (int i = 0; i < this.tracks.size(); i++)
        {
            RecordedTrack track = this.tracks.get(i);
            if (!track.isRecorded() && track.getKeyframes().x.isEmpty())
            {
                continue;
            }

            // Simplify channels
            track.getKeyframes().compressItemChannels();
            for (KeyframeChannel<?> channel : track.getKeyframes().getChannels())
            {
                channel.simplify();
            }

            // Find or add replay
            Replay replay;
            if (i < film.replays.getList().size())
            {
                replay = film.replays.getList().get(i);
            }
            else
            {
                replay = film.replays.addReplay();
            }

            replay.label.set(track.getName());
            if (track.getForm() != null)
            {
                replay.form.set(FormUtils.copy(track.getForm()));
            }
            replay.keyframes.copyOver(track.getKeyframes(), 0);
        }
    }
}
