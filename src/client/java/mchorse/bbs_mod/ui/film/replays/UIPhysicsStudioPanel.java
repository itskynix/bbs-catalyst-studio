package mchorse.bbs_mod.ui.film.replays;

import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.physics.PhysicsMaterial;
import mchorse.bbs_mod.physics.PhysicsStudio;
import mchorse.bbs_mod.physics.ReplayPhysics;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.ui.framework.UIContext;
import mchorse.bbs_mod.ui.framework.elements.UIElement;
import mchorse.bbs_mod.ui.framework.elements.UISection;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIButton;
import mchorse.bbs_mod.ui.framework.elements.buttons.UIToggle;
import mchorse.bbs_mod.ui.framework.elements.input.UITrackpad;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIMessageOverlayPanel;
import mchorse.bbs_mod.ui.framework.elements.overlay.UIOverlay;
import mchorse.bbs_mod.ui.framework.elements.utils.UILabel;
import mchorse.bbs_mod.ui.utils.UI;
import mchorse.bbs_mod.ui.utils.UIConstants;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.colors.Colors;
import org.joml.Vector3d;

import java.util.function.Consumer;

/**
 * The physics studio's controls, beside the dope sheet's Physics tab: four folding categories that hold every
 * setting of the replay's simulation and the studio's one-click jobs. The keyframed handles live on the dope
 * sheet next to them; what is here is what is not keyed, and the buttons that key things at the cursor.
 */
public class UIPhysicsStudioPanel extends UIElement
{
    private static final int GAP = 4;

    private final UIFilmPanel filmPanel;
    private Replay replay;

    private final UIElement content;
    private UILabel status;

    private UIToggle enabled;
    private UIToggle ragdoll;
    private UITrackpad animation;
    private UITrackpad muscles;

    private final UIButton[] materials = new UIButton[PhysicsMaterial.values().length];
    private UITrackpad mass;
    private UITrackpad friction;
    private UITrackpad restitution;

    private UIToggle balloon;
    private UITrackpad lift;
    private UIToggle rope;
    private UIButton ropeAnchor;
    private UITrackpad ropeLength;

    private UITrackpad pushX;
    private UITrackpad pushY;
    private UITrackpad pushZ;
    private UITrackpad blastStrength;
    private UITrackpad blastRadius;
    private UILabel cache;

    /** Asked every frame: whether the Details area is the studio's right now */
    private final java.util.function.BooleanSupplier shown;

    public UIPhysicsStudioPanel(UIFilmPanel filmPanel, java.util.function.BooleanSupplier shown)
    {
        this.filmPanel = filmPanel;
        this.shown = shown;

        this.status = new UILabel(UIKeys.FILM_PHYSICS_UNAVAILABLE).color(Colors.setA(Colors.WHITE, 0.6F));
        this.status.h(20);

        /* 1. Ragdoll and character */
        this.enabled = this.bound(new UIToggle(UIKeys.FILM_PHYSICS_ENABLED, (b) -> this.edit((p) -> p.enabled.set(b.getValue()))), (r) -> this.enabled.setValue(r.physics.enabled.get()));
        this.enabled.tooltip(UIKeys.FILM_PHYSICS_ENABLED_TOOLTIP);
        this.ragdoll = this.bound(new UIToggle(UIKeys.FILM_PHYSICS_RAGDOLL, (b) -> this.edit((p) -> p.ragdoll.set(b.getValue()))), (r) -> this.ragdoll.setValue(r.physics.ragdoll.get()));
        this.ragdoll.tooltip(UIKeys.FILM_PHYSICS_RAGDOLL_TOOLTIP);
        this.animation = this.bound(new UITrackpad((v) -> this.keyAnimation(v.floatValue())), (r) -> this.animation.setValue(r.physics.animationAt(this.filmPanel.getCursor())));
        this.animation.limit(0, 1).values(0.05, 0.01, 0.1).tooltip(UIKeys.FILM_PHYSICS_ANIMATION_TOOLTIP);
        this.muscles = this.bound(new UITrackpad((v) -> this.edit((p) -> p.muscles.set(v.floatValue()))), (r) -> this.muscles.setValue(r.physics.muscles.get()));
        this.muscles.limit(0, 1).values(0.05, 0.01, 0.1).tooltip(UIKeys.FILM_PHYSICS_MUSCLES_TOOLTIP);

        UIButton release = new UIButton(UIKeys.FILM_PHYSICS_RELEASE, (b) -> this.keyAnimation(0F));
        UIButton hold = new UIButton(UIKeys.FILM_PHYSICS_HOLD, (b) -> this.keyAnimation(1F));

        release.tooltip(UIKeys.FILM_PHYSICS_RELEASE_TOOLTIP);
        hold.tooltip(UIKeys.FILM_PHYSICS_HOLD_TOOLTIP);

        UISection ragdollSection = new UISection(UIKeys.FILM_PHYSICS_SECTIONS_RAGDOLL);

        UIButton tear = new UIButton(UIKeys.FILM_PHYSICS_TEAR, (b) -> this.openTearMenu());

        tear.tooltip(UIKeys.FILM_PHYSICS_TEAR_TOOLTIP);
        this.bound(tear, (r) -> tear.label = r.physics.tearBone.get().isEmpty() || r.physics.tearTick.get() < 0
            ? UIKeys.FILM_PHYSICS_TEAR : UIKeys.FILM_PHYSICS_TEAR_SET.format(r.physics.tearBone.get(), r.physics.tearTick.get()));

        ragdollSection.fields.add(this.enabled, this.ragdoll, tear, this.labelled(UIKeys.FILM_PHYSICS_ANIMATION, this.animation),
            this.labelled(UIKeys.FILM_PHYSICS_MUSCLES, this.muscles), UI.row(release, hold), this.note(UIKeys.FILM_PHYSICS_NOTE_JOINTS));

        /* 2. Rigid body and structure */
        UIElement presets = UI.row(0, 0, 20);

        for (PhysicsMaterial material : PhysicsMaterial.values())
        {
            UIButton button = new UIButton(label(material), (b) -> this.edit((p) ->
            {
                p.material.set(material.id);
                p.friction.set(material.friction);
                p.restitution.set(material.restitution);
                p.mass.set(0F);
            }));

            this.materials[material.ordinal()] = button;
            presets.add(button);
        }

        this.mass = this.bound(new UITrackpad((v) -> this.edit((p) -> p.mass.set(v.floatValue()))), (r) -> this.mass.setValue(r.physics.mass.get()));
        this.mass.limit(0, 5000).values(1, 0.1, 10);
        this.friction = this.bound(new UITrackpad((v) -> this.edit((p) -> p.friction.set(v.floatValue()))), (r) -> this.friction.setValue(r.physics.friction.get()));
        this.friction.limit(0, 2).values(0.05, 0.01, 0.1);
        this.restitution = this.bound(new UITrackpad((v) -> this.edit((p) -> p.restitution.set(v.floatValue()))), (r) -> this.restitution.setValue(r.physics.restitution.get()));
        this.restitution.limit(0, 1).values(0.05, 0.01, 0.1);

        UISection solidSection = new UISection(UIKeys.FILM_PHYSICS_SECTIONS_SOLID);

        solidSection.fields.add(UI.label(UIKeys.FILM_PHYSICS_MATERIAL, 14), presets, this.labelled(UIKeys.FILM_PHYSICS_MASS, this.mass),
            this.labelled(UIKeys.FILM_PHYSICS_FRICTION, this.friction), this.labelled(UIKeys.FILM_PHYSICS_RESTITUTION, this.restitution),
            this.note(UIKeys.FILM_PHYSICS_NOTE_DESTRUCTION));

        /* 3. Cloth, balloon and rope */
        this.balloon = this.bound(new UIToggle(UIKeys.FILM_PHYSICS_BALLOON, (b) -> this.edit((p) -> p.balloon.set(b.getValue()))), (r) -> this.balloon.setValue(r.physics.balloon.get()));
        this.balloon.tooltip(UIKeys.FILM_PHYSICS_BALLOON_TOOLTIP);
        this.lift = this.bound(new UITrackpad((v) -> this.edit((p) -> p.lift.set(v.floatValue()))), (r) -> this.lift.setValue(r.physics.lift.get()));
        this.lift.limit(0.01, 3).values(0.05, 0.01, 0.1);
        this.rope = this.bound(new UIToggle(UIKeys.FILM_PHYSICS_ROPE, (b) -> this.edit((p) -> p.rope.set(b.getValue()))), (r) -> this.rope.setValue(r.physics.rope.get()));
        this.rope.tooltip(UIKeys.FILM_PHYSICS_ROPE_TOOLTIP);
        this.ropeAnchor = this.bound(new UIButton(IKey.EMPTY, (b) -> this.openAnchorMenu()), (r) -> this.ropeAnchor.label = UIKeys.FILM_PHYSICS_ROPE_ANCHOR.format(this.anchorName(r.physics.ropeAnchor.get())));
        this.ropeLength = this.bound(new UITrackpad((v) -> this.edit((p) -> p.ropeLength.set(v.floatValue()))), (r) -> this.ropeLength.setValue(r.physics.ropeLength.get()));
        this.ropeLength.limit(0.2, 64).values(0.1, 0.01, 1);

        UISection softSection = new UISection(UIKeys.FILM_PHYSICS_SECTIONS_SOFT);

        softSection.fields.add(this.balloon, this.labelled(UIKeys.FILM_PHYSICS_LIFT, this.lift), this.rope, this.ropeAnchor,
            this.labelled(UIKeys.FILM_PHYSICS_ROPE_LENGTH, this.ropeLength), this.note(UIKeys.FILM_PHYSICS_NOTE_CLOTH));

        /* 4. Force, blast and bake */
        this.pushX = new UITrackpad();
        this.pushY = new UITrackpad();
        this.pushZ = new UITrackpad();

        for (UITrackpad pad : new UITrackpad[] {this.pushX, this.pushY, this.pushZ})
        {
            pad.limit(-100, 100).values(0.5, 0.1, 2).tooltip(UIKeys.FILM_PHYSICS_PUSH_TOOLTIP);
        }

        this.pushY.setValue(4);

        UIButton push = new UIButton(UIKeys.FILM_PHYSICS_PUSH_ADD, (b) -> this.push());

        push.tooltip(UIKeys.FILM_PHYSICS_PUSH_TOOLTIP);

        this.blastStrength = new UITrackpad();
        this.blastStrength.limit(0, 200).values(1, 0.1, 5);
        this.blastStrength.setValue(12);
        this.blastRadius = this.bound(new UITrackpad((v) -> this.edit((p) -> p.blastRadius.set(v.floatValue()))), (r) -> this.blastRadius.setValue(r.physics.blastRadius.get()));
        this.blastRadius.limit(0.5, 64).values(0.5, 0.1, 2);

        UIButton blast = new UIButton(UIKeys.FILM_PHYSICS_BLAST_ADD, (b) -> this.blast());

        blast.tooltip(UIKeys.FILM_PHYSICS_BLAST_TOOLTIP);

        UIButton bake = new UIButton(UIKeys.FILM_PHYSICS_BAKE, (b) -> this.bake());

        bake.tooltip(UIKeys.FILM_PHYSICS_BAKE_TOOLTIP);

        this.cache = new UILabel(UIKeys.FILM_PHYSICS_CACHE_NONE).color(Colors.setA(Colors.WHITE, 0.6F));
        this.cache.h(14);

        UISection forcesSection = new UISection(UIKeys.FILM_PHYSICS_SECTIONS_FORCES);

        forcesSection.fields.add(UI.label(UIKeys.FILM_PHYSICS_PUSH, 14), UI.row(this.pushX, this.pushY, this.pushZ), push,
            this.labelled(UIKeys.FILM_PHYSICS_BLAST_STRENGTH, this.blastStrength), this.labelled(UIKeys.FILM_PHYSICS_BLAST_RADIUS, this.blastRadius), blast);

        /* The hand tools: picked here for the preview, or given to the player to hold */
        UIButton physicsTool = new UIButton(UIKeys.FILM_PHYSICS_TOOL_PHYSICS, (b) -> mchorse.bbs_mod.physics.PhysicsToolController.pick(mchorse.bbs_mod.physics.PhysicsToolController.Tool.PHYSICS));
        UIButton gravityTool = new UIButton(UIKeys.FILM_PHYSICS_TOOL_GRAVITY, (b) -> mchorse.bbs_mod.physics.PhysicsToolController.pick(mchorse.bbs_mod.physics.PhysicsToolController.Tool.GRAVITY));

        physicsTool.tooltip(UIKeys.FILM_PHYSICS_TOOL_PHYSICS_TOOLTIP);
        gravityTool.tooltip(UIKeys.FILM_PHYSICS_TOOL_GRAVITY_TOOLTIP);
        physicsTool.color(0x3AD7FF);
        gravityTool.color(0xFF8C1C);

        UIButton givePhysics = new UIButton(UIKeys.FILM_PHYSICS_GIVE_PHYSICS, (b) -> this.give(mchorse.bbs_mod.items.PhysicsToolItem.Kind.PHYSICS));
        UIButton giveGravity = new UIButton(UIKeys.FILM_PHYSICS_GIVE_GRAVITY, (b) -> this.give(mchorse.bbs_mod.items.PhysicsToolItem.Kind.GRAVITY));

        /* The three one-click actions on top; everything else waits folded under the advanced settings */
        UIButton dropRagdoll = new UIButton(UIKeys.FILM_PHYSICS_ACTION_RAGDOLL, (b) -> this.dropAsRagdoll());
        UIButton makeRigid = new UIButton(UIKeys.FILM_PHYSICS_ACTION_RIGID, (b) -> this.makeRigid());
        UIButton backToAnimation = new UIButton(UIKeys.FILM_PHYSICS_ACTION_ANIMATION, (b) -> this.keyAnimation(1F));

        dropRagdoll.tooltip(UIKeys.FILM_PHYSICS_ACTION_RAGDOLL_TOOLTIP);
        makeRigid.tooltip(UIKeys.FILM_PHYSICS_ACTION_RIGID_TOOLTIP);
        backToAnimation.tooltip(UIKeys.FILM_PHYSICS_ACTION_ANIMATION_TOOLTIP);
        dropRagdoll.color(0x40E0A0);
        dropRagdoll.h(24);
        makeRigid.h(24);
        backToAnimation.h(24);

        UIToggle shapes = new UIToggle(UIKeys.FILM_PHYSICS_SHAPES, (b) -> mchorse.bbs_mod.physics.PhysicsStudio.showShapes = b.getValue());

        shapes.valueBinding(() -> shapes.setValue(mchorse.bbs_mod.physics.PhysicsStudio.showShapes));
        shapes.tooltip(UIKeys.FILM_PHYSICS_SHAPES_TOOLTIP);

        UIElement main = UI.column(4, 0, 20, dropRagdoll, makeRigid, backToAnimation, shapes,
            UI.label(UIKeys.FILM_PHYSICS_TOOLS, 14), UI.row(physicsTool, gravityTool), givePhysics, giveGravity, bake, this.cache);

        UISection advanced = new UISection(UIKeys.FILM_PHYSICS_ADVANCED);

        advanced.fields.add(ragdollSection, solidSection, softSection, forcesSection);
        advanced.setExpanded(false);
        ragdollSection.setExpanded(false);
        solidSection.setExpanded(false);
        softSection.setExpanded(false);
        forcesSection.setExpanded(false);

        this.content = UI.scrollView(UIConstants.MARGIN, UIConstants.SCROLL_PADDING, this.status, main, advanced);
        this.content.relative(this).x(0).y(0).w(1F).h(1F);

        this.add(this.content);
        this.setReplay(null);
    }

    public void setReplay(Replay replay)
    {
        this.replay = replay;

        boolean ready = replay != null && PhysicsStudio.available();

        for (UIElement child : this.content.getChildren(UIElement.class))
        {
            child.setVisible(child == this.status ? !ready : ready);
        }

        this.status.label = replay == null ? UIKeys.FILM_PHYSICS_NO_REPLAY : UIKeys.FILM_PHYSICS_UNAVAILABLE;
        this.content.resize();
    }

    /** Bound to the replay on every frame the field is drawn, so it never shows what the last replay held */
    private <T extends UIElement> T bound(T element, Consumer<Replay> read)
    {
        element.valueBinding(() ->
        {
            if (this.replay != null)
            {
                read.accept(this.replay);
            }
        });

        return element;
    }

    private void edit(Consumer<ReplayPhysics> consumer)
    {
        if (this.replay != null)
        {
            BaseValue.edit(this.replay.physics, consumer);
        }
    }

    private static IKey label(PhysicsMaterial material)
    {
        return switch (material)
        {
            case FEATHER -> UIKeys.FILM_PHYSICS_MATERIAL_FEATHER;
            case WOOD -> UIKeys.FILM_PHYSICS_MATERIAL_WOOD;
            case STONE -> UIKeys.FILM_PHYSICS_MATERIAL_STONE;
            case METAL -> UIKeys.FILM_PHYSICS_MATERIAL_METAL;
            case HEAVY -> UIKeys.FILM_PHYSICS_MATERIAL_HEAVY;
        };
    }

    private UIElement labelled(IKey label, UIElement field)
    {
        return UI.row(UI.label(label, 20).labelAnchor(0F, 0.5F), field);
    }

    private UILabel note(IKey text)
    {
        UILabel label = new UILabel(text).color(Colors.setA(Colors.WHITE, 0.5F));

        label.h(34);

        return label;
    }

    /* Jobs */

    /**
     * The animation handle, keyed at the cursor. The tick before it gets the value the track had there, so a change
     * is one tick wide instead of a ramp from wherever the last key was.
     */
    private void keyAnimation(float value)
    {
        if (this.replay == null)
        {
            return;
        }

        int tick = this.filmPanel.getCursor();

        this.edit((p) ->
        {
            if (tick > 0)
            {
                p.animation.insert(tick - 1, (double) p.animationAt(tick - 1));
            }

            p.animation.insert(tick, (double) value);
        });

        this.filmPanel.replayEditor.updateChannelsList();
    }

    /** One click: physics on, a jointed ragdoll, limp, and let go from the cursor tick on */
    private void dropAsRagdoll()
    {
        this.edit((p) ->
        {
            p.enabled.set(true);
            p.ragdoll.set(true);
            p.rigid.set(false);

            if (p.musclesTrack.isEmpty())
            {
                p.muscles.set(0F);
            }
        });

        this.keyAnimation(0F);
    }

    /** One click: the form is one solid body that falls and tumbles from the cursor tick on */
    private void makeRigid()
    {
        this.edit((p) ->
        {
            p.enabled.set(true);
            p.ragdoll.set(true);
            p.rigid.set(true);
        });

        this.keyAnimation(0F);
    }

    private void push()
    {
        if (this.replay != null)
        {
            PhysicsStudio.push(this.replay, this.filmPanel.getCursor(), (float) this.pushX.getValue(), (float) this.pushY.getValue(), (float) this.pushZ.getValue());
            this.filmPanel.replayEditor.updateChannelsList();
        }
    }

    /** The blast comes from the middle of the orbit camera's view, the cross the editor draws there */
    private void blast()
    {
        if (this.replay == null)
        {
            return;
        }

        Vector3d origin = this.filmPanel.getController().orbit.getOrbitCenter(0F);

        PhysicsStudio.blast(this.replay, this.filmPanel.getCursor(), origin, (float) this.blastStrength.getValue());
        this.filmPanel.replayEditor.updateChannelsList();
    }

    private void bake()
    {
        if (this.replay == null || this.filmPanel.getData() == null)
        {
            return;
        }

        PhysicsStudio.BakeResult result = PhysicsStudio.bake(this.filmPanel.getData(), this.replay);
        UIContext context = this.getContext();

        this.filmPanel.replayEditor.updateChannelsList();

        if (context != null)
        {
            UIOverlay.addOverlay(context, new UIMessageOverlayPanel(UIKeys.FILM_PHYSICS_TITLE,
                result == null ? UIKeys.FILM_PHYSICS_BAKE_FAILED : UIKeys.FILM_PHYSICS_BAKE_DONE.format(result.keyframes())));
        }
    }

    /** The bones of the replay's model, to tear one off at the cursor tick, or none */
    private void openTearMenu()
    {
        if (this.replay == null)
        {
            return;
        }

        int tick = this.filmPanel.getCursor();

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.FILM_PHYSICS_TEAR_NONE, () -> this.edit((p) ->
            {
                p.tearBone.set("");
                p.tearTick.set(-1);
            }));

            if (this.replay.form.get() instanceof mchorse.bbs_mod.forms.forms.ModelForm form)
            {
                mchorse.bbs_mod.cubic.ModelInstance model = mchorse.bbs_mod.forms.renderers.ModelFormRenderer.getModel(form);

                if (model != null && model.model instanceof mchorse.bbs_mod.cubic.data.model.Model cubic)
                {
                    for (mchorse.bbs_mod.cubic.data.model.ModelGroup group : cubic.getAllGroups())
                    {
                        if (!group.cubes.isEmpty())
                        {
                            menu.action(Icons.LIMB, IKey.constant(group.id), () -> this.edit((p) ->
                            {
                                p.tearBone.set(group.id);
                                p.tearTick.set(tick);
                            }));
                        }
                    }
                }
            }
        });
    }

    private void give(mchorse.bbs_mod.items.PhysicsToolItem.Kind kind)
    {
        if (!mchorse.bbs_mod.physics.PhysicsToolController.give(kind) && this.getContext() != null)
        {
            UIOverlay.addOverlay(this.getContext(), new UIMessageOverlayPanel(UIKeys.FILM_PHYSICS_TITLE, UIKeys.FILM_PHYSICS_GIVE_FAILED));
        }
    }

    private String anchorName(String id)
    {
        Replay anchor = this.findReplay(id);

        return anchor == null ? UIKeys.FILM_PHYSICS_ROPE_ANCHOR_NONE.get() : anchor.getName();
    }

    private Replay findReplay(String id)
    {
        if (id == null || id.isEmpty() || this.filmPanel.getData() == null)
        {
            return null;
        }

        for (Replay other : this.filmPanel.getData().replays.getList())
        {
            if (other.getId().equals(id))
            {
                return other;
            }
        }

        return null;
    }

    private void openAnchorMenu()
    {
        if (this.replay == null || this.filmPanel.getData() == null)
        {
            return;
        }

        this.getContext().replaceContextMenu((menu) ->
        {
            menu.action(Icons.CLOSE, UIKeys.FILM_PHYSICS_ROPE_ANCHOR_NONE, () -> this.edit((p) -> p.ropeAnchor.set("")));

            for (Replay other : this.filmPanel.getData().replays.getList())
            {
                if (other != this.replay)
                {
                    menu.action(Icons.PLAYER, IKey.constant(other.getName()), () -> this.edit((p) -> p.ropeAnchor.set(other.getId())));
                }
            }
        });
    }

    @Override
    public boolean isVisible()
    {
        return super.isVisible() && this.shown.getAsBoolean();
    }

    @Override
    public void render(UIContext context)
    {
        this.area.render(context.batcher, 0xF20B0F19);

        int ticks = PhysicsStudio.cachedTicks();

        this.cache.label = ticks < 0 ? UIKeys.FILM_PHYSICS_CACHE_NONE : UIKeys.FILM_PHYSICS_CACHE.format(ticks + 1);

        super.render(context);
    }

    @Override
    protected boolean subMouseClicked(UIContext context)
    {
        /* The strip is part of the editor and owns its area, so a click on it never falls through to the timeline under */
        return this.area.isInside(context) && super.subMouseClicked(context) || this.area.isInside(context);
    }

    /** The width the editor reserves for the panel next to the timeline */
    public static int width()
    {
        return 250 + GAP;
    }
}
