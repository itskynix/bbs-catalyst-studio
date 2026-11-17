package mchorse.bbs_mod.physics.structure;

import mchorse.bbs_mod.physics.forms.ValueData;

public interface IStructurePhysicsForm
{
    ValueData bbs_physics$getDestruction();
    DestructionState bbs_physics$getDestructionState();
    void bbs_physics$setDestructionState(DestructionState state);
}
