/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.clips.Clips
 *  mchorse.bbs_mod.utils.factory.IFactory
 *  mchorse.bbs_mod.utils.keyframes.Keyframe
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.pose.Transform
 *  net.minecraft.item.ItemStack
 */
package mchorse.bbs_mod.camera.pov.actions;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.BossBarPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.CameraShakePovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ChatPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.GuiPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.MenuPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ParticleEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ScreenEffectPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.SemanticHudPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.StatusEffectsPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.clip.ToastPovActionClip;
import mchorse.bbs_mod.camera.pov.actions.timeline.PovActionTimelineFactory;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.clips.Clips;
import mchorse.bbs_mod.utils.factory.IFactory;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import net.minecraft.item.ItemStack;

public final class RecordedPovActions
extends Clips {
    private final List<PovActionClip> sessionClips = new ArrayList<PovActionClip>();

    public RecordedPovActions() {
        super("pov_actions", (IFactory)new PovActionTimelineFactory());
    }

    public PovActionClip add(PovActionType type, int tick, int duration) {
        PovActionClip clip = switch (type) {
            case GUI -> new GuiPovActionClip();
            case MENU -> new MenuPovActionClip();
            case CAMERA_SHAKE -> new CameraShakePovActionClip();
            case PARTICLE_EFFECT -> new ParticleEffectPovActionClip();
            case BOSS_BARS -> new BossBarPovActionClip();
            case SCREEN_EFFECT -> new ScreenEffectPovActionClip();
            case STATUS_EFFECTS -> new StatusEffectsPovActionClip();
            case TOASTS -> new ToastPovActionClip();
            case CHAT -> new ChatPovActionClip();
            default -> new SemanticHudPovActionClip(type);
        };
        clip.tick.set(Math.max(0, tick));
        clip.duration.set(Math.max(1, duration));
        clip.layer.set(type.seedLayer());
        this.addClip(clip);
        this.sessionClips.add(clip);
        this.sync();
        return clip;
    }

    public ChatPovActionClip getActiveChat(float tick) {
        ChatPovActionClip top = null;
        for (Clip clip : this.get()) {
            ChatPovActionClip chat;
            if (!(clip instanceof ChatPovActionClip) || !(chat = (ChatPovActionClip)clip).isActive(tick) || top != null && (Integer)chat.layer.get() < (Integer)top.layer.get()) continue;
            top = chat;
        }
        return top;
    }

    public CameraShakePovActionClip getActiveCameraShake(float tick) {
        CameraShakePovActionClip top = null;
        for (Clip clip : this.get()) {
            CameraShakePovActionClip shake;
            if (!(clip instanceof CameraShakePovActionClip) || !(shake = (CameraShakePovActionClip)clip).isActive(tick) || top != null && (Integer)shake.layer.get() < (Integer)top.layer.get()) continue;
            top = shake;
        }
        return top;
    }

    public StatusEffectsPovActionClip getActiveStatusEffects(float tick) {
        StatusEffectsPovActionClip top = null;
        for (Clip clip : this.get()) {
            StatusEffectsPovActionClip effects;
            if (!(clip instanceof StatusEffectsPovActionClip) || !(effects = (StatusEffectsPovActionClip)clip).isActive(tick) || top != null && (Integer)effects.layer.get() < (Integer)top.layer.get()) continue;
            top = effects;
        }
        return top;
    }

    public List<PovActionClip> takeSessionClips() {
        ArrayList<PovActionClip> recorded = new ArrayList<PovActionClip>(this.sessionClips);
        this.sessionClips.clear();
        return recorded;
    }

    public void clearAll() {
        for (Clip clip : new ArrayList<Clip>(this.get())) {
            this.remove(clip);
        }
        this.sync();
    }

    public void trimForRecordingRange(int startTick, int endTick) {
        ArrayList<Clip> toAdd = new ArrayList<Clip>();
        for (Clip c : new ArrayList<Clip>(this.get())) {
            int duration;
            int start = (Integer)c.tick.get();
            int end = start + (duration = ((Integer)c.duration.get()).intValue());
            if (end <= startTick || start >= endTick) continue;
            if (start >= startTick && end <= endTick) {
                this.remove(c);
                continue;
            }
            if (start < startTick && end > endTick) {
                Clip rightPiece = c.copy();
                int cutAmount = endTick - start;
                rightPiece.tick.set(endTick);
                rightPiece.duration.set(Math.max(1, end - endTick));
                if (rightPiece instanceof PovActionClip) {
                    PovActionClip povRight = (PovActionClip)rightPiece;
                    for (KeyframeChannel<?> channel : povRight.getChannels()) {
                        RecordedPovActions.trimLeftChannel(channel, cutAmount);
                    }
                }
                toAdd.add(rightPiece);
                int leftDuration = Math.max(1, startTick - start);
                c.duration.set(leftDuration);
                if (!(c instanceof PovActionClip)) continue;
                PovActionClip povClip = (PovActionClip)c;
                for (KeyframeChannel<?> channel : povClip.getChannels()) {
                    RecordedPovActions.trimRightChannel(channel, leftDuration);
                }
                continue;
            }
            if (start < startTick && end <= endTick) {
                int newDuration = Math.max(1, startTick - start);
                c.duration.set(newDuration);
                if (!(c instanceof PovActionClip)) continue;
                PovActionClip povClip = (PovActionClip)c;
                for (KeyframeChannel<?> channel : povClip.getChannels()) {
                    RecordedPovActions.trimRightChannel(channel, newDuration);
                }
                continue;
            }
            if (start >= endTick || end <= endTick) continue;
            int cutAmount = endTick - start;
            c.tick.set(endTick);
            c.duration.set(Math.max(1, end - endTick));
            if (!(c instanceof PovActionClip)) continue;
            PovActionClip povClip = (PovActionClip)c;
            for (KeyframeChannel<?> channel : povClip.getChannels()) {
                RecordedPovActions.trimLeftChannel(channel, cutAmount);
            }
        }
        for (Clip clip : toAdd) {
            this.addClip(clip);
        }
        this.sync();
    }

    public void trimForRecordingAt(int timelineTick) {
        this.trimForRecordingRange(timelineTick, Integer.MAX_VALUE);
    }

    private static void trimRightChannel(KeyframeChannel channel, float maxDuration) {
        Object initial;
        Object endValue;
        Keyframe kf;
        if (channel.isEmpty()) {
            return;
        }
        boolean hasKeyAtEnd = false;
        for (Object obj : channel.getKeyframes()) {
            kf = (Keyframe)obj;
            if (!(Math.abs(kf.getTick() - maxDuration) < 1.0E-4f)) continue;
            hasKeyAtEnd = true;
            break;
        }
        if (!hasKeyAtEnd && (endValue = channel.interpolate(maxDuration, initial = ((Keyframe)channel.getKeyframes().get(0)).getValue())) != null) {
            if (endValue instanceof ItemStack) {
                ItemStack stack = (ItemStack)endValue;
                endValue = stack.copy();
            } else if (endValue instanceof Transform) {
                Transform transform = (Transform)endValue;
                endValue = transform.copy();
            }
            channel.insert(maxDuration, endValue);
        }
        List list = channel.getKeyframes();
        for (int i = list.size() - 1; i >= 0; --i) {
            kf = (Keyframe)list.get(i);
            if (!(kf.getTick() > maxDuration + 1.0E-4f)) continue;
            channel.remove(i);
        }
        channel.sort();
    }

    private static void trimLeftChannel(KeyframeChannel channel, float cutAmount) {
        Object initial;
        Object cutValue;
        Keyframe kf;
        if (channel.isEmpty()) {
            return;
        }
        boolean hasKeyAtCut = false;
        for (Object obj : channel.getKeyframes()) {
            kf = (Keyframe)obj;
            if (!(Math.abs(kf.getTick() - cutAmount) < 1.0E-4f)) continue;
            hasKeyAtCut = true;
            break;
        }
        if (!hasKeyAtCut && (cutValue = channel.interpolate(cutAmount, initial = ((Keyframe)channel.getKeyframes().get(0)).getValue())) != null) {
            if (cutValue instanceof ItemStack) {
                ItemStack stack = (ItemStack)cutValue;
                cutValue = stack.copy();
            } else if (cutValue instanceof Transform) {
                Transform transform = (Transform)cutValue;
                cutValue = transform.copy();
            }
            channel.insert(cutAmount, cutValue);
        }
        List list = channel.getKeyframes();
        for (int i = list.size() - 1; i >= 0; --i) {
            kf = (Keyframe)list.get(i);
            if (!(kf.getTick() < cutAmount - 1.0E-4f)) continue;
            channel.remove(i);
        }
        for (Object obj : channel.getKeyframes()) {
            Keyframe kf2 = (Keyframe)obj;
            kf2.setTick(kf2.getTick() - cutAmount);
        }
        channel.sort();
    }

    public List<PovActionClip> getActive(float tick) {
        ArrayList<PovActionClip> active = new ArrayList<PovActionClip>();
        for (Clip clip2 : this.get()) {
            PovActionClip povClip;
            if (!(clip2 instanceof PovActionClip) || !(povClip = (PovActionClip)clip2).isActive(tick)) continue;
            active.add(povClip);
        }
        active.sort(Comparator.comparingInt(clip -> (Integer)clip.layer.get()));
        return active;
    }

    public MenuPovActionClip getActiveMenu(float tick) {
        MenuPovActionClip top = null;
        for (Clip clip : this.get()) {
            MenuPovActionClip menu;
            if (!(clip instanceof MenuPovActionClip) || !(menu = (MenuPovActionClip)clip).isActive(tick) || top != null && (Integer)menu.layer.get() < (Integer)top.layer.get()) continue;
            top = menu;
        }
        return top;
    }

    public GuiPovActionClip getActiveGui(float tick) {
        GuiPovActionClip top = null;
        for (Clip clip : this.get()) {
            GuiPovActionClip gui;
            if (!(clip instanceof GuiPovActionClip) || !(gui = (GuiPovActionClip)clip).isActive(tick) || top != null && (Integer)gui.layer.get() < (Integer)top.layer.get()) continue;
            top = gui;
        }
        return top;
    }

    public List<BossBarPovActionClip> getActiveBossBars(float tick) {
        ArrayList<BossBarPovActionClip> active = new ArrayList<BossBarPovActionClip>();
        for (Clip clip : this.get()) {
            BossBarPovActionClip bar;
            if (!(clip instanceof BossBarPovActionClip) || !(bar = (BossBarPovActionClip)clip).isActive(tick)) continue;
            active.add(bar);
        }
        active.sort(Comparator.comparingInt(c -> (Integer)c.layer.get()));
        return active;
    }

    public BossBarPovActionClip getActiveBossBar(float tick) {
        BossBarPovActionClip top = null;
        for (Clip clip : this.get()) {
            BossBarPovActionClip bar;
            if (!(clip instanceof BossBarPovActionClip) || !(bar = (BossBarPovActionClip)clip).isActive(tick) || top != null && (Integer)bar.layer.get() < (Integer)top.layer.get()) continue;
            top = bar;
        }
        return top;
    }

    public void fromData(BaseType data) {
        super.fromData(data);
        for (Clip clip : this.get()) {
            if (!(clip instanceof PovActionClip)) continue;
            PovActionClip povClip = (PovActionClip)clip;
            povClip.normalize();
        }
    }
}

