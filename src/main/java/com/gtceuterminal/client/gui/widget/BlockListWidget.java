package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.common.multiblock.DismantleScanner;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.world.level.block.Block;

import java.util.Map;

public class BlockListWidget extends DraggableScrollableWidgetGroup {

    private static final int ROW_HEIGHT = 20;
    private static final int COLOR_BG = 0xFF1F1F1F;
    private static final int COLOR_BG_HOVER = 0xFF2F2F2F;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;

    private final DismantleScanner.ScanResult scanResult;

    public BlockListWidget(int x, int y, int width, int height,
                          DismantleScanner.ScanResult scanResult) {
        super(x, y, width, height);
        this.scanResult = scanResult;

        setBackground(new ColorRectTexture(COLOR_BG));
        setYScrollBarWidth(6);
        setYBarStyle(
                new ColorRectTexture(0xFF0A0A0A),
                new ColorRectTexture(0xFF5A5A5A)
        );

        buildContent();
    }

    private void buildContent() {
        int yPos = 2;

        Map<Block, Integer> blocks = scanResult.getBlockCounts();

        for (Map.Entry<Block, Integer> entry : blocks.entrySet()) {
            addWidget(createBlockRow(entry.getKey(), entry.getValue(), yPos));
            yPos += ROW_HEIGHT;
        }
    }

    private WidgetGroup createBlockRow(Block block, int count, int yPos) {
        WidgetGroup row = new WidgetGroup(0, yPos, getSize().width - 12, ROW_HEIGHT);

        // Background hover
        row.setBackground(new ColorRectTexture(COLOR_BG));

        // Block name
        String blockName = block.getName().getString();
        LabelWidget nameLabel = new LabelWidget(5, ROW_HEIGHT / 2 - 4, blockName);
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        row.addWidget(nameLabel);

        // Count
        String countText = "§7x" + count;
        LabelWidget countLabel = new LabelWidget(getSize().width - 50, ROW_HEIGHT / 2 - 4, countText);
        countLabel.setTextColor(COLOR_TEXT_GRAY);
        row.addWidget(countLabel);

        // Separator line
        row.addWidget(new ImageWidget(0, ROW_HEIGHT - 1, getSize().width - 12, 1,
                new ColorRectTexture(0xFF0A0A0A)));

        return row;
    }
}