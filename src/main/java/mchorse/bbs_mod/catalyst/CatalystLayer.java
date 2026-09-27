package mchorse.bbs_mod.catalyst;

import mchorse.bbs_mod.data.types.MapType;

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

            switch (s.toUpperCase())
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
    public int       startFrame = 0;
    public int       duration   = 300;
    public int       opacity    = 100; // 0 - 100 %
    public String    blendMode  = "NORMAL"; // NORMAL, MULTIPLY, SCREEN, ADD, OVERLAY
    public String    resourcePath = ""; // Resource or asset file path (film id, image, video, audio)

    /* ── Media & Audio Properties ── */
    public float     volume       = 1.0F; // 0.0 to 1.0
    public int       audioOffset  = 0; // Frame offset
    public int       mediaOffset  = 0; // In-point cut/split offset in frames
    public int       mediaDuration = 0; // Cached media duration in frames (if known)

    /* ── Transient Cache (Waveform, Video & Profiler) ── */
    public transient Object  cachedWaveform = null;
    public transient boolean isWaveformLoading = false;
    public transient String  cachedWaveformPath = null;
    public transient Object  cachedVideoTexture = null;
    public transient int     lastVideoFrameIndex = -1;
    public transient double  lastRenderMs = 0.0;

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
        data.putInt   ("startFrame",   this.startFrame);
        data.putInt   ("duration",     this.duration);
        data.putInt   ("opacity",      this.opacity);
        data.putString("blendMode",    this.blendMode);
        data.putString("resourcePath", this.resourcePath);

        /* Media & Audio properties */
        data.putFloat ("volume",       this.volume);
        data.putInt   ("audioOffset",  this.audioOffset);
        data.putInt   ("mediaOffset",  this.mediaOffset);
        data.putInt   ("mediaDuration", this.mediaDuration);

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
        if (data.has("startFrame"))   this.startFrame   = data.getInt("startFrame");
        if (data.has("duration"))     this.duration     = data.getInt("duration");
        if (data.has("opacity"))      this.opacity      = data.getInt("opacity");
        if (data.has("blendMode"))    this.blendMode    = data.getString("blendMode");
        if (data.has("resourcePath")) this.resourcePath = data.getString("resourcePath");

        /* Media & Audio properties */
        if (data.has("volume"))       this.volume       = data.getFloat("volume");
        if (data.has("audioOffset"))  this.audioOffset  = data.getInt("audioOffset");
        if (data.has("mediaOffset"))  this.mediaOffset  = data.getInt("mediaOffset");
        if (data.has("mediaDuration")) this.mediaDuration = data.getInt("mediaDuration");

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
    }
}
