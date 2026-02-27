package com.gtceuterminal.client.gui.energy;

import com.gtceuterminal.client.gui.factory.EnergyAnalyzerUIFactory;
import com.gtceuterminal.common.config.ItemsConfig;
import com.gtceuterminal.common.energy.EnergyDataCollector;
import com.gtceuterminal.common.energy.EnergySnapshot;
import com.gtceuterminal.common.energy.LinkedMachineData;

import com.lowdragmc.lowdraglib.gui.widget.Widget;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Widget responsible for periodically collecting energy data on server and sending to client
public class EnergyUpdateWidget extends Widget {

    private static final int UPDATE_ID = 1;

    private final EnergyAnalyzerUIFactory.EnergyAnalyzerHolder holder;
    private int tickCounter = 0;

    private Runnable rebuildCallback;

    public EnergyUpdateWidget(EnergyAnalyzerUIFactory.EnergyAnalyzerHolder holder) {
        super(0, 0, 0, 0);
        this.holder = holder;
    }

    public void setRebuildCallback(Runnable callback) {
        this.rebuildCallback = callback;
    }

    // ─── Server side ──────────────────────────────────────────────────────────
    @Override
    public void detectAndSendChanges() {
        tickCounter++;
        int interval = ItemsConfig.getEARefreshIntervalTicks();
        if (tickCounter < interval) return;
        tickCounter = 0;

        if (gui == null) return;
        ServerPlayer player = (ServerPlayer) gui.entityPlayer;
        if (player == null) return;

        List<EnergySnapshot> snapshots = collectSnapshots(player);

        writeUpdateInfo(UPDATE_ID, buf -> {
            buf.writeInt(snapshots.size());
            for (EnergySnapshot s : snapshots) s.encode(buf);
        });
    }

    private List<EnergySnapshot> collectSnapshots(ServerPlayer player) {
        List<EnergySnapshot> result = new ArrayList<>();
        List<LinkedMachineData> machines = holder.machines;

        for (LinkedMachineData m : machines) {
            ServerLevel targetLevel = null;
            for (ServerLevel sl : player.getServer().getAllLevels()) {
                if (sl.dimension().location().toString().equals(m.getDimensionId())) {
                    targetLevel = sl;
                    break;
                }
            }

            if (targetLevel != null) {
                result.add(EnergyDataCollector.collect(
                        targetLevel, m.getPos(), m.getCustomName(), m.getMachineType()));
            } else {
                EnergySnapshot offline = new EnergySnapshot();
                offline.machineName = m.getDisplayName();
                offline.machineType = m.getMachineType();
                offline.mode = EnergySnapshot.MachineMode.UNKNOWN;
                offline.isFormed = false;
                result.add(offline);
            }
        }
        return result;
    }

    // ─── Client side ──────────────────────────────────────────────────────────
    @Override
    public void readUpdateInfo(int id, FriendlyByteBuf buffer) {
        if (id != UPDATE_ID) return;

        int count = buffer.readInt();
        List<EnergySnapshot> fresh = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            fresh.add(EnergySnapshot.decode(buffer));
        }

        // Replace snapshots in the holder
        holder.snapshots.clear();
        holder.snapshots.addAll(fresh);

        // Trigger UI rebuild on client
        if (rebuildCallback != null) {
            rebuildCallback.run();
        }
    }
}