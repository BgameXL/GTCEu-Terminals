package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.multiblock.ComponentDetailUI;
import com.gtceuterminal.client.gui.widget.LDLMaterialListWidget;
import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.network.CPacketComponentUpgrade;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Upgrade Dialog for Components
public class ComponentUpgradeDialog extends DialogWidget {

    private static final int DIALOG_WIDTH = 400;
    private static final int DIALOG_HEIGHT = 350;
    private static final int DIALOG_SPACING = 10;

    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_SUCCESS = 0xFF00FF00;
    private static final int COLOR_ERROR = 0xFFFF0000;

    private final ComponentDetailDialog parentDialog;
    private final ComponentGroup group;
    private final MultiblockInfo multiblock;
    private final Player player;

    private int selectedTier = -1;
    private List<MaterialAvailability> materials;
    private boolean hasEnough = false;

    private WidgetGroup tierSelectionPanel;
    private WidgetGroup materialsPanel;
    private ButtonWidget confirmButton;

    public ComponentUpgradeDialog(WidgetGroup parent,
                                  ComponentDetailUI parentUI,
                                  ComponentDetailDialog parentDialog,
                                  ComponentGroup group,
                                  MultiblockInfo multiblock,
                                  Player player) {
        super(parent, true);

        this.parentDialog = parentDialog;
        this.group = group;
        this.multiblock = multiblock;
        this.player = player;

        initDialog();
    }

    private void initDialog() {
        int detailDialogWidth = 400;
        int totalWidth = DIALOG_WIDTH + DIALOG_SPACING + detailDialogWidth;

        int centerX = (parent.getSize().width - totalWidth) / 2;
        int leftX = centerX;
        int centerY = (parent.getSize().height - DIALOG_HEIGHT) / 2;

        setSize(new com.lowdragmc.lowdraglib.utils.Size(DIALOG_WIDTH, DIALOG_HEIGHT));
        setSelfPosition(new com.lowdragmc.lowdraglib.utils.Position(leftX, centerY));

        setBackground(new ColorRectTexture(COLOR_BG_DARK));

        // Borders
        addWidget(new ImageWidget(0, 0, DIALOG_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(0, 0, 2, DIALOG_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(DIALOG_WIDTH - 2, 0, 2, DIALOG_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        addWidget(new ImageWidget(0, DIALOG_HEIGHT - 2, DIALOG_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        addWidget(createHeader());
        addWidget(createInfoPanel());

        tierSelectionPanel = createTierSelection();
        addWidget(tierSelectionPanel);

        materialsPanel = createMaterialsPanel();
        addWidget(materialsPanel);

        addWidget(createButtons());
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, DIALOG_WIDTH - 4, 24);
        header.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        String title = "§l§fUpgrade " + group.getType().name().replace("_", " ");
        LabelWidget titleLabel = new LabelWidget(10, 7, title);
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        return header;
    }

    private WidgetGroup createInfoPanel() {
        WidgetGroup panel = new WidgetGroup(10, 30, DIALOG_WIDTH - 20, 36);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            LabelWidget countLabel = new LabelWidget(10, 5,
                    "§7Count: §f" + group.getCount() + " components");
            countLabel.setTextColor(COLOR_TEXT_WHITE);
            panel.addWidget(countLabel);

            LabelWidget currentLabel = new LabelWidget(10, 18,
                    "§7Current Tier: §f" + rep.getTierName());
            currentLabel.setTextColor(COLOR_TEXT_WHITE);
            panel.addWidget(currentLabel);
        }

        return panel;
    }

     // Tier selection:
    private WidgetGroup createTierSelection() {
        WidgetGroup panel = new WidgetGroup(10, 70, DIALOG_WIDTH - 20, 75);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        LabelWidget label = new LabelWidget(10, 4, "§l§7Select Target Tier:");
        label.setTextColor(COLOR_TEXT_WHITE);
        panel.addWidget(label);

        ComponentInfo rep = group.getRepresentative();
        if (rep != null) {
            List<Integer> tiers = ComponentUpgradeHelper.getAvailableTiers(rep.getType());

            // Scroll to avoid clipping
            DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(
                    10, 18, (DIALOG_WIDTH - 20) - 20, 55
            );
            panel.addWidget(scroll);

            int xPos = 0;
            int yPos = 0;
            int btnWidth = 48;
            int btnHeight = 24;
            int spacing = 3;
            int buttonsPerRow = 7;

            int added = 0;
            for (int i = 0; i < tiers.size(); i++) {
                int tier = tiers.get(i);

                if (tier == rep.getTier()) continue;

                if (added > 0 && added % buttonsPerRow == 0) {
                    xPos = 0;
                    yPos += btnHeight + spacing;
                }

                ButtonWidget tierBtn = createTierButton(tier, xPos, yPos, btnWidth, btnHeight);
                scroll.addWidget(tierBtn);

                xPos += btnWidth + spacing;
                added++;
            }
        }

        return panel;
    }

    private void refreshTierSelectionPanel() {
        if (tierSelectionPanel != null) {
            waitToRemoved.add(tierSelectionPanel);
        }
        tierSelectionPanel = createTierSelection();
        addWidget(tierSelectionPanel);
    }

    private static String safeTierName(int tier) {
        String[] vn = com.gregtechceu.gtceu.api.GTValues.VN;
        if (tier >= 0 && tier < vn.length) {
            String s = vn[tier];
            if (s != null && !s.isBlank()) return s;
        }
        return "T" + tier; // Fallback
    }

    private ButtonWidget createTierButton(int tier, int x, int y, int width, int height) {
        String tierName = safeTierName(tier);
        boolean isSelected = (tier == selectedTier);

        ButtonWidget btn = new ButtonWidget(
                x, y, width, height,
                new GuiTextureGroup(
                        new ColorRectTexture(isSelected ? COLOR_SUCCESS : COLOR_BG_LIGHT),
                        new ColorBorderTexture(1, isSelected ? 0xFF00FF00 : COLOR_BORDER_LIGHT)
                ),
                cd -> selectTier(tier)
        );

        btn.setButtonTexture(new TextTexture("§f" + tierName)
                .setWidth(width)
                .setType(TextTexture.TextType.NORMAL));

        btn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(isSelected ? COLOR_SUCCESS : COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        return btn;
    }

    private void selectTier(int tier) {
        this.selectedTier = tier;

        refreshTierSelectionPanel();

        calculateMaterials();
        refreshMaterialsPanel();

        GTCEUTerminalMod.LOGGER.info("Selected tier: {}", tier);
    }

    private void calculateMaterials() {
        if (selectedTier == -1) return;

        ComponentInfo rep = group.getRepresentative();
        if (rep == null) return;

        // Calculate Necessary Materials
        Map<Item, Integer> singleRequired = ComponentUpgradeHelper.getUpgradeItems(rep, selectedTier);

        Map<Item, Integer> totalRequired = new HashMap<>();
        for (var entry : singleRequired.entrySet()) {
            totalRequired.put(entry.getKey(), entry.getValue() * group.getCount());
        }

        // Creative
        if (player.isCreative()) {
            materials = MaterialCalculator.checkMaterialsAvailability(
                    totalRequired, player, player.level()
            );
            hasEnough = true;
            return;
        }

        // Survival
        materials = MaterialCalculator.checkMaterialsAvailability(
                totalRequired, player, player.level()
        );
        hasEnough = MaterialCalculator.hasEnoughMaterials(materials);
    }

    private WidgetGroup createMaterialsPanel() {
        WidgetGroup panel = new WidgetGroup(10, 149, DIALOG_WIDTH - 20, 156);

        LabelWidget label = new LabelWidget(5, 4, "§l§7Required Materials:");
        label.setTextColor(COLOR_TEXT_WHITE);
        panel.addWidget(label);

        if (player.isCreative()) {
            LabelWidget creativeLabel = new LabelWidget(5, 60, "§a§lCREATIVE MODE");
            creativeLabel.setTextColor(COLOR_SUCCESS);
            panel.addWidget(creativeLabel);

            LabelWidget infoLabel = new LabelWidget(5, 75, "§7Select a tier to see materials");
            infoLabel.setTextColor(COLOR_TEXT_GRAY);
            panel.addWidget(infoLabel);
        } else if (selectedTier == -1) {
            LabelWidget placeholder = new LabelWidget(5, 70, "§7Select a tier to see materials");
            placeholder.setTextColor(COLOR_TEXT_GRAY);
            panel.addWidget(placeholder);
        }

        return panel;
    }

    private void refreshMaterialsPanel() {
        waitToRemoved.add(materialsPanel);

        materialsPanel = new WidgetGroup(10, 149, DIALOG_WIDTH - 20, 156);

        LabelWidget label = new LabelWidget(5, 4, "§l§7Required Materials:");
        label.setTextColor(COLOR_TEXT_WHITE);
        materialsPanel.addWidget(label);

        if (selectedTier != -1 && materials != null && !materials.isEmpty()) {
            if (player.isCreative()) {
                LabelWidget creativeNote = new LabelWidget(5, 16, "§a[Creative Mode - Not Required]");
                creativeNote.setTextColor(COLOR_SUCCESS);
                materialsPanel.addWidget(creativeNote);
            }

            int yOffset = player.isCreative() ? 30 : 18;
            int height = player.isCreative() ? 121 : 133;

            LDLMaterialListWidget matWidget = new LDLMaterialListWidget(
                    5, yOffset, DIALOG_WIDTH - 30, height, materials
            );
            materialsPanel.addWidget(matWidget);
        } else if (player.isCreative()) {
            LabelWidget creativeLabel = new LabelWidget(5, 60, "§a§lCREATIVE MODE");
            creativeLabel.setTextColor(COLOR_SUCCESS);
            materialsPanel.addWidget(creativeLabel);

            LabelWidget infoLabel = new LabelWidget(5, 75, "§7No materials required");
            infoLabel.setTextColor(COLOR_TEXT_GRAY);
            materialsPanel.addWidget(infoLabel);
        } else {
            LabelWidget placeholder = new LabelWidget(5, 70, "§7Select a tier to see materials");
            placeholder.setTextColor(COLOR_TEXT_GRAY);
            materialsPanel.addWidget(placeholder);
        }

        addWidget(materialsPanel);

        if (confirmButton != null) {
            confirmButton.setActive(selectedTier != -1);  // Permitir intentar, server validará
        }
    }

    private WidgetGroup createButtons() {
        WidgetGroup buttonPanel = new WidgetGroup(10, DIALOG_HEIGHT - 32, DIALOG_WIDTH - 20, 28);

        String btnText;
        if (player.isCreative()) {
            btnText = "§a§lConfirm Change";
        } else if (hasEnough) {
            btnText = "§aConfirm Change";
        } else {
            btnText = "§eConfirm Change";
        }

        confirmButton = new ButtonWidget(
                0, 3, 180, 20,
                new GuiTextureGroup(
                        new ColorRectTexture(hasEnough || player.isCreative() ? COLOR_SUCCESS : COLOR_ERROR),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> {
                    if ((hasEnough || player.isCreative()) && selectedTier != -1) {
                        performUpgrade();
                    }
                }
        );

        confirmButton.setButtonTexture(new TextTexture(btnText)
                .setWidth(180)
                .setType(TextTexture.TextType.NORMAL));

        confirmButton.setActive(selectedTier != -1);  // Permitir intentar, server validará
        buttonPanel.addWidget(confirmButton);

        ButtonWidget cancelBtn = new ButtonWidget(
                190, 3, 180, 20,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_MEDIUM),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> close()
        );

        cancelBtn.setButtonTexture(new TextTexture("Cancel")
                .setWidth(180)
                .setType(TextTexture.TextType.NORMAL));

        cancelBtn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_MEDIUM),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        buttonPanel.addWidget(cancelBtn);

        return buttonPanel;
    }

    private void performUpgrade() {
        for (ComponentInfo component : group.getComponents()) {
            TerminalNetwork.CHANNEL.sendToServer(
                    new CPacketComponentUpgrade(
                            component.getPosition(),
                            selectedTier,
                            multiblock.getControllerPos()
                    )
            );
        }

        player.displayClientMessage(
                Component.literal("§aChanging " + group.getCount() + " components..."),
                true
        );

        closeAll();
    }

    @Override
    public void close() {
        super.close();
    }

    private void closeAll() {
        super.close();
        if (parentDialog != null) {
            parentDialog.close();
        }
    }
}