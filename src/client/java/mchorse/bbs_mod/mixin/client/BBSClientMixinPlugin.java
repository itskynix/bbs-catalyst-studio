package mchorse.bbs_mod.mixin.client;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

public class BBSClientMixinPlugin implements IMixinConfigPlugin
{
    private static final boolean IRIS_LOADED = FabricLoader.getInstance().isModLoaded("iris");
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");
    private static final boolean MTR_LOADED = FabricLoader.getInstance().isModLoaded("mtr");

    @Override
    public void onLoad(String mixinPackage)
    {}

    @Override
    public String getRefMapperConfig()
    {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName)
    {
        if (mixinClassName.contains(".iris.") || targetClassName.startsWith("net.irisshaders.iris."))
        {
            return IRIS_LOADED;
        }

        if (mixinClassName.contains(".sodium.") || targetClassName.startsWith("me.jellysquid.mods.sodium."))
        {
            return SODIUM_LOADED;
        }

        if (mixinClassName.contains(".mtr.") || targetClassName.startsWith("org.mtr."))
        {
            return MTR_LOADED;
        }

        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets)
    {}

    @Override
    public List<String> getMixins()
    {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo)
    {}
}

