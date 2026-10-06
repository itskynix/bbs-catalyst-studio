/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  mchorse.bbs_mod.cubic.model.ModelManager
 *  mchorse.bbs_mod.data.DataToString
 *  mchorse.bbs_mod.data.types.BaseType
 *  mchorse.bbs_mod.data.types.MapType
 *  mchorse.bbs_mod.resources.AssetProvider
 *  mchorse.bbs_mod.resources.Link
 *  mchorse.bbs_mod.utils.IOUtils
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.Shadow
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package mchorse.bbs_mod.camera.pov.integration.mixin.bbs;

import mchorse.bbs_mod.camera.pov.PovAddon;
import java.io.File;
import java.io.InputStream;
import mchorse.bbs_mod.cubic.model.ModelManager;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.resources.AssetProvider;
import mchorse.bbs_mod.resources.Link;
import mchorse.bbs_mod.utils.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={ModelManager.class}, remap=false)
public abstract class ModelManagerPovMixin {
    @Shadow
    public AssetProvider provider;

    @Inject(method={"loadConfig"}, at={@At(value="HEAD")}, cancellable=true)
    private void bbsPov$overrideConfig(Link modelLink, CallbackInfoReturnable<MapType> info) {
        block11: {
            File externalFile;
            if (modelLink == null) {
                return;
            }
            Link configLink = modelLink.combine("config.json");
            if (this.provider != null && (externalFile = this.provider.getFile(configLink)) != null && externalFile.exists()) {
                return;
            }
            String povPath = "assets/bbs_pov/assets/" + configLink.path;
            try {
                InputStream stream = PovAddon.class.getClassLoader().getResourceAsStream(povPath);
                if (stream == null) break block11;
                try (InputStream s = stream;){
                    String string = IOUtils.readText((InputStream)s);
                    BaseType data = DataToString.fromString((String)string);
                    if (data instanceof MapType) {
                        MapType map = (MapType)data;
                        info.setReturnValue(map);
                    }
                }
            }
            catch (Exception e) {
                PovAddon.LOGGER.error("Failed to load POV override config for " + String.valueOf(modelLink), (Throwable)e);
            }
        }
    }
}

