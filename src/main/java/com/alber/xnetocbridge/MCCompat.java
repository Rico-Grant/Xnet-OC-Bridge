package com.alber.xnetocbridge;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.event.world.BlockEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class MCCompat {

    private MCCompat() {
    }

    static BlockPos offset(BlockPos pos, EnumFacing facing) {
        return (BlockPos) invoke(pos, "offset", "func_177972_a",
                new Class<?>[]{EnumFacing.class}, facing);
    }

    static TileEntity getTileEntity(World world, BlockPos pos) {
        return (TileEntity) invoke(world, "getTileEntity", "func_175625_s",
                new Class<?>[]{BlockPos.class}, pos);
    }

    static World getWorld(TileEntity tileEntity) {
        return (World) invoke(tileEntity, "getWorld", "func_145831_w", new Class<?>[0]);
    }

    static EnumFacing getOpposite(EnumFacing facing) {
        return (EnumFacing) invoke(facing, "getOpposite", "func_176734_d",
                new Class<?>[0]);
    }

    static int getX(BlockPos pos) {
        return (Integer) invoke(pos, "getX", "func_177958_n", new Class<?>[0]);
    }

    static int getY(BlockPos pos) {
        return (Integer) invoke(pos, "getY", "func_177956_o", new Class<?>[0]);
    }

    static int getZ(BlockPos pos) {
        return (Integer) invoke(pos, "getZ", "func_177952_p", new Class<?>[0]);
    }

    static int getRedstonePowerFromNeighbors(World world, BlockPos pos) {
        return (Integer) invoke(world, "getRedstonePowerFromNeighbors", "func_175687_A",
                new Class<?>[]{BlockPos.class}, pos);
    }

    static long getTotalWorldTime(World world) {
        return (Long) invoke(world, "getTotalWorldTime", "func_82737_E",
                new Class<?>[0]);
    }

    static BlockPos getPos(BlockEvent.BreakEvent event) {
        return (BlockPos) invoke(event, "getPos", "func_180727_a", new Class<?>[0]);
    }

    private static Object invoke(Object target, String mcpName, String srgName,
                                 Class<?>[] parameterTypes, Object... args) {
        Method method = findMethod(target.getClass(), mcpName, parameterTypes);
        if (method == null) {
            method = findMethod(target.getClass(), srgName, parameterTypes);
        }
        if (method == null) {
            throw new IllegalStateException("Missing Minecraft method " + mcpName + "/" + srgName);
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access Minecraft method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("Minecraft method failed " + method.getName(), cause);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        Class<?> current = owner;
        while (current != null) {
            try {
                return current.getMethod(name, parameterTypes);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
}
