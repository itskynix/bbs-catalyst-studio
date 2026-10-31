package mchorse.bbs_mod.utils;

import net.minecraft.util.Identifier;

/**
 * Identifier factory that hides the constructor going private in 1.21.
 */
public class Ids
{
    public static Identifier of(String namespace, String path)
    {
        //? if >=1.21 {
        /*return Identifier.of(namespace, path);
        *///?} else {
        return new Identifier(namespace, path);
        //?}
    }

    public static Identifier of(String id)
    {
        //? if >=1.21 {
        /*return Identifier.of(id);
        *///?} else {
        return new Identifier(id);
        //?}
    }
}
