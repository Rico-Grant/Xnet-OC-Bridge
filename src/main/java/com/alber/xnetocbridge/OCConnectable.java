package com.alber.xnetocbridge;

import mcjty.xnet.api.channels.IConnectable;
import mcjty.xnet.blocks.cables.ConnectorTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import li.cil.oc.api.network.Environment;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Registered as an XNet IConnectable, this tells XNet connectors that they
 * can connect to OC-compatible blocks (those implementing {@code Environment}
 * or having an OC driver registered).
 */
public class OCConnectable implements IConnectable {

    @Override
    public ConnectResult canConnect(@Nonnull IBlockAccess access,
                                    @Nonnull BlockPos connectorPos,
                                    @Nonnull BlockPos blockPos,
                                    @Nullable TileEntity tileEntity,
                                    @Nonnull EnumFacing facing) {
        if (tileEntity instanceof ConnectorTileEntity) {
            return ConnectResult.NO;
        }

        // OC-native tile entities (computers, cables, adapters, etc.)
        if (tileEntity instanceof Environment) {
            return ConnectResult.YES;
        }

        // Blocks with OC drivers (forge furnaces, modded machines, etc.).
        // XNet asks this on the client for GUI/arm rendering too, so do not
        // restrict the lookup to WorldServer.
        World world = access instanceof World ? (World) access
                : tileEntity != null ? MCCompat.getWorld(tileEntity) : null;
        if (world != null) {
            if (OCChannelType.hasDriver(world, blockPos, facing)
                    || OCChannelType.hasDriver(world, blockPos, MCCompat.getOpposite(facing))) {
                return ConnectResult.YES;
            }
        }

        return ConnectResult.DEFAULT;
    }
}
