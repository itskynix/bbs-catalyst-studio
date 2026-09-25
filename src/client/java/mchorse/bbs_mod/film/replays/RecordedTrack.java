package mchorse.bbs_mod.film.replays;

import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.forms.Form;
import org.joml.Vector3d;
import org.joml.Vector4f;

/**
 * Represents a single actor/replay track in a live multi-track recording session.
 */
public class RecordedTrack
{
    private final int index;
    private final String id;
    private String name;
    private Form form;
    private final ReplayKeyframes keyframes;
    private final Replay replay;

    private Vector3d spawnPos;
    private Vector4f spawnRot;
    private int startTick = 0;
    private int endTick = 0;
    private boolean isRecorded = false;

    private double lastX, lastY, lastZ;
    private float lastYaw, lastPitch, lastHeadYaw, lastBodyYaw;
    private boolean hasRecordedTransform = false;

    public RecordedTrack(int index, String id, String name, Form form)
    {
        this.index = index;
        this.id = id;
        this.name = name != null ? name : "Track " + (index + 1);
        this.form = FormUtils.copy(form);
        this.keyframes = new ReplayKeyframes("keyframes");
        this.replay = new Replay(id);
        this.replay.label.set(this.name);
        if (this.form != null)
        {
            this.replay.form.set(this.form);
        }
    }

    public int getIndex()
    {
        return this.index;
    }

    public String getId()
    {
        return this.id;
    }

    public String getName()
    {
        return this.name;
    }

    public void setName(String name)
    {
        this.name = name;
        this.replay.label.set(name);
    }

    public Form getForm()
    {
        return this.form;
    }

    public void setForm(Form form)
    {
        this.form = FormUtils.copy(form);
        this.replay.form.set(this.form);
    }

    public ReplayKeyframes getKeyframes()
    {
        return this.keyframes;
    }

    public Replay getReplay()
    {
        return this.replay;
    }

    public IEntity getGhostEntity()
    {
        return null;
    }

    public Vector3d getSpawnPos()
    {
        return this.spawnPos;
    }

    public void setSpawnPos(Vector3d pos, Vector4f rot)
    {
        this.spawnPos = pos;
        this.spawnRot = rot;
    }

    public Vector4f getSpawnRot()
    {
        return this.spawnRot;
    }

    public int getStartTick()
    {
        return this.startTick;
    }

    public void setStartTick(int startTick)
    {
        this.startTick = startTick;
    }

    public int getEndTick()
    {
        return this.endTick;
    }

    public void setEndTick(int endTick)
    {
        this.endTick = endTick;
    }

    public boolean isRecorded()
    {
        return this.isRecorded;
    }

    public void setRecorded(boolean recorded)
    {
        this.isRecorded = recorded;
    }

    /**
     * Ghost logic has been removed: inactive tracks are played natively by BaseFilmController.
     */
    public void tickGhost(int currentTick)
    {
    }

    public boolean hasRecordedTransform()
    {
        return this.hasRecordedTransform;
    }

    public void setLastTransform(double x, double y, double z, float yaw, float pitch, float headYaw, float bodyYaw)
    {
        this.lastX = x;
        this.lastY = y;
        this.lastZ = z;
        this.lastYaw = yaw;
        this.lastPitch = pitch;
        this.lastHeadYaw = headYaw;
        this.lastBodyYaw = bodyYaw;
        this.hasRecordedTransform = true;
    }

    public double getLastX() { return this.lastX; }
    public double getLastY() { return this.lastY; }
    public double getLastZ() { return this.lastZ; }
    public float getLastYaw() { return this.lastYaw; }
    public float getLastPitch() { return this.lastPitch; }
    public float getLastHeadYaw() { return this.lastHeadYaw; }
    public float getLastBodyYaw() { return this.lastBodyYaw; }
}
