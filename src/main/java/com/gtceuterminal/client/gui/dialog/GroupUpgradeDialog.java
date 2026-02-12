package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.network.CPacketComponentUpgrade;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Position;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Group Upgrade Confirmation Dialog
public class GroupUpgradeDialog extends DialogWidget {

    private static final int dialogW = 320;
    private static final int dialogH = 260;

    // GTCEu Colors
    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF00FF00;
    private static final int COLOR_ERROR = 0xFFFF0000;
    private static final int COLOR_CREATIVE = 0xFF88FF88;

    private final ComponentGroup group;
    private final int targetTier;
    private final Player player;
    private final Runnable onSuccess;
    private final Runnable onClose;

    private List<MaterialAvailability> materials;
    private boolean hasEnough;

    public GroupUpgradeDialog(
            WidgetGroup parent,
            ComponentGroup group,
            int targetTier,
            MultiblockInfo multiblock,
            Player player,
            Runnable onSuccess,
            Runnable onClose
    ) {
        super(parent, true);  // true = modal
        this.group = group;
        this.targetTier = targetTier;
        this.player = player;
        this.onSuccess = onSuccess;
        this.onClose = onClose;

        initDialog();
    }

    private void initDialog() {
        var mc = Minecraft.getInstance();
        int sw = mc.screen != null ? mc.screen.width : mc.getWindow().getGuiScaledWidth();
        int sh = mc.screen != null ? mc.screen.height : mc.getWindow().getGuiScaledHeight();

        int margin = 10;

        int x = (sw - dialogW) / 2;
        int y = (sh - dialogH) / 2;

        x = Mth.clamp(x, margin, sw - dialogW - margin);
        y = Mth.clamp(y, margin, sh - dialogH - margin);

        setSize(new Size(dialogW, dialogH));
        setSelfPosition(new Position(x, y));

        // Background
        setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Borders
        addWidget(new ImageWidget(0, 0, dialogW, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(0, 0, 2, dialogH,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(dialogW - 2, 0, 2, dialogH,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        addWidget(new ImageWidget(0, dialogH - 2, dialogW, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        // Calculate materials
        calculateMaterials();

        // Header
        addWidget(createHeader());

        // Group info
        addWidget(createGroupInfo());

        // Materials list or creative message
        addWidget(createMaterialsPanel());

        // Buttons
        addWidget(createButtons());
    }

    private void calculateMaterials() {
        boolean isCreative = player.isCreative();
        ComponentInfo rep = group.getRepresentative();

        if (rep != null) {
            Map<Item, Integer> singleRequired = ComponentUpgradeHelper.getUpgradeItems(rep, targetTier);
            Map<Item, Integer> totalRequired = new HashMap<>();

            for (var entry : singleRequired.entrySet()) {
                totalRequired.put(entry.getKey(), entry.getValue() * group.getCount());
            }

            if (isCreative) {
                materials = new ArrayList<>();
                hasEnough = true;
            } else {
                materials = MaterialCalculator.checkMaterialsAvailability(
                        totalRequired, player, Minecraft.getInstance().level);
                hasEnough = MaterialCalculator.hasEnoughMaterials(materials);
            }
        } else {
            materials = new ArrayList<>();
            hasEnough = false;
        }
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, dialogW - 4, 28);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        LabelWidget title = new LabelWidget(dialogW / 2 - 90, 10,
                "§l§fUpgrade Group Confirmation");
        title.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(title);

        return header;
    }

    private WidgetGroup createGroupInfo() {
        WidgetGroup info = new WidgetGroup(10, 35, dialogW - 20, 60);
        info.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        int yPos = 8;

        // Component count and name
        String componentName = group.getType().getDisplayName();
        LabelWidget countLabel = new LabelWidget(10, yPos,
                "§eUpgrade " + group.getCount() + "x " + componentName);
        countLabel.setTextColor(COLOR_TEXT_WHITE);
        info.addWidget(countLabel);

        yPos += 15;

        // From tier
        String fromName = getDisplayName(group.getType(), group.getTier());
        LabelWidget fromLabel = new LabelWidget(10, yPos, "§7From: §f" + fromName);
        fromLabel.setTextColor(COLOR_TEXT_WHITE);
        info.addWidget(fromLabel);

        yPos += 12;

        // To tier
        String toName = getDisplayName(group.getType(), targetTier);
        LabelWidget toLabel = new LabelWidget(10, yPos, "§7To: §f" + toName);
        toLabel.setTextColor(COLOR_TEXT_WHITE);
        info.addWidget(toLabel);

        return info;
    }

    private WidgetGroup createMaterialsPanel() {
        if (player.isCreative()) {
            return createCreativePanel();
        } else {
            return createSurvivalPanel();
        }
    }

    private WidgetGroup createCreativePanel() {
        WidgetGroup panel = new WidgetGroup(10, 100, dialogW - 20, 40);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        LabelWidget creativeMsg = new LabelWidget(10, 15,
                "§aCreative Mode - No materials required");
        creativeMsg.setTextColor(COLOR_CREATIVE);
        panel.addWidget(creativeMsg);

        return panel;
    }

    private WidgetGroup createSurvivalPanel() {
        int listHeight = Math.min(materials.size() * 12 + 25, 110);
        WidgetGroup panel = new WidgetGroup(10, 100, dialogW - 20, listHeight);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        // Header
        LabelWidget headerLabel = new LabelWidget(10, 5, "§7Required materials:");
        headerLabel.setTextColor(COLOR_TEXT_GRAY);
        panel.addWidget(headerLabel);

        // Materials list
        int yPos = 18;
        for (MaterialAvailability mat : materials) {
            String itemName = mat.getItemName();
            int required = mat.getRequired();
            long available = mat.getAvailable();

            int color = available >= required ? COLOR_SUCCESS : COLOR_ERROR;
            String matText = "  " + itemName + ": " + available + "/" + required;

            LabelWidget matLabel = new LabelWidget(10, yPos, matText);
            matLabel.setTextColor(color);
            panel.addWidget(matLabel);

            yPos += 12;
        }

        return panel;
    }

    private WidgetGroup createButtons() {
        WidgetGroup buttons = new WidgetGroup(10, dialogH - 35, dialogW - 20, 28);

        // Confirm button
        String confirmText = hasEnough ? "Upgrade All (" + group.getCount() + ")" : "Missing Materials";
        ButtonWidget confirmBtn = new ButtonWidget(
                0, 0, 150, 24,
                new GuiTextureGroup(
                        new ColorRectTexture(hasEnough ? COLOR_SUCCESS : COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, hasEnough ? COLOR_SUCCESS : COLOR_BORDER_DARK)
                ),
                cd -> {
                    if (hasEnough) {
                        performGroupUpgrade();
                    }
                }
        );
        confirmBtn.setButtonTexture(new TextTexture(confirmText)
                .setWidth(150)
                .setType(TextTexture.TextType.NORMAL));

        if (hasEnough) {
            confirmBtn.setHoverTexture(new GuiTextureGroup(
                    new ColorRectTexture(COLOR_SUCCESS),
                    new ColorBorderTexture(2, COLOR_TEXT_WHITE)
            ));
        }

        buttons.addWidget(confirmBtn);

        // Cancel button
        ButtonWidget cancelBtn = new ButtonWidget(
                dialogW - 150, 0, 140, 24,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_DARK)
                ),
                cd -> closeDialog()
        );
        cancelBtn.setButtonTexture(new TextTexture("Cancel")
                .setWidth(140)
                .setType(TextTexture.TextType.NORMAL));
        cancelBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));

        buttons.addWidget(cancelBtn);

        return buttons;
    }

    private void performGroupUpgrade() {
        List<BlockPos> positions = new ArrayList<>();
        for (ComponentInfo comp : group.getComponents()) {
            positions.add(comp.getPosition());
        }

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketComponentUpgrade(positions, targetTier)
        );

        if (onSuccess != null) {
            onSuccess.run();
        }

        closeDialog();
    }

    private void closeDialog() {
        if (onClose != null) {
            onClose.run();
        }
        if (parent != null) {
            parent.waitToRemoved(this);
        }
    }

    private String getDisplayName(ComponentType type, int tier) {
        if (type == ComponentType.COIL) {
            return getCoilName(tier);
        } else if (type == ComponentType.MAINTENANCE) {
            return type.getDisplayName();
        } else {
            String tierName = com.gregtechceu.gtceu.api.GTValues.VN[tier].toUpperCase(java.util.Locale.ROOT);
            return type.getDisplayName() + " (" + tierName + ")";
        }
    }

    private String getCoilName(int tier) {
        return switch (tier) {
            case 0 -> "Cupronickel";
            case 1 -> "Kanthal";
            case 2 -> "Nichrome";
            case 3 -> "RTM Alloy";
            case 4 -> "HSS-G";
            case 5 -> "Naquadah";
            case 6 -> "Trinium";
            case 7 -> "Tritanium";
            default -> "Unknown Coil";
        };
    }
}