/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.settings.values.base.BaseValue
 *  mchorse.bbs_mod.settings.values.base.BaseValueGroup
 *  mchorse.bbs_mod.settings.values.numeric.ValueBoolean
 *  mchorse.bbs_mod.utils.clips.Clip
 *  mchorse.bbs_mod.utils.keyframes.KeyframeChannel
 *  mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories
 */
package mchorse.bbs_mod.camera.pov.actions.clip;

import mchorse.bbs_mod.camera.pov.actions.PovActionType;
import mchorse.bbs_mod.camera.pov.actions.clip.PovActionClip;
import mchorse.bbs_mod.settings.values.base.BaseValue;
import mchorse.bbs_mod.settings.values.base.BaseValueGroup;
import mchorse.bbs_mod.settings.values.numeric.ValueBoolean;
import mchorse.bbs_mod.utils.clips.Clip;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.keyframes.factories.KeyframeFactories;

public class ChatPovActionClip
extends PovActionClip {
    public final KeyframeChannel<String> text = this.channel("chat_text", KeyframeFactories.STRING);
    public final KeyframeChannel<Boolean> barVisible = this.channel("chat_bar_visible", KeyframeFactories.BOOLEAN);
    public final KeyframeChannel<Integer> cursorPos = this.channel("chat_cursor_pos", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> selStart = this.channel("chat_sel_start", KeyframeFactories.INTEGER);
    public final KeyframeChannel<Integer> selEnd = this.channel("chat_sel_end", KeyframeFactories.INTEGER);
    public final KeyframeChannel<String> executedText = this.channel("executed_text", KeyframeFactories.STRING);
    public final KeyframeChannel<Integer> chatScroll = this.channel("chat_scroll", KeyframeFactories.INTEGER);
    public final ValueBoolean showRecommendations = new ValueBoolean("show_recommendations", true);

    public ChatPovActionClip() {
        this.add((BaseValue)this.showRecommendations);
    }

    @Override
    public PovActionType getActionType() {
        return PovActionType.CHAT;
    }

    public void createDefaultKeyframes() {
        if (this.text.isEmpty()) {
            this.text.insert(0.0f, "");
        }
        if (this.barVisible.isEmpty()) {
            this.barVisible.insert(0.0f, true);
        }
        if (this.cursorPos.isEmpty()) {
            this.cursorPos.insert(0.0f, 0);
        }
        if (this.selStart.isEmpty()) {
            this.selStart.insert(0.0f, -1);
        }
        if (this.selEnd.isEmpty()) {
            this.selEnd.insert(0.0f, -1);
        }
        if (this.chatScroll.isEmpty()) {
            this.chatScroll.insert(0.0f, 0);
        }
    }

    protected Clip create() {
        ChatPovActionClip clip = new ChatPovActionClip();
        clip.copy((BaseValueGroup)this);
        return clip;
    }
}

