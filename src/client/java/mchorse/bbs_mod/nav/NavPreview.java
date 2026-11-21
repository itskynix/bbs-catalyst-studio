package mchorse.bbs_mod.nav;

/**
 * Whether the navigation meshes are shown: the P key switches it, in the world and in the film editor,
 * and every navigation volume that is drawn draws its mesh while it is on. The boxes and paths the
 * navigation wand saved, and the boxes of the volumes, are shown with it too, the wand in hand or not.
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

        NavigationWandController.notify((visible ? mchorse.bbs_mod.ui.UIKeys.NAV_WAND_PREVIEW_ON : mchorse.bbs_mod.ui.UIKeys.NAV_WAND_PREVIEW_OFF).get());
    }
}
