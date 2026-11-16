package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Represents a single layer within a CatalystComposition.
 *
 * LayerType enum:
 *   SOLID  - flat color plate / background
 *   SCENE  - BBS film / replay scene layer
 *   AUDIO  - audio track
 *   NULL   - null/guide layer (invisible, logic only)
 */
public class CatalystLayer
{
    public enum LayerType
    {
        SOLID, AUDIO, ADJUSTMENT, NULL, TEXT, SCENE, VIDEO, IMAGE, NESTED, NESTED_AUDIO;

        public static LayerType fromString(String s)
        {
            if (s == null) return SOLID;

            switch (s.toUpperCase(Locale.ROOT))
            {
                case "AUDIO":
                    return AUDIO;
                case "ADJUSTMENT":
                    return ADJUSTMENT;
                case "NULL":
                    return NULL;
                case "TEXT":
                    return TEXT;
                case "SCENE":
                case "FILM":
                case "ACTOR":
                    return SCENE;
                case "VIDEO":
                    return VIDEO;
                case "NESTED":
                case "SUBSEQUENCE":
                    return NESTED;
                case "NESTED_AUDIO":
                    return NESTED_AUDIO;
                case "IMAGE":
                case "PICTURE":
                    return IMAGE;
                default:
                    return SOLID;
            }
        }

        public int defaultColor()
        {
            switch (this)
            {
                case AUDIO:       return 0xFF88602A; // Gold/Brown
                case ADJUSTMENT:  return 0xFFE67E22; // Orange
                case NULL:        return 0xFF444455; // Slate Gray
                case TEXT:        return 0xFF27AE60; // Green
                case SCENE:       return 0xFF2980B9; // Blue
                case VIDEO:       return 0xFF8E44AD; // Purple
                case IMAGE:       return 0xFF16A085; // Teal
                case NESTED:      return 0xFF3E7C9C; // Steel blue
                case NESTED_AUDIO: return 0xFF2F6B5A; // Deep green
                default:          return 0xFF6B4A82; // Magenta (SOLID)
            }
        }

        public String getBadge()
        {
            switch (this)
            {
                case AUDIO:       return "[A]";
                case ADJUSTMENT:  return "[ADJ]";
                case NULL:        return "[N]";
                case TEXT:        return "[T]";
                case SCENE:       return "[FILM]";
                case VIDEO:       return "[V]";
                case IMAGE:       return "[IMG]";
                default:          return "[SOL]";
            }
        }
    }

    /* ── Fields ── */
    public String    id         = UUID.randomUUID().toString().substring(0, 8);
    public String    name       = "Layer";
    public LayerType layerType  = LayerType.SOLID;
    public int       color      = 0xFF6B4A82;
    public boolean   visible    = true;
    public boolean   locked     = false;
    public boolean   muted      = false;  // Quick-mute for AUDIO / VIDEO audio tracks
    public boolean   solo       = false;  // Solo mode: only this layer is audible/visible
    // ── AE-style twirl-down transform hierarchy state (transient UI, not saved) ──
    public transient boolean expanded       = false;  // true = transform tree is open
    // Bitmask of which sub-property rows are expanded (P=1, S=2, R=4, T=8, A=16)
    public transient int     expandedProps  = 0;
    public int       startFrame = 0;
    public int       duration   = 300;
    public int       opacity    = 100; // 0 - 100 %
    public String    blendMode  = "NORMAL"; // NORMAL, MULTIPLY, SCREEN, ADD, OVERLAY
    public String    resourcePath = ""; // Resource or asset file path (film id, image, video, audio)

    /* ── Media & Audio Properties ── */
    public float     volume       = 1.0F; // 0.0 to 1.0
    /** The gain put on the clip over its volume, in decibels: -24 to +24 from the gain window */
    public float     gainDb       = 0.0F;
    public float     pan          = 0.0F; // -1.0 (Left) .. 0.0 (Center) .. +1.0 (Right)
    public int       audioOffset  = 0; // Frame offset
    public int       mediaOffset  = 0; // In-point cut/split offset in frames
    public int       mediaDuration = 0; // Cached media duration in frames (if known)
    public int       filmFps      = 20; // FPS / tick-rate for SCENE/Film layer (default 20 TPS)

    /* ── Edit timeline ── */
    /** The track the clip sits on, counted within its kind (audio clips on audio tracks, the rest on video ones); -1 until placed */
    public int       track        = -1;

    /** Crop, as fractions of the picture cut off each side (0 to 1) */
    public float     cropLeft     = 0F;
    public float     cropRight    = 0F;
    public float     cropTop      = 0F;
    public float     cropBottom   = 0F;
    public boolean   flipH        = false;
    public boolean   flipV        = false;
    /** Zoom X and Y are edited together */
    public boolean   scaleLocked  = true;

    /** The clip's post-process effects, from the effect library */
    public final mchorse.bbs_mod.camera.clips.fx.PostEffectStack effects = new mchorse.bbs_mod.camera.clips.fx.PostEffectStack("effects");

    /** A video clip and the audio clip made from its sound share this id; empty when the clip stands alone */
    public String    linkId       = "";
    /** A video clip whose sound lives on a linked audio clip plays none of its own */
    public boolean   muteOwnAudio = false;

    /** How the clip comes in: "" (cut), "cross_dissolve", "dip_to_black" or "slide", over transitionFrames */
    public String    transitionIn     = "";
    public int       transitionFrames = 15;

    /** Whether the clip lives on the audio tracks */
    /** An audio clip sits on the audio tracks: a sound, or the sound of a nested timeline */
    public boolean isAudioClip()
    {
        return this.layerType == LayerType.AUDIO || this.layerType == LayerType.NESTED_AUDIO;
    }

    /** A clip that plays another timeline of the project, whose id is the clip's path */
    public boolean isNested()
    {
        return this.layerType == LayerType.NESTED || this.layerType == LayerType.NESTED_AUDIO;
    }

    /* ── Transient Cache (Waveform, Video, Image & Profiler) ── */
    public transient volatile Object  cachedWaveform = null;
    public transient volatile boolean isWaveformLoading = false;
    public transient volatile String  cachedWaveformPath = null;
    public transient volatile Object  cachedVideoTexture = null;
    public transient volatile int     lastVideoFrameIndex = -1;
    public transient volatile Object  cachedImageTexture = null;
    public transient volatile String  cachedImagePath = null;
    public transient volatile double  lastRenderMs = 0.0;

    /* ── Text Layer Properties ── */
    public int       textColor    = 0xFFFFFFFF;
    public int       fontSize     = 16;
    public boolean   lineWrapping = true;
    public boolean   shadow       = true;

    /* ── Transform / Pivot Properties ── */
    public float     posX         = 0.0F; // X offset in pixels from canvas center
    public float     posY         = 0.0F; // Y offset in pixels from canvas center
    public float     scaleX       = 1.0F;
    public float     scaleY       = 1.0F;
    public float     rotation     = 0.0F; // Rotation in degrees
    public float     anchorX      = 0.5F; // 0.0 to 1.0 (pivot point)
    public float     anchorY      = 0.5F; // 0.0 to 1.0 (pivot point)

    /* ── Keyframe Animation Channels & Stopwatch Flags ── */
    public boolean animPosX     = false;
    public boolean animPosY     = false;
    public boolean animScaleX   = false;
    public boolean animScaleY   = false;
    public boolean animRotation = false;
    public boolean animOpacity  = false;
    public boolean animAnchorX  = false;
    public boolean animAnchorY  = false;
    public boolean animVolume   = false;
    public boolean animPan      = false;

    public final KeyframeChannel<Float> channelPosX     = new KeyframeChannel<>("posX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelPosY     = new KeyframeChannel<>("posY", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelScaleX   = new KeyframeChannel<>("scaleX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelScaleY   = new KeyframeChannel<>("scaleY", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelRotation = new KeyframeChannel<>("rotation", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelOpacity  = new KeyframeChannel<>("opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelAnchorX  = new KeyframeChannel<>("anchorX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelAnchorY  = new KeyframeChannel<>("anchorY", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelVolume   = new KeyframeChannel<>("volume", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelPan      = new KeyframeChannel<>("pan", KeyframeFactories.FLOAT);

    /** The channel of one axis, named by a SUB_PROP_ constant */
    public KeyframeChannel<Float> channelFor(int singleProp)
    {
        switch (singleProp)
        {
            case SUB_PROP_POS_X:    return this.channelPosX;
            case SUB_PROP_POS_Y:    return this.channelPosY;
            case SUB_PROP_SCALE_X:  return this.channelScaleX;
            case SUB_PROP_SCALE_Y:  return this.channelScaleY;
            case SUB_PROP_ROTATION: return this.channelRotation;
            case SUB_PROP_OPACITY:  return this.channelOpacity;
            case SUB_PROP_ANCHOR_X: return this.channelAnchorX;
            case SUB_PROP_ANCHOR_Y: return this.channelAnchorY;
            case SUB_PROP_VOLUME:   return this.channelVolume;
            case SUB_PROP_PAN:      return this.channelPan;
            default: return null;
        }
    }

    /**
     * The diamond of a field: a key is put down at the frame holding what the field shows there, or
     * the key that is there is taken away. Returns whether the frame has a key now.
     */
    public boolean toggleKeyframeSingle(int singleProp, int frame)
    {
        if (this.hasKeyframeAtSingle(singleProp, frame))
        {
            this.removeKeyframeSingle(singleProp, frame);

            return false;
        }

        float[] e = this.computeEffective(frame);

        switch (singleProp)
        {
            case SUB_PROP_POS_X:    this.posX = e[0]; break;
            case SUB_PROP_POS_Y:    this.posY = e[1]; break;
            case SUB_PROP_SCALE_X:  this.scaleX = e[2]; break;
            case SUB_PROP_SCALE_Y:  this.scaleY = e[3]; break;
            case SUB_PROP_ROTATION: this.rotation = e[4]; break;
            case SUB_PROP_OPACITY:  this.opacity = Math.round(e[5]); break;
            case SUB_PROP_ANCHOR_X: this.anchorX = e[6]; break;
            case SUB_PROP_ANCHOR_Y: this.anchorY = e[7]; break;
            case SUB_PROP_VOLUME:   this.volume = e[8]; break;
            case SUB_PROP_PAN:      this.pan = e[9]; break;
            default: return false;
        }

        this.addKeyframeSingle(singleProp, frame);

        return true;
    }

    /** The frame of the closest key before ({@code direction} -1) or after (1) a frame on a channel, or -1 */
    public static int neighbourKey(KeyframeChannel<?> channel, int frame, int direction)
    {
        if (channel == null)
        {
            return -1;
        }

        int found = -1;

        for (Keyframe<?> key : channel.getKeyframes())
        {
            int tick = Math.round(key.getTick());

            if (direction < 0 && tick < frame && tick > found)
            {
                found = tick;
            }
            else if (direction > 0 && tick > frame && (found < 0 || tick < found))
            {
                found = tick;
            }
        }

        return found;
    }

    /** Keys made or removed in the curve editor: a channel with keys animates, one without does not */
    public void syncAnimFlags()
    {
        this.animPosX = !this.channelPosX.isEmpty();
        this.animPosY = !this.channelPosY.isEmpty();
        this.animScaleX = !this.channelScaleX.isEmpty();
        this.animScaleY = !this.channelScaleY.isEmpty();
        this.animRotation = !this.channelRotation.isEmpty();
        this.animOpacity = !this.channelOpacity.isEmpty();
        this.animAnchorX = !this.channelAnchorX.isEmpty();
        this.animAnchorY = !this.channelAnchorY.isEmpty();
        this.animVolume = !this.channelVolume.isEmpty();
        this.animPan = !this.channelPan.isEmpty();
    }

    /**
     * Takes the clip that follows this one into it: it runs on to the end of the other, and the keys of
     * the other's channels, the effects' too, come across, apart from any that sit where this one's already are.
     */
    public void absorb(CatalystLayer next)
    {
        this.duration += next.duration;

        mergeKeys(this.channelPosX, next.channelPosX);
        mergeKeys(this.channelPosY, next.channelPosY);
        mergeKeys(this.channelScaleX, next.channelScaleX);
        mergeKeys(this.channelScaleY, next.channelScaleY);
        mergeKeys(this.channelRotation, next.channelRotation);
        mergeKeys(this.channelOpacity, next.channelOpacity);
        mergeKeys(this.channelAnchorX, next.channelAnchorX);
        mergeKeys(this.channelAnchorY, next.channelAnchorY);
        mergeKeys(this.channelVolume, next.channelVolume);
        mergeKeys(this.channelPan, next.channelPan);

        List<mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer> mine = this.effects.getAllTyped();
        List<mchorse.bbs_mod.camera.clips.fx.PostEffectStack.Layer> theirs = next.effects.getAllTyped();

        for (int i = 0; i < Math.min(mine.size(), theirs.size()); i++)
        {
            if (mine.get(i).effect.get().equals(theirs.get(i).effect.get()))
            {
                List<KeyframeChannel<Double>> to = mine.get(i).channels();
                List<KeyframeChannel<Double>> from = theirs.get(i).channels();

                for (int c = 0; c < to.size(); c++)
                {
                    mergeKeys(to.get(c), from.get(c));
                }
            }
        }

        this.syncAnimFlags();
    }

    private static <T> void mergeKeys(KeyframeChannel<T> into, KeyframeChannel<T> from)
    {
        for (Keyframe<T> key : from.getKeyframes())
        {
            boolean there = false;

            for (Keyframe<T> own : into.getKeyframes())
            {
                there |= Math.abs(own.getTick() - key.getTick()) < 0.5F;
            }

            if (!there)
            {
                Keyframe<T> copy = new Keyframe<>(key.getId(), key.getFactory());

                copy.copy(key);
                into.add(copy);
            }
        }

        into.sort();
    }

    /** Slides every key of the clip's ten channels by some frames, for a clip that is moved along the timeline */
    public void moveKeyframes(int delta)
    {
        if (delta == 0)
        {
            return;
        }

        this.channelPosX.moveX(delta);
        this.channelPosY.moveX(delta);
        this.channelScaleX.moveX(delta);
        this.channelScaleY.moveX(delta);
        this.channelRotation.moveX(delta);
        this.channelOpacity.moveX(delta);
        this.channelAnchorX.moveX(delta);
        this.channelAnchorY.moveX(delta);
        this.channelVolume.moveX(delta);
        this.channelPan.moveX(delta);
        this.effects.moveX(delta);
    }

    public boolean hasAnyKeyframes(int propBit)
    {
        switch (propBit)
        {
            case 1:  return !this.channelPosX.isEmpty() || !this.channelPosY.isEmpty();
            case 2:  return !this.channelScaleX.isEmpty() || !this.channelScaleY.isEmpty();
            case 4:  return !this.channelRotation.isEmpty();
            case 8:  return !this.channelOpacity.isEmpty();
            case 16: return !this.channelAnchorX.isEmpty() || !this.channelAnchorY.isEmpty();
            case 32: return !this.channelVolume.isEmpty();
            case 64: return !this.channelPan.isEmpty();
            default: return false;
        }
    }

    public boolean hasAnyKeyframesTotal()
    {
        return !this.channelPosX.isEmpty() || !this.channelPosY.isEmpty()
            || !this.channelScaleX.isEmpty() || !this.channelScaleY.isEmpty()
            || !this.channelRotation.isEmpty() || !this.channelOpacity.isEmpty()
            || !this.channelAnchorX.isEmpty() || !this.channelAnchorY.isEmpty()
            || !this.channelVolume.isEmpty() || !this.channelPan.isEmpty();
    }

    public void addKeyframe(int propBit, int frame)
    {
        switch (propBit)
        {
            case 1:
                this.animPosX = true;
                this.animPosY = true;
                this.channelPosX.insert(frame, this.posX);
                this.channelPosY.insert(frame, this.posY);
                break;
            case 2:
                this.animScaleX = true;
                this.animScaleY = true;
                this.channelScaleX.insert(frame, this.scaleX);
                this.channelScaleY.insert(frame, this.scaleY);
                break;
            case 4:
                this.animRotation = true;
                this.channelRotation.insert(frame, this.rotation);
                break;
            case 8:
                this.animOpacity = true;
                this.channelOpacity.insert(frame, (float) this.opacity);
                break;
            case 16:
                this.animAnchorX = true;
                this.animAnchorY = true;
                this.channelAnchorX.insert(frame, this.anchorX);
                this.channelAnchorY.insert(frame, this.anchorY);
                break;
            case 32:
                this.animVolume = true;
                this.channelVolume.insert(frame, this.volume);
                break;
            case 64:
                this.animPan = true;
                this.channelPan.insert(frame, this.pan);
                break;
        }
    }

    /* Single-axis property bit constants */
    public static final int SUB_PROP_POS_X    = 101;
    public static final int SUB_PROP_POS_Y    = 102;
    public static final int SUB_PROP_SCALE_X  = 103;
    public static final int SUB_PROP_SCALE_Y  = 104;
    public static final int SUB_PROP_ROTATION = 105;
    public static final int SUB_PROP_OPACITY  = 106;
    public static final int SUB_PROP_ANCHOR_X = 107;
    public static final int SUB_PROP_ANCHOR_Y = 108;
    public static final int SUB_PROP_VOLUME   = 109;
    public static final int SUB_PROP_PAN      = 110;

    public void addKeyframeSingle(int singlePropBit, int frame)
    {
        switch (singlePropBit)
        {
            case SUB_PROP_POS_X:
                this.animPosX = true;
                this.channelPosX.insert(frame, this.posX);
                break;
            case SUB_PROP_POS_Y:
                this.animPosY = true;
                this.channelPosY.insert(frame, this.posY);
                break;
            case SUB_PROP_SCALE_X:
                this.animScaleX = true;
                this.channelScaleX.insert(frame, this.scaleX);
                break;
            case SUB_PROP_SCALE_Y:
                this.animScaleY = true;
                this.channelScaleY.insert(frame, this.scaleY);
                break;
            case SUB_PROP_ROTATION:
            case 4:
                this.animRotation = true;
                this.channelRotation.insert(frame, this.rotation);
                break;
            case SUB_PROP_OPACITY:
            case 8:
                this.animOpacity = true;
                this.channelOpacity.insert(frame, (float) this.opacity);
                break;
            case SUB_PROP_ANCHOR_X:
                this.animAnchorX = true;
                this.channelAnchorX.insert(frame, this.anchorX);
                break;
            case SUB_PROP_ANCHOR_Y:
                this.animAnchorY = true;
                this.channelAnchorY.insert(frame, this.anchorY);
                break;
            case SUB_PROP_VOLUME:
            case 32:
                this.animVolume = true;
                this.channelVolume.insert(frame, this.volume);
                break;
            case SUB_PROP_PAN:
            case 64:
                this.animPan = true;
                this.channelPan.insert(frame, this.pan);
                break;
        }
    }

    public boolean hasKeyframeAtSingle(int singlePropBit, int frame)
    {
        switch (singlePropBit)
        {
            case SUB_PROP_POS_X:    return hasKeyAt(this.channelPosX, frame);
            case SUB_PROP_POS_Y:    return hasKeyAt(this.channelPosY, frame);
            case SUB_PROP_SCALE_X:  return hasKeyAt(this.channelScaleX, frame);
            case SUB_PROP_SCALE_Y:  return hasKeyAt(this.channelScaleY, frame);
            case SUB_PROP_ROTATION:
            case 4:                 return hasKeyAt(this.channelRotation, frame);
            case SUB_PROP_OPACITY:
            case 8:                 return hasKeyAt(this.channelOpacity, frame);
            case SUB_PROP_ANCHOR_X: return hasKeyAt(this.channelAnchorX, frame);
            case SUB_PROP_ANCHOR_Y: return hasKeyAt(this.channelAnchorY, frame);
            case SUB_PROP_VOLUME:
            case 32:                return hasKeyAt(this.channelVolume, frame);
            case SUB_PROP_PAN:
            case 64:                return hasKeyAt(this.channelPan, frame);
            default: return false;
        }
    }

    public void removeKeyframeSingle(int singlePropBit, int frame)
    {
        switch (singlePropBit)
        {
            case SUB_PROP_POS_X:
                removeKeyAt(this.channelPosX, frame);
                this.animPosX = !this.channelPosX.isEmpty();
                break;
            case SUB_PROP_POS_Y:
                removeKeyAt(this.channelPosY, frame);
                this.animPosY = !this.channelPosY.isEmpty();
                break;
            case SUB_PROP_SCALE_X:
                removeKeyAt(this.channelScaleX, frame);
                this.animScaleX = !this.channelScaleX.isEmpty();
                break;
            case SUB_PROP_SCALE_Y:
                removeKeyAt(this.channelScaleY, frame);
                this.animScaleY = !this.channelScaleY.isEmpty();
                break;
            case SUB_PROP_ROTATION:
            case 4:
                removeKeyAt(this.channelRotation, frame);
                this.animRotation = !this.channelRotation.isEmpty();
                break;
            case SUB_PROP_OPACITY:
            case 8:
                removeKeyAt(this.channelOpacity, frame);
                this.animOpacity = !this.channelOpacity.isEmpty();
                break;
            case SUB_PROP_ANCHOR_X:
                removeKeyAt(this.channelAnchorX, frame);
                this.animAnchorX = !this.channelAnchorX.isEmpty();
                break;
            case SUB_PROP_ANCHOR_Y:
                removeKeyAt(this.channelAnchorY, frame);
                this.animAnchorY = !this.channelAnchorY.isEmpty();
                break;
            case SUB_PROP_VOLUME:
            case 32:
                removeKeyAt(this.channelVolume, frame);
                this.animVolume = !this.channelVolume.isEmpty();
                break;
            case SUB_PROP_PAN:
            case 64:
                removeKeyAt(this.channelPan, frame);
                this.animPan = !this.channelPan.isEmpty();
                break;
        }
    }

    public static int getSinglePropBit(int propBit, int component)
    {
        switch (propBit)
        {
            case 1:  return component == 0 ? SUB_PROP_POS_X : SUB_PROP_POS_Y;
            case 2:  return component == 0 ? SUB_PROP_SCALE_X : SUB_PROP_SCALE_Y;
            case 4:  return SUB_PROP_ROTATION;
            case 8:  return SUB_PROP_OPACITY;
            case 16: return component == 0 ? SUB_PROP_ANCHOR_X : SUB_PROP_ANCHOR_Y;
            case 32: return SUB_PROP_VOLUME;
            case 64: return SUB_PROP_PAN;
            default: return propBit;
        }
    }

    private static void removeKeyAt(KeyframeChannel<?> ch, int frame)
    {
        if (ch == null)
        {
            return;
        }
        List<? extends Keyframe<?>> keyframes = ch.getKeyframes();
        for (int i = keyframes.size() - 1; i >= 0; i--)
        {
            if (Math.round(keyframes.get(i).getTick()) == frame)
            {
                ch.remove(i);
            }
        }
    }

    /* ── Constructors ── */

    public CatalystLayer() {}

    public CatalystLayer(String name, String typeStr, int color)
    {
        this.name      = name;
        this.layerType = LayerType.fromString(typeStr);
        this.color     = color;
    }

    public CatalystLayer(String name, LayerType type, int color)
    {
        this.name      = name;
        this.layerType = type;
        this.color     = color;
    }

    /** Quick factory: Solid plate (plain color). */
    public static CatalystLayer solid(String name)
    {
        return new CatalystLayer(name, LayerType.SOLID, LayerType.SOLID.defaultColor());
    }

    /** Quick factory: Scene / film layer. */
    public static CatalystLayer scene(String name)
    {
        return new CatalystLayer(name, LayerType.SCENE, LayerType.SCENE.defaultColor());
    }

    public float[] computeEffective(int frame)
    {
        return this.computeEffective(frame, new float[10]);
    }

    public float[] computeEffective(int frame, float[] dest)
    {
        if (dest == null || dest.length < 10)
        {
            dest = new float[10];
        }

        dest[0] = (this.animPosX && !this.channelPosX.isEmpty()) ? this.channelPosX.interpolate(frame, this.posX) : this.posX;
        dest[1] = (this.animPosY && !this.channelPosY.isEmpty()) ? this.channelPosY.interpolate(frame, this.posY) : this.posY;
        dest[2] = (this.animScaleX && !this.channelScaleX.isEmpty()) ? this.channelScaleX.interpolate(frame, this.scaleX) : this.scaleX;
        dest[3] = (this.animScaleY && !this.channelScaleY.isEmpty()) ? this.channelScaleY.interpolate(frame, this.scaleY) : this.scaleY;
        dest[4] = (this.animRotation && !this.channelRotation.isEmpty()) ? this.channelRotation.interpolate(frame, this.rotation) : this.rotation;
        dest[5] = (this.animOpacity && !this.channelOpacity.isEmpty()) ? (float) Math.round(this.channelOpacity.interpolate(frame, (float) this.opacity)) : (float) this.opacity;
        dest[6] = (this.animAnchorX && !this.channelAnchorX.isEmpty()) ? this.channelAnchorX.interpolate(frame, this.anchorX) : this.anchorX;
        dest[7] = (this.animAnchorY && !this.channelAnchorY.isEmpty()) ? this.channelAnchorY.interpolate(frame, this.anchorY) : this.anchorY;
        dest[8] = (this.animVolume && !this.channelVolume.isEmpty()) ? this.channelVolume.interpolate(frame, this.volume) : this.volume;
        dest[9] = (this.animPan && !this.channelPan.isEmpty()) ? this.channelPan.interpolate(frame, this.pan) : this.pan;

        return dest;
    }

    /** The gain as the factor it multiplies the sound by */
    public float gainFactor()
    {
        return this.gainDb == 0F ? 1F : (float) Math.pow(10D, this.gainDb / 20D);
    }

    /** The volume at a frame with the gain on it: what is played and mixed */
    public float computeVolumeWithGain(int frame)
    {
        return this.computeEffectiveVolume(frame) * this.gainFactor();
    }

    public float computeEffectiveVolume(int frame)
    {
        return (this.animVolume && !this.channelVolume.isEmpty()) ? this.channelVolume.interpolate(frame, this.volume) : this.volume;
    }

    public float computeEffectivePan(int frame)
    {
        return (this.animPan && !this.channelPan.isEmpty()) ? this.channelPan.interpolate(frame, this.pan) : this.pan;
    }

    @Deprecated
    public void evaluateChannels(int frame)
    {
        /* Legacy in-place evaluation for backwards compatibility */
        float[] eff = this.computeEffective(frame);

        this.posX = eff[0];
        this.posY = eff[1];
        this.scaleX = eff[2];
        this.scaleY = eff[3];
        this.rotation = eff[4];
        this.opacity = Math.round(eff[5]);
        this.anchorX = eff[6];
        this.anchorY = eff[7];
        this.volume = eff[8];
        this.pan = eff[9];
    }

    private static final String[] CHANNEL_KEYS = {"channelPosX", "channelPosY", "channelScaleX", "channelScaleY", "channelRotation", "channelOpacity", "channelAnchorX", "channelAnchorY", "channelVolume", "channelPan"};

    /** A copy of the clip with an id of its own, keyframes and all */
    public CatalystLayer duplicate()
    {
        CatalystLayer copy = new CatalystLayer();

        copy.fromData(this.toData());
        copy.id = UUID.randomUUID().toString().substring(0, 8);
        /* A copy is not part of the original's pair */
        copy.linkId = "";

        return copy;
    }

    /**
     * Cuts the clip in two at {@code frame}: this clip keeps what is before it, the returned one gets
     * the rest - the same media from where the cut is, and the keyframes past it. Null when the frame
     * isn't inside the clip. The second part comes in with a plain cut.
     */
    public CatalystLayer splitAt(int frame)
    {
        if (frame <= this.startFrame || frame >= this.startFrame + this.duration)
        {
            return null;
        }

        MapType data = this.toData();

        for (String key : CHANNEL_KEYS)
        {
            data.remove(key);
        }

        CatalystLayer second = new CatalystLayer();
        int first = frame - this.startFrame;

        second.fromData(data);
        second.id = UUID.randomUUID().toString().substring(0, 8);
        second.startFrame = frame;
        second.duration = this.duration - first;
        second.mediaOffset = this.mediaOffset + first;
        second.transitionIn = "";

        this.duration = first;
        this.splitChannelsAt(frame, second);

        return second;
    }

    public void splitChannelsAt(int splitFrame, CatalystLayer target)
    {
        if (target == null)
        {
            return;
        }

        /* Split all 10 animation channels */
        this.splitChannel(this.channelPosX, target.channelPosX, splitFrame, this.posX);
        this.splitChannel(this.channelPosY, target.channelPosY, splitFrame, this.posY);
        this.splitChannel(this.channelScaleX, target.channelScaleX, splitFrame, this.scaleX);
        this.splitChannel(this.channelScaleY, target.channelScaleY, splitFrame, this.scaleY);
        this.splitChannel(this.channelRotation, target.channelRotation, splitFrame, this.rotation);
        this.splitChannel(this.channelOpacity, target.channelOpacity, splitFrame, (float) this.opacity);
        this.splitChannel(this.channelAnchorX, target.channelAnchorX, splitFrame, this.anchorX);
        this.splitChannel(this.channelAnchorY, target.channelAnchorY, splitFrame, this.anchorY);
        this.splitChannel(this.channelVolume, target.channelVolume, splitFrame, this.volume);
        this.splitChannel(this.channelPan, target.channelPan, splitFrame, this.pan);

        /* Update stopwatch animation flags for target and source */
        target.animPosX = this.animPosX && !target.channelPosX.isEmpty();
        target.animPosY = this.animPosY && !target.channelPosY.isEmpty();
        target.animScaleX = this.animScaleX && !target.channelScaleX.isEmpty();
        target.animScaleY = this.animScaleY && !target.channelScaleY.isEmpty();
        target.animRotation = this.animRotation && !target.channelRotation.isEmpty();
        target.animOpacity = this.animOpacity && !target.channelOpacity.isEmpty();
        target.animAnchorX = this.animAnchorX && !target.channelAnchorX.isEmpty();
        target.animAnchorY = this.animAnchorY && !target.channelAnchorY.isEmpty();
        target.animVolume = this.animVolume && !target.channelVolume.isEmpty();
        target.animPan = this.animPan && !target.channelPan.isEmpty();

        this.animPosX = this.animPosX && !this.channelPosX.isEmpty();
        this.animPosY = this.animPosY && !this.channelPosY.isEmpty();
        this.animScaleX = this.animScaleX && !this.channelScaleX.isEmpty();
        this.animScaleY = this.animScaleY && !this.channelScaleY.isEmpty();
        this.animRotation = this.animRotation && !this.channelRotation.isEmpty();
        this.animOpacity = this.animOpacity && !this.channelOpacity.isEmpty();
        this.animAnchorX = this.animAnchorX && !this.channelAnchorX.isEmpty();
        this.animAnchorY = this.animAnchorY && !this.channelAnchorY.isEmpty();
        this.animVolume = this.animVolume && !this.channelVolume.isEmpty();
        this.animPan = this.animPan && !this.channelPan.isEmpty();
    }

    private void splitChannel(KeyframeChannel<Float> source, KeyframeChannel<Float> target, int splitFrame, float defaultValue)
    {
        if (source == null || target == null || source.isEmpty())
        {
            return;
        }

        /* Check if there are keys on both sides of the split point */
        boolean hasBefore = false;
        boolean hasAfter = false;
        boolean hasAtSplit = false;

        for (Keyframe<Float> kf : source.getKeyframes())
        {
            float tick = kf.getTick();

            if (Math.round(tick) == splitFrame)
            {
                hasAtSplit = true;
            }
            else if (tick < splitFrame)
            {
                hasBefore = true;
            }
            else
            {
                hasAfter = true;
            }
        }

        /* If split occurs in the middle of an active interpolation, preserve the curve at the cut boundary */
        if (!hasAtSplit && hasBefore && hasAfter)
        {
            float splitVal = source.interpolate(splitFrame, defaultValue);

            source.insert(splitFrame, splitVal);
        }

        List<Keyframe<Float>> keyframes = source.getKeyframes();

        for (int i = keyframes.size() - 1; i >= 0; i--)
        {
            Keyframe<Float> kf = keyframes.get(i);

            if (kf.getTick() >= splitFrame)
            {
                Keyframe<Float> copy = new Keyframe<>(kf.getId(), kf.getFactory());

                copy.copy(kf);
                target.add(copy);

                /* Strictly later keys belong to target piece; key exactly at splitFrame is retained in both */
                if (kf.getTick() > splitFrame)
                {
                    source.remove(i);
                }
            }
        }

        target.sort();
        source.sort();
    }

    public void copyChannelsTo(CatalystLayer target)
    {
        if (target == null)
        {
            return;
        }

        target.animPosX = this.animPosX;
        target.animPosY = this.animPosY;
        target.animScaleX = this.animScaleX;
        target.animScaleY = this.animScaleY;
        target.animRotation = this.animRotation;
        target.animOpacity = this.animOpacity;
        target.animAnchorX = this.animAnchorX;
        target.animAnchorY = this.animAnchorY;

        target.channelPosX.copy(this.channelPosX);
        target.channelPosY.copy(this.channelPosY);
        target.channelScaleX.copy(this.channelScaleX);
        target.channelScaleY.copy(this.channelScaleY);
        target.channelRotation.copy(this.channelRotation);
        target.channelOpacity.copy(this.channelOpacity);
        target.channelAnchorX.copy(this.channelAnchorX);
        target.channelAnchorY.copy(this.channelAnchorY);
        target.channelVolume.copy(this.channelVolume);
        target.channelPan.copy(this.channelPan);
    }

    public boolean hasKeyframeAt(int propBit, int frame)
    {
        switch (propBit)
        {
            case 1:  return hasKeyAt(this.channelPosX, frame) || hasKeyAt(this.channelPosY, frame);
            case 2:  return hasKeyAt(this.channelScaleX, frame) || hasKeyAt(this.channelScaleY, frame);
            case 4:  return hasKeyAt(this.channelRotation, frame);
            case 8:  return hasKeyAt(this.channelOpacity, frame);
            case 16: return hasKeyAt(this.channelAnchorX, frame) || hasKeyAt(this.channelAnchorY, frame);
            case 32: return hasKeyAt(this.channelVolume, frame);
            case 64: return hasKeyAt(this.channelPan, frame);
            default: return false;
        }
    }

    private static boolean hasKeyAt(KeyframeChannel<?> ch, int frame)
    {
        for (Keyframe<?> kf : ch.getKeyframes())
        {
            if (Math.round(kf.getTick()) == frame) return true;
        }
        return false;
    }

    public void updatePropertyValue(int propBit, int frame, float val1, float val2)
    {
        switch (propBit)
        {
            case 1:
                this.posX = val1;
                this.posY = val2;
                if (this.animPosX || !this.channelPosX.isEmpty()) this.channelPosX.insert(frame, val1);
                if (this.animPosY || !this.channelPosY.isEmpty()) this.channelPosY.insert(frame, val2);
                break;
            case 2:
                this.scaleX = val1;
                this.scaleY = val2;
                if (this.animScaleX || !this.channelScaleX.isEmpty()) this.channelScaleX.insert(frame, val1);
                if (this.animScaleY || !this.channelScaleY.isEmpty()) this.channelScaleY.insert(frame, val2);
                break;
            case 4:
                this.rotation = val1;
                if (this.animRotation || !this.channelRotation.isEmpty()) this.channelRotation.insert(frame, val1);
                break;
            case 8:
                this.opacity = Math.max(0, Math.min(100, Math.round(val1)));
                if (this.animOpacity || !this.channelOpacity.isEmpty()) this.channelOpacity.insert(frame, (float) this.opacity);
                break;
            case 16:
                this.anchorX = val1;
                this.anchorY = val2;
                if (this.animAnchorX || !this.channelAnchorX.isEmpty()) this.channelAnchorX.insert(frame, val1);
                if (this.animAnchorY || !this.channelAnchorY.isEmpty()) this.channelAnchorY.insert(frame, val2);
                break;
            case 32:
                this.volume = Math.max(0.0F, Math.min(2.0F, val1));
                if (this.animVolume || !this.channelVolume.isEmpty()) this.channelVolume.insert(frame, this.volume);
                break;
            case 64:
                this.pan = Math.max(-1.0F, Math.min(1.0F, val1));
                if (this.animPan || !this.channelPan.isEmpty()) this.channelPan.insert(frame, this.pan);
                break;
        }
    }

    /* ── Serialization ── */

    public MapType toData()
    {
        MapType data = new MapType();

        data.putString("id",           this.id);
        data.putString("name",         this.name);
        data.putString("type",         this.layerType.name());
        data.putInt   ("color",        this.color);
        data.putBool  ("visible",      this.visible);
        data.putBool  ("locked",       this.locked);
        data.putBool  ("muted",        this.muted);
        data.putBool  ("solo",         this.solo);
        data.putInt   ("startFrame",   this.startFrame);
        data.putInt   ("duration",     this.duration);
        data.putInt   ("opacity",      this.opacity);
        data.putString("blendMode",    this.blendMode);
        data.putString("resourcePath", this.resourcePath);

        /* Media & Audio properties */
        data.putFloat ("volume",       this.volume);
        data.putFloat ("gainDb",       this.gainDb);
        data.putFloat ("pan",          this.pan);
        data.putInt   ("audioOffset",  this.audioOffset);
        data.putInt   ("mediaOffset",  this.mediaOffset);
        data.putInt   ("mediaDuration", this.mediaDuration);
        data.putInt   ("filmFps",      this.filmFps);

        /* Edit timeline */
        data.putInt   ("track",        this.track);
        data.putFloat ("cropLeft",     this.cropLeft);
        data.putFloat ("cropRight",    this.cropRight);
        data.putFloat ("cropTop",      this.cropTop);
        data.putFloat ("cropBottom",   this.cropBottom);
        data.putBool  ("flipH",        this.flipH);
        data.putBool  ("flipV",        this.flipV);
        data.putBool  ("scaleLocked",  this.scaleLocked);
        data.putString("linkId",       this.linkId);
        data.putBool  ("muteOwnAudio", this.muteOwnAudio);
        data.putString("transitionIn", this.transitionIn);
        data.putInt   ("transitionFrames", this.transitionFrames);

        if (!this.effects.getAllTyped().isEmpty())
        {
            data.put("effects", this.effects.toData());
        }

        /* Text properties */
        data.putInt   ("textColor",    this.textColor);
        data.putInt   ("fontSize",     this.fontSize);
        data.putBool  ("lineWrapping", this.lineWrapping);
        data.putBool  ("shadow",       this.shadow);

        /* Transform properties */
        data.putFloat ("posX",         this.posX);
        data.putFloat ("posY",         this.posY);
        data.putFloat ("scaleX",       this.scaleX);
        data.putFloat ("scaleY",       this.scaleY);
        data.putFloat ("rotation",     this.rotation);
        data.putFloat ("anchorX",      this.anchorX);
        data.putFloat ("anchorY",      this.anchorY);

        /* Keyframe animation channels */
        if (!this.channelPosX.isEmpty()) data.put("channelPosX", this.channelPosX.toData());
        if (!this.channelPosY.isEmpty()) data.put("channelPosY", this.channelPosY.toData());
        if (!this.channelScaleX.isEmpty()) data.put("channelScaleX", this.channelScaleX.toData());
        if (!this.channelScaleY.isEmpty()) data.put("channelScaleY", this.channelScaleY.toData());
        if (!this.channelRotation.isEmpty()) data.put("channelRotation", this.channelRotation.toData());
        if (!this.channelOpacity.isEmpty()) data.put("channelOpacity", this.channelOpacity.toData());
        if (!this.channelAnchorX.isEmpty()) data.put("channelAnchorX", this.channelAnchorX.toData());
        if (!this.channelAnchorY.isEmpty()) data.put("channelAnchorY", this.channelAnchorY.toData());
        if (!this.channelVolume.isEmpty()) data.put("channelVolume", this.channelVolume.toData());
        if (!this.channelPan.isEmpty()) data.put("channelPan", this.channelPan.toData());

        data.putBool("animPosX", this.animPosX);
        data.putBool("animPosY", this.animPosY);
        data.putBool("animScaleX", this.animScaleX);
        data.putBool("animScaleY", this.animScaleY);
        data.putBool("animRotation", this.animRotation);
        data.putBool("animOpacity", this.animOpacity);
        data.putBool("animAnchorX", this.animAnchorX);
        data.putBool("animAnchorY", this.animAnchorY);
        data.putBool("animVolume", this.animVolume);
        data.putBool("animPan", this.animPan);

        return data;
    }

    public void fromData(MapType data)
    {
        if (data == null) return;

        if (data.has("id"))           this.id           = data.getString("id");
        if (data.has("name"))         this.name         = data.getString("name");
        if (data.has("type"))         this.layerType    = LayerType.fromString(data.getString("type"));
        if (data.has("color"))        this.color        = data.getInt("color");
        if (data.has("visible"))      this.visible      = data.getBool("visible");
        if (data.has("locked"))       this.locked       = data.getBool("locked");
        if (data.has("muted"))        this.muted        = data.getBool("muted");
        if (data.has("solo"))         this.solo         = data.getBool("solo");
        if (data.has("startFrame"))   this.startFrame   = data.getInt("startFrame");
        if (data.has("duration"))     this.duration     = Math.max(1, data.getInt("duration"));
        if (data.has("opacity"))      this.opacity      = data.getInt("opacity");
        if (data.has("blendMode"))    this.blendMode    = data.getString("blendMode");
        if (data.has("resourcePath")) this.resourcePath = data.getString("resourcePath").replace('\\', '/');

        /* Media & Audio properties */
        if (data.has("volume"))       this.volume       = data.getFloat("volume");
        if (data.has("gainDb"))       this.gainDb       = data.getFloat("gainDb");
        if (data.has("pan"))          this.pan          = data.getFloat("pan");
        if (data.has("audioOffset"))  this.audioOffset  = data.getInt("audioOffset");
        if (data.has("mediaOffset"))  this.mediaOffset  = data.getInt("mediaOffset");
        if (data.has("mediaDuration")) this.mediaDuration = data.getInt("mediaDuration");
        if (data.has("filmFps"))      this.filmFps      = Math.max(1, data.getInt("filmFps"));

        /* Edit timeline */
        if (data.has("track"))        this.track        = data.getInt("track");
        if (data.has("cropLeft"))     this.cropLeft     = data.getFloat("cropLeft");
        if (data.has("cropRight"))    this.cropRight    = data.getFloat("cropRight");
        if (data.has("cropTop"))      this.cropTop      = data.getFloat("cropTop");
        if (data.has("cropBottom"))   this.cropBottom   = data.getFloat("cropBottom");
        if (data.has("flipH"))        this.flipH        = data.getBool("flipH");
        if (data.has("flipV"))        this.flipV        = data.getBool("flipV");
        if (data.has("scaleLocked"))  this.scaleLocked  = data.getBool("scaleLocked");
        if (data.has("linkId"))       this.linkId       = data.getString("linkId");
        if (data.has("muteOwnAudio")) this.muteOwnAudio = data.getBool("muteOwnAudio");
        if (data.has("transitionIn")) this.transitionIn = data.getString("transitionIn");
        if (data.has("transitionFrames")) this.transitionFrames = Math.max(1, data.getInt("transitionFrames"));

        if (data.has("effects"))
        {
            this.effects.fromData(data.get("effects"));
        }
        else
        {
            this.effects.fromData(new mchorse.bbs_mod.data.types.ListType());
        }

        /* Text properties */
        if (data.has("textColor"))    this.textColor    = data.getInt("textColor");
        if (data.has("fontSize"))     this.fontSize     = data.getInt("fontSize");
        if (data.has("lineWrapping")) this.lineWrapping = data.getBool("lineWrapping");
        if (data.has("shadow"))       this.shadow       = data.getBool("shadow");

        /* Transform properties */
        if (data.has("posX"))         this.posX         = data.getFloat("posX");
        if (data.has("posY"))         this.posY         = data.getFloat("posY");
        if (data.has("scaleX"))       this.scaleX       = data.getFloat("scaleX");
        if (data.has("scaleY"))       this.scaleY       = data.getFloat("scaleY");
        if (data.has("rotation"))     this.rotation     = data.getFloat("rotation");
        if (data.has("anchorX"))      this.anchorX      = data.getFloat("anchorX");
        if (data.has("anchorY"))      this.anchorY      = data.getFloat("anchorY");

        /* Keyframe animation channels */
        if (data.has("channelPosX")) this.channelPosX.fromData(data.get("channelPosX"));
        if (data.has("channelPosY")) this.channelPosY.fromData(data.get("channelPosY"));
        if (data.has("channelScaleX")) this.channelScaleX.fromData(data.get("channelScaleX"));
        if (data.has("channelScaleY")) this.channelScaleY.fromData(data.get("channelScaleY"));
        if (data.has("channelRotation")) this.channelRotation.fromData(data.get("channelRotation"));
        if (data.has("channelOpacity")) this.channelOpacity.fromData(data.get("channelOpacity"));
        if (data.has("channelAnchorX")) this.channelAnchorX.fromData(data.get("channelAnchorX"));
        if (data.has("channelAnchorY")) this.channelAnchorY.fromData(data.get("channelAnchorY"));
        if (data.has("channelVolume")) this.channelVolume.fromData(data.get("channelVolume"));
        if (data.has("channelPan")) this.channelPan.fromData(data.get("channelPan"));

        if (data.has("animPosX")) this.animPosX = data.getBool("animPosX");
        if (data.has("animPosY")) this.animPosY = data.getBool("animPosY");
        if (data.has("animScaleX")) this.animScaleX = data.getBool("animScaleX");
        if (data.has("animScaleY")) this.animScaleY = data.getBool("animScaleY");
        if (data.has("animRotation")) this.animRotation = data.getBool("animRotation");
        if (data.has("animOpacity")) this.animOpacity = data.getBool("animOpacity");
        if (data.has("animAnchorX")) this.animAnchorX = data.getBool("animAnchorX");
        if (data.has("animAnchorY")) this.animAnchorY = data.getBool("animAnchorY");
        if (data.has("animVolume")) this.animVolume = data.getBool("animVolume");
        if (data.has("animPan")) this.animPan = data.getBool("animPan");
    }
}
