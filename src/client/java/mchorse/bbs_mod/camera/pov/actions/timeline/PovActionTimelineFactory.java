/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.BBSModClient
 *  mchorse.bbs_mod.camera.clips.ClipFactoryData
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.ui.utils.icons.Icon
 *  mchorse.bbs_mod.ui.utils.icons.Icons
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.factory.IFactory
 */
package mchorse.bbs_mod.camera.pov.actions.timeline;

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
import java.lang.invoke.StringConcatFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import mchorse.bbs_mod.BBSModClient;
import mchorse.bbs_mod.camera.clips.ClipFactoryData;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.factory.IFactory;

public final class PovActionTimelineFactory
implements IFactory<Clip, ClipFactoryData> {
    private final Map<Link, PovActionType> types = new LinkedHashMap<Link, PovActionType>();
    private final Map<PovActionType, Link> links = new EnumMap<PovActionType, Link>(PovActionType.class);

    public PovActionTimelineFactory() {
        for (PovActionType type : PovActionType.values()) {
            Link link = new Link("bbs_pov", type.id);
            this.types.put(link, type);
            this.links.put(type, link);
        }
        this.registerFriendlyNames();
    }

    private void registerFriendlyNames() {
        if (BBSModClient.getL10n() == null) {
            return;
        }
        for (PovActionType type : PovActionType.values()) {
            Link link = this.links.get(type);
            mchorse.bbs_mod.l10n.keys.LangKey key = BBSModClient.getL10n().getKey("bbs.ui.camera.clips." + link, type.title);
            if (key != null && (key.content == null || key.content.isEmpty() || "GUI".equals(key.content) || "Menu".equals(key.content))) {
                key.content = type.title;
            }
        }
    }

    public Link getType(Clip clip) {
        PovActionType povActionType;
        if (clip instanceof PovActionClip) {
            PovActionClip povClip = (PovActionClip)clip;
            povActionType = povClip.getActionType();
        } else {
            povActionType = PovActionType.GUI;
        }
        PovActionType type = povActionType;
        return this.links.get(type);
    }

    public Clip create(Link link) {
        PovActionType type = this.types.getOrDefault(link, PovActionType.GUI);
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
        clip.layer.set(type.seedLayer());
        return clip;
    }

    public ClipFactoryData getData(Clip clip) {
        return this.getData(this.getType(clip));
    }

    public ClipFactoryData getData(Link link) {
        PovActionType type = this.types.getOrDefault(link, PovActionType.GUI);
        Icon icon = Icons.ACTION;
        int color = type.color;
        if (type == PovActionType.GUI || type == PovActionType.MENU) {
            icon = Icons.LAYOUT;
        } else if (type == PovActionType.PARTICLE_EFFECT) {
            icon = Icons.PARTICLE;
        } else if (type == PovActionType.CAMERA_SHAKE) {
            icon = Icons.SPHERE;
        } else if (type == PovActionType.SCREEN_EFFECT) {
            icon = Icons.FADING;
        } else if (type == PovActionType.STATUS_EFFECTS) {
            icon = Icons.HEART;
        } else if (type == PovActionType.TOASTS) {
            icon = Icons.BUBBLE;
        } else if (type == PovActionType.CHAT) {
            icon = Icons.CONSOLE;
        } else if (type == PovActionType.BOSS_BARS) {
            icon = Icons.SKULL;
        }
        return new ClipFactoryData(icon, color);
    }

    public Collection<Link> getKeys() {
        return new ArrayList<Link>(this.types.keySet());
    }
}

