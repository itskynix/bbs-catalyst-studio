package mchorse.bbs_mod.mixin.client.bbs;

import mchorse.bbs_mod.graphics.texture.Texture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import qualet.irlite.client.light.ShadowResourceRevision;
import qualet.irlite.client.light.ShadowResourceVersions;

@Mixin(value = Texture.class, remap = false)
public class TextureShadowRevisionMixin implements ShadowResourceRevision
{
    @Unique private long irlite$revision;

    @Inject(method = {"uploadTexture(IIIILjava/nio/ByteBuffer;)V", "setSize(II)V",
        "setParameter(II)V", "generateMipmap()V", "delete()V"}, at = @At("HEAD"), require = 0)
    private void irlite$changed(CallbackInfo ci) { irlite$revision = ShadowResourceVersions.next(); }

    @Override public long irlite$shadowRevision() { return irlite$revision; }
}

