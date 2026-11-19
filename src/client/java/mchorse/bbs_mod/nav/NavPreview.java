package mchorse.bbs_mod.nav;

/**
 * Whether the navigation meshes are shown: the P key switches it, in the world and in the film editor,
 * and every navigation volume that is drawn draws its mesh while it is on.
 */
public final class NavPreview
{
    private static boolean visible;

    private NavPreview()
    {}

    public static boolean isVisible()
    {
        return visible;
    }

    public static void toggle()
    {
        visible = !visible;
    }
}
