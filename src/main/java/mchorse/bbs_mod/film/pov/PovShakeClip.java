package mchorse.bbs_mod.film.pov;

import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

/**
 * The game's hurt and death tilt of the view, and a shake on top of it: how far into a hurt the
 * view is (counting down to 0, out of the max), which way the hit came from, how long the replay
 * has been dying, how strong the whole of it is, and how wide and how fast the shake trembles while
 * the hurt lasts.
 *
 * <p>Left unkeyed the clip still does what it is for: it plays one hurt from its first tick, as the
 * game does when a hit lands (ten ticks counting down).</p>
 */
public class PovShakeClip extends PovClip
{
    public final KeyframeChannel<Double> hurtTime = this.channel("hurt_time", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> maxHurtTime = this.channel("max_hurt_time", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> damageTiltYaw = this.channel("damage_tilt_yaw", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> deathTime = this.channel("death_time", KeyframeFactories.DOUBLE);
    /** How strong the tilt and the shake are, 1 the game's */
    public final KeyframeChannel<Double> intensity = this.channel("intensity", KeyframeFactories.DOUBLE);
    /** The shake: how far it trembles at the hit (degrees) and how fast (times a second) */
    public final KeyframeChannel<Double> shakeAmplitude = this.channel("shake_amplitude", KeyframeFactories.DOUBLE);
    public final KeyframeChannel<Double> shakeFrequency = this.channel("shake_frequency", KeyframeFactories.DOUBLE);

    @Override
    protected Clip create()
    {
        return new PovShakeClip();
    }
}
