package mchorse.bbs_mod.forms.forms;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.core.ValueLink;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.settings.values.numeric.ValueFloat;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 3D Audio Form: emits spatialized 3D audio in the world, with support for
 * looping, time offset, stereo pan, radius attenuation, wireframe visualization,
 * and acoustic occlusion (muffling).
 */
public class AudioForm extends Form
{
    public static final Icon AUDIO_ICON = Icons.SOUND;
    private static final AtomicLong INSTANCE_COUNTER = new AtomicLong(1L);

    /** Unique runtime ID per form instance to isolate Replay clones from ModelBlock forms. */
    private long runtimeInstanceId = INSTANCE_COUNTER.getAndIncrement();

    /* Static authoring properties (hidden from timeline tracks) */
    public final ValueLink sound = new ValueLink("sound", null);
    public final ValueBoolean loop = new ValueBoolean("loop", false);
    public final ValueBoolean muffle = new ValueBoolean("muffle", true);
    public final ValueBoolean showWireframe = new ValueBoolean("showWireframe", true);
    public final ValueBoolean stream = new ValueBoolean("stream", false);

    /* Animatable properties (exposed in UIAudioFormPanel and "Audio Tracks" Dope Sheet tab) */
    public final ValueFloat volume = new ValueFloat("volume", 1.0F, 0.0F, 2.0F);
    public final ValueFloat pitch = new ValueFloat("pitch", 1.0F, 0.5F, 2.0F);
    public final ValueFloat offset = new ValueFloat("offset", 0.0F, -3600.0F, 3600.0F);
    public final ValueFloat pan = new ValueFloat("pan", 0.0F, -1.0F, 1.0F);
    public final ValueFloat radius = new ValueFloat("radius", 16.0F, 0.1F, 256.0F);
    public final ValueFloat occlusionFactor = new ValueFloat("occlusionFactor", 1.0F, 0.0F, 2.0F);

    public AudioForm()
    {
        super();

        /* Static switches do not create timeline tracks */
        this.sound.invisible();
        this.loop.invisible();
        this.muffle.invisible();
        this.showWireframe.invisible();
        this.stream.invisible();

        this.add(this.sound);
        this.add(this.loop);
        this.add(this.muffle);
        this.add(this.showWireframe);
        this.add(this.stream);

        this.add(this.volume);
        this.add(this.pitch);
        this.add(this.offset);
        this.add(this.pan);
        this.add(this.radius);
        this.add(this.occlusionFactor);
    }

    public long getRuntimeInstanceId()
    {
        return this.runtimeInstanceId;
    }

    @Override
    public void copy(BaseValueGroup group)
    {
        super.copy(group);

        /* Assign a fresh runtime instance ID whenever cloned so Replay and ModelBlock never collide */
        this.runtimeInstanceId = INSTANCE_COUNTER.getAndIncrement();
    }

    @Override
    public void fromData(BaseType data)
    {
        super.fromData(data);

        this.runtimeInstanceId = INSTANCE_COUNTER.getAndIncrement();
    }

    public double getVolume(float transition)
    {
        return this.volume.get();
    }

    public double getPitch(float transition)
    {
        return this.pitch.get();
    }

    public double getOffset(float transition)
    {
        return this.offset.get();
    }

    public double getPan(float transition)
    {
        return this.pan.get();
    }

    public double getRadius(float transition)
    {
        return this.radius.get();
    }

    public double getOcclusionFactor(float transition)
    {
        return this.occlusionFactor.get();
    }

    @Override
    public String getDefaultDisplayName()
    {
        Link link = this.sound.get();

        return link == null ? "audio" : link.toString();
    }

    @Override
    public Icon getIcon()
    {
        return AUDIO_ICON;
    }
}
