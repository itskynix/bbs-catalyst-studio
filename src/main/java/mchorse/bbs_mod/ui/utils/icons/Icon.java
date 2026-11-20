package mchorse.bbs_mod.ui.utils.icons;

import mchorse.bbs_mod.resources.Link;

public class Icon
{
    public final Link texture;
    public final String id;
    public final int x;
    public final int y;
    public final int w;
    public final int h;
    public int textureW = 256;
    public int textureH = 256;

    /**
     * Whether the icon is drawn in its own colours (a logo, a colour wheel) rather than plain white:
     * such an icon is never tinted dark on light surfaces.
     */
    public boolean colored;

    public Icon(Link texture, String id, int x, int y)
    {
        this(texture, id, x, y, 16, 16);
    }

    public Icon(Link texture, String id, int x, int y, int w, int h)
    {
        this.texture = texture;
        this.id = id;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    public Icon(Link texture, String id, int x, int y, int w, int h, int textureW, int textureH)
    {
        this(texture, id, x, y, w, h);

        this.textureW = textureW;
        this.textureH = textureH;
    }

    /** Marks the icon as drawn in its own colours, see {@link #colored} */
    public Icon colored()
    {
        this.colored = true;

        return this;
    }
}