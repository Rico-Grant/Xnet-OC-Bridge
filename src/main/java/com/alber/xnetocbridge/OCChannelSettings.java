package com.alber.xnetocbridge;

import com.google.gson.JsonObject;
import li.cil.oc.api.network.Node;
import mcjty.xnet.api.channels.Color;
import mcjty.xnet.api.channels.IChannelSettings;
import mcjty.xnet.api.channels.IConnectorSettings;
import mcjty.xnet.api.channels.IControllerContext;
import mcjty.xnet.api.gui.IEditorGui;
import mcjty.xnet.api.gui.IndicatorIcon;
import mcjty.xnet.api.keys.SidedConsumer;
import mcjty.xnet.blocks.cables.ConnectorTileEntity;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Channel settings for the OpenComputers XNet channel.
 *
 * The tick method builds one OC hub for the XNet channel, then connects LINK
 * nodes and ADAPTER proxy environments to that hub.
 */
public class OCChannelSettings implements IChannelSettings {

    private static final ResourceLocation ICON_GUI_ELEMENTS =
            new ResourceLocation(XNetOCBridge.MODID, "textures/gui/oc_guielements.png");

    private int lastLinkCount = 0;
    private int lastAdapterCount = 0;
    private boolean lastHadError = false;

    private OCLinkNode hubNode;

    @Override
    public void tick(int channel, IControllerContext context) {
        try {
            tickImpl(channel, context);
        } catch (Exception e) {
            lastHadError = true;
            XNetOCBridge.LOGGER.error("Error in OC channel tick: " + e.getMessage(), e);
        }
    }

    private void tickImpl(int channel, IControllerContext context) {
        World world = context.getControllerWorld();
        if (!(world instanceof WorldServer)) return;

        Map<SidedConsumer, IConnectorSettings> connectors = context.getConnectors(channel);
        if (connectors == null || connectors.isEmpty()) {
            destroyHub();
            lastLinkCount = 0;
            lastAdapterCount = 0;
            lastHadError = false;
            return;
        }

        lastHadError = false;

        long worldTime = MCCompat.getTotalWorldTime(world);
        int dim = XNetOCBridge.getDimensionId(world);

        List<ConnectorEntry> linkEntries = new ArrayList<>();
        List<ConnectorEntry> adapterEntries = new ArrayList<>();

        for (Map.Entry<SidedConsumer, IConnectorSettings> entry : connectors.entrySet()) {
            SidedConsumer sc = entry.getKey();
            IConnectorSettings settings = entry.getValue();

            if (!(settings instanceof OCConnectorSettings)) {
                continue;
            }
            OCConnectorSettings ocSettings = (OCConnectorSettings) settings;

            BlockPos connectorPos = context.findConsumerPosition(sc.getConsumerId());
            if (connectorPos == null) continue;

            if (!checkRedstoneActual(world, connectorPos, ocSettings)) {
                deactivateConnector(dim, sc, connectorPos);
                continue;
            }

            if (!context.matchColor(ocSettings.getColorsMask())) {
                deactivateConnector(dim, sc, connectorPos);
                continue;
            }

            XNetOCBridge.BridgeData data = XNetOCBridge.getOrCreateBridgeData(dim, connectorPos);
            if (data.owningChannel != -1 && data.owningChannel != channel) {
                continue;
            }
            data.owningChannel = channel;
            data.mode = ocSettings.getMode();
            data.active = true;
            data.lastActiveTick = worldTime;

            ConnectorEntry ce = new ConnectorEntry(sc, ocSettings, connectorPos, data);
            if (ocSettings.getMode() == XNetOCBridge.BridgeMode.LINK) {
                linkEntries.add(ce);
            } else {
                adapterEntries.add(ce);
            }
        }

        lastLinkCount = linkEntries.size();
        lastAdapterCount = adapterEntries.size();

        if (linkEntries.isEmpty() && adapterEntries.isEmpty()) {
            destroyHub();
            return;
        }

        ensureHub(world);

        for (ConnectorEntry ce : linkEntries) {
            BridgeManager.ensureLinkNode(world, ce.pos, ce.sc.getSide(), ce.data);
        }

        for (ConnectorEntry ce : linkEntries) {
            if (ce.data.linkNode != null && ce.data.linkNode.node() != null) {
                Node linkNode = ce.data.linkNode.node();
                Node hubNd = hubNode.node();
                try {
                    if (hubNd != null && linkNode.network() != null && !linkNode.isNeighborOf(hubNd)) {
                        linkNode.connect(hubNd);
                    }
                } catch (Exception ignored) {
                }
            }
        }

        for (ConnectorEntry ce : adapterEntries) {
            BridgeManager.ensureAdapterProxy(world, ce.pos, ce.sc.getSide(), ce.data, ce.settings, hubNode, true);
        }
    }

    private void ensureHub(World world) {
        if (hubNode == null || hubNode.node() == null) {
            hubNode = new OCLinkNode(world, new BlockPos(0, 0, 0));
        }
        if (hubNode.node() != null && hubNode.node().network() == null) {
            li.cil.oc.api.Network.joinNewNetwork(hubNode.node());
        }
    }

    private void destroyHub() {
        if (hubNode != null) {
            if (hubNode.node() != null) {
                hubNode.node().remove();
            }
            hubNode = null;
        }
    }

    private boolean checkRedstoneActual(World world, BlockPos pos, OCConnectorSettings settings) {
        switch (settings.getRsMode()) {
            case IGNORED:
                return true;
            case OFF:
                return MCCompat.getRedstonePowerFromNeighbors(world, pos) == 0;
            case ON:
                return MCCompat.getRedstonePowerFromNeighbors(world, pos) > 0;
            case PULSE: {
                TileEntity te = MCCompat.getTileEntity(world, pos);
                if (te instanceof ConnectorTileEntity) {
                    ConnectorTileEntity cte = (ConnectorTileEntity) te;
                    int pulse = cte.getPulseCounter();
                    int prevPulse = settings.getPrevPulse();
                    if (pulse != prevPulse) {
                        settings.setPrevPulse(pulse);
                        return true;
                    }
                }
                return false;
            }
            default:
                return true;
        }
    }

    private void deactivateConnector(int dim, SidedConsumer sc, BlockPos pos) {
        XNetOCBridge.BridgeData data = XNetOCBridge.getBridgeData(dim, pos);
        if (data != null) {
            data.active = false;
            data.owningChannel = -1;
        }
    }

    @Override
    public void cleanCache() {
    }

    @Override
    public int getColors() {
        return 1 << Color.GRAY.ordinal();
    }

    @Nullable
    @Override
    public IndicatorIcon getIndicatorIcon() {
        return new IndicatorIcon(ICON_GUI_ELEMENTS, 0, 0, 11, 10);
    }

    @Nullable
    @Override
    public String getIndicator() {
        return lastHadError ? "!" : null;
    }

    @Override
    public boolean isEnabled(String tag) {
        return true;
    }

    @Override
    public void createGui(IEditorGui gui) {
        gui.label("OC");
    }

    @Override
    public void update(Map<String, Object> data) {
    }

    @Override
    public void readFromNBT(NBTTagCompound tag) {
    }

    @Override
    public void writeToNBT(NBTTagCompound tag) {
    }

    @Override
    public JsonObject writeToJson() {
        return new JsonObject();
    }

    @Override
    public void readFromJson(JsonObject data) {
    }

    private static class ConnectorEntry {
        final SidedConsumer sc;
        final OCConnectorSettings settings;
        final BlockPos pos;
        final XNetOCBridge.BridgeData data;

        ConnectorEntry(SidedConsumer sc, OCConnectorSettings settings, BlockPos pos, XNetOCBridge.BridgeData data) {
            this.sc = sc;
            this.settings = settings;
            this.pos = pos;
            this.data = data;
        }
    }
}
