package mchorse.bbs_mod.ui.film;

import mchorse.bbs_mod.forms.entities.IEntity;
import mchorse.bbs_mod.ui.framework.UIContext;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A clips editor with targets of its own to follow in place of a film's actors: Recap Studio, whose
 * targets are the recorded players.
 */
public interface IUIClipTargets
{
    /** A menu of the targets; picking none gives an empty id. */
    void showTargets(UIContext context, String current, Consumer<String> callback);

    /** A menu of a target's bones; nothing opens for a target without any. */
    void showTargetBones(UIContext context, String target, String current, Consumer<String> callback);

    String targetName(String target);

    /** The targets as the camera sees them, id to entity: what a flown camera is solved against. */
    Map<String, IEntity> targetEntities();
}
