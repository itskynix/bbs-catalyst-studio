package mchorse.bbs_mod.ui.dashboard.panels.motion;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.forms.FormUtils;
import mchorse.bbs_mod.forms.forms.Form;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;
import mchorse.bbs_mod.utils.MathUtils;
import mchorse.bbs_mod.utils.keyframes.Keyframe;
import mchorse.bbs_mod.utils.keyframes.KeyframeChannel;
import mchorse.bbs_mod.utils.pose.Transform;
import org.joml.Vector3f;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.StringNbtReader;
import net.minecraft.registry.Registries;

import java.io.File;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Disk persistence and snapshotting for mMotion scenes.
 *
 * <p>A scene is one JSON file per scene id in the motion scenes folder. The same serializer
 * feeds the undo/redo history, so a snapshot is exactly what would be written to disk.
 * Values that are derived from the keyframe channels (live transform, tint, visibility, pose)
 * are only written while their channel is empty, which keeps playback and scrubbing from
 * looking like an edit.</p>
 */
public final class MotionSceneIO
{
    public static final int VERSION = 1;

    private MotionSceneIO()
    {}

    public static File fileFor(String id)
    {
        return new File(UIMMotionHubScreen.getScenesFolder(), id + ".json");
    }

    /* ── Scene ── */

    public static MapType toData(UIMMotionPanel.MotionScene scene, List<UIMMotionPanel.MotionLayer> layers)
    {
        MapType map = new MapType();

        map.putInt("version", VERSION);
        map.putString("id", scene.id);
        map.putString("name", scene.name);
        map.putInt("duration", scene.duration);
        map.putInt("fps", scene.fps);
        map.putInt("width", scene.width);
        map.putInt("height", scene.height);
        map.putLong("lastModified", scene.lastModified);

        ListType list = new ListType();

        for (UIMMotionPanel.MotionLayer layer : layers)
        {
            list.add(layerToData(layer));
        }

        map.put("layers", list);

        return map;
    }

    /** Compact string used by the undo history; excludes the volatile modification time. */
    public static String snapshot(UIMMotionPanel.MotionScene scene, List<UIMMotionPanel.MotionLayer> layers)
    {
        MapType map = toData(scene, layers);

        map.remove("lastModified");

        return DataToString.toString(map, true);
    }

    public static UIMMotionPanel.MotionScene fromData(MapType map)
    {
        String id = map.getString("id", "scene_" + System.currentTimeMillis());
        UIMMotionPanel.MotionScene scene = new UIMMotionPanel.MotionScene(id, map.getString("name", id), map.getInt("duration", 300));

        applySettings(scene, map);
        scene.lastModified = map.getLong("lastModified", System.currentTimeMillis());
        scene.layers.addAll(readLayers(map));

        return scene;
    }

    /** Project settings only (name, duration, fps, resolution). */
    public static void applySettings(UIMMotionPanel.MotionScene scene, MapType map)
    {
        scene.name = map.getString("name", scene.name);
        scene.duration = Math.max(1, map.getInt("duration", scene.duration));
        scene.fps = Math.max(1, map.getInt("fps", scene.fps));
        scene.width = Math.max(16, map.getInt("width", scene.width));
        scene.height = Math.max(16, map.getInt("height", scene.height));
    }

    public static List<UIMMotionPanel.MotionLayer> readLayers(MapType map)
    {
        List<UIMMotionPanel.MotionLayer> out = new ArrayList<>();
        ListType list = map.getList("layers");

        for (int i = 0; i < list.size(); i++)
        {
            BaseType type = list.get(i);

            if (type != null && type.isMap())
            {
                try
                {
                    out.add(layerFromData(type.asMap()));
                }
                catch (Exception e)
                {
                    System.err.println("[mMotion] Skipping unreadable layer: " + e);
                }
            }
        }

        return out;
    }

    /**
     * Writes the scene next to its file first, then swaps it in with one atomic move that also
     * replaces the old save. The target is never deleted ahead of time, so at any moment the disk
     * holds either the complete old save or the complete new one; a failed write only leaves the
     * temporary file behind, which is removed.
     */
    public static boolean save(UIMMotionPanel.MotionScene scene, List<UIMMotionPanel.MotionLayer> layers)
    {
        File file = fileFor(scene.id);
        File tmp = new File(file.getParentFile(), scene.id + ".json.tmp");

        try
        {
            scene.lastModified = System.currentTimeMillis();

            File parent = file.getParentFile();

            if (parent != null && !parent.exists())
            {
                parent.mkdirs();
            }

            DataToString.write(tmp, toData(scene, layers), true);

            Path source = tmp.toPath();
            Path target = file.toPath();

            try
            {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            }
            catch (AtomicMoveNotSupportedException e)
            {
                /* File systems without atomic rename: still a single replacing move, never delete + rename */
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }

            return true;
        }
        catch (Exception e)
        {
            System.err.println("[mMotion] Could not save scene " + scene.id + ": " + e);

            try
            {
                Files.deleteIfExists(tmp.toPath());
            }
            catch (Exception ignored)
            {}

            return false;
        }
    }

    public static List<UIMMotionPanel.MotionScene> loadAll()
    {
        List<UIMMotionPanel.MotionScene> scenes = new ArrayList<>();
        File[] files = UIMMotionHubScreen.getScenesFolder().listFiles((dir, name) -> name.endsWith(".json"));

        if (files == null)
        {
            return scenes;
        }

        for (File file : files)
        {
            try
            {
                BaseType data = DataToString.read(file);

                if (data != null && data.isMap())
                {
                    scenes.add(fromData(data.asMap()));
                }
            }
            catch (Exception e)
            {
                System.err.println("[mMotion] Could not read " + file.getName() + ": " + e);
            }
        }

        scenes.sort((a, b) -> Long.compare(b.lastModified, a.lastModified));

        return scenes;
    }

    public static void delete(UIMMotionPanel.MotionScene scene)
    {
        File file = fileFor(scene.id);

        if (file.exists())
        {
            file.delete();
        }
    }

    /* ── Layer ── */

    public static MapType layerToData(UIMMotionPanel.MotionLayer l)
    {
        MapType m = new MapType();

        m.putString("id", l.id);

        if (l.parentId != null)
        {
            m.putString("parentId", l.parentId);
        }

        m.putBool("collapsed", l.collapsed);
        m.putString("name", l.name == null ? "" : l.name);
        m.putString("type", l.type == null ? "" : l.type);
        m.putBool("locked", l.locked);
        m.putInt("color", l.color);
        m.putString("defaultName", l.defaultName == null ? "" : l.defaultName);
        m.putInt("defaultColor", l.defaultColor);
        m.putBool("shading", l.shading);

        /* Live values only matter while nothing keyframed drives them */
        if (l.channelTransform.isEmpty())
        {
            m.putDouble("posX", l.posX);
            m.putDouble("posY", l.posY);
            m.putDouble("posZ", l.posZ);
            m.putDouble("rotPitch", l.rotPitch);
            m.putDouble("rotYaw", l.rotYaw);
            m.putDouble("rotRoll", l.rotRoll);
            m.putDouble("scaleX", l.scaleX);
            m.putDouble("scaleY", l.scaleY);
            m.putDouble("scaleZ", l.scaleZ);
            m.putDouble("anchorX", l.anchorX);
            m.putDouble("anchorY", l.anchorY);
            m.putDouble("anchorZ", l.anchorZ);
        }

        if (l.channelColor.isEmpty())
        {
            m.putDouble("opacity", l.opacity);
            m.putFloat("tintR", l.tintR);
            m.putFloat("tintG", l.tintG);
            m.putFloat("tintB", l.tintB);
        }

        if (l.channelVisible.isEmpty())
        {
            m.putBool("visible", l.visible);
        }

        if (l.channelPose.isEmpty())
        {
            m.putDouble("entityPose", l.entityPose);
        }

        m.putInt("blockLight", l.blockLight);
        m.putInt("itemCount", l.itemCount);
        m.putBool("itemGlint", l.itemGlint);
        m.putString("itemTransformMode", l.itemTransformMode == null ? ModelTransformationMode.FIXED.name() : l.itemTransformMode.name());
        m.putString("entityModel", l.entityModel == null ? "" : l.entityModel);
        m.putString("structureName", l.structureName == null ? "" : l.structureName);
        m.putDouble("structureOffsetX", l.structureOffsetX);
        m.putDouble("structureOffsetY", l.structureOffsetY);
        m.putDouble("structureOffsetZ", l.structureOffsetZ);
        m.putInt("lightType", l.lightType);
        m.putDouble("lightIntensity", l.lightIntensity);
        m.putInt("lightColor", l.lightColor);
        m.putDouble("lightRange", l.lightRange);
        m.putDouble("lightCone", l.lightCone);
        m.putDouble("lightPenumbra", l.lightPenumbra);
        m.putDouble("lightWidth", l.lightWidth);
        m.putDouble("lightHeight", l.lightHeight);
        m.putDouble("lightGlow", l.lightGlow);
        m.putDouble("cameraFov", l.cameraFov);
        m.putBool("renderCamera", l.renderCamera);

        if (l.blockState != null)
        {
            m.putString("blockState", NbtHelper.fromBlockState(l.blockState).toString());
        }

        if (l.itemStack != null)
        {
            m.putString("itemStack", l.itemStack.writeNbt(new NbtCompound()).toString());
        }

        if (l.form != null)
        {
            m.put("form", FormUtils.toData(l.form));
        }

        m.put("channelTransform", l.channelTransform.toData());

        /* Written only when used, so scenes without an overlay keep their old shape */
        if (!l.channelTransformOverlay.isEmpty())
        {
            m.put("channelTransformOverlay", l.channelTransformOverlay.toData());
        }
        m.put("channelPose", l.channelPose.toData());
        m.put("channelColor", l.channelColor.toData());
        m.put("channelVisible", l.channelVisible.toData());
        m.put("channelIntensity", l.channelIntensity.toData());
        m.put("channelRange", l.channelRange.toData());
        m.put("channelCone", l.channelCone.toData());

        return m;
    }

    public static UIMMotionPanel.MotionLayer layerFromData(MapType m)
    {
        String type = m.getString("type", "Block");
        int color = m.getInt("color", 0xFF10B981);
        UIMMotionPanel.MotionLayer l = new UIMMotionPanel.MotionLayer(m.getString("name", "Layer"), type, iconFor(type), color);

        l.id = m.getString("id", l.id);
        l.parentId = m.has("parentId") ? m.getString("parentId") : null;
        l.collapsed = m.getBool("collapsed", false);
        l.locked = m.getBool("locked", false);
        l.color = color;
        l.defaultName = m.getString("defaultName", l.name);
        l.defaultColor = m.getInt("defaultColor", color);
        l.shading = m.getBool("shading", true);

        l.posX = m.getDouble("posX", l.posX);
        l.posY = m.getDouble("posY", l.posY);
        l.posZ = m.getDouble("posZ", l.posZ);
        l.rotPitch = m.getDouble("rotPitch", l.rotPitch);
        l.rotYaw = m.getDouble("rotYaw", l.rotYaw);
        l.rotRoll = m.getDouble("rotRoll", l.rotRoll);
        l.scaleX = m.getDouble("scaleX", l.scaleX);
        l.scaleY = m.getDouble("scaleY", l.scaleY);
        l.scaleZ = m.getDouble("scaleZ", l.scaleZ);
        l.anchorX = m.getDouble("anchorX", l.anchorX);
        l.anchorY = m.getDouble("anchorY", l.anchorY);
        l.anchorZ = m.getDouble("anchorZ", l.anchorZ);
        l.opacity = m.getDouble("opacity", l.opacity);
        l.tintR = m.getFloat("tintR", l.tintR);
        l.tintG = m.getFloat("tintG", l.tintG);
        l.tintB = m.getFloat("tintB", l.tintB);
        l.visible = m.getBool("visible", l.visible);
        l.entityPose = m.getDouble("entityPose", l.entityPose);

        l.blockLight = m.getInt("blockLight", l.blockLight);
        l.itemCount = m.getInt("itemCount", l.itemCount);
        l.itemGlint = m.getBool("itemGlint", l.itemGlint);
        l.entityModel = m.getString("entityModel", "");
        l.structureName = m.getString("structureName", "");
        l.structureOffsetX = m.getDouble("structureOffsetX", 0D);
        l.structureOffsetY = m.getDouble("structureOffsetY", 0D);
        l.structureOffsetZ = m.getDouble("structureOffsetZ", 0D);
        l.lightType = m.getInt("lightType", 0);
        l.lightIntensity = m.getDouble("lightIntensity", 1D);
        l.lightColor = m.getInt("lightColor", l.lightColor);
        l.lightRange = m.getDouble("lightRange", 8D);
        l.lightCone = m.getDouble("lightCone", 45D);
        l.lightPenumbra = m.getDouble("lightPenumbra", 0.3D);
        l.lightWidth = m.getDouble("lightWidth", 2D);
        l.lightHeight = m.getDouble("lightHeight", 2D);
        l.lightGlow = m.getDouble("lightGlow", 0.5D);
        l.cameraFov = m.getDouble("cameraFov", 70D);
        l.renderCamera = m.getBool("renderCamera", false);

        try
        {
            l.itemTransformMode = ModelTransformationMode.valueOf(m.getString("itemTransformMode", ModelTransformationMode.FIXED.name()));
        }
        catch (IllegalArgumentException e)
        {
            l.itemTransformMode = ModelTransformationMode.FIXED;
        }

        if (m.has("form"))
        {
            Form form = FormUtils.fromData(m.get("form"));

            if (form != null)
            {
                l.form = form;
            }
        }

        if (m.has("blockState"))
        {
            try
            {
                BlockState state = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), StringNbtReader.parse(m.getString("blockState")));

                l.blockState = state;

                if (l.form instanceof mchorse.bbs_mod.forms.forms.BlockForm bf)
                {
                    bf.blockState.set(state);
                }
            }
            catch (Exception e)
            {
                System.err.println("[mMotion] Bad block state on layer " + l.name + ": " + e);
            }
        }

        if (m.has("itemStack"))
        {
            try
            {
                ItemStack stack = ItemStack.fromNbt(StringNbtReader.parse(m.getString("itemStack")));

                l.itemStack = stack;

                if (l.form instanceof mchorse.bbs_mod.forms.forms.ItemForm iform)
                {
                    iform.stack.set(stack.copy());
                }
            }
            catch (Exception e)
            {
                System.err.println("[mMotion] Bad item stack on layer " + l.name + ": " + e);
            }
        }

        readChannel(m, "channelTransform", l.channelTransform);
        migrateLegacyTransformKeyframe(l);
        /* Older scenes have no overlay: readChannel skips the missing key and the channel stays empty */
        readChannel(m, "channelTransformOverlay", l.channelTransformOverlay);
        readChannel(m, "channelPose", l.channelPose);
        readChannel(m, "channelColor", l.channelColor);
        readChannel(m, "channelVisible", l.channelVisible);
        readChannel(m, "channelIntensity", l.channelIntensity);
        readChannel(m, "channelRange", l.channelRange);
        readChannel(m, "channelCone", l.channelCone);
        l.enforceVisibleHold();

        return l;
    }

    /*
     * Older scenes saved an auto-injected tick-0 transform keyframe on every actor. A lone tick-0 keyframe is
     * equivalent to a static transform, so it is folded into the layer fields and the channel is emptied.
     */
    private static void migrateLegacyTransformKeyframe(UIMMotionPanel.MotionLayer l)
    {
        if (l.channelTransform.getKeyframes().size() != 1)
        {
            return;
        }

        Keyframe<Transform> only = l.channelTransform.getKeyframes().get(0);

        if (only == null || only.getValue() == null || Math.round(only.getTick()) != 0)
        {
            return;
        }

        Transform t = only.getValue();
        Vector3f euler = new Vector3f();

        t.getEulerRotation(euler);

        l.posX = t.translate.x;
        l.posY = t.translate.y;
        l.posZ = t.translate.z;
        l.rotPitch = MathUtils.toDeg(euler.x);
        l.rotYaw = MathUtils.toDeg(euler.y);
        l.rotRoll = MathUtils.toDeg(euler.z);
        l.scaleX = t.scale.x;
        l.scaleY = t.scale.y;
        l.scaleZ = t.scale.z;
        l.anchorX = t.anchor.x;
        l.anchorY = t.anchor.y;
        l.anchorZ = t.anchor.z;

        l.channelTransform.removeAll();
    }

    private static void readChannel(MapType m, String key, KeyframeChannel<?> channel)
    {
        if (!m.has(key))
        {
            return;
        }

        channel.removeAll();
        channel.fromData(m.get(key));
    }

    private static Icon iconFor(String type)
    {
        if ("Block".equalsIgnoreCase(type)) return Icons.BLOCK;
        if ("Item".equalsIgnoreCase(type)) return Icons.ARMOR_CHESTPLATE;
        if ("Entity".equalsIgnoreCase(type)) return Icons.POSE;
        if ("Structure".equalsIgnoreCase(type)) return Icons.STRUCTURE;
        if ("Light".equalsIgnoreCase(type)) return Icons.SUN;
        if ("PointLight".equalsIgnoreCase(type)) return Icons.SPARKLES;
        if ("SpotLight".equalsIgnoreCase(type)) return Icons.SUN;
        if ("RectLight".equalsIgnoreCase(type)) return Icons.BLOCK;
        if ("Camera".equalsIgnoreCase(type)) return Icons.CAMERA;

        return Icons.CURVES;
    }
}
