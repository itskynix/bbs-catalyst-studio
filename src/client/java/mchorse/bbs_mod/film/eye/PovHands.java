package mchorse.bbs_mod.film.eye;

import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.client.renderer.ItemPredicateDonor;
import mchorse.bbs_mod.cubic.animation.ItemUsePose;
import mchorse.bbs_mod.film.BaseFilmController;
import mchorse.bbs_mod.film.Film;
import mchorse.bbs_mod.film.replays.PovBodyParts;
import mchorse.bbs_mod.film.replays.PovRig;
import mchorse.bbs_mod.film.replays.Replay;
import mchorse.bbs_mod.film.replays.ReplayKeyframes;
import mchorse.bbs_mod.film.replays.ReplayPov;
import mchorse.bbs_mod.forms.FormUtilsClient;
import mchorse.bbs_mod.forms.entities.EntityState;
import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.forms.entities.MCEntity;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.forms.renderers.FormRenderType;
import mchorse.bbs_mod.forms.renderers.FormRenderingContext;
import mchorse.bbs_mod.forms.renderers.ModelFormRenderer;
import mchorse.bbs_mod.ui.dashboard.UIDashboard;
import mchorse.bbs_mod.ui.film.UIFilmPanel;
import mchorse.bbs_mod.utils.MatrixStackUtils;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.KeyframeSegment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Arm;
import net.minecraft.util.UseAction;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

import java.util.Map;

/**
 * The first-person hands of a replay, as its point of view animates them: the POV channels (which
 * hand shows, its swing, its equip dip, the walking bob, the lag behind a turning view), the items
 * of its hotbar slot and off hand, the rig that places each arm, item, armor sleeve and arm tip, and
 * the POV body parts hung on the rig. Drawn in view space (the eye at the origin, looking down -Z)
 * by the game's hand pass and by the POV model editor alike, so the editor shows what the film shows.
 *
 * <p>Without a replay it is the plain first person: the entity's own items and swing.
 */
public final class PovHands
{
    private static final float PI = (float) Math.PI;

    /* Where the game holds an item and an empty hand, before the swing */
    private static final float ITEM_X = 0.56F;
    private static final float ITEM_Y = -0.52F;
    private static final float ARM_X = 0.64F;
    private static final float ARM_Y = -0.6F;
    private static final float HAND_Z = -0.72F;

    private PovHands()
    {}

    /** The replay of a film on screen, by its id: the film being edited first, then the ones playing. */
    public static Replay findReplay(String id)
    {
        if (id == null || id.isEmpty())
        {
            return null;
        }

        UIDashboard dashboard = BBSModClient.getDashboardIfCreated();
        UIFilmPanel panel = dashboard == null ? null : dashboard.getPanel(UIFilmPanel.class);
        Replay replay = panel == null ? null : find(panel.getData(), id);

        if (replay != null)
        {
            return replay;
        }

        for (BaseFilmController controller : BBSModClient.getFilms().getControllers())
        {
            replay = find(controller.film, id);

            if (replay != null)
            {
                return replay;
            }
        }

        return null;
    }

    private static Replay find(Film film, String id)
    {
        if (film == null)
        {
            return null;
        }

        for (Replay replay : film.replays.getList())
        {
            if (replay.getId().equals(id))
            {
                return replay;
            }
        }

        return null;
    }

    /** The form whose arms are shown: the POV's own hand model, else the replay's form. */
    public static Form handForm(Replay replay, IEntity entity)
    {
        Form hand = replay == null ? null : replay.pov.handForm.get();

        return hand != null ? hand : entity.getForm();
    }

    /**
     * @param tick the replay's tick, with the partial tick in it
     * @param frames when not null, gets where each rig bone ended up (for the editor's axes)
     */
    public static void render(MatrixStack matrices, VertexConsumerProvider.Immediate consumers, int light, Replay replay, IEntity entity, float tick, float transition, Map<String, Matrix4f> frames)
    {
        Form form = handForm(replay, entity);

        if (form == null)
        {
            return;
        }

        ReplayPov pov = replay == null ? null : replay.pov;
        ModelFormRenderer model = FormUtilsClient.getRenderer(form) instanceof ModelFormRenderer renderer ? renderer : null;
        float swing = entity.getHandSwingProgress(transition);
        boolean offSwing = entity.isSwingingOffHand();

        matrices.push();

        if (pov != null)
        {
            float[] bob = bob(replay, entity, tick, transition);

            if (pov.cameraShake.get() || !pov.bobStrength.isEmpty())
            {
                applyBob(matrices, bob[0], bob[1]);
            }

            /* The lag of the hands behind a turning view */
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) value(pov.renderPitch, tick, 0D)));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) value(pov.renderYaw, tick, 0D)));
        }

        boolean mainRight = isMainArmRight(entity);
        boolean swingingRight = mainRight != offSwing;

        Side right = sideOf(true, mainRight, replay, entity, pov, tick, swingingRight ? swing : 0F);
        Side left = sideOf(false, !mainRight, replay, entity, pov, tick, swingingRight ? 0F : swing);

        renderSide(matrices, consumers, light, replay, entity, model, right, tick, transition, frames);
        renderSide(matrices, consumers, light, replay, entity, model, left, tick, transition, frames);

        matrices.pop();
        consumers.draw();
    }

    /** One hand's state at a tick: whether it is the main hand, what it holds, shown or not, and how far into its swing and its equip dip. */
    private record Side(boolean right, boolean mainHand, ItemStack stack, boolean visible, float swing, float equip)
    {}

    private static Side sideOf(boolean right, boolean mainHand, Replay replay, IEntity entity, ReplayPov pov, float tick, float swing)
    {
        ItemStack stack = heldItem(replay, entity, mainHand, tick);
        boolean holding = stack != null && !stack.isEmpty();
        /* Main hand is always visible by default; off-hand is hidden when empty and visible when holding an item,
         * unless explicitly overridden by a keyframe on right_hand_visible / left_hand_visible in the Dope Sheet */
        boolean defaultVisible = mainHand || holding;
        KeyframeChannel<Boolean> visibility = pov == null ? null : (right ? pov.rightHandVisible : pov.leftHandVisible);
        boolean visible = flag(visibility, tick, defaultVisible);
        float handSwing = pov == null ? swing : swingAt(right ? pov.rightSwing : pov.leftSwing, tick, swing);
        float equip = pov == null ? 0F : (float) value(mainHand ? pov.mainEquip : pov.offhandEquip, tick, 0D);

        return new Side(right, mainHand, stack, visible, handSwing, equip);
    }

    private static boolean isMainArmRight(IEntity entity)
    {
        if (entity instanceof MCEntity)
        {
            return entity.isRightHanded();
        }

        MinecraftClient mc = MinecraftClient.getInstance();

        if (mc != null && mc.options != null && mc.options.getMainArm() != null)
        {
            return mc.options.getMainArm().getValue() == Arm.RIGHT;
        }

        return entity == null || entity.isRightHanded();
    }

    /**
     * A swing channel between its keyframes. A swing climbs from 0 to 1 and starts over, so where
     * a keyframe is lower than the one before it the swing went on through 1 (it ended, or a new one
     * began, as when digging) and the hand keeps going forward, as the game's own swing does,
     * instead of sweeping back through the whole swing in a fraction of a tick.
     */
    private static float swingAt(KeyframeChannel<Double> channel, float tick, float fallback)
    {
        if (channel.isEmpty())
        {
            return fallback;
        }

        KeyframeSegment<Double> segment = channel.find(tick);

        if (segment == null || segment.a == null)
        {
            return fallback;
        }

        float a = segment.a.getValue().floatValue();

        if (segment.b == null || segment.b == segment.a)
        {
            return a;
        }

        float b = segment.b.getValue().floatValue();

        if (b >= a)
        {
            return a + (b - a) * segment.x;
        }

        float value = a + (b + 1F - a) * segment.x;

        return value >= 1F ? value - 1F : value;
    }

    private static void renderSide(MatrixStack matrices, VertexConsumerProvider consumers, int light, Replay replay, IEntity entity, ModelFormRenderer model, Side side, float tick, float transition, Map<String, Matrix4f> frames)
    {
        PovRig rig = replay == null ? null : replay.pov.rig;
        String armId = side.right ? PovRig.RIGHT_ARM : PovRig.LEFT_ARM;
        PovRig.Bone arm = rig == null ? null : rig.getBone(armId);

        /* A hand switched off, by its channel or by its rig bone, is not drawn at all */
        if (!side.visible || (arm != null && !arm.visible.get()))
        {
            return;
        }

        ItemStack stack = side.stack;
        boolean holding = stack != null && !stack.isEmpty();
        float f = side.right ? 1F : -1F;
        float hx = f * (holding ? ITEM_X : ARM_X);
        float hy = holding ? ITEM_Y : ARM_Y;
        Hand hand = side.right ? Hand.MAIN_HAND : Hand.OFF_HAND;

        matrices.push();

        /* The arm's own place, turned about the hand rather than the eye */
        matrices.translate(hx, hy, HAND_Z);
        bone(matrices, arm, armId, frames);
        matrices.translate(-hx, -hy, -HAND_Z);
        renderParts(matrices, light, replay, entity, armId, transition);

        if (holding)
        {
            ItemUsePose.Use use = ItemUsePose.get(entity, side.mainHand);

            if (use != null && use.action() != UseAction.NONE)
            {
                /* Eating, drinking, drawing a bow, raising a shield...: the game's pose for it */
                applyUse(matrices, use, f, side.equip);
                renderEnd(matrices, light, replay, entity, rig, side.right, transition, frames);
            }
            else
            {
                /* The game's held item: pushed out through the swing, at its place, then swung */
                float root = (float) Math.sqrt(side.swing);

                matrices.translate(f * -0.4F * Math.sin(root * PI), 0.2F * Math.sin(root * PI * 2F), -0.2F * Math.sin(side.swing * PI));
                applyEquipOffset(matrices, f, side.equip);
                renderEnd(matrices, light, replay, entity, rig, side.right, transition, frames);
                applySwingOffset(matrices, f, side.swing);
            }

            String itemId = PovRig.itemOf(side.right);
            PovRig.Bone item = rig == null ? null : rig.getBone(itemId);

            matrices.push();
            bone(matrices, item, itemId, frames);

            if (item == null || item.visible.get())
            {
                MinecraftClient mc = MinecraftClient.getInstance();
                /* The film's use state answers the item's model predicates through the donor, so a
                 * drawn bow shows its pulling stages and a loading crossbow its own, as in the game */
                LivingEntity holder = use != null && use.action() != UseAction.NONE ? ItemPredicateDonor.get(stack, use) : null;

                mc.getItemRenderer().renderItem(holder, stack, side.right ? ModelTransformationMode.FIRST_PERSON_RIGHT_HAND : ModelTransformationMode.FIRST_PERSON_LEFT_HAND,
                    !side.right, matrices, consumers, mc.world, light, OverlayTexture.DEFAULT_UV, 0);
            }

            renderParts(matrices, light, replay, entity, itemId, transition);
            matrices.pop();
        }
        else if (model != null)
        {
            /* The game's empty hand: the swing's push and the equip dip in its place, then turned
             * about the hand by the swing, then the arm's own frame */
            float root = (float) Math.sqrt(side.swing);
            float push = -0.3F * (float) Math.sin(root * PI);
            float lift = 0.4F * (float) Math.sin(root * PI * 2F);
            float reach = -0.4F * (float) Math.sin(side.swing * PI);

            matrices.translate(f * (push + ARM_X), lift + ARM_Y - side.equip * 0.6F, reach + HAND_Z);

            matrices.push();
            renderEnd(matrices, light, replay, entity, rig, side.right, transition, frames);
            matrices.pop();

            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * 45F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * (float) Math.sin(root * PI) * 70F));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * (float) Math.sin(side.swing * side.swing * PI) * -20F));
            matrices.translate(f * -1F, 3.6F, 3.5F);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * 120F));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(200F));
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -135F));
            matrices.translate(f * 5.6F, 0F, 0F);

            model.renderFirstPersonHand(matrices, light, hand);

            String armorId = PovRig.armorOf(side.right);
            PovRig.Bone armor = rig == null ? null : rig.getBone(armorId);

            if (frames != null)
            {
                frames.put(armorId, new Matrix4f(matrices.peek().getPositionMatrix()));
                frames.put(parentFrame(armorId), new Matrix4f(matrices.peek().getPositionMatrix()));
            }

            if (armor == null || armor.visible.get())
            {
                model.renderFirstPersonArmor(entity, matrices, light, hand, armor == null ? null : armor.transform.get());
            }
        }

        matrices.pop();
    }

    /** The game's place of a held item, dipped by the equip */
    private static void applyEquipOffset(MatrixStack matrices, float f, float equip)
    {
        matrices.translate(f * ITEM_X, ITEM_Y - equip * 0.6F, HAND_Z);
    }

    /** The game's swing of a held item: a turn out, a tip down and a turn back */
    private static void applySwingOffset(MatrixStack matrices, float f, float swing)
    {
        float turn = (float) Math.sin(swing * swing * PI);
        float tip = (float) Math.sin(Math.sqrt(swing) * PI);

        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * (45F + turn * -20F)));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * tip * -20F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(tip * -80F));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * -45F));
    }

    /**
     * The game's first-person poses of an item in use, by how long the use has gone on: food and
     * drink brought up to the mouth and bobbing, a bow and a crossbow drawn and trembling, a
     * trident raised, a shield (and anything else) just held where it is.
     */
    private static void applyUse(MatrixStack matrices, ItemUsePose.Use use, float f, float equip)
    {
        float elapsed = Math.max(0F, use.elapsed());
        float window = Math.max(1F, use.window());

        switch (use.action())
        {
            case EAT, DRINK ->
            {
                float left = Math.max(0F, window - elapsed);
                float g = left / window;

                if (g < 0.8F)
                {
                    matrices.translate(0F, Math.abs(Math.cos(left / 4F * PI) * 0.1F), 0F);
                }

                float h = 1F - (float) Math.pow(g, 27D);

                matrices.translate(h * 0.6F * f, h * -0.5F, 0F);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * h * 90F));
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(h * 10F));
                matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * h * 30F));
                applyEquipOffset(matrices, f, equip);
            }
            case BOW -> drawn(matrices, f, equip, elapsed, 20F, -0.2785682F, 0.18344387F, 0.15731531F, -13.935F, 35.3F, 0.04F);
            case CROSSBOW -> drawn(matrices, f, equip, elapsed, window, -0.4785682F, -0.094387F, 0.05731531F, -11.935F, 65.3F, 0.04F);
            case SPEAR -> drawn(matrices, f, equip, elapsed, 10F, -0.5F, 0.7F, 0.1F, -55F, 35.3F, 0.2F);
            default -> applyEquipOffset(matrices, f, equip);
        }
    }

    /** A drawn weapon: held across the view, pulled back as it charges, trembling once charged enough */
    private static void drawn(MatrixStack matrices, float f, float equip, float elapsed, float full, float x, float y, float z, float pitch, float yaw, float pull)
    {
        applyEquipOffset(matrices, f, equip);
        matrices.translate(f * x, y, z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(f * yaw));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(f * -9.785F));

        float charge = elapsed / full;

        if (full == 20F)
        {
            /* The bow's own curve */
            charge = (charge * charge + charge * 2F) / 3F;
        }

        charge = Math.min(charge, 1F);

        if (charge > 0.1F)
        {
            float tremble = (float) Math.sin((elapsed - 0.1F) * 1.3F) * (charge - 0.1F);

            matrices.translate(0F, tremble * 0.004F, 0F);
        }

        matrices.translate(0F, 0F, charge * pull);
        matrices.scale(1F, 1F, 1F + charge * 0.2F);
        matrices.multiply(RotationAxis.NEGATIVE_Y.rotationDegrees(f * 45F));
    }

    /** The arm's tip, where the hand is: what the POV body parts on it hang from. */
    private static void renderEnd(MatrixStack matrices, int light, Replay replay, IEntity entity, PovRig rig, boolean right, float transition, Map<String, Matrix4f> frames)
    {
        String endId = PovRig.endOf(right);

        matrices.push();
        bone(matrices, rig == null ? null : rig.getBone(endId), endId, frames);
        renderParts(matrices, light, replay, entity, endId, transition);
        matrices.pop();
    }

    /** The key of a bone's frame before its own transform: where the editor's gizmo moves it from */
    public static String parentFrame(String id)
    {
        return id + "#parent";
    }

    private static void bone(MatrixStack matrices, PovRig.Bone bone, String id, Map<String, Matrix4f> frames)
    {
        if (frames != null)
        {
            frames.put(parentFrame(id), new Matrix4f(matrices.peek().getPositionMatrix()));
        }

        if (bone != null)
        {
            MatrixStackUtils.applyTransform(matrices, bone.transform.get());
        }

        if (frames != null)
        {
            frames.put(id, new Matrix4f(matrices.peek().getPositionMatrix()));
        }
    }

    private static void renderParts(MatrixStack matrices, int light, Replay replay, IEntity entity, String bone, float transition)
    {
        if (replay == null)
        {
            return;
        }

        for (PovBodyParts.Part part : replay.pov.bodyParts.getAllTyped())
        {
            Form form = part.form.get();

            if (form == null || !part.enabled.get() || !part.bone.get().equals(bone))
            {
                continue;
            }

            matrices.push();
            MatrixStackUtils.applyTransform(matrices, part.transform.get());
            FormUtilsClient.render(form, new FormRenderingContext().set(FormRenderType.ITEM_FP, entity, matrices, light, OverlayTexture.DEFAULT_UV, transition));
            matrices.pop();
        }
    }

    /**
     * What a hand holds: the off hand's channel, or the hotbar slot the selected slot points at,
     * when the replay keys them; what the entity holds otherwise.
     */
    private static ItemStack heldItem(Replay replay, IEntity entity, boolean mainHand, float tick)
    {
        if (replay != null)
        {
            ReplayKeyframes keyframes = replay.keyframes;

            if (!mainHand && !keyframes.offHand.isEmpty())
            {
                return keyframes.offHand.interpolate(tick);
            }

            if (mainHand && !keyframes.selectedSlot.isEmpty())
            {
                int slot = keyframes.selectedSlot.interpolate(tick);

                if (slot >= 0 && slot < keyframes.hotbar.size() && !keyframes.hotbar.get(slot).isEmpty())
                {
                    return keyframes.hotbar.get(slot).interpolate(tick);
                }
            }
        }

        return entity.getEquipmentStack(mainHand ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
    }

    /**
     * The walking bob's phase (how far along the step cycle, in {@code horizontalSpeed} units) and
     * strength (the stride, {@code 0..0.1}): from the POV's own channels when keyed, else from the
     * replay's step path or the actor's limbs.
     */
    public static float[] bob(Replay replay, IEntity entity, float tick, float transition)
    {
        ReplayPov pov = replay == null ? null : replay.pov;
        float[] step = defaultBob(replay, entity, tick, transition);
        float phase = pov != null && !pov.bobPhase.isEmpty() ? pov.bobPhase.interpolate(tick, (double) step[0]).floatValue() : step[0];
        float strength = pov != null && !pov.bobStrength.isEmpty() ? pov.bobStrength.interpolate(tick, (double) step[1]).floatValue() : step[1];

        return new float[] {phase, strength};
    }

    private static float[] defaultBob(Replay replay, IEntity entity, float tick, float transition)
    {
        if (replay != null && (!replay.keyframes.x.isEmpty() || !replay.keyframes.z.isEmpty()))
        {
            ReplayKeyframes kf = replay.keyframes;
            KeyframeChannel<Double> swimmingChannel = kf.state(EntityState.SWIMMING);
            KeyframeChannel<Double> ridingChannel = kf.state(EntityState.RIDING);
            KeyframeChannel<Double> flyingChannel = kf.state(EntityState.FLYING);
            KeyframeChannel<Double> glidingChannel = kf.state(EntityState.GLIDING);
            int full = Math.max(0, (int) Math.floor(tick));
            float frac = Math.max(0F, tick - full);
            double prevX = kf.x.interpolate(0F, 0D);
            double prevZ = kf.z.interpolate(0F, 0D);
            float speed = 0F;
            float prevSpeed = 0F;
            float stride = 0F;
            float prevStride = 0F;

            for (int t = 1; t <= full + 1; t++)
            {
                prevSpeed = speed;
                prevStride = stride;

                double curX = kf.x.interpolate((float) t, prevX);
                double curZ = kf.z.interpolate((float) t, prevZ);
                float dist = (float) Math.hypot(curX - prevX, curZ - prevZ);

                prevX = curX;
                prevZ = curZ;

                if (dist > 4F)
                {
                    dist = 0F;
                }

                speed += dist * 0.6F;

                boolean grounded = kf.grounded.isEmpty() || EntityState.isOn(kf.grounded.interpolate((float) t, 1D));
                boolean swimming = !swimmingChannel.isEmpty() && EntityState.isOn(swimmingChannel.interpolate((float) t, 0D));
                boolean riding = !ridingChannel.isEmpty() && EntityState.isOn(ridingChannel.interpolate((float) t, 0D));
                boolean flying = !flyingChannel.isEmpty() && EntityState.isOn(flyingChannel.interpolate((float) t, 0D));
                boolean gliding = !glidingChannel.isEmpty() && EntityState.isOn(glidingChannel.interpolate((float) t, 0D));
                float step = grounded && !swimming && !riding && !flying && !gliding ? Math.min(0.1F, dist) : 0F;

                stride += (step - stride) * 0.4F;
            }

            return new float[]
            {
                -(prevSpeed + (speed - prevSpeed) * frac),
                prevStride + (stride - prevStride) * frac
            };
        }

        if (entity instanceof MCEntity mc && mc.getMcEntity() != null)
        {
            Entity raw = mc.getMcEntity();
            float speed = MathHelper.lerp(transition, raw.prevHorizontalSpeed, raw.horizontalSpeed);
            float stride;

            if (raw instanceof PlayerEntity player)
            {
                stride = MathHelper.lerp(transition, player.prevStrideDistance, player.strideDistance);
            }
            else
            {
                stride = raw.isOnGround() && !raw.isSwimming() && !raw.hasVehicle()
                    ? Math.min(0.1F, entity.getLimbSpeed(transition) * 0.25F)
                    : 0F;
            }

            return new float[] {-speed, stride};
        }

        if (entity != null)
        {
            boolean grounded = entity.isOnGround() && !entity.isSwimming() && !entity.isRiding() && !entity.isFallFlying();
            float phase = -entity.getLimbPos(transition) * 0.15F;
            float strength = grounded ? Math.min(0.1F, entity.getLimbSpeed(transition) * 0.25F) : 0F;

            return new float[] {phase, strength};
        }

        return new float[] {0F, 0F};
    }

    /** The walking bob of the hands: a step's sway to the side and dip, a little roll and tilt. */
    private static void applyBob(MatrixStack matrices, double phase, double strength)
    {
        if (strength == 0D)
        {
            return;
        }

        float p = (float) (phase * Math.PI);
        float g = (float) strength;

        matrices.translate(Math.sin(p) * g * 0.5F, -Math.abs(Math.cos(p) * g), 0F);
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((float) Math.sin(p) * g * 3F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((float) Math.abs(Math.cos(p - 0.2F) * g) * 5F));
    }

    private static double value(KeyframeChannel<Double> channel, float tick, double fallback)
    {
        return channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }

    private static boolean flag(KeyframeChannel<Boolean> channel, float tick, boolean fallback)
    {
        return channel == null || channel.isEmpty() ? fallback : channel.interpolate(tick, fallback);
    }
}
