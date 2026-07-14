package com.alber.xnetocbridge;

import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import mcjty.xnet.api.IXNet;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod(modid = XNetOCBridge.MODID, name = XNetOCBridge.MODNAME, version = XNetOCBridge.VERSION,
        dependencies = "required-after:xnet;required-after:opencomputers")
public class XNetOCBridge {

    public static final String MODID = "xnetocbridge";
    public static final String MODNAME = "XNet OC Bridge";
    public static final String VERSION = "1.3.15";

    public static final Logger LOGGER = LogManager.getLogger(MODNAME);

    public static final Map<Integer, Map<BlockPos, BridgeData>> bridgeState = new HashMap<>();

    public static class BridgeData {
        public BridgeMode mode = BridgeMode.LINK;
        public OCLinkNode linkNode;
        public final Map<EnumFacing, ManagedEnvironment> adapterEnvironments = new EnumMap<>(EnumFacing.class);
        public final Map<EnumFacing, String> adapterDriverNames = new EnumMap<>(EnumFacing.class);
        public final Map<EnumFacing, NBTTagCompound> adapterNbtData = new EnumMap<>(EnumFacing.class);
        public boolean active;
        public int owningChannel = -1;
        public long lastActiveTick = 0;
    }

    public enum BridgeMode {
        LINK,
        ADAPTER
    }

    @Mod.Instance
    public static XNetOCBridge instance;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("XNet OC Bridge initializing...");
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        registerWithXNet();
    }

    private void registerWithXNet() {
        try {
            IXNet xnet = mcjty.xnet.XNet.xNetApi;
            if (xnet != null) {
                xnet.registerChannelType(new OCChannelType());
                xnet.registerConnectable(new OCConnectable());
                LOGGER.info("Registered OpenComputers channel type with XNet");
            } else {
                LOGGER.error("Could not access XNet API!");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to register with XNet: " + e.getMessage(), e);
        }
    }

    public static BridgeData getOrCreateBridgeData(int dimension, BlockPos pos) {
        Map<BlockPos, BridgeData> dimMap = bridgeState.computeIfAbsent(dimension, k -> new HashMap<>());
        return dimMap.computeIfAbsent(pos, k -> new BridgeData());
    }

    @Nullable
    public static BridgeData getBridgeData(int dimension, BlockPos pos) {
        Map<BlockPos, BridgeData> dimMap = bridgeState.get(dimension);
        return dimMap != null ? dimMap.get(pos) : null;
    }

    public static void removeBridgeData(int dimension, BlockPos pos) {
        Map<BlockPos, BridgeData> dimMap = bridgeState.get(dimension);
        if (dimMap != null) {
            BridgeData data = dimMap.remove(pos);
            if (data != null) {
                cleanupBridgeNodes(data);
            }
        }
    }

    private static void cleanupBridgeNodes(BridgeData data) {
        if (data.linkNode != null) {
            Node node = data.linkNode.node();
            if (node != null) node.remove();
            data.linkNode = null;
        }
        for (ManagedEnvironment env : data.adapterEnvironments.values()) {
            if (env != null && env.node() != null) {
                env.node().remove();
            }
        }
        data.adapterEnvironments.clear();
        data.adapterDriverNames.clear();
        data.adapterNbtData.clear();
    }

    public static void cleanupOrphans(long currentTick, int maxAgeTicks) {
        for (Map.Entry<Integer, Map<BlockPos, BridgeData>> dimEntry : bridgeState.entrySet()) {
            int dim = dimEntry.getKey();
            List<BlockPos> orphans = new ArrayList<>();
            for (Map.Entry<BlockPos, BridgeData> entry : dimEntry.getValue().entrySet()) {
                if (currentTick - entry.getValue().lastActiveTick > maxAgeTicks) {
                    orphans.add(entry.getKey());
                }
            }
            for (BlockPos pos : orphans) {
                removeBridgeData(dim, pos);
            }
        }
    }

    public static int getDimensionId(World world) {
        try {
            if (world instanceof net.minecraft.world.WorldServer) {
                Integer[] ids = net.minecraftforge.common.DimensionManager.getIDs();
                if (ids != null) {
                    for (int id : ids) {
                        if (net.minecraftforge.common.DimensionManager.getWorld(id) == world) {
                            return id;
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to resolve dimension ID, falling back to 0: " + e.getMessage());
        }
        return 0;
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockPos pos = MCCompat.getPos(event);
        for (Map.Entry<Integer, Map<BlockPos, BridgeData>> dimEntry : bridgeState.entrySet()) {
            if (dimEntry.getValue().containsKey(pos)) {
                removeBridgeData(dimEntry.getKey(), pos);
                break;
            }
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            BridgeManager.tickAll();
        }
    }
}
