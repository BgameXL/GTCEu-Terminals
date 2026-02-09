package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.common.material.MaterialAvailability;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.ImageWidget;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LDLMaterialListWidget extends WidgetGroup {

    private final List<MaterialAvailability> materials;

    // GTCEu colors
    private static final int BG_COLOR = 0xFF2B2B2B;
    private static final int BG_HOVER = 0xFF3F3F3F;
    private static final int BORDER_LIGHT = 0xFF5A5A5A;
    private static final int BORDER_DARK = 0xFF1A1A1A;

    private static final int STATUS_GREEN = 0xFF00FF00;
    private static final int STATUS_YELLOW = 0xFFFFFF00;
    private static final int STATUS_RED = 0xFFFF0000;

    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;

    public LDLMaterialListWidget(int x, int y, int width, int height, List<MaterialAvailability> materials) {
        super(x, y, width, height);
        this.materials = materials;
        initWidget();
    }

    @Override
    public void initWidget() {
        super.initWidget();

        // Background panel
        setBackground(new ColorRectTexture(BG_COLOR));

        // Borders
        addWidget(new ImageWidget(0, 0, getSize().width, 2,
                new ColorRectTexture(BORDER_LIGHT)));
        addWidget(new ImageWidget(0, 0, 2, getSize().height,
                new ColorRectTexture(BORDER_LIGHT)));
        addWidget(new ImageWidget(getSize().width - 2, 0, 2, getSize().height,
                new ColorRectTexture(BORDER_DARK)));
        addWidget(new ImageWidget(0, getSize().height - 2, getSize().width, 2,
                new ColorRectTexture(BORDER_DARK)));

        // Scrollable content
        DraggableScrollableWidgetGroup scrollWidget = new DraggableScrollableWidgetGroup(
                4, 4, getSize().width - 16, getSize().height - 8
        );
        scrollWidget.setYScrollBarWidth(8);
        scrollWidget.setYBarStyle(
                new ColorRectTexture(BORDER_DARK),
                new ColorRectTexture(BORDER_LIGHT)
        );

        int yPos = 0;
        for (int i = 0; i < materials.size(); i++) {
            MaterialAvailability mat = materials.get(i);
            scrollWidget.addWidget(createMaterialEntry(mat, i, yPos));
            yPos += 16;
        }

        addWidget(scrollWidget);
    }

    private WidgetGroup createMaterialEntry(MaterialAvailability mat, int index, int yPos) {
        WidgetGroup entry = new WidgetGroup(0, yPos, getSize().width - 20, 14);
        entry.setBackground(new ColorRectTexture(0x00000000));

        // Status indicator
        int dotColor = mat.hasEnough() ? STATUS_GREEN :
                mat.getTotalAvailable() > 0 ? STATUS_YELLOW : STATUS_RED;

        ImageWidget statusDot = new ImageWidget(3, 5, 4, 4, new ColorRectTexture(dotColor));
        entry.addWidget(statusDot);

        // Item name
        String itemName = mat.getItemName();
        LabelWidget nameLabel = new LabelWidget(12, 3, itemName);
        nameLabel.setTextColor(TEXT_WHITE);
        entry.addWidget(nameLabel);

        // Quantity display
        String quantityText = formatQuantity(mat);
        LabelWidget quantityLabel = new LabelWidget(getSize().width - 110, 3, quantityText);
        quantityLabel.setTextColor(TEXT_GRAY);
        entry.addWidget(quantityLabel);

        // Tooltip on hover
        entry.setHoverTooltips(createTooltip(mat));

        return entry;
    }

    private String formatQuantity(MaterialAvailability mat) {
        if (mat.getInMENetwork() >= mat.getRequired()) {
            return String.format("§a%d§7/%d §a[ME]",
                    mat.getTotalAvailable(), mat.getRequired());
        } else if (mat.getInInventory() >= mat.getRequired()) {
            return String.format("§7%d/%d [Inv]",
                    mat.getTotalAvailable(), mat.getRequired());
        } else if (mat.hasEnough()) {
            return String.format("§e%d§7/%d §e[Mix]",
                    mat.getTotalAvailable(), mat.getRequired());
        } else {
            return String.format("§c%d§7/%d §c[-%d]",
                    mat.getTotalAvailable(), mat.getRequired(), mat.getMissing());
        }
    }

    private List<Component> createTooltip(MaterialAvailability mat) {
        List<Component> tooltip = new ArrayList<>();

        // Title
        tooltip.add(Component.literal("§f§l" + mat.getItemName()));
        tooltip.add(Component.empty());

        // Required vs Available
        String availColor = mat.hasEnough() ? "§a" : "§c";
        tooltip.add(Component.literal("§7Required: §f" + mat.getRequired()));
        tooltip.add(Component.literal("§7Available: " + availColor + mat.getTotalAvailable()));

        // Separator
        tooltip.add(Component.empty());
        tooltip.add(Component.literal("§7§l--- Sources ---"));

        // Inventory
        if (mat.getInInventory() > 0) {
            tooltip.add(Component.literal("  §7Inventory: §f" + mat.getInInventory()));
        } else {
            tooltip.add(Component.literal("  §8Inventory: 0"));
        }

        // Chests
        if (mat.getInNearbyChests() > 0) {
            tooltip.add(Component.literal("  §7Chests: §f" + mat.getInNearbyChests()));
        } else {
            tooltip.add(Component.literal("  §8Chests: 0"));
        }

        // ME Network - MODIFICADO para mostrar "(pending verification)"
        if (mat.getInMENetwork() > 0) {
            tooltip.add(Component.literal("  §e§lME Network: §f" + mat.getInMENetwork() + " §8(pending verification)"));
        } else {
            tooltip.add(Component.literal("  §8ME Network: 0"));
        }

        // Status
        tooltip.add(Component.empty());
        if (mat.hasEnough()) {
            tooltip.add(Component.literal("§a✓ Sufficient materials"));
            String primarySource = mat.getPrimarySource();
            if (primarySource.equals("ME Network")) {
                tooltip.add(Component.literal("§7Primary source: §e" + primarySource + " §8(will verify on confirm)"));
            } else {
                tooltip.add(Component.literal("§7Primary source: §f" + primarySource));
            }
        } else {
            tooltip.add(Component.literal("§c✗ Missing " + mat.getMissing() + " items"));
        }

        return tooltip;
    }
}