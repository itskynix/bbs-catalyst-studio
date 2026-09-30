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
        SOLID, AUDIO, ADJUSTMENT, NULL, TEXT, SCENE, VIDEO, IMAGE;

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
    public float     pan          = 0.0F; // -1.0 (Left) .. 0.0 (Center) .. +1.0 (Right)
    public int       audioOffset  = 0; // Frame offset
    public int       mediaOffset  = 0; // In-point cut/split offset in frames
    public int       mediaDuration = 0; // Cached media duration in frames (if known)
    public int       filmFps      = 20; // FPS / tick-rate for SCENE/Film layer (default 20 TPS)

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

    public final KeyframeChannel<Float> channelPosX     = new KeyframeChannel<>("posX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelPosY     = new KeyframeChannel<>("posY", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelScaleX   = new KeyframeChannel<>("scaleX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelScaleY   = new KeyframeChannel<>("scaleY", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelRotation = new KeyframeChannel<>("rotation", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelOpacity  = new KeyframeChannel<>("opacity", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelAnchorX  = new KeyframeChannel<>("anchorX", KeyframeFactories.FLOAT);
    public final KeyframeChannel<Float> channelAnchorY  = new KeyframeChannel<>("anchorY", KeyframeFactories.FLOAT);

    public boolean hasAnyKeyframes(int propBit)
    {
        switch (propBit)
        {
            case 1:  return !this.channelPosX.isEmpty() || !this.channelPosY.isEmpty();
            case 2:  return !this.channelScaleX.isEmpty() || !this.channelScaleY.isEmpty();
            case 4:  return !this.channelRotation.isEmpty();
            case 8:  return !this.channelOpacity.isEmpty();
            case 16: return !this.channelAnchorX.isEmpty() || !this.channelAnchorY.isEmpty();
            default: return false;
        }
    }

    public boolean hasAnyKeyframesTotal()
    {
        return !this.channelPosX.isEmpty() || !this.channelPosY.isEmpty()
            || !this.channelScaleX.isEmpty() || !this.channelScaleY.isEmpty()
            || !this.channelRotation.isEmpty() || !this.channelOpacity.isEmpty()
            || !this.channelAnchorX.isEmpty() || !this.channelAnchorY.isEmpty();
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
        return this.computeEffective(frame, new float[8]);
    }

    public float[] computeEffective(int frame, float[] dest)
    {
        if (dest == null || dest.length < 8)
        {
            dest = new float[8];
        }

        dest[0] = (this.animPosX && !this.channelPosX.isEmpty()) ? this.channelPosX.interpolate(frame, this.posX) : this.posX;
        dest[1] = (this.animPosY && !this.channelPosY.isEmpty()) ? this.channelPosY.interpolate(frame, this.posY) : this.posY;
        dest[2] = (this.animScaleX && !this.channelScaleX.isEmpty()) ? this.channelScaleX.interpolate(frame, this.scaleX) : this.scaleX;
        dest[3] = (this.animScaleY && !this.channelScaleY.isEmpty()) ? this.channelScaleY.interpolate(frame, this.scaleY) : this.scaleY;
        dest[4] = (this.animRotation && !this.channelRotation.isEmpty()) ? this.channelRotation.interpolate(frame, this.rotation) : this.rotation;
        dest[5] = (this.animOpacity && !this.channelOpacity.isEmpty()) ? (float) Math.round(this.channelOpacity.interpolate(frame, (float) this.opacity)) : (float) this.opacity;
        dest[6] = (this.animAnchorX && !this.channelAnchorX.isEmpty()) ? this.channelAnchorX.interpolate(frame, this.anchorX) : this.anchorX;
        dest[7] = (this.animAnchorY && !this.channelAnchorY.isEmpty()) ? this.channelAnchorY.interpolate(frame, this.anchorY) : this.anchorY;

        return dest;
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
    }

    public void splitChannelsAt(int splitFrame, CatalystLayer target)
    {
        if (target == null)
        {
            return;
        }

        /* Split all 8 animation channels */
        this.splitChannel(this.channelPosX, target.channelPosX, splitFrame, this.posX);
        this.splitChannel(this.channelPosY, target.channelPosY, splitFrame, this.posY);
        this.splitChannel(this.channelScaleX, target.channelScaleX, splitFrame, this.scaleX);
        this.splitChannel(this.channelScaleY, target.channelScaleY, splitFrame, this.scaleY);
        this.splitChannel(this.channelRotation, target.channelRotation, splitFrame, this.rotation);
        this.splitChannel(this.channelOpacity, target.channelOpacity, splitFrame, (float) this.opacity);
        this.splitChannel(this.channelAnchorX, target.channelAnchorX, splitFrame, this.anchorX);
        this.splitChannel(this.channelAnchorY, target.channelAnchorY, splitFrame, this.anchorY);

        /* Update stopwatch animation flags for target and source */
        target.animPosX = this.animPosX && !target.channelPosX.isEmpty();
        target.animPosY = this.animPosY && !target.channelPosY.isEmpty();
        target.animScaleX = this.animScaleX && !target.channelScaleX.isEmpty();
        target.animScaleY = this.animScaleY && !target.channelScaleY.isEmpty();
        target.animRotation = this.animRotation && !target.channelRotation.isEmpty();
        target.animOpacity = this.animOpacity && !target.channelOpacity.isEmpty();
        target.animAnchorX = this.animAnchorX && !target.channelAnchorX.isEmpty();
        target.animAnchorY = this.animAnchorY && !target.channelAnchorY.isEmpty();

        this.animPosX = this.animPosX && !this.channelPosX.isEmpty();
        this.animPosY = this.animPosY && !this.channelPosY.isEmpty();
        this.animScaleX = this.animScaleX && !this.channelScaleX.isEmpty();
        this.animScaleY = this.animScaleY && !this.channelScaleY.isEmpty();
        this.animRotation = this.animRotation && !this.channelRotation.isEmpty();
        this.animOpacity = this.animOpacity && !this.channelOpacity.isEmpty();
        this.animAnchorX = this.animAnchorX && !this.channelAnchorX.isEmpty();
        this.animAnchorY = this.animAnchorY && !this.channelAnchorY.isEmpty();
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
        data.putFloat ("pan",          this.pan);
        data.putInt   ("audioOffset",  this.audioOffset);
        data.putInt   ("mediaOffset",  this.mediaOffset);
        data.putInt   ("mediaDuration", this.mediaDuration);
        data.putInt   ("filmFps",      this.filmFps);

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

        data.putBool("animPosX", this.animPosX);
        data.putBool("animPosY", this.animPosY);
        data.putBool("animScaleX", this.animScaleX);
        data.putBool("animScaleY", this.animScaleY);
        data.putBool("animRotation", this.animRotation);
        data.putBool("animOpacity", this.animOpacity);
        data.putBool("animAnchorX", this.animAnchorX);
        data.putBool("animAnchorY", this.animAnchorY);

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
        if (data.has("pan"))          this.pan          = data.getFloat("pan");
        if (data.has("audioOffset"))  this.audioOffset  = data.getInt("audioOffset");
        if (data.has("mediaOffset"))  this.mediaOffset  = data.getInt("mediaOffset");
        if (data.has("mediaDuration")) this.mediaDuration = data.getInt("mediaDuration");
        if (data.has("filmFps"))      this.filmFps      = Math.max(1, data.getInt("filmFps"));

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

        if (data.has("animPosX")) this.animPosX = data.getBool("animPosX");
        if (data.has("animPosY")) this.animPosY = data.getBool("animPosY");
        if (data.has("animScaleX")) this.animScaleX = data.getBool("animScaleX");
        if (data.has("animScaleY")) this.animScaleY = data.getBool("animScaleY");
        if (data.has("animRotation")) this.animRotation = data.getBool("animRotation");
        if (data.has("animOpacity")) this.animOpacity = data.getBool("animOpacity");
        if (data.has("animAnchorX")) this.animAnchorX = data.getBool("animAnchorX");
        if (data.has("animAnchorY")) this.animAnchorY = data.getBool("animAnchorY");
    }
}
