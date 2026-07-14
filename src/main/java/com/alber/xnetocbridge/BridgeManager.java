package com.alber.xnetocbridge;

import li.cil.oc.api.Driver;
import li.cil.oc.api.Network;
import li.cil.oc.api.driver.DriverBlock;
import li.cil.oc.api.network.Environment;
import li.cil.oc.api.network.ManagedEnvironment;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.SidedEnvironment;
import li.cil.oc.common.capabilities.Capabilities;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

/**
 * Manages the life cycle of OC nodes associated with XNet bridge connectors.
 * <p>
 * Design principle: OC nodes are transient. They are created on-demand when
 * an XNet channel detects an active connector, and destroyed when the connector
 * is removed, broken, or unloaded. No persistence — addresses are re-assigned
 * by OC on each world load anyway.
 * <p>
 * Network topology: star (hub-and-spoke). Each XNet channel has one hub node.
 * All LINK connector nodes and ADAPTER proxy environments connect to the hub.
 * This replaces the v1.0.0 O(n^2) pairwise approach.
 */
public class BridgeManager {

    // -----------------------------------------------------------------------
    // LINK node management
    // -----------------------------------------------------------------------

    /**
     * Ensure the given connector position has an active OC link node.
     * Creates one if missing. Connects to physically adjacent OC blocks.
     */
    public static void ensureLinkNode(World world, BlockPos pos, EnumFacing side,
                                      XNetOCBridge.BridgeData data) {
        if (data.linkNode == null || data.linkNode.node() == null) {
            data.linkNode = new OCLinkNode(world, pos);
        }

        Node ourNode = data.linkNode.node();
        if (ourNode == null) return;

        // Ensure the node is in a network
        if (ourNode.network() == null) {
            Network.joinNewNetwork(ourNode);
        }

        connectToNeighbor(world, pos, side, ourNode);
    }

    // -----------------------------------------------------------------------
    // ADAPTER proxy management
    // -----------------------------------------------------------------------

    /**
     * For an ADAPTER-mode connector, ensure the adjacent block is
     * proxied into the OC network as a managed environment, connected
     * to the channel's hub node.
     *
     * @param hubNode the channel's hub node (all proxies connect to it)
     * @param fullSync if true, do a full driver re-check
     */
    public static void ensureAdapterProxy(World world, BlockPos connectorPos,
                                          EnumFacing side,
                                          XNetOCBridge.BridgeData data,
                                          OCConnectorSettings settings,
                                          OCLinkNode hubNode,
                                          boolean fullSync) {
        EnumFacing targetFacing = side;
        BlockPos targetPos = MCCompat.offset(connectorPos, targetFacing);

        TileEntity targetTE = MCCompat.getTileEntity(world, targetPos);
        if (targetTE == null) {
            removeAdapterProxy(targetFacing, data, settings, false);
            return;
        }

        // Don't proxy OC-native blocks (they handle their own networking)
        if (targetTE instanceof Environment) {
            removeAdapterProxy(targetFacing, data, settings, false);
            return;
        }

        ManagedEnvironment existingEnv = data.adapterEnvironments.get(targetFacing);

        if (!fullSync && existingEnv != null && existingEnv.node() != null) {
            // Already proxied — just ensure connection to hub
            connectToHub(existingEnv, hubNode);
            saveAdapterState(targetFacing, data, settings, existingEnv);
            return;
        }

        // Full sync: query OC driver
        DriverBlock driver = Driver.driverFor(world, targetPos, targetFacing);
        if (driver == null) {
            removeAdapterProxy(targetFacing, data, settings, false);
            return;
        }
        String driverName = driver.getClass().getName();
        String existingDriverName = data.adapterDriverNames.get(targetFacing);

        if (existingEnv != null && existingEnv.node() != null
                && driverName.equals(existingDriverName)) {
            connectToHub(existingEnv, hubNode);
            saveAdapterState(targetFacing, data, settings, existingEnv);
            return;
        }

        // Remove old environment if driver changed
        if (existingEnv != null) {
            removeAdapterProxy(targetFacing, data, settings, true);
        }

        // Create new environment
        ManagedEnvironment env = driver.createEnvironment(world, targetPos, targetFacing);
        if (env == null) {
            data.adapterEnvironments.remove(targetFacing);
            return;
        }

        NBTTagCompound saved = settings.getAdapterNbtData();
        String environmentName = env.getClass().getName();
        if (saved != null && environmentName.equals(settings.getAdapterEnvironmentName())) {
            try {
                env.load(saved);
            } catch (RuntimeException | LinkageError e) {
                settings.clearAdapterNbtData();
            }
        }

        data.adapterEnvironments.put(targetFacing, env);
        data.adapterDriverNames.put(targetFacing, driverName);

        // Join network and connect to hub
        if (env.node() != null) {
            Network.joinNewNetwork(env.node());
            connectToHub(env, hubNode);
            saveAdapterState(targetFacing, data, settings, env);
        }
    }

    // -----------------------------------------------------------------------
    // Hub connections (star topology)
    // -----------------------------------------------------------------------

    private static void connectToHub(ManagedEnvironment proxyEnv, OCLinkNode hubNode) {
        if (proxyEnv == null || proxyEnv.node() == null || hubNode == null) return;
        Node hubNd = hubNode.node();
        if (hubNd == null) return;

        try {
            Node proxyNode = proxyEnv.node();
            if (proxyNode.network() != null && hubNd.network() != null
                    && !proxyNode.isNeighborOf(hubNd)) {
                proxyNode.connect(hubNd);
            }
        } catch (Exception e) { /* ignore */ }
    }

    private static void disconnectFromHub(ManagedEnvironment proxyEnv, OCLinkNode hubNode) {
        if (proxyEnv == null || proxyEnv.node() == null || hubNode == null) return;
        Node hubNd = hubNode.node();
        if (hubNd == null) return;

        try {
            Node proxyNode = proxyEnv.node();
            if (proxyNode.network() != null && hubNd.network() != null
                    && proxyNode.isNeighborOf(hubNd)) {
                proxyNode.disconnect(hubNd);
            }
        } catch (Exception e) { /* ignore */ }
    }

    // -----------------------------------------------------------------------
    // Adapter proxy cleanup
    // -----------------------------------------------------------------------

    private static void removeAdapterProxy(EnumFacing side,
                                           XNetOCBridge.BridgeData data,
                                           OCConnectorSettings settings,
                                           boolean clearPersistentState) {
        ManagedEnvironment env = data.adapterEnvironments.remove(side);
        data.adapterDriverNames.remove(side);
        if (clearPersistentState) {
            data.adapterNbtData.remove(side);
            settings.clearAdapterNbtData();
        } else if (env != null) {
            saveAdapterState(side, data, settings, env);
        }
        if (env != null && env.node() != null) {
            env.node().remove();
        }
    }

    private static void saveAdapterState(EnumFacing side,
                                         XNetOCBridge.BridgeData data,
                                         OCConnectorSettings settings,
                                         ManagedEnvironment env) {
        if (env == null) return;
        NBTTagCompound tag = new NBTTagCompound();
        try {
            env.save(tag);
            data.adapterNbtData.put(side, NBTCompat.copy(tag));
            settings.setAdapterNbtData(tag);
            settings.setAdapterEnvironmentName(env.getClass().getName());
            if (env.node() != null) {
                settings.setAdapterAddress(env.node().address());
            }
        } catch (RuntimeException | LinkageError e) {
            // Some third-party driver environments do not serialize cleanly.
        }
    }

    // -----------------------------------------------------------------------
    // Physical neighbor connection
    // -----------------------------------------------------------------------

    /**
     * Try to connect our OC node to an OC node of an adjacent tile entity.
     * This is how a LINK connector physically plugs into an adjacent OC computer.
     */
    private static void connectToNeighbor(World world, BlockPos pos,
                                          EnumFacing facing, Node ourNode) {
        BlockPos neighborPos = MCCompat.offset(pos, facing);
        Network.joinOrCreateNetwork(world, neighborPos);
        TileEntity neighborTE = MCCompat.getTileEntity(world, neighborPos);
        Node neighborNode = getNetworkNode(neighborTE, MCCompat.getOpposite(facing));
        if (neighborNode != null && ourNode != null) {
            try {
                if (neighborNode.network() == null) {
                    Network.joinNewNetwork(neighborNode);
                }
                if (ourNode.network() != null && neighborNode.network() != null
                        && !ourNode.isNeighborOf(neighborNode)) {
                    // Match OpenComputers cable logic: the already-known
                    // neighboring node pulls the local node into its network.
                    neighborNode.connect(ourNode);
                }
            } catch (Exception e) { /* ignore */ }
        }
    }

    private static Node getNetworkNode(TileEntity tileEntity, EnumFacing side) {
        if (tileEntity == null) return null;

        try {
            if (Capabilities.SidedEnvironmentCapability != null
                    && tileEntity.hasCapability(Capabilities.SidedEnvironmentCapability, side)) {
                SidedEnvironment host = tileEntity.getCapability(
                        Capabilities.SidedEnvironmentCapability, side);
                if (host != null) {
                    Node sidedNode = host.sidedNode(side);
                    if (sidedNode != null) return sidedNode;
                }
            }

            if (Capabilities.EnvironmentCapability != null
                    && tileEntity.hasCapability(Capabilities.EnvironmentCapability, side)) {
                Environment host = tileEntity.getCapability(
                        Capabilities.EnvironmentCapability, side);
                if (host != null) return host.node();
            }
        } catch (RuntimeException | LinkageError e) {
            // Fall back to direct interfaces below; some third-party TEs throw here.
        }

        if (tileEntity instanceof SidedEnvironment) {
            Node sidedNode = ((SidedEnvironment) tileEntity).sidedNode(side);
            if (sidedNode != null) return sidedNode;
        }
        if (tileEntity instanceof Environment) {
            return ((Environment) tileEntity).node();
        }
        return null;
    }

    // -----------------------------------------------------------------------
    // Global periodic maintenance
    // -----------------------------------------------------------------------

    private static int tickCounter = 0;
    private static final int ORPHAN_CLEANUP_INTERVAL = 40;  // ticks (2s)
    private static final int ORPHAN_MAX_AGE = 60;            // ticks (3s)

    public static void tickAll() {
        tickCounter++;

        if (tickCounter % ORPHAN_CLEANUP_INTERVAL == 0) {
            // Clean up bridge data for connectors that haven't been seen
            // by any channel tick for a long time (implying the connector
            // was removed or the channel was deleted)
            XNetOCBridge.cleanupOrphans(tickCounter, ORPHAN_MAX_AGE);
        }
    }
}
