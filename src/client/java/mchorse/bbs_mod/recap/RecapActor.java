package mchorse.bbs_mod.recap;

import mchorse.bbs_mod.camera.clips.IFocusPoint;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.forms.MobForm;
import mchorse.bbs_mod.forms.renderers.MobFormRenderer;
import mchorse.bbs_mod.forms.renderers.utils.MatrixCache;
import mchorse.bbs_mod.mixin.client.RecapLimbAccessor;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;

/**
 * A recorded player, as the tracking clips see it: the live player's own entity for what it is, but
 * standing where the recording had it. Its feet, body, head and look come from the recording at two
 * ticks, the one at the cursor and the next, so a clip that follows it moves in step with the
 * picture between ticks too.
 *
 * <p><b>Bones.</b> A player without a form of its own is given a player model form, so the clips that
 * follow or focus on a bone (the tracker, the third person arm, the focus of the pro camera) have
 * the head, the body, the arms and the legs to pick, posed as the recording had them.</p>
 */
public class RecapActor extends MCEntity implements IFocusPoint
{
    private final RecapMotion.Frame now = new RecapMotion.Frame();
    private final RecapMotion.Frame before = new RecapMotion.Frame();
    private final boolean other;
    private MobForm body;
    private boolean placed;

    public RecapActor(Entity player)
    {
        this(player, false);
    }

    /**
     * @param other one of the other recorded players, which is not the live player: its form is never
     *              the live player's own
     */
    public RecapActor(Entity player, boolean other)
    {
        super(player);

        this.other = other;
    }

    /** Puts the actor at the recording's {@code previous} and {@code current} moments. */
    public void place(RecapTrack track, RecapMotion motion, float previous, float current)
    {
        double eye = this.getMcEntity() instanceof net.minecraft.entity.player.PlayerEntity player ? player.getStandingEyeHeight() : 1.62D;

        this.placed = RecapScene.frameAt(track, motion, previous, this.before, eye) && RecapScene.frameAt(track, motion, current, this.now, eye);
        this.pose();
    }

    /** Puts another recorded player where its own track had it. */
    public void place(RecapEntities.Frame at)
    {
        this.now.x = at.x;
        this.now.y = at.y;
        this.now.z = at.z;
        this.now.bodyYaw = at.body;
        this.now.headYaw = at.head;
        this.now.pitch = at.pitch;
        this.now.limbPos = at.limbPos;
        this.now.limbSpeed = at.limbSpeed;
        this.now.swing = at.swing;

        EntityPose[] poses = EntityPose.values();

        this.now.pose = at.pose >= 0 && at.pose < poses.length ? poses[at.pose] : EntityPose.STANDING;
        this.now.flags = 0;
        this.before.x = this.now.x;
        this.before.y = this.now.y;
        this.before.z = this.now.z;
        this.before.bodyYaw = this.now.bodyYaw;
        this.before.headYaw = this.now.headYaw;
        this.before.pitch = this.now.pitch;
        this.placed = true;
        this.pose();
    }

    public boolean isPlaced()
    {
        return this.placed;
    }

    /** The form its bones are read from: the player's own morph, else a player model. */
    @Override
    public Form getForm()
    {
        if (!this.other)
        {
            Form own = super.getForm();

            if (own != null)
            {
                return own;
            }
        }

        if (this.body == null)
        {
            this.body = new MobForm();
            this.body.mobID.set("minecraft:player");
            this.body.slim.set(this.getMcEntity() instanceof AbstractClientPlayerEntity player
                && player.getSkinTextures().model() == net.minecraft.client.util.SkinTextures.Model.SLIM);
        }

        return this.body;
    }

    /** Poses the model the bones are read from as the recording had the player: nothing of it ticks. */
    private void pose()
    {
        if (!this.placed || !(this.getForm() instanceof MobForm form) || form != this.body
            || !(FormUtilsClient.getRenderer(form) instanceof MobFormRenderer renderer)
            || !(renderer.standInEntity() instanceof LivingEntity model))
        {
            return;
        }

        float head = this.now.headYaw - this.now.bodyYaw;

        model.setYaw(0F);
        model.prevYaw = 0F;
        model.bodyYaw = model.prevBodyYaw = 0F;
        model.headYaw = model.prevHeadYaw = head;
        model.setPitch(this.now.pitch);
        model.prevPitch = this.now.pitch;
        model.setPose(this.now.pose);
        model.setSneaking(this.now.pose == EntityPose.CROUCHING);
        model.handSwingProgress = model.lastHandSwingProgress = this.now.swing;

        RecapLimbAccessor limbs = (RecapLimbAccessor) model.limbAnimator;

        limbs.bbs$setPos(this.now.limbPos + this.now.limbSpeed);
        limbs.bbs$setSpeed(this.now.limbSpeed);
        limbs.bbs$setPrevSpeed(this.now.limbSpeed);
    }

    @Override
    public boolean focusPoint(String bone, float transition, Vector3d out)
    {
        Form form = this.getForm();

        if (!this.placed || form == null || bone == null || bone.isEmpty())
        {
            return false;
        }

        MatrixCache bones = FormUtilsClient.getRenderer(form).collectMatrices(this, transition);

        if (!bones.has(bone))
        {
            return false;
        }

        Matrix4f matrix = new Matrix4f().rotateY((float) Math.toRadians(-this.now.bodyYaw));
        Matrix4f at = bones.get(bone).matrix();

        if (at == null)
        {
            return false;
        }

        Vector3f point = matrix.mul(at).getTranslation(new Vector3f());

        out.set(this.now.x + point.x, this.now.y + point.y, this.now.z + point.z);

        return true;
    }

    /** The eyes as the recorded pose held them, not as the live player stands. */
    @Override
    public double getEyeHeight()
    {
        if (!this.placed)
        {
            return super.getEyeHeight();
        }

        return switch (this.now.pose)
        {
            case CROUCHING -> 1.27D;
            case SWIMMING, FALL_FLYING, SPIN_ATTACK -> 0.4D;
            case SLEEPING -> 0.2D;
            default -> 1.62D;
        };
    }

    @Override
    public boolean isSneaking()
    {
        return this.placed ? this.now.pose == EntityPose.CROUCHING : super.isSneaking();
    }

    @Override
    public double getX()
    {
        return this.placed ? this.now.x : super.getX();
    }

    @Override
    public double getPrevX()
    {
        return this.placed ? this.before.x : super.getPrevX();
    }

    @Override
    public double getY()
    {
        return this.placed ? this.now.y : super.getY();
    }

    @Override
    public double getPrevY()
    {
        return this.placed ? this.before.y : super.getPrevY();
    }

    @Override
    public double getZ()
    {
        return this.placed ? this.now.z : super.getZ();
    }

    @Override
    public double getPrevZ()
    {
        return this.placed ? this.before.z : super.getPrevZ();
    }

    @Override
    public float getYaw()
    {
        return this.placed ? this.now.headYaw : super.getYaw();
    }

    @Override
    public float getPrevYaw()
    {
        return this.placed ? this.before.headYaw : super.getPrevYaw();
    }

    @Override
    public float getHeadYaw()
    {
        return this.getYaw();
    }

    @Override
    public float getPrevHeadYaw()
    {
        return this.getPrevYaw();
    }

    @Override
    public float getBodyYaw()
    {
        return this.placed ? this.now.bodyYaw : super.getBodyYaw();
    }

    @Override
    public float getPrevBodyYaw()
    {
        return this.placed ? this.before.bodyYaw : super.getPrevBodyYaw();
    }

    @Override
    public float getPitch()
    {
        return this.placed ? this.now.pitch : super.getPitch();
    }

    @Override
    public float getPrevPitch()
    {
        return this.placed ? this.before.pitch : super.getPrevPitch();
    }
}
