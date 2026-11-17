package mchorse.bbs_mod.physics.client.clips;

import mchorse.bbs_mod.ui.film.IUIClipsDelegate;
import mchorse.bbs_mod.ui.film.clips.actions.UIActionClip;
import mchorse.bbs_mod.ui.film.clips.modules.UIPointModule;
import mchorse.bbs_mod.ui.film.utils.UICameraUtils;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.camera.data.Point;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.physics.actions.ImpulseActionClip;
import mchorse.bbs_mod.physics.client.forms.PhysicsKeys;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;

/**
 * The impulse clip's panel: where the push happens, which shape it takes (an explosion away from
 * the point or a shove along a direction), how hard and how far.
 *
 * <p>The point's context menu can take the spot the player is looking at — placing an explosion is
 * aiming, and typing world coordinates by hand is nobody's idea of aiming. The exact hit point on
 * the surface, not the block corner: an explosion half a block off reads as "under him" instead of
 * "at his feet".</p>
 */
public class UIImpulseActionClip extends UIActionClip<ImpulseActionClip>
{
    public UIPointModule point;
    public UIToggle radial;
    public UIToggle onlyThisReplay;
    public UITrackpad strength;
    public UITrackpad radius;
    public UIPointModule direction;

    public UIImpulseActionClip(ImpulseActionClip clip, IUIClipsDelegate editor)
    {
        super(clip, editor);
    }

    @Override
    protected void registerUI()
    {
        super.registerUI();

        this.point = new UIPointModule(this.editor, PhysicsKeys.CLIP_POINT);
        this.point.context((menu) ->
        {
            UICameraUtils.pointContextMenu(menu, this.editor, this.clip.point);

            /* BBS CS: in the film editor this is the preview camera's crosshair, not the player's */
            PhysicsAimMenus.point(menu, this.clip.point, this::fillData);
        });

        this.radial = new UIToggle(PhysicsKeys.CLIP_RADIAL, (toggle) -> this.editor.editMultiple(this.clip.radial, (radial) -> radial.set(toggle.getValue())));
        this.radial.tooltip(PhysicsKeys.CLIP_RADIAL_TOOLTIP);

        this.onlyThisReplay = new UIToggle(PhysicsKeys.CLIP_ONLY_THIS_REPLAY, (toggle) -> this.editor.editMultiple(this.clip.onlyThisReplay, (value) -> value.set(toggle.getValue())));
        this.onlyThisReplay.tooltip(PhysicsKeys.CLIP_ONLY_THIS_REPLAY_TOOLTIP);

        this.strength = new UITrackpad((v) -> this.editor.editMultiple(this.clip.strength, (strength) -> strength.set(v.floatValue())));
        this.strength.tooltip(PhysicsKeys.CLIP_STRENGTH_TOOLTIP);

        this.radius = new UITrackpad((v) -> this.editor.editMultiple(this.clip.radius, (radius) -> radius.set(v.floatValue())));
        this.radius.limit(0F).tooltip(PhysicsKeys.CLIP_RADIUS_TOOLTIP);

        /* The blast's strength and reach are set while aiming it, so they offer the crosshair too */
        this.strength.context((menu) -> PhysicsAimMenus.point(menu, this.clip.point, this::fillData));
        this.radius.context((menu) -> PhysicsAimMenus.point(menu, this.clip.point, this::fillData));

        this.direction = new UIPointModule(this.editor, PhysicsKeys.CLIP_DIRECTION);
        this.direction.context((menu) ->
        {
            UICameraUtils.pointContextMenu(menu, this.editor, this.clip.direction);

            /* Taking a direction means a shove along it, not a blast away from the point */
            PhysicsAimMenus.direction(menu, this.clip.direction, () ->
            {
                BaseValue.edit(this.clip.radial, (radial) -> radial.set(false));
                this.fillData();
            });
        });
    }

    @Override
    protected void registerPanels()
    {
        super.registerPanels();

        this.panels.add(this.point);
        this.panels.add(this.section(PhysicsKeys.CLIP_STRENGTH, this.strength));
        this.panels.add(this.section(PhysicsKeys.CLIP_RADIUS, this.radius));
        this.panels.add(this.radial);
        this.panels.add(this.onlyThisReplay);
        this.panels.add(this.direction);
    }

    @Override
    public void fillData()
    {
        super.fillData();

        this.point.fill(this.clip.point);
        this.radial.setValue(this.clip.radial.get());
        this.onlyThisReplay.setValue(this.clip.onlyThisReplay.get());
        this.strength.setValue(this.clip.strength.get());
        this.radius.setValue(this.clip.radius.get());
        this.direction.fill(this.clip.direction);
    }
}
