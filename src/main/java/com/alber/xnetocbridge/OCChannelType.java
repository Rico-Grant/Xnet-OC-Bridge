package com.alber.xnetocbridge;

import li.cil.oc.api.Driver;
import li.cil.oc.api.network.Environment;
import mcjty.xnet.api.channels.IChannelSettings;
import mcjty.xnet.api.channels.IChannelType;
import mcjty.xnet.api.channels.IConnectorSettings;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * XNet channel type for OpenComputers bridging.
 *
 * LINK mode behaves like virtual OC cable. ADAPTER mode behaves like an OC
 * adapter on the connector's configured side.
 */
public class OCChannelType implements IChannelType {

    public static final String ID = "OC";
    public static final String NAME = "OC";

    @Override
    public String getID() {
        return ID;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public boolean supportsBlock(@Nonnull World world, @Nonnull BlockPos pos, @Nullable EnumFacing side) {
        TileEntity te = MCCompat.getTileEntity(world, pos);

        if (te instanceof Environment) {
            return true;
        }

        return hasDriver(world, pos, side);
    }

    static boolean hasDriver(@Nonnull World world, @Nonnull BlockPos pos, @Nullable EnumFacing side) {
        if (side != null) {
            return hasDriverOnSide(world, pos, side);
        }
        for (EnumFacing facing : EnumFacing.values()) {
            if (hasDriverOnSide(world, pos, facing)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDriverOnSide(World world, BlockPos pos, EnumFacing side) {
        try {
            return Driver.driverFor(world, pos, side) != null;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }

    @Nonnull
    @Override
    public IConnectorSettings createConnector(@Nonnull EnumFacing side) {
        return new OCConnectorSettings(side);
    }

    @Nonnull
    @Override
    public IChannelSettings createChannel() {
        return new OCChannelSettings();
    }
}
