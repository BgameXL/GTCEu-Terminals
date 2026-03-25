package com.gtceuterminal.client.gui.widget;

import com.gtceuterminal.common.ae2.MENetworkScanner;
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
            return Component.translatable(
                    "gui.gtceuterminal.material_list.quantity.me",
                    mat.getTotalAvailable(),
                    mat.getRequired()
            ).getString();
        } else if (mat.getInInventory() >= mat.getRequired()) {
            return Component.translatable(
                    "gui.gtceuterminal.material_list.quantity.inv",
                    mat.getTotalAvailable(),
                    mat.getRequired()
            ).getString();
        } else if (mat.hasEnough()) {
            return Component.translatable(
                    "gui.gtceuterminal.material_list.quantity.mix",
                    mat.getTotalAvailable(),
                    mat.getRequired()
            ).getString();
        } else {
            return Component.translatable(
                    "gui.gtceuterminal.material_list.quantity.missing",
                    mat.getTotalAvailable(),
                    mat.getRequired(),
                    mat.getMissing()
            ).getString();
        }
    }

    private List<Component> createTooltip(MaterialAvailability mat) {
        List<Component> tooltip = new ArrayList<>();

        // Title
        tooltip.add(Component.literal("§f§l" + mat.getItemName()));
        tooltip.add(Component.empty());

        // Required vs Available
        String availColor = mat.hasEnough() ? "§a" : "§c";
        tooltip.add(Component.translatable(
                "gui.gtceuterminal.material_list.tooltip.required",
                mat.getRequired()
        ));
        tooltip.add(Component.translatable(
                "gui.gtceuterminal.material_list.tooltip.available_colored",
                availColor + mat.getTotalAvailable()
        ));

        // Separator
        tooltip.add(Component.empty());
        tooltip.add(Component.translatable("gui.gtceuterminal.material_list.tooltip.sources_header"));

        // Inventory
        if (mat.getInInventory() > 0) {
            tooltip.add(Component.translatable(
                    "gui.gtceuterminal.material_list.tooltip.source.inventory_nonzero",
                    mat.getInInventory()
            ));
        } else {
            tooltip.add(Component.translatable("gui.gtceuterminal.material_list.tooltip.source.inventory_zero"));
        }

        // Chests
        if (mat.getInNearbyChests() > 0) {
            tooltip.add(Component.translatable(
                    "gui.gtceuterminal.material_list.tooltip.source.chests_nonzero",
                    mat.getInNearbyChests()
            ));
        } else {
            tooltip.add(Component.translatable("gui.gtceuterminal.material_list.tooltip.source.chests_zero"));
        }

        // ME Network — only shown when AE2 is present
        if (MENetworkScanner.isAE2Available()) {
            if (mat.getInMENetwork() > 0) {
                tooltip.add(Component.translatable(
                        "gui.gtceuterminal.material_list.tooltip.source.me_network_nonzero",
                        mat.getInMENetwork()
                ));
            } else {
                tooltip.add(Component.translatable("gui.gtceuterminal.material_list.tooltip.source.me_network_zero"));
            }
        }

        // Status
        tooltip.add(Component.empty());
        if (mat.hasEnough()) {
            tooltip.add(Component.translatable("gui.gtceuterminal.material_list.tooltip.status.sufficient"));
            String primarySource = mat.getPrimarySource();
            String primarySourceName = switch (primarySource) {
                case "ME Network" -> Component.translatable("gui.gtceuterminal.material_list.source.me_network").getString();
                case "Inventory" -> Component.translatable("gui.gtceuterminal.material_list.source.inventory").getString();
                case "Chests" -> Component.translatable("gui.gtceuterminal.material_list.source.chests").getString();
                case "ME + Others" -> Component.translatable("gui.gtceuterminal.material_list.source.me_plus_others").getString();
                case "Inventory + Others" -> Component.translatable("gui.gtceuterminal.material_list.source.inventory_plus_others").getString();
                case "None" -> Component.translatable("gui.gtceuterminal.material_list.source.none").getString();
                default -> primarySource;
            };

            boolean pendingVerify = primarySource.equals("ME Network");
            String coloredSource = (pendingVerify ? "§e" : "§f") + primarySourceName;
            tooltip.add(Component.translatable(
                    pendingVerify
                            ? "gui.gtceuterminal.material_list.tooltip.primary_source.me_pending"
                            : "gui.gtceuterminal.material_list.tooltip.primary_source.normal",
                    coloredSource
            ));
        } else {
            tooltip.add(Component.translatable(
                    "gui.gtceuterminal.material_list.tooltip.status.missing",
                    mat.getMissing()
            ));
        }

        return tooltip;
    }
}