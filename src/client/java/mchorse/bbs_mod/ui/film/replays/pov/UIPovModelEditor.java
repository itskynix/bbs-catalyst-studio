package mchorse.bbs_mod.ui.film.replays.pov;

import mchorse.bbs_mod.film.replays.PovRig;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIIcon;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UIPropTransform;
import mchorse.bbs_mod.ui.framework.elements.input.list.UIStringList;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlayPanel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.pose.Transform;

/**
 * The POV model editor: the replay's first-person arms in a 16:9 view, the rig's bones beside it,
 * and the picked bone's visibility and transform. The view is locked to the eye unless the free
 * camera is on, in which case it orbits, so the arms can be seen from outside while being placed.
 * A bone can be picked in the view as well as in the list, and dragged by its gizmo's handles.
 * Every edit lands on the replay's {@code pov.rig}, which the film's hand pass reads.
 */
public class UIPovModelEditor extends UIOverlayPanel
{
    private static final int SIDEBAR = 200;

    public final UIPovViewport viewport;
    public final UIStringList bones;
    public final UIToggle visible;
    public final UIPropTransform transform;
    public final UIIcon freeCamera;
    public final UIIcon resetBone;
    public final UIIcon gizmoMode;

    private final Replay replay;
    private String bone = PovRig.RIGHT_ARM;

    public UIPovModelEditor(UIFilmPanel filmPanel, Replay replay)
    {
        super(UIKeys.FILM_REPLAY_POV_EDITOR_TITLE);

        this.replay = replay;

        this.viewport = new UIPovViewport(() -> this.replay,
            () -> filmPanel.getController().getEntities().get(this.replay.getId()),
            () -> filmPanel.getKeyframeCursor(this.getContext() == null ? 0F : this.getContext().getTransition()));
        this.viewport.relative(this.content).x(0).y(0).w(1F, -SIDEBAR).h(1F);

        this.bones = new UIStringList((l) -> this.pick(l.get(0)))
        {
            @Override
            protected boolean sortElements()
            {
                return false;
            }

            @Override
            protected String elementToString(UIContext context, int i, String element)
            {
                return PovRig.isArm(element) ? element : "  └ " + element;
            }
        };
        this.bones.add(PovRig.BONES);
        this.bones.background();

        this.visible = new UIToggle(UIKeys.FILM_REPLAY_POV_EDITOR_VISIBLE, (b) ->
        {
            PovRig.Bone bone = this.getBone();

            if (bone != null)
            {
                bone.visible.set(b.getValue());
            }
        });

        this.transform = new UIPropTransform().callbacks(() -> this.getBone() == null ? null : this.getBone().transform);

        UIElement sidebar = UI.column(4, 6, this.bones, this.visible, this.transform);

        this.bones.h(16 * PovRig.BONES.size() + 4);
        sidebar.relative(this.content).x(1F, -SIDEBAR).y(0).w(SIDEBAR).h(1F);

        this.freeCamera = new UIIcon(() -> this.viewport.isFree() ? Icons.UNLOCKED : Icons.LOCKED, (b) -> this.viewport.setFree(!this.viewport.isFree()));
        this.freeCamera.tooltip(() -> (this.viewport.isFree() ? UIKeys.FILM_REPLAY_POV_EDITOR_FREE : UIKeys.FILM_REPLAY_POV_EDITOR_LOCKED).get());

        this.resetBone = new UIIcon(Icons.REFRESH, (b) -> this.resetBone());
        this.resetBone.tooltip(UIKeys.FILM_REPLAY_POV_EDITOR_RESET);

        /* The gizmo's handles move the bone, or turn it */
        this.gizmoMode = new UIIcon(() -> this.viewport.isRotating() ? Icons.ORBIT : Icons.ALL_DIRECTIONS, (b) -> this.viewport.setRotating(!this.viewport.isRotating()));
        this.gizmoMode.tooltip(() -> (this.viewport.isRotating() ? UIKeys.FILM_REPLAY_POV_EDITOR_ROTATE : UIKeys.FILM_REPLAY_POV_EDITOR_TRANSLATE).get());

        /* A bone picked in the view is picked in the list; a drag shows in the fields as it goes */
        this.viewport.callbacks((bone) ->
        {
            this.pick(bone);
            this.bones.setCurrentScroll(bone);
        }, () -> this.transform.setTransform(this.getBone() == null ? new Transform() : this.getBone().transform.get()));

        this.icons.add(this.freeCamera, this.gizmoMode, this.resetBone);
        this.content.add(this.viewport, sidebar);

        this.pick(PovRig.RIGHT_ARM);
        this.bones.setCurrentScroll(PovRig.RIGHT_ARM);
    }

    private PovRig.Bone getBone()
    {
        return this.replay.pov.rig.getBone(this.bone);
    }

    private void pick(String bone)
    {
        this.bone = bone;

        PovRig.Bone value = this.getBone();

        this.viewport.setSelected(bone);

        if (value != null)
        {
            this.visible.setValue(value.visible.get());
            this.transform.setTransform(value.transform.get());
        }
    }

    private void resetBone()
    {
        PovRig.Bone bone = this.getBone();

        if (bone == null)
        {
            return;
        }

        BaseValue.edit(bone.transform, (value) -> value.get().copy(new Transform()));
        bone.visible.set(true);
        this.pick(this.bone);
    }
}
