package com.alber.xnetocbridge;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class NBTCompat {

    private NBTCompat() {
    }

    static boolean hasKey(NBTTagCompound tag, String key) {
        return (Boolean) invoke(tag, "hasKey", "func_74764_b",
                new Class<?>[]{String.class}, key);
    }

    static int getInteger(NBTTagCompound tag, String key) {
        return (Integer) invoke(tag, "getInteger", "func_74762_e",
                new Class<?>[]{String.class}, key);
    }

    static void setInteger(NBTTagCompound tag, String key, int value) {
        invoke(tag, "setInteger", "func_74768_a",
                new Class<?>[]{String.class, int.class}, key, value);
    }

    static byte getByte(NBTTagCompound tag, String key) {
        return (Byte) invoke(tag, "getByte", "func_74771_c",
                new Class<?>[]{String.class}, key);
    }

    static void setByte(NBTTagCompound tag, String key, byte value) {
        invoke(tag, "setByte", "func_74774_a",
                new Class<?>[]{String.class, byte.class}, key, value);
    }

    static String getString(NBTTagCompound tag, String key) {
        return (String) invoke(tag, "getString", "func_74779_i",
                new Class<?>[]{String.class}, key);
    }

    static void setString(NBTTagCompound tag, String key, String value) {
        invoke(tag, "setString", "func_74778_a",
                new Class<?>[]{String.class, String.class}, key, value);
    }

    static NBTTagCompound getCompoundTag(NBTTagCompound tag, String key) {
        return (NBTTagCompound) invoke(tag, "getCompoundTag", "func_74775_l",
                new Class<?>[]{String.class}, key);
    }

    static void setTag(NBTTagCompound tag, String key, NBTBase value) {
        invoke(tag, "setTag", "func_74782_a",
                new Class<?>[]{String.class, NBTBase.class}, key, value);
    }

    static NBTTagCompound copy(NBTTagCompound tag) {
        if (tag == null) {
            return null;
        }
        return (NBTTagCompound) invoke(tag, "copy", "func_74737_b",
                new Class<?>[0]);
    }

    private static Object invoke(Object target, String mcpName, String srgName,
                                 Class<?>[] parameterTypes, Object... args) {
        Method method = findMethod(target.getClass(), mcpName, parameterTypes);
        if (method == null) {
            method = findMethod(target.getClass(), srgName, parameterTypes);
        }
        if (method == null) {
            throw new IllegalStateException("Missing NBT method " + mcpName + "/" + srgName);
        }
        try {
            return method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access NBT method " + method.getName(), e);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("NBT method failed " + method.getName(), cause);
        }
    }

    private static Method findMethod(Class<?> owner, String name, Class<?>[] parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
