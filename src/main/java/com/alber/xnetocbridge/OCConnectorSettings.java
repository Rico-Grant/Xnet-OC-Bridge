package com.alber.xnetocbridge;

import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonObject;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.helper.AbstractConnectorSettings;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.Set;

/**
 * Per-connector settings for the OpenComputers channel type.
 *
 * LINK is a virtual OC cable endpoint. ADAPTER is a directional virtual OC
 * adapter for the connector side.
 */
public class OCConnectorSettings extends AbstractConnectorSettings {

    public static final String TAG_MODE = "ocmode";

    private static final ResourceLocation LINK_ICON =
            new ResourceLocation(XNetOCBridge.MODID, "textures/gui/oc_link.png");
    private static final ResourceLocation ADAPTER_ICON =
            new ResourceLocation(XNetOCBridge.MODID, "textures/gui/oc_adapter.png");

    private XNetOCBridge.BridgeMode mode = XNetOCBridge.BridgeMode.LINK;
    private String adapterAddress = "";
    private String adapterEnvironmentName = "";
    private NBTTagCompound adapterNbtData;

    private static final Set<String> TAGS = ImmutableSet.of(
            TAG_MODE, TAG_RS, TAG_COLOR + "0", TAG_COLOR + "1",
            TAG_COLOR + "2", TAG_COLOR + "3", TAG_FACING);

    public OCConnectorSettings(@Nonnull EnumFacing side) {
        super(side);
    }

    public XNetOCBridge.BridgeMode getMode() {
        return mode;
    }

    public String getAdapterAddress() {
        return adapterAddress == null ? "" : adapterAddress;
    }

    public void setAdapterAddress(@Nullable String adapterAddress) {
        this.adapterAddress = adapterAddress == null ? "" : adapterAddress;
    }

    public String getAdapterEnvironmentName() {
        return adapterEnvironmentName == null ? "" : adapterEnvironmentName;
    }

    public void setAdapterEnvironmentName(@Nullable String adapterEnvironmentName) {
        this.adapterEnvironmentName = adapterEnvironmentName == null ? "" : adapterEnvironmentName;
    }

    @Nullable
    public NBTTagCompound getAdapterNbtData() {
        return NBTCompat.copy(adapterNbtData);
    }

    public void setAdapterNbtData(@Nullable NBTTagCompound adapterNbtData) {
        this.adapterNbtData = NBTCompat.copy(adapterNbtData);
    }

    public void clearAdapterNbtData() {
        adapterNbtData = null;
        adapterAddress = "";
        adapterEnvironmentName = "";
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        if (mode == XNetOCBridge.BridgeMode.LINK) {
            return new IndicatorIcon(LINK_ICON, 0, 0, 13, 10);
        }
        return new IndicatorIcon(ADAPTER_ICON, 0, 0, 13, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return null;
    }

    @Override
    public boolean isEnabled(String tag) {
        if (tag.equals(TAG_FACING)) {
            return advanced;
        }
        return TAGS.contains(tag);
    }

    @Override
    public void createGui(IEditorGui gui) {
        advanced = gui.isAdvanced();
        sideGui(gui);
        colorsGui(gui);
        redstoneGui(gui);
        gui.nl()
                .choices(TAG_MODE, "Bridge mode", mode.name(),
                        XNetOCBridge.BridgeMode.LINK.name(),
                        XNetOCBridge.BridgeMode.ADAPTER.name());
    }

    @Override
    public void update(Map<String, Object> data) {
        super.update(data);
        if (data.containsKey(TAG_MODE)) {
            mode = XNetOCBridge.BridgeMode.valueOf(
                    ((String) data.get(TAG_MODE)).toUpperCase());
        }
    }

    @Override
    public JsonObject writeToJson() {
        JsonObject object = new JsonObject();
        super.writeToJsonInternal(object);
        setEnumSafe(object, "bridgemode", mode);
        object.addProperty("adapterAddress", getAdapterAddress());
        object.addProperty("adapterEnvironment", getAdapterEnvironmentName());
        return object;
    }

    @Override
    public void readFromJson(JsonObject object) {
        super.readFromJsonInternal(object);
        mode = getEnumSafe(object, "bridgemode",
                s -> XNetOCBridge.BridgeMode.valueOf(s));
        if (object.has("adapterAddress")) {
            adapterAddress = object.get("adapterAddress").getAsString();
        }
        if (object.has("adapterEnvironment")) {
            adapterEnvironmentName = object.get("adapterEnvironment").getAsString();
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
        super.readFromNBT(tag);
        if (NBTCompat.hasKey(tag, "ocMode2")) {
            mode = XNetOCBridge.BridgeMode.values()[NBTCompat.getByte(tag, "ocMode2")];
        }
        if (NBTCompat.hasKey(tag, "ocAdapterAddr")) {
            adapterAddress = NBTCompat.getString(tag, "ocAdapterAddr");
        }
        if (NBTCompat.hasKey(tag, "ocAdapterEnv")) {
            adapterEnvironmentName = NBTCompat.getString(tag, "ocAdapterEnv");
        }
        if (NBTCompat.hasKey(tag, "ocAdapterNbt")) {
            adapterNbtData = NBTCompat.copy(NBTCompat.getCompoundTag(tag, "ocAdapterNbt"));
        }
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
        super.writeToNBT(tag);
        NBTCompat.setByte(tag, "ocMode2", (byte) mode.ordinal());
        if (adapterAddress != null && !adapterAddress.isEmpty()) {
            NBTCompat.setString(tag, "ocAdapterAddr", adapterAddress);
        }
        if (adapterEnvironmentName != null && !adapterEnvironmentName.isEmpty()) {
            NBTCompat.setString(tag, "ocAdapterEnv", adapterEnvironmentName);
        }
        if (adapterNbtData != null) {
            NBTCompat.setTag(tag, "ocAdapterNbt", NBTCompat.copy(adapterNbtData));
        }
    }
}
