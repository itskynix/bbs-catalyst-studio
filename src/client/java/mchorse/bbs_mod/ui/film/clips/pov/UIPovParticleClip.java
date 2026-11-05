package mchorse.bbs_mod.ui.film.clips.pov;

import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.film.pov.PovParticleClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.utils.UI;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * The particle clip's panel: the particle, picked from a grid of the game's particles (the ones
 * that need no settings of their own), how many in a puff and how often, where in front of the eyes, how far
 * they scatter and how fast they fly off.
 */
public class UIPovParticleClip extends UIPovClip<PovParticleClip>
{
    public UIButton particle;
    public UITrackpad count;
    public UITrackpad interval;
    public UITrackpad offsetX;
    public UITrackpad offsetY;
    public UITrackpad offsetZ;
    public UITrackpad spread;
    public UITrackpad speed;

    public UIPovParticleClip(PovParticleClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    /** The particles a puff can be made of: the ones without settings of their own */
    public static List<String> particles()
    {
        List<String> ids = new ArrayList<>();

        for (Identifier id : Registries.PARTICLE_TYPE.getIds())
        {
            if (Registries.PARTICLE_TYPE.get(id) instanceof ParticleEffect)
            {
                ids.add(id.toString());
            }
        }

        ids.sort(String::compareTo);

        return ids;
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.particle = new UIButton(key("particle.pick"), (b) ->
        {
            UIOverlay.addOverlay(this.getContext(), new UIPovParticlePicker(this.clip.particle.get(), (id) ->
            {
                this.editor.editMultiple(this.clip.particle, (v) -> v.set(id));
            }), 0.7F, 0.8F);
        });
        this.bind(this.particle, () -> this.particle.label = IKey.constant(UIPovParticlePicker.name(this.clip.particle.get())));

        this.count = this.trackpad(this.clip.count);
        this.count.integer().tooltip(key("particle.count"));
        this.interval = this.trackpad(this.clip.interval);
        this.interval.integer().tooltip(key("particle.interval"));
        this.offsetX = this.offset((p) -> p.x, (p, v) -> p.x = v);
        this.offsetY = this.offset((p) -> p.y, (p, v) -> p.y = v);
        this.offsetZ = this.offset((p) -> p.z, (p, v) -> p.z = v);
        this.spread = this.trackpad(this.clip.spread);
        this.spread.tooltip(key("particle.spread"));
        this.speed = this.trackpad(this.clip.speed);
        this.speed.tooltip(key("particle.speed"));
    }

    private UITrackpad offset(Function<Point, Double> get, BiConsumer<Point, Double> set)
    {
        UITrackpad trackpad = new UITrackpad((v) -> this.editor.editMultiple(this.clip.offset, (value) -> BaseValue.edit(value, (p) -> set.accept(p.get(), v))));

        trackpad.tooltip(key("particle.offset"));

        return this.bind(trackpad, () -> trackpad.setValue(get.apply(this.clip.offset.get())));
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.section(key("particle.title"), this.particle, UI.row(this.count, this.interval),
            UI.row(this.offsetX, this.offsetY, this.offsetZ), UI.row(this.spread, this.speed)));
    }
}
