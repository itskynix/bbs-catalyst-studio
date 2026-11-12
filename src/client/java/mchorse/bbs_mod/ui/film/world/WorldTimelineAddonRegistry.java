package mchorse.bbs_mod.ui.film.world;

import mchorse.bbs.compat.mtr.MtrCompatHelper;
import mchorse.bbs_mod.film.world.WorldEventBlock;
import mchorse.bbs_mod.l10n.keys.IKey;
import mchorse.bbs_mod.ui.UIKeys;
import mchorse.bbs_mod.ui.utils.icons.Icon;
import mchorse.bbs_mod.ui.utils.icons.Icons;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dynamic addon provider and operation registry for the World Timeline.
 * Scans and exposes MTR operations when MTR is present and allows future mods/addons
 * to register their own World Timeline event blocks dynamically.
 */
public final class WorldTimelineAddonRegistry
{
    public static final String ADDON_MTR = "mtr";

    public static final int CATEGORY_TRANSIT = 0;
    public static final int CATEGORY_FLIGHT = 1;
    public static final int CATEGORY_STATION = 2;
    public static final int CATEGORY_ADDONS = 3;

    private static final Map<String, AddonDescriptor> ADDONS = new LinkedHashMap<>();
    private static final List<OperationEntry> OPERATIONS = new ArrayList<>();
    private static boolean initialized = false;

    private WorldTimelineAddonRegistry()
    {}

    public static synchronized void ensureInitialized()
    {
        if (initialized)
        {
            return;
        }

        initialized = true;

        if (MtrCompatHelper.isMtrLoaded())
        {
            registerAddon(new AddonDescriptor(ADDON_MTR, UIKeys.WORLD_TIMELINE_MENU_ADDON_MTR, Icons.POSE));

            registerOperation(new OperationEntry(
                "mtr:dispatch_train",
                ADDON_MTR,
                CATEGORY_TRANSIT,
                WorldEventBlock.TYPE_DISPATCH_TRAIN,
                UIKeys.WORLD_TIMELINE_CONTEXT_DISPATCH_TRAIN,
                Icons.FORWARD,
                WorldEventBlock.COLOR_DISPATCH_TRAIN,
                (startTick, duration, layer) -> WorldEventBlock.createDispatchTrain(UIKeys.WORLD_TIMELINE_CONTEXT_DISPATCH_TRAIN.get(), startTick, duration, layer)
            ));

            registerOperation(new OperationEntry(
                "mtr:flight_operation",
                ADDON_MTR,
                CATEGORY_FLIGHT,
                WorldEventBlock.TYPE_FLIGHT_OPERATION,
                UIKeys.WORLD_TIMELINE_CONTEXT_FLIGHT_OPERATION,
                Icons.PLANE,
                WorldEventBlock.COLOR_FLIGHT_OPERATION,
                (startTick, duration, layer) -> WorldEventBlock.createFlightOperation(UIKeys.WORLD_TIMELINE_CONTEXT_FLIGHT_OPERATION.get(), startTick, duration, layer)
            ));

            registerOperation(new OperationEntry(
                "mtr:station_doors",
                ADDON_MTR,
                CATEGORY_STATION,
                WorldEventBlock.TYPE_STATION_DOORS,
                UIKeys.WORLD_TIMELINE_CONTEXT_STATION_DOORS,
                Icons.TIME,
                WorldEventBlock.COLOR_STATION_DOORS,
                (startTick, duration, layer) -> WorldEventBlock.createStationDoors(UIKeys.WORLD_TIMELINE_CONTEXT_STATION_DOORS.get(), startTick, duration, layer)
            ));
        }
    }

    public static synchronized void registerAddon(AddonDescriptor addon)
    {
        if (addon != null && addon.id != null && !addon.id.isEmpty())
        {
            ADDONS.put(addon.id, addon);
        }
    }

    public static synchronized void registerOperation(OperationEntry entry)
    {
        if (entry == null || entry.id == null)
        {
            return;
        }

        for (int i = 0; i < OPERATIONS.size(); i++)
        {
            if (entry.id.equals(OPERATIONS.get(i).id))
            {
                OPERATIONS.set(i, entry);

                return;
            }
        }

        OPERATIONS.add(entry);
    }

    public static List<AddonDescriptor> getAddons()
    {
        ensureInitialized();

        return new ArrayList<>(ADDONS.values());
    }

    public static List<OperationEntry> getOperations()
    {
        ensureInitialized();

        return Collections.unmodifiableList(OPERATIONS);
    }

    public static List<OperationEntry> getOperationsForTab(int tabCategory, String addonFilterId)
    {
        ensureInitialized();

        List<OperationEntry> result = new ArrayList<>();

        for (OperationEntry entry : OPERATIONS)
        {
            if (tabCategory == CATEGORY_ADDONS)
            {
                if (addonFilterId == null || addonFilterId.isEmpty() || addonFilterId.equals(entry.addonId))
                {
                    result.add(entry);
                }
            }
            else if (entry.category == tabCategory)
            {
                result.add(entry);
            }
        }

        return result;
    }

    public interface IWorldBlockFactory
    {
        WorldEventBlock create(int startTick, int duration, int layer);
    }

    public static final class AddonDescriptor
    {
        public final String id;
        public final IKey title;
        public final Icon icon;

        public AddonDescriptor(String id, IKey title, Icon icon)
        {
            this.id = id;
            this.title = title;
            this.icon = icon;
        }
    }

    public static final class OperationEntry
    {
        public final String id;
        public final String addonId;
        public final int category;
        public final String operationType;
        public final IKey title;
        public final Icon icon;
        public final int color;
        public final IWorldBlockFactory factory;

        public OperationEntry(
            String id,
            String addonId,
            int category,
            String operationType,
            IKey title,
            Icon icon,
            int color,
            IWorldBlockFactory factory
        )
        {
            this.id = id;
            this.addonId = addonId;
            this.category = category;
            this.operationType = operationType;
            this.title = title;
            this.icon = icon;
            this.color = color;
            this.factory = factory;
        }
    }
}
