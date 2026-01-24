package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.common.multiblock.DismantleScanner;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

    // Widget to preview a multiblock structure.

public class MultiblockPreviewWidget extends WidgetGroup {

    private static final int COLOR_BG = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFF888888;

    private final DismantleScanner.ScanResult scanResult;

    public MultiblockPreviewWidget(int x, int y, int width, int height,
                                   DismantleScanner.ScanResult scanResult) {
        super(x, y, width, height);
        this.scanResult = scanResult;

        setBackground(new ColorRectTexture(COLOR_BG));

        // Títle
        LabelWidget titleLabel = new LabelWidget(width / 2 - 30, height / 2 - 30, "§73D Preview");
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        addWidget(titleLabel);

        // Coming soon
        LabelWidget comingSoonLabel = new LabelWidget(width / 2 - 35, height / 2 - 15, "§8(Coming Soon)");
        comingSoonLabel.setTextColor(COLOR_TEXT_GRAY);
        addWidget(comingSoonLabel);

        // Total Blocks
        String totalText = String.format("§f%d §7blocks total", scanResult.getTotalBlocks());
        LabelWidget totalLabel = new LabelWidget(width / 2 - 40, height / 2 + 10, totalText);
        totalLabel.setTextColor(COLOR_TEXT_WHITE);
        addWidget(totalLabel);
    }
}