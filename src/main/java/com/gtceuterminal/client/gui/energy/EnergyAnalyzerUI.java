package com.gtceuterminal.client.gui.energy;

import com.gtceuterminal.client.gui.factory.EnergyAnalyzerUIFactory;
import com.gtceuterminal.common.energy.EnergySnapshot;
import com.gtceuterminal.common.energy.LinkedMachineData;
import com.gtceuterminal.common.energy.RecipeHistoryEntry;
import com.gtceuterminal.client.gui.widget.EnergyGraphWidget;
import com.gtceuterminal.common.network.CPacketEnergyAnalyzerAction;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.editor.ColorPattern;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.math.BigInteger;
import java.util.List;

// UI for the Energy Analyzer machine. Displays energy stats for linked machines, with a sidebar to select between them.
public class EnergyAnalyzerUI {

    private static final int W         = 520;
    private static final int H         = 380;
    private static final int SIDEBAR_W = 148;
    private static final int DETAIL_X  = SIDEBAR_W + 6;
    private static final int DETAIL_W  = W - DETAIL_X - 6;
    private static final int HEADER_H  = 26;
    private static final int GRAPH_W   = DETAIL_W - 68;
    private static final int GRAPH_H   = 80;
    private static final int PAD       = 6;
    private static final int ITEM_H    = 36;

    private static final int C_BG     = 0xFF1A1A1A;
    private static final int C_PANEL  = 0xFF252525;
    private static final int C_SEL    = 0xFF333333;
    private static final int C_BORDER = 0xFF444444;
    private static final int C_WHITE  = 0xFFFFFFFF;
    private static final int C_GRAY   = 0xFFAAAAAA;
    private static final int C_GOLD   = 0xFFFFAA00;
    private static final int C_GREEN  = 0xFF55FF55;
    private static final int C_RED    = 0xFFFF5555;
    private static final int C_BLUE   = 0xFF5599FF;
    private static final int C_ORANGE = 0xFFFF8800;

    private final EnergyAnalyzerUIFactory.EnergyAnalyzerHolder holder;
    private final Player player;
    private int selectedIndex;

    // Mutable widget refs updated in-place
    private EnergyGraphWidget graphWidget;
    private WidgetGroup energyBarFill;
    private WidgetGroup recipeBarFill;
    private WidgetGroup recipeBarBg;
    private ButtonWidget recipeBtn;
    private WidgetGroup rootGroup;
    // Sidebar selection highlights, one per machine
    private WidgetGroup[] sidebarSelects;

    private EnergyAnalyzerUI(EnergyAnalyzerUIFactory.EnergyAnalyzerHolder holder, Player player) {
        this.holder        = holder;
        this.player        = player;
        this.selectedIndex = holder.initialIndex;
    }

    public static ModularUI create(EnergyAnalyzerUIFactory.EnergyAnalyzerHolder holder, Player player) {
        return new EnergyAnalyzerUI(holder, player).buildUI();
    }

    private ModularUI buildUI() {
        rootGroup = new WidgetGroup(0, 0, W, H);
        rootGroup.setBackground(new ColorRectTexture(C_BG));
        rootGroup.addWidget(buildHeader());

        WidgetGroup div = new WidgetGroup(SIDEBAR_W + 2, HEADER_H, 2, H - HEADER_H);
        div.setBackground(new ColorRectTexture(C_BORDER));
        rootGroup.addWidget(div);

        rootGroup.addWidget(buildSidebar());
        rootGroup.addWidget(buildDetail());

        EnergyUpdateWidget updater = new EnergyUpdateWidget(holder);
        updater.setRebuildCallback(this::onDataRefresh);
        rootGroup.addWidget(updater);

        return wrapUI(rootGroup);
    }

    // ─── Header ───────────────────────────────────────────────────────────────
    private WidgetGroup buildHeader() {
        WidgetGroup g = new WidgetGroup(0, 0, W, HEADER_H);
        g.setBackground(new ColorRectTexture(C_PANEL));
        LabelWidget title = new LabelWidget(PAD, 8, "Energy Analyzer");
        title.setTextColor(C_GOLD);
        g.addWidget(title);
        int count = holder.machines.size();
        LabelWidget sub = new LabelWidget(W - 130, 8,
                count + " machine" + (count == 1 ? "" : "s") + " linked");
        sub.setTextColor(C_GRAY);
        g.addWidget(sub);
        return g;
    }

    // ─── Sidebar ──────────────────────────────────────────────────────────────
    private WidgetGroup buildSidebar() {
        WidgetGroup g = new WidgetGroup(0, HEADER_H, SIDEBAR_W + 2, H - HEADER_H);
        g.setBackground(new ColorRectTexture(C_PANEL));

        List<LinkedMachineData> machines = holder.machines;
        if (machines.isEmpty()) {
            LabelWidget lbl = new LabelWidget(PAD, 16, "No machines linked.");
            lbl.setTextColor(C_GRAY);
            g.addWidget(lbl);
            return g;
        }

        sidebarSelects = new WidgetGroup[machines.size()];

        for (int i = 0; i < machines.size(); i++) {
            final int idx = i;
            LinkedMachineData m = machines.get(i);
            int y = PAD + idx * (ITEM_H + 2);

            // Selection highlight
            sidebarSelects[i] = new WidgetGroup(2, y, SIDEBAR_W - 4, ITEM_H);
            sidebarSelects[i].setBackground(new ColorRectTexture(C_SEL));
            sidebarSelects[i].setVisible(idx == selectedIndex);
            g.addWidget(sidebarSelects[i]);

            // Status dot
            var dot = new WidgetGroup(PAD + 2, y + 5, 7, 7);
            dot.setBackground(new ColorRectTexture(statusColor(snapAt(idx))));
            g.addWidget(dot);

            LabelWidget name = new LabelWidget(PAD + 12, y + 4,
                    () -> truncate(m.getDisplayName(), 14));
            name.setClientSideWidget();
            name.setTextColor(C_GRAY);
            g.addWidget(name);

            LabelWidget rate = new LabelWidget(PAD + 12, y + 18, () -> {
                EnergySnapshot s = snapAt(idx);
                return (s != null && s.isFormed)
                        ? formatEU(s.inputPerSec / 20) + "/t" : "---";
            });
            rate.setClientSideWidget();
            rate.setTextColor(C_GRAY);
            g.addWidget(rate);

            // Main click area (left portion) selects the machine
            ButtonWidget btn = new ButtonWidget(2, y, SIDEBAR_W - 22, ITEM_H,
                    new ColorRectTexture(0x00000000), cd -> selectMachine(idx));
            g.addWidget(btn);

            // ⚙ options button — right side of each sidebar item
            ButtonWidget gearBtn = new ButtonWidget(SIDEBAR_W - 20, y + 8, 16, 16,
                    new ColorRectTexture(0x00000000),
                    cd -> openMachineOptionsDialog(idx));
            gearBtn.setButtonTexture(
                    new TextTexture("§7⚙").setWidth(16).setType(TextTexture.TextType.NORMAL));
            gearBtn.setHoverTexture(new ColorRectTexture(0x33FFFFFF));
            g.addWidget(gearBtn);
        }
        return g;
    }

    // ─── Detail panel ─────────────────────────────────────────────────────────
    private WidgetGroup buildDetail() {
        WidgetGroup g = new WidgetGroup(DETAIL_X, HEADER_H, DETAIL_W, H - HEADER_H);
        int y = PAD;

        // Machine name
        LabelWidget title = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap(); return s != null ? s.machineName : "";
        });
        title.setClientSideWidget();
        title.setTextColor(C_WHITE);
        g.addWidget(title); y += 14;

        // Mode badge
        LabelWidget badge = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap(); return s != null ? modeBadge(s) : "";
        });
        badge.setClientSideWidget();
        badge.setTextColor(C_BLUE);
        g.addWidget(badge); y += 16;

        // Storage label
        LabelWidget storedLbl = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            return (s != null && s.isFormed) ? "Storage: " + storedStr(s) : "";
        });
        storedLbl.setClientSideWidget();
        storedLbl.setTextColor(C_WHITE);
        g.addWidget(storedLbl); y += 13;

        // Energy bar
        WidgetGroup barBg = new WidgetGroup(0, y, DETAIL_W - 4, 8);
        barBg.setBackground(new ColorRectTexture(0xFF333333));
        g.addWidget(barBg);

        energyBarFill = new WidgetGroup(0, y, 0, 8);
        energyBarFill.setBackground(new ColorRectTexture(C_GREEN));
        g.addWidget(energyBarFill); y += 10;

        // Low buffer warning
        LabelWidget lowBuf = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            return (s != null && s.isFormed && s.chargePercent() < 0.10f
                    && s.energyCapacity > 0) ? "Low buffer!" : "";
        });
        lowBuf.setClientSideWidget();
        lowBuf.setTextColor(C_RED);
        g.addWidget(lowBuf); y += 12;

        // ETA
        LabelWidget etaLbl = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isFormed) return "";
            long secs = s.secondsUntilChange();
            if (secs < 0 || secs >= 86400) return "";
            return (s.netPerSec() > 0 ? "Full in " : "Empty in ") + formatTime(secs);
        });
        etaLbl.setClientSideWidget();
        etaLbl.setTextColor(C_GRAY);
        g.addWidget(etaLbl); y += 12;

        // In / Out / Net / Voltage
        LabelWidget inL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isFormed) return "";
            return "In:  " + formatEU(s.inputPerSec / 20) + "/t  ("
                    + formatEU(s.inputPerSec) + "/s)";
        });
        inL.setClientSideWidget();
        inL.setTextColor(C_GREEN);
        g.addWidget(inL); y += 12;

        LabelWidget outL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isFormed) return "";
            return "Out: " + formatEU(s.outputPerSec / 20) + "/t  ("
                    + formatEU(s.outputPerSec) + "/s)";
        });
        outL.setClientSideWidget();
        outL.setTextColor(C_RED);
        g.addWidget(outL); y += 12;

        LabelWidget netL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isFormed) return "";
            long net = s.netPerSec();
            return "Net: " + (net >= 0 ? "+" : "") + formatEU(net) + "/s";
        });
        netL.setClientSideWidget();
        netL.setTextColor(C_GRAY);
        g.addWidget(netL); y += 12;

        LabelWidget volL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isFormed || s.inputVoltage <= 0) return "";
            return "Voltage: " + getVoltageTier(s.inputVoltage) + " ("
                    + s.inputVoltage + "V)  " + s.inputAmperage + "A max";
        });
        volL.setClientSideWidget();
        volL.setTextColor(C_BLUE);
        g.addWidget(volL); y += 14;

        // Recipe section — only shown when a recipe is actively running.
        // The button opens the recipe log. Name, progress bar and % are hidden when idle.
        recipeBtn = new ButtonWidget(0, y, 95, 10,
                new ColorRectTexture(0x00000000),
                cd -> { EnergySnapshot s = selSnap(); if (s != null) openRecipeHistoryDialog(s); });
        recipeBtn.setButtonTexture(new TextTexture("§6Recipe: §7[log ▶]")
                .setWidth(95).setType(TextTexture.TextType.LEFT));
        recipeBtn.setHoverTexture(new ColorRectTexture(0x22FFFFFF));
        // Visible only when a recipe is actively running
        recipeBtn.setVisible(false); // updated in updateDynamicWidgets
        g.addWidget(recipeBtn); y += 12;

        LabelWidget recipeNameL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isRecipeActive || s.recipeId.isEmpty()) return "";
            return truncate(s.recipeId, 38);
        });
        recipeNameL.setClientSideWidget();
        recipeNameL.setTextColor(C_WHITE);
        g.addWidget(recipeNameL); y += 12;

        // Recipe bar (background always present to hold the space, fill driven by data)
        recipeBarBg = new WidgetGroup(0, y, GRAPH_W, 6);
        recipeBarBg.setBackground(new ColorRectTexture(0x00000000));
        g.addWidget(recipeBarBg);

        recipeBarFill = new WidgetGroup(0, y, 0, 6);
        recipeBarFill.setBackground(new ColorRectTexture(0xFF4488FF));
        g.addWidget(recipeBarFill); y += 9;

        LabelWidget recipePctL = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || !s.isRecipeActive || s.recipeDuration == 0) return "";
            int pct       = (int) (s.recipeProgress * 100);
            int secsLeft  = Math.max(0, (s.recipeDuration - s.recipeProgressTicks) / 20);
            int secsDone  = s.recipeProgressTicks / 20;
            int secsTotal = s.recipeDuration / 20;
            String timeStr = secsLeft > 0 ? formatTime(secsLeft) + " left" : "finishing...";
            return pct + "%  " + timeStr + "  (" + secsDone + "s / " + secsTotal + "s)";
        });
        recipePctL.setClientSideWidget();
        recipePctL.setTextColor(C_GRAY);
        g.addWidget(recipePctL); y += 14;

        // Hatch table — capped at MAX_HATCHES rows so the graph always fits.
        // The graph is pinned to an absolute Y position from the bottom of the panel,
        // so it never overflows regardless of how many hatches are shown.
        final int MAX_HATCHES = 5;
        LabelWidget hatchHdr = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            return (s != null && !s.hatches.isEmpty()) ? "Hatches:" : "";
        });
        hatchHdr.setClientSideWidget();
        hatchHdr.setTextColor(C_GOLD);
        g.addWidget(hatchHdr); y += 12;

        for (int h = 0; h < MAX_HATCHES; h++) {
            final int hi = h;
            LabelWidget hatchL = new LabelWidget(0, y, () -> {
                EnergySnapshot s = selSnap();
                if (s == null || hi >= s.hatches.size()) return "";
                EnergySnapshot.HatchInfo hatch = s.hatches.get(hi);
                return "  " + (hatch.isInput() ? "IN " : "OUT") + "  "
                        + hatch.name() + "  " + hatch.voltage() + "V";
            });
            hatchL.setClientSideWidget();
            hatchL.setTextColor(C_GREEN);
            g.addWidget(hatchL); y += 11;
        }

        // "+N more" overflow indicator
        LabelWidget moreHatches = new LabelWidget(0, y, () -> {
            EnergySnapshot s = selSnap();
            if (s == null || s.hatches.size() <= MAX_HATCHES) return "";
            return "  §8+ " + (s.hatches.size() - MAX_HATCHES) + " more...";
        });
        moreHatches.setClientSideWidget();
        moreHatches.setTextColor(C_GRAY);
        g.addWidget(moreHatches);

        // ── History graph — pinned to absolute Y from the bottom of the panel ──────
        final int GRAPH_BLOCK_H = 13 + GRAPH_H + 12;
        final int GRAPH_SECTION_Y = (H - HEADER_H) - GRAPH_BLOCK_H - PAD;
        final int GRAPH_Y = GRAPH_SECTION_Y + 13;

        LabelWidget graphHdr = new LabelWidget(0, GRAPH_SECTION_Y, "History (EU/s):");
        graphHdr.setTextColor(C_GOLD);
        g.addWidget(graphHdr);

        graphWidget = new EnergyGraphWidget(0, GRAPH_Y, GRAPH_W, GRAPH_H, new long[0], new long[0]);
        g.addWidget(graphWidget);

        // Y-axis labels (right of graph)
        for (int i = 0; i <= 4; i++) {
            final int ti = i;
            LabelWidget axisL = new LabelWidget(GRAPH_W + 3, GRAPH_Y + (GRAPH_H * i) / 4 - 4, () -> {
                EnergySnapshot s = selSnap();
                if (s == null) return "";
                long max = 1;
                for (long v : s.inputHistory)  max = Math.max(max, v);
                for (long v : s.outputHistory) max = Math.max(max, v);
                long maxEuT = max / 20;
                long val = maxEuT - ((maxEuT * ti) / 4);
                return formatEU(val) + "/t";
            });
            axisL.setClientSideWidget();
            axisL.setTextColor(0xFF666666);
            g.addWidget(axisL);
        }

        LabelWidget legIn = new LabelWidget(0, GRAPH_Y + GRAPH_H + 5, "— In");
        legIn.setTextColor(0xFF00CC55);
        g.addWidget(legIn);
        LabelWidget legOut = new LabelWidget(34, GRAPH_Y + GRAPH_H + 5, "— Out");
        legOut.setTextColor(0xFFDD3333);
        g.addWidget(legOut);

        return g;
    }

    // ─── Update logic ──────────────────────────────────────────────────────────
    private void onDataRefresh() {
        updateDynamicWidgets();
    }

    private void selectMachine(int idx) {
        if (idx == selectedIndex) return;
        selectedIndex = idx;
        // Update sidebar highlights
        if (sidebarSelects != null) {
            for (int i = 0; i < sidebarSelects.length; i++) {
                sidebarSelects[i].setVisible(i == selectedIndex);
            }
        }
        updateDynamicWidgets();
    }

    private void updateDynamicWidgets() {
        EnergySnapshot snap = selSnap();

        // Energy bar fill
        if (energyBarFill != null) {
            float pct = snap != null ? snap.chargePercent() : 0f;
            int fw = (int) ((DETAIL_W - 4) * Math.min(1f, Math.max(0f, pct)));
            energyBarFill.setSize(new Size(Math.max(fw, 0), 8));
            if (snap != null) {
                int fc = pct < 0.1f ? C_RED : pct < 0.25f ? C_ORANGE : C_GREEN;
                energyBarFill.setBackground(new ColorRectTexture(fc));
            }
        }

        // Recipe section — show only when a recipe is actively running
        boolean recipeActive = snap != null && snap.isRecipeActive;
        if (recipeBtn != null) {
            recipeBtn.setVisible(recipeActive);
        }
        if (recipeBarBg != null) {
            recipeBarBg.setBackground(new ColorRectTexture(recipeActive ? 0xFF333333 : 0x00000000));
        }
        // Recipe bar fill
        if (recipeBarFill != null && recipeActive) {
            int rw = (int) (GRAPH_W * Math.min(1f, Math.max(0f, snap.recipeProgress)));
            recipeBarFill.setSize(new Size(Math.max(rw, 0), 6));
        } else if (recipeBarFill != null) {
            recipeBarFill.setSize(new Size(0, 6));
        }

        // Graph data
        if (graphWidget != null) {
            graphWidget.updateData(
                    snap != null ? snap.inputHistory  : new long[0],
                    snap != null ? snap.outputHistory : new long[0]);
        }
    }

    // ─── Machine options dialog ───────────────────────────────────────────────
    private void openMachineOptionsDialog(int machineIdx) {
        if (machineIdx < 0 || machineIdx >= holder.machines.size()) return;
        LinkedMachineData m = holder.machines.get(machineIdx);

        final int DW = 220;
        final int DH = 110;
        final int DX = (W - DW) / 2;
        final int DY = (H - DH) / 2;

        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xA0000000));
        dialog.setClickClose(true);

        WidgetGroup panel = new WidgetGroup(DX, DY, DW, DH);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(0xFF1E1E1E),
                ColorPattern.GRAY.borderTexture(-1)));
        dialog.addWidget(panel);

        // Title bar
        WidgetGroup titleBar = new WidgetGroup(0, 0, DW, 18);
        titleBar.setBackground(new ColorRectTexture(0xFF2A2A2A));
        panel.addWidget(titleBar);

        LabelWidget titleLbl = new LabelWidget(8, 4, "§6Machine Options");
        titleLbl.setTextColor(C_GOLD);
        titleBar.addWidget(titleLbl);

        ButtonWidget closeBtn = new ButtonWidget(DW - 18, 1, 16, 16,
                new ColorRectTexture(0x80FF0000), cd -> dialog.close());
        closeBtn.setButtonTexture(
                new TextTexture("§fX").setWidth(16).setType(TextTexture.TextType.NORMAL));
        closeBtn.setHoverTexture(new ColorRectTexture(0xFFFF3333));
        titleBar.addWidget(closeBtn);

        // Machine name subtitle
        LabelWidget nameLbl = new LabelWidget(8, 22,
                "§7" + truncate(m.getDisplayName(), 28));
        nameLbl.setTextColor(C_GRAY);
        panel.addWidget(nameLbl);

        // ── Rename button ──────────────────────────────────────────────────────
        ButtonWidget renameBtn = new ButtonWidget(8, 38, DW - 16, 18,
                new ColorRectTexture(0xFF333333),
                cd -> {
                    dialog.close();
                    openRenameDialog(machineIdx, m);
                });
        renameBtn.setButtonTexture(
                new TextTexture("§e✎  Rename").setWidth(DW - 16).setType(TextTexture.TextType.LEFT));
        renameBtn.setHoverTexture(new ColorRectTexture(0xFF3A3A1A));
        panel.addWidget(renameBtn);

        // ── Unlink button ──────────────────────────────────────────────────────
        ButtonWidget unlinkBtn = new ButtonWidget(8, 60, DW - 16, 18,
                new ColorRectTexture(0xFF333333),
                cd -> {
                    dialog.close();
                    openUnlinkConfirmDialog(machineIdx, m);
                });
        unlinkBtn.setButtonTexture(
                new TextTexture("§c✖  Unlink machine").setWidth(DW - 16).setType(TextTexture.TextType.LEFT));
        unlinkBtn.setHoverTexture(new ColorRectTexture(0xFF3A1515));
        panel.addWidget(unlinkBtn);

        // ── Cancel button ──────────────────────────────────────────────────────
        ButtonWidget cancelBtn = new ButtonWidget(8, 84, DW - 16, 18,
                new ColorRectTexture(0xFF2A2A2A),
                cd -> dialog.close());
        cancelBtn.setButtonTexture(
                new TextTexture("§7Cancel").setWidth(DW - 16).setType(TextTexture.TextType.NORMAL));
        cancelBtn.setHoverTexture(new ColorRectTexture(0xFF333333));
        panel.addWidget(cancelBtn);
    }


    // Opens a rename sub-dialog with a text field.
    private void openRenameDialog(int machineIdx, LinkedMachineData m) {
        final int DW = 240;
        final int DH = 80;
        final int DX = (W - DW) / 2;
        final int DY = (H - DH) / 2;

        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xA0000000));
        dialog.setClickClose(false); // keep open while typing

        WidgetGroup panel = new WidgetGroup(DX, DY, DW, DH);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(0xFF1E1E1E),
                ColorPattern.GRAY.borderTexture(-1)));
        dialog.addWidget(panel);

        // Title
        WidgetGroup titleBar = new WidgetGroup(0, 0, DW, 18);
        titleBar.setBackground(new ColorRectTexture(0xFF2A2A2A));
        panel.addWidget(titleBar);
        LabelWidget titleLbl = new LabelWidget(8, 4, "§6Rename Machine");
        titleLbl.setTextColor(C_GOLD);
        titleBar.addWidget(titleLbl);

        // Text input
        TextFieldWidget textField = new TextFieldWidget(8, 22, DW - 16, 16, null, s -> {});
        textField.setMaxStringLength(32);
        textField.setBordered(true);
        textField.setCurrentString(m.getCustomName()); // pre-fill with existing name (one-time, not bound)
        panel.addWidget(textField);

        // Confirm button
        ButtonWidget confirmBtn = new ButtonWidget(8, 44, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF1A4A1A),
                cd -> {
                    String name = textField.getCurrentString().trim();
                    TerminalNetwork.CHANNEL.sendToServer(
                            new CPacketEnergyAnalyzerAction(
                                    CPacketEnergyAnalyzerAction.Action.RENAME,
                                    machineIdx, name));
                    dialog.close();
                });
        confirmBtn.setButtonTexture(
                new TextTexture("§aConfirm").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        confirmBtn.setHoverTexture(new ColorRectTexture(0xFF1E6A1E));
        panel.addWidget(confirmBtn);

        // Cancel button
        ButtonWidget cancelBtn = new ButtonWidget(DW / 2 + 2, 44, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF2A2A2A),
                cd -> dialog.close());
        cancelBtn.setButtonTexture(
                new TextTexture("§7Cancel").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        cancelBtn.setHoverTexture(new ColorRectTexture(0xFF333333));
        panel.addWidget(cancelBtn);
    }


    // pens an unlink confirmation dialog.
    private void openUnlinkConfirmDialog(int machineIdx, LinkedMachineData m) {
        final int DW = 220;
        final int DH = 90;
        final int DX = (W - DW) / 2;
        final int DY = (H - DH) / 2;

        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xA0000000));
        dialog.setClickClose(true);

        WidgetGroup panel = new WidgetGroup(DX, DY, DW, DH);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(0xFF1E1E1E),
                ColorPattern.GRAY.borderTexture(-1)));
        dialog.addWidget(panel);

        // Title
        WidgetGroup titleBar = new WidgetGroup(0, 0, DW, 18);
        titleBar.setBackground(new ColorRectTexture(0xFF2A0000));
        panel.addWidget(titleBar);
        LabelWidget titleLbl = new LabelWidget(8, 4, "§cUnlink Machine");
        titleLbl.setTextColor(C_RED);
        titleBar.addWidget(titleLbl);

        // Confirm message
        LabelWidget confirmLbl = new LabelWidget(8, 22,
                "§7Remove §f" + truncate(m.getDisplayName(), 22) + "§7?");
        confirmLbl.setTextColor(C_GRAY);
        panel.addWidget(confirmLbl);

        // Unlink confirm
        ButtonWidget unlinkBtn = new ButtonWidget(8, 40, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF4A1A1A),
                cd -> {
                    TerminalNetwork.CHANNEL.sendToServer(
                            new CPacketEnergyAnalyzerAction(
                                    CPacketEnergyAnalyzerAction.Action.UNLINK,
                                    machineIdx, ""));
                    // Close the UI entirely so the stale machine list doesn't show
                    if (player instanceof net.minecraft.client.player.LocalPlayer lp) {
                        lp.closeContainer();
                    }
                });
        unlinkBtn.setButtonTexture(
                new TextTexture("§cUnlink").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        unlinkBtn.setHoverTexture(new ColorRectTexture(0xFF6A2A2A));
        panel.addWidget(unlinkBtn);

        // Cancel
        ButtonWidget cancelBtn = new ButtonWidget(DW / 2 + 2, 40, (DW - 20) / 2, 18,
                new ColorRectTexture(0xFF2A2A2A),
                cd -> dialog.close());
        cancelBtn.setButtonTexture(
                new TextTexture("§7Cancel").setWidth((DW - 20) / 2).setType(TextTexture.TextType.NORMAL));
        cancelBtn.setHoverTexture(new ColorRectTexture(0xFF333333));
        panel.addWidget(cancelBtn);

        LabelWidget hint = new LabelWidget(DW / 2 - 50, DH - 12, "§8Click outside to cancel");
        hint.setTextColor(0xFF444444);
        panel.addWidget(hint);
    }

    // ─── Recipe history dialog ─────────────────────────────────────────────────
    private void openRecipeHistoryDialog(EnergySnapshot snap) {
        DialogWidget dialog = new DialogWidget(rootGroup, true);
        dialog.setBackground(new ColorRectTexture(0xA0000000));
        dialog.setClickClose(true);

        int entries = snap.recipeHistory.size();
        int dW = 340;
        int dH = Math.min(300, 55 + Math.max(1, entries) * 14 + 20);
        int dX = (W - dW) / 2;
        int dY = (H - dH) / 2;

        WidgetGroup panel = new WidgetGroup(dX, dY, dW, dH);
        panel.setBackground(new GuiTextureGroup(
                new ColorRectTexture(0xFF1E1E1E),
                ColorPattern.GRAY.borderTexture(-1)));
        dialog.addWidget(panel);

        WidgetGroup titleBar = new WidgetGroup(0, 0, dW, 18);
        titleBar.setBackground(new ColorRectTexture(0xFF2D2D2D));
        panel.addWidget(titleBar);
        LabelWidget titleLbl = new LabelWidget(8, 4, "§6Recipe Log  §7— " + snap.machineName);
        titleLbl.setTextColor(C_GOLD);
        titleBar.addWidget(titleLbl);

        ButtonWidget closeBtn = new ButtonWidget(dW - 18, 1, 16, 16,
                new ColorRectTexture(0x80FF0000), cd -> dialog.close());
        closeBtn.setButtonTexture(
                new TextTexture("§fX").setWidth(16).setType(TextTexture.TextType.NORMAL));
        closeBtn.setHoverTexture(new ColorRectTexture(0xFFFF3333));
        panel.addWidget(closeBtn);

        int cy = 22;
        // Column headers
        panel.addWidget(mkLabel(18,  cy, "§7Output",   0xFF888888));
        panel.addWidget(mkLabel(210, cy, "§7Time",     0xFF888888));
        panel.addWidget(mkLabel(275, cy, "§7When",     0xFF888888));
        cy += 10;
        var sep = new WidgetGroup(0, cy, dW, 1);
        sep.setBackground(new ColorRectTexture(0xFF333333));
        panel.addWidget(sep);
        cy += 4;

        if (snap.recipeHistory.isEmpty()) {
            panel.addWidget(mkLabel(10, cy, "§7No recipes recorded yet.", C_GRAY));
        } else {
            List<RecipeHistoryEntry> list = snap.recipeHistory;
            int shown = Math.min(list.size(), 17);
            for (int i = list.size() - 1; i >= list.size() - shown; i--) {
                RecipeHistoryEntry e = list.get(i);

                var dot = new WidgetGroup(8, cy + 3, 6, 6);
                dot.setBackground(new ColorRectTexture(e.completed ? 0xFF00CC55 : 0xFFDD3333));
                panel.addWidget(dot);

                panel.addWidget(mkLabel(18,  cy, truncate(e.outputName, 22),
                        e.completed ? C_WHITE : 0xFF888888));
                panel.addWidget(mkLabel(210, cy, "§7" + e.durationStr(), 0xFF888888));

                int ago = e.secondsAgo();
                String agoStr = ago < 60 ? ago + "s" : (ago / 60) + "m " + (ago % 60) + "s";
                panel.addWidget(mkLabel(275, cy, "§8" + agoStr, 0xFF555555));
                cy += 13;
            }
        }

        LabelWidget hint = new LabelWidget(dW / 2 - 50, dH - 14, "§8Click outside to close");
        hint.setTextColor(0xFF444444);
        panel.addWidget(hint);
    }

    private LabelWidget mkLabel(int x, int y, String text, int color) {
        LabelWidget l = new LabelWidget(x, y, text);
        l.setTextColor(color);
        return l;
    }

    /** Creates a client-side LabelWidget with a dynamic supplier. Must be client-side
     *  so the supplier is evaluated in updateScreen() each frame, not sent via packet. */
    private LabelWidget dynLabel(int x, int y, java.util.function.Supplier<String> sup, int color) {
        LabelWidget l = new LabelWidget(x, y, sup);
        l.setClientSideWidget();
        l.setTextColor(color);
        return l;
    }

    // ─── Snapshot helpers ──────────────────────────────────────────────────────
    private EnergySnapshot selSnap() {
        List<EnergySnapshot> snaps = holder.snapshots;
        if (snaps.isEmpty() || selectedIndex >= snaps.size()) return null;
        return snaps.get(selectedIndex);
    }

    private EnergySnapshot snapAt(int idx) {
        List<EnergySnapshot> snaps = holder.snapshots;
        return (idx < snaps.size()) ? snaps.get(idx) : null;
    }

    // ─── String / display helpers ──────────────────────────────────────────────
    private String storedStr(EnergySnapshot s) {
        if (s.usesBigInt)
            return formatBigEU(s.bigStored) + " / " + formatBigEU(s.bigCapacity);
        return formatEU(s.energyStored) + " / " + formatEU(s.energyCapacity);
    }

    private static String formatEU(long eu) {
        if (eu >= 1_000_000_000L) return String.format("%.2fGEU", eu / 1_000_000_000.0);
        if (eu >= 1_000_000L)     return String.format("%.2fMEU", eu / 1_000_000.0);
        if (eu >= 1_000L)         return String.format("%.2fkEU", eu / 1_000.0);
        return eu + " EU";
    }

    private static String formatBigEU(BigInteger eu) {
        if (eu == null) return "?";
        BigInteger ZETTA = BigInteger.TEN.pow(21);
        BigInteger EXA   = BigInteger.TEN.pow(18);
        BigInteger PETA  = BigInteger.TEN.pow(15);
        BigInteger TERA  = BigInteger.TEN.pow(12);
        if (eu.compareTo(ZETTA) >= 0) return String.format("%.2f ZEU", eu.doubleValue() / 1e21);
        if (eu.compareTo(EXA)   >= 0) return String.format("%.2f EEU", eu.doubleValue() / 1e18);
        if (eu.compareTo(PETA)  >= 0) return String.format("%.2f PEU", eu.doubleValue() / 1e15);
        if (eu.compareTo(TERA)  >= 0) return String.format("%.2f TEU", eu.doubleValue() / 1e12);
        return formatEU(eu.min(BigInteger.valueOf(Long.MAX_VALUE)).longValue());
    }

    private static String formatTime(long secs) {
        if (secs >= 3600) return String.format("%dh %dm", secs / 3600, (secs % 3600) / 60);
        if (secs >= 60)   return String.format("%dm %ds", secs / 60, secs % 60);
        return secs + "s";
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }

    private static String getVoltageTier(long v) {
        long[]   t = {8,32,128,512,2048,8192,32768,131072,524288,2097152,8388608};
        String[] n = {"ULV","LV","MV","HV","EV","IV","LuV","ZPM","UV","UHV","UEV"};
        for (int i = t.length - 1; i >= 0; i--) if (v >= t[i]) return n[i];
        return "ULV";
    }

    private static String modeBadge(EnergySnapshot s) {
        if (!s.isFormed) return "[NOT FORMED]";
        return switch (s.mode) {
            case CONSUMER  -> "[Consumer]";
            case GENERATOR -> "[Generator]";
            case STORAGE   -> "[Storage]";
            default        -> "[Unknown]";
        };
    }

    private static int statusColor(EnergySnapshot s) {
        if (s == null || !s.isFormed) return C_RED;
        if (s.chargePercent() < 0.1f) return C_ORANGE;
        return C_GREEN;
    }

    private ModularUI wrapUI(WidgetGroup content) {
        int w = W;
        int h = H;
        try {
            if (net.minecraftforge.fml.loading.FMLEnvironment.dist.isClient()) {
                var mc = Minecraft.getInstance();
                if (mc != null && mc.getWindow() != null) {
                    w = Math.min(W, mc.getWindow().getGuiScaledWidth()  - 16);
                    h = Math.min(H, mc.getWindow().getGuiScaledHeight() - 16);
                }
            }
        } catch (Exception ignored) {}
        ModularUI ui = new ModularUI(new Size(w, h), holder, player);
        ui.widget(content);
        ui.background(new ColorRectTexture(0x90000000));
        return ui;
    }
}