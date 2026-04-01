package com.gtceuterminal.client.gui.factory;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.energy.EnergyDataCollector;
import com.gtceuterminal.common.energy.EnergySnapshot;
import com.gtceuterminal.common.energy.LinkedMachineData;
import com.gtceuterminal.common.item.EnergyAnalyzerItem;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import com.gtceuterminal.common.theme.ItemTheme;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** IMPORTANT:
 * This class must be safe to load on a dedicated server.
 * Do NOT reference net.minecraft.client.* or client-only GUI classes directly.
 * UI creation is done via reflection (only executed on the client).
 */
public class EnergyAnalyzerUIFactory extends UIFactory<EnergyAnalyzerUIFactory.EnergyAnalyzerHolder> {

    public static final ResourceLocation UI_ID = ResourceLocation.fromNamespaceAndPath(
            GTCEUTerminalMod.MOD_ID, "energy_analyzer"
    );

    public static final EnergyAnalyzerUIFactory INSTANCE = new EnergyAnalyzerUIFactory();

    private EnergyAnalyzerUIFactory() {
        super(UI_ID);
    }

    // ─── Open UI ─────────────────────────────────────────────────────────────
    public void openUI(ServerPlayer player, int initialIndex) {
        // Collect snapshots server-side before opening
        ItemStack stack = findAnalyzerItem(player);
        ItemTheme theme = ItemTheme.load(stack);
        List<EnergySnapshot> snapshots = new ArrayList<>();
        List<LinkedMachineData> machines = EnergyAnalyzerItem.loadMachines(stack);

        for (LinkedMachineData m : machines) {
            String dimId = m.getDimensionId();
            ServerLevel targetLevel = null;
            for (ServerLevel sl : player.getServer().getAllLevels()) {
                if (sl.dimension().location().toString().equals(dimId)) {
                    targetLevel = sl;
                    break;
                }
            }
            if (targetLevel != null) {
                snapshots.add(EnergyDataCollector.collect(
                        targetLevel, m.getPos(), m.getCustomName(), m.getControllerBlockKey()));
            } else {
                EnergySnapshot offline = new EnergySnapshot();
                m.applyToSnapshotIdentity(offline);
                offline.mode = EnergySnapshot.MachineMode.UNKNOWN;
                offline.isFormed = false;
                snapshots.add(offline);
            }
        }

        EnergyAnalyzerHolder holder = new EnergyAnalyzerHolder(false, snapshots, machines, initialIndex, theme);
        super.openUI(holder, player);
    }

    // ─── UIFactory impl ───────────────────────────────────────────────────────
    @Override
    protected ModularUI createUITemplate(EnergyAnalyzerHolder holder, Player entityPlayer) {
        holder.attach(entityPlayer);
        try {
            Class<?> uiClass = Class.forName("com.gtceuterminal.client.gui.energy.EnergyAnalyzerUI");
            var m = uiClass.getMethod("create", EnergyAnalyzerHolder.class, Player.class);
            return (ModularUI) m.invoke(null, holder, entityPlayer);
        } catch (Throwable t) {
            GTCEUTerminalMod.LOGGER.error("Failed to create Energy Analyzer UI", t);
            throw new RuntimeException("Failed to create Energy Analyzer UI", t);
        }
    }

    @Override
    protected EnergyAnalyzerHolder readHolderFromSyncData(FriendlyByteBuf buf) {
        GTCEUTerminalMod.LOGGER.info("=== readHolderFromSyncData called");
        int index = buf.readInt();
        int count = buf.readInt();
        List<EnergySnapshot> snapshots = new ArrayList<>();
        for (int i = 0; i < count; i++) snapshots.add(EnergySnapshot.decode(buf));

        int mcount = buf.readInt();
        List<LinkedMachineData> machines = new ArrayList<>();
        for (int i = 0; i < mcount; i++) machines.add(LinkedMachineData.fromNBT(buf.readNbt()));

        net.minecraft.nbt.CompoundTag themeTag = buf.readNbt();
        ItemTheme theme = ItemTheme.fromNBT(themeTag);
        return new EnergyAnalyzerHolder(true, snapshots, machines, index, theme);
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf buf, EnergyAnalyzerHolder holder) {
        buf.writeInt(holder.initialIndex);
        buf.writeInt(holder.snapshots.size());
        for (EnergySnapshot s : holder.snapshots) s.encode(buf);
        buf.writeInt(holder.machines.size());
        for (LinkedMachineData m : holder.machines) buf.writeNbt(m.toNBT());
        buf.writeNbt(holder.theme.toNBT());
    }

    // ─── Helper ───────────────────────────────────────────────────────────────
    private static ItemStack findAnalyzerItem(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack s = player.getInventory().getItem(i);
            if (s.getItem() instanceof EnergyAnalyzerItem) return s;
        }
        return ItemStack.EMPTY;
    }

    // ─── Holder ───────────────────────────────────────────────────────────────
    public static class EnergyAnalyzerHolder implements IUIHolder {
        private final boolean remote;
        private Player player;

        public final List<EnergySnapshot> snapshots;
        public final List<LinkedMachineData> machines;
        public final int initialIndex;
        public final ItemTheme theme;

        public EnergyAnalyzerHolder(boolean remote, List<EnergySnapshot> snapshots,
                                    List<LinkedMachineData> machines, int initialIndex, ItemTheme theme) {
            this.remote = remote;
            this.snapshots = snapshots;
            this.machines = machines;
            this.initialIndex = initialIndex;
            this.theme = theme != null ? theme : new ItemTheme();
        }

        public void attach(Player p) { if (this.player == null) this.player = p; }
        public Player getPlayer() { return player; }

        @Override public ModularUI createUI(Player p) { attach(p); return INSTANCE.createUITemplate(this, p); }
        @Override public boolean isInvalid() { return player != null && player.isRemoved(); }
        @Override public boolean isRemote() { return remote; }
        @Override public void markAsDirty() {}
    }
}