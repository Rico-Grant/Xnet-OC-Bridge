package com.alber.xnetocbridge;

import li.cil.oc.api.Network;
import li.cil.oc.api.network.*;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/**
 * A managed OC environment representing an XNet connector in the
 * OpenComputers component network.
 * <p>
 * This node:
 * <ul>
 *   <li>Has {@link Visibility#Network} reachability — visible network-wide.</li>
 *   <li>Registers as a named OC component when an address is provided,
 *       so Lua code can reference it as {@code component.<address>}.</li>
 *   <li>Provides a {@code getXNetInfo()} callback returning the connector's
 *       world position and mode for diagnostics.</li>
 *   <li>Connects to physically adjacent OC blocks (handled by BridgeManager).</li>
 *   <li>Connects to the channel hub node for cross-connector bridging.</li>
 * </ul>
 */
public class OCLinkNode extends AbstractManagedEnvironment implements EnvironmentHost {

    private final World world;
    private final BlockPos pos;

    /**
     * Create a new LINK node.
     *
     * @param world   the world
     * @param pos     the connector's position
     * @param address the stable OC address (UUID format). If non-empty,
     *                the node will be registered as {@code component.<address>}.
     */
    public OCLinkNode(World world, BlockPos pos) {
        this.world = world;
        this.pos = pos;

        // Plain network node: joins OC networks without becoming a component.
        setNode(Network.newNode(this, Visibility.None)
                .create());
    }

    public BlockPos getPos() {
        return pos;
    }

    // -----------------------------------------------------------------------
    // EnvironmentHost — world location of this node
    // -----------------------------------------------------------------------

    @Override
    public World world() {
        return world;
    }

    @Override
    public double xPosition() {
        return MCCompat.getX(pos) + 0.5;
    }

    @Override
    public double yPosition() {
        return MCCompat.getY(pos) + 0.5;
    }

    @Override
    public double zPosition() {
        return MCCompat.getZ(pos) + 0.5;
    }

    @Override
    public void markChanged() {
        // no-op: virtual environment
    }

    // -----------------------------------------------------------------------
    // Environment callbacks
    // -----------------------------------------------------------------------

    @Override
    public void onConnect(Node other) {
    }

    @Override
    public void onDisconnect(Node other) {
    }

    @Override
    public void onMessage(Message message) {
    }

    // -----------------------------------------------------------------------
    // ManagedEnvironment
    // -----------------------------------------------------------------------

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public void update() {
    }

    // -----------------------------------------------------------------------
    // Persistence
    // -----------------------------------------------------------------------

    @Override
    public void load(NBTTagCompound nbt) {
        super.load(nbt);
    }

    @Override
    public void save(NBTTagCompound nbt) {
        super.save(nbt);
    }
}
