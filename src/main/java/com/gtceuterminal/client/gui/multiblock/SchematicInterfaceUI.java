package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.factory.SchematicUIFactory;
import com.gtceuterminal.client.gui.widget.SchematicPreviewWidget;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.network.CPacketSchematicAction;
import com.gtceuterminal.common.network.TerminalNetwork;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.*;
import com.lowdragmc.lowdraglib.gui.widget.*;
import com.lowdragmc.lowdraglib.utils.Size;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SchematicInterfaceUI {

    private static final int GUI_WIDTH = 480;
    private static final int GUI_HEIGHT = 320;

    private static final int COLOR_BG_DARK = 0xFF1A1A1A;
    private static final int COLOR_BG_MEDIUM = 0xFF2B2B2B;
    private static final int COLOR_BG_LIGHT = 0xFF3F3F3F;
    private static final int COLOR_BORDER_LIGHT = 0xFF5A5A5A;
    private static final int COLOR_BORDER_DARK = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLOR_HOVER = 0x40FFFFFF;
    private static final int COLOR_SUCCESS = 0xFF4CAF50;
    private static final int COLOR_ERROR = 0xFFFF5252;
    private static final int COLOR_WARNING = 0xFFFFA726;
    private static final int COLOR_INFO = 0xFF42A5F5;

    private final SchematicUIFactory.SchematicHolder holder;
    private final Player player;
    private List<SchematicData> schematics = new ArrayList<>();
    private int selectedIndex = -1;
    private ModularUI gui;
    private TextFieldWidget nameInput;
    private WidgetGroup rightPanel;
    private DraggableScrollableWidgetGroup schematicsListWidget;

    public SchematicInterfaceUI(SchematicUIFactory.SchematicHolder holder, Player player) {
        this.holder = holder;
        this.player = player;
        loadSchematics();
    }

    private void loadSchematics() {
        this.schematics = new ArrayList<>();

        ItemStack currentItem = holder.getTerminalItem();
        CompoundTag tag = currentItem.getTag();
        if (tag != null && tag.contains("SavedSchematics")) {
            ListTag list = tag.getList("SavedSchematics", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag schematicTag = list.getCompound(i);
                SchematicData data = SchematicData.fromNBT(schematicTag, player.level().registryAccess());
                if (!"Clipboard".equals(data.getName())) {
                    schematics.add(data);
                }
            }
        }

        if (!schematics.isEmpty() && selectedIndex < 0) {
            selectedIndex = 0;
            GTCEUTerminalMod.LOGGER.info("Auto-selected first schematic: {}", schematics.get(0).getName());
        }

        GTCEUTerminalMod.LOGGER.info("Loaded {} schematics (filtered Clipboard), selectedIndex: {}",
                schematics.size(), selectedIndex);
    }

    private void reloadSchematicsFromItem() {
        this.schematics = new ArrayList<>();

        ItemStack currentItem = holder.getTerminalItem();
        CompoundTag tag = currentItem.getTag();

        if (tag != null && tag.contains("SavedSchematics")) {
            ListTag list = tag.getList("SavedSchematics", 10);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag schematicTag = list.getCompound(i);
                SchematicData data = SchematicData.fromNBT(schematicTag, player.level().registryAccess());
                if (!"Clipboard".equals(data.getName())) {
                    schematics.add(data);
                }
            }
        }

        if (!schematics.isEmpty() && selectedIndex >= schematics.size()) {
            selectedIndex = schematics.size() - 1;
        }
        if (schematics.isEmpty()) {
            selectedIndex = -1;
        }

        GTCEUTerminalMod.LOGGER.info("Reloaded {} schematics from item, selectedIndex: {}",
                schematics.size(), selectedIndex);
    }

    public ModularUI createUI() {
        WidgetGroup mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        mainGroup.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        mainGroup.addWidget(createBorders());
        mainGroup.addWidget(createHeader());
        mainGroup.addWidget(createLeftPanel());

        this.rightPanel = createRightPanel();
        mainGroup.addWidget(rightPanel);

        mainGroup.addWidget(createButtonSection());

        this.gui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), holder, player);
        gui.widget(mainGroup);
        gui.background(new ColorRectTexture(0x90000000));

        return gui;
    }

    private WidgetGroup createBorders() {
        WidgetGroup borders = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);

        borders.addWidget(new ImageWidget(0, 0, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        borders.addWidget(new ImageWidget(0, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_LIGHT)));
        borders.addWidget(new ImageWidget(GUI_WIDTH - 2, 0, 2, GUI_HEIGHT,
                new ColorRectTexture(COLOR_BORDER_DARK)));
        borders.addWidget(new ImageWidget(0, GUI_HEIGHT - 2, GUI_WIDTH, 2,
                new ColorRectTexture(COLOR_BORDER_DARK)));

        return borders;
    }

    private WidgetGroup createHeader() {
        WidgetGroup header = new WidgetGroup(2, 2, GUI_WIDTH - 4, 32);
        header.setBackground(new ColorRectTexture(COLOR_BG_LIGHT));

        LabelWidget titleLabel = new LabelWidget(12, 10, "§f§lSchematic Interface");
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        boolean hasClipboard = hasClipboard();
        String clipboardText = hasClipboard ? "§a✓ Clipboard Ready" : "§7✗ Clipboard Empty";
        LabelWidget clipboardLabel = new LabelWidget(GUI_WIDTH - 180, 10, clipboardText);
        clipboardLabel.setTextColor(hasClipboard ? COLOR_SUCCESS : COLOR_TEXT_GRAY);
        header.addWidget(clipboardLabel);

        return header;
    }

    private WidgetGroup createLeftPanel() {
        int panelWidth = 220;
        WidgetGroup leftPanel = new WidgetGroup(4, 38, panelWidth, GUI_HEIGHT - 82);
        leftPanel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        LabelWidget nameLabel = new LabelWidget(8, 8, "§7Schematic Name:");
        nameLabel.setTextColor(COLOR_TEXT_GRAY);
        leftPanel.addWidget(nameLabel);

        this.nameInput = new TextFieldWidget(8, 22, panelWidth - 16, 14, null, s -> {});
        nameInput.setMaxStringLength(32);
        nameInput.setTextColor(COLOR_TEXT_WHITE);
        nameInput.setBackground(new ColorRectTexture(COLOR_BG_DARK));
        nameInput.setBordered(true);
        leftPanel.addWidget(nameInput);

        LabelWidget listTitle = new LabelWidget(8, 44, "§7Saved Schematics:");
        listTitle.setTextColor(COLOR_TEXT_GRAY);
        leftPanel.addWidget(listTitle);

        this.schematicsListWidget = new DraggableScrollableWidgetGroup(
                8, 58, panelWidth - 16, GUI_HEIGHT - 150
        );
        schematicsListWidget.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        populateSchematicsList();

        leftPanel.addWidget(schematicsListWidget);
        return leftPanel;
    }

    private void populateSchematicsList() {
        if (schematics.isEmpty()) {
            LabelWidget emptyLabel = new LabelWidget(10, 40, "§7No schematics saved");
            schematicsListWidget.addWidget(emptyLabel);

            LabelWidget hintLabel = new LabelWidget(10, 55, "§8Shift+Click on a formed");
            schematicsListWidget.addWidget(hintLabel);

            LabelWidget hintLabel2 = new LabelWidget(10, 65, "§8multiblock to copy it");
            schematicsListWidget.addWidget(hintLabel2);
        } else {
            int yPos = 5;
            for (int i = 0; i < schematics.size(); i++) {
                final int index = i;
                SchematicData schematic = schematics.get(i);

                WidgetGroup entry = createSchematicEntry(schematic, index, yPos);
                schematicsListWidget.addWidget(entry);

                yPos += 50;
            }
        }
    }

    private WidgetGroup createSchematicEntry(SchematicData schematic, int index, int yPos) {
        boolean isSelected = index == selectedIndex;
        int entryWidth = 204;

        WidgetGroup entry = new WidgetGroup(0, yPos, entryWidth, 45);
        entry.setBackground(new ColorRectTexture(isSelected ? COLOR_BG_LIGHT : COLOR_BG_MEDIUM));

        ButtonWidget clickArea = new ButtonWidget(0, 0, entryWidth, 45,
                new ColorRectTexture(0x00000000),
                cd -> {
                    if (this.selectedIndex != index) {
                        GTCEUTerminalMod.LOGGER.info("Selected schematic changed from {} to {}",
                                this.selectedIndex, index);
                        this.selectedIndex = index;
                        refreshLeftPanel();
                        player.displayClientMessage(
                                Component.literal("§7Selected: §f" + schematic.getName()),
                                true
                        );
                    }
                });

        clickArea.setHoverTexture(new ColorRectTexture(COLOR_HOVER));
        entry.addWidget(clickArea);

        String displayName = schematic.getName();
        if (displayName.length() > 22) {
            displayName = displayName.substring(0, 19) + "...";
        }
        LabelWidget nameLabel = new LabelWidget(8, 6, "§f" + displayName);
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        entry.addWidget(nameLabel);

        int blockCount = schematic.getBlocks().size();
        String sizeInfo = String.format("§8%d blocks", blockCount);
        LabelWidget infoLabel = new LabelWidget(8, 22, sizeInfo);
        infoLabel.setTextColor(COLOR_TEXT_GRAY);
        entry.addWidget(infoLabel);

        if (isSelected) {
            ImageWidget indicator = new ImageWidget(2, 2, 3, 41,
                    new ColorRectTexture(COLOR_INFO));
            entry.addWidget(indicator);
        }

        return entry;
    }

    private WidgetGroup createRightPanel() {
        int panelX = 228;
        int panelWidth = GUI_WIDTH - panelX - 4;
        WidgetGroup rightPanel = new WidgetGroup(panelX, 38, panelWidth, GUI_HEIGHT - 82);
        rightPanel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        int previewSize = panelWidth - 16;
        int previewHeight = GUI_HEIGHT - 82 - 20;
        WidgetGroup previewArea = new WidgetGroup(8, 8, previewSize, previewHeight);
        previewArea.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        GTCEUTerminalMod.LOGGER.info("Creating right panel - selectedIndex: {}, schematics.size(): {}",
                selectedIndex, schematics.size());

        if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
            SchematicData selected = schematics.get(selectedIndex);
            GTCEUTerminalMod.LOGGER.info("Adding preview for selected schematic: {}", selected.getName());
            addPreviewContent(previewArea, selected, previewSize);
        } else if (hasClipboard()) {
            GTCEUTerminalMod.LOGGER.info("No selection, showing clipboard hint");
            LabelWidget clipboardInfo = new LabelWidget(10, 10, "§7Clipboard content:");
            previewArea.addWidget(clipboardInfo);

            LabelWidget hint = new LabelWidget(10, 30, "§8Save it to see preview");
            previewArea.addWidget(hint);
        } else {
            GTCEUTerminalMod.LOGGER.info("No selection and no clipboard");
            LabelWidget noPreview = new LabelWidget(previewSize/2 - 40, previewHeight/2, "§7No preview");
            previewArea.addWidget(noPreview);
        }

        rightPanel.addWidget(previewArea);
        return rightPanel;
    }

    private void addPreviewContent(WidgetGroup area, SchematicData schematic, int size) {
        GTCEUTerminalMod.LOGGER.info("Creating preview for schematic: {} with {} blocks",
                schematic.getName(), schematic.getBlocks().size());

        int previewHeight = size - 60;

        // Only create preview widget on CLIENT side
        // On dedicated server, SchematicPreviewWidget causes ClassNotFoundException because it uses net.minecraft.client.renderer.MultiBufferSource (I learned it the hard way)
        if (holder.isRemote()) {
            // CLIENT: Create actual 3D preview
            SchematicPreviewWidget previewWidget = new SchematicPreviewWidget(
                    5, 5, size - 10, previewHeight, schematic
            );
            previewWidget.setBackground(new ColorRectTexture(0xFF0A0A0A));
            area.addWidget(previewWidget);

            GTCEUTerminalMod.LOGGER.info("Preview widget added at position (5, 5) with size {}x{}",
                    size - 10, previewHeight);
        } else {
            // SERVER: Add placeholder (will be replaced on client)
            GTCEUTerminalMod.LOGGER.info("Skipping preview widget creation on server (will be created on client)");
            LabelWidget placeholder = new LabelWidget(10, previewHeight / 2, "§7Preview loading...");
            placeholder.setTextColor(COLOR_TEXT_GRAY);
            area.addWidget(placeholder);
        }

        int textY = size - 50;

        // Name label (shown on both server and client)
        String displayName = getMultiblockName(schematic);
        if (displayName.length() > 25) {
            displayName = displayName.substring(0, 22) + "...";
        }
        LabelWidget nameLabel = new LabelWidget(10, textY, "§f§l" + displayName);
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        area.addWidget(nameLabel);
        textY += 15;

        // Type label
        BlockPos size1 = schematic.getSize();
        String infoText = String.format("§7%d blocks | %dx%dx%d",
                schematic.getBlocks().size(),
                size1.getX(),
                size1.getY(),
                size1.getZ());
        LabelWidget infoLabel = new LabelWidget(10, textY, infoText);
        infoLabel.setTextColor(COLOR_TEXT_GRAY);
        area.addWidget(infoLabel);
    }

    private WidgetGroup createButtonSection() {
        WidgetGroup buttonSection = new WidgetGroup(4, GUI_HEIGHT - 40, GUI_WIDTH - 8, 36);
        buttonSection.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        int buttonWidth = 90;
        int buttonHeight = 24;
        int spacing = 10;
        int startX = 10;

        ButtonWidget saveButton = new ButtonWidget(startX, 6, buttonWidth, buttonHeight,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_SUCCESS),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> saveSchematic());
        saveButton.setButtonTexture(new TextTexture("§f§lSave")
                .setWidth(buttonWidth)
                .setType(TextTexture.TextType.NORMAL));
        saveButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFF66BB6A),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));
        buttonSection.addWidget(saveButton);

        startX += buttonWidth + spacing;
        ButtonWidget loadButton = new ButtonWidget(startX, 6, buttonWidth, buttonHeight,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_INFO),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> loadSchematic());
        loadButton.setButtonTexture(new TextTexture("§f§lLoad")
                .setWidth(buttonWidth)
                .setType(TextTexture.TextType.NORMAL));
        loadButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFF42A5F5),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));
        buttonSection.addWidget(loadButton);

        startX += buttonWidth + spacing;
        ButtonWidget deleteButton = new ButtonWidget(startX, 6, buttonWidth, buttonHeight,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_ERROR),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> deleteSchematic());
        deleteButton.setButtonTexture(new TextTexture("§f§lDelete")
                .setWidth(buttonWidth)
                .setType(TextTexture.TextType.NORMAL));
        deleteButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFFEF5350),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));
        buttonSection.addWidget(deleteButton);

        ButtonWidget closeButton = new ButtonWidget(GUI_WIDTH - 110, 6, 90, buttonHeight,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_LIGHT),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> gui.entityPlayer.closeContainer());
        closeButton.setButtonTexture(new TextTexture("§7Close")
                .setWidth(90)
                .setType(TextTexture.TextType.NORMAL));
        closeButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));
        buttonSection.addWidget(closeButton);

        return buttonSection;
    }

    private String getMultiblockName(SchematicData schematic) {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            return "Multiblock Structure";
        }

        for (Map.Entry<BlockPos, BlockState> entry : schematic.getBlocks().entrySet()) {
            BlockState state = entry.getValue();
            String blockId = state.getBlock().getDescriptionId().toLowerCase();

            if (blockId.contains("gtceu")) {
                if (blockId.contains("casing") ||
                        blockId.contains("hatch") ||
                        blockId.contains("pipe") ||
                        blockId.contains("coil") ||
                        blockId.contains("glass")) {
                    continue;
                }

                String displayName = state.getBlock().getName().getString();
                if (displayName.length() > 35) {
                    displayName = displayName.substring(0, 32) + "...";
                }
                return displayName;
            }
        }

        String type = schematic.getMultiblockType();
        if (type != null && !type.isEmpty()) {
            type = type.replace("WorkableElectricMultiblockMachine", "Electric Machine")
                    .replace("CoilWorkableElectricMultiblockMachine", "Coil Machine")
                    .replace("ElectricMultiblockMachine", "Electric Machine");

            if (type.length() > 35) {
                type = type.substring(0, 32) + "...";
            }
            return type;
        }

        return "Multiblock Structure";
    }

    private void refreshLeftPanel() {
        if (schematicsListWidget == null) return;

        GTCEUTerminalMod.LOGGER.info("Refreshing left panel - {} schematics, selectedIndex: {}",
                schematics.size(), selectedIndex);

        schematicsListWidget.clearAllWidgets();
        populateSchematicsList();

        refreshRightPanel();
    }

    private void refreshRightPanel() {
        if (rightPanel == null) return;

        GTCEUTerminalMod.LOGGER.info("Refreshing right panel - selectedIndex: {}", selectedIndex);

        rightPanel.clearAllWidgets();

        int panelX = 228;
        int panelWidth = GUI_WIDTH - panelX - 4;
        int previewSize = panelWidth - 16;
        int previewHeight = GUI_HEIGHT - 82 - 20;

        WidgetGroup previewArea = new WidgetGroup(8, 8, previewSize, previewHeight);
        previewArea.setBackground(new ColorRectTexture(COLOR_BG_DARK));

        if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
            SchematicData selected = schematics.get(selectedIndex);
            GTCEUTerminalMod.LOGGER.info("Adding preview for: {}", selected.getName());
            addPreviewContent(previewArea, selected, previewSize);
        } else if (hasClipboard()) {
            LabelWidget clipboardInfo = new LabelWidget(10, 10, "§7Clipboard content:");
            previewArea.addWidget(clipboardInfo);

            LabelWidget hint = new LabelWidget(10, 30, "§8Save it to see preview");
            previewArea.addWidget(hint);
        } else {
            LabelWidget noPreview = new LabelWidget(previewSize/2 - 40, previewHeight/2, "§7No preview");
            previewArea.addWidget(noPreview);
        }

        rightPanel.addWidget(previewArea);
    }

    private void saveSchematic() {
        if (!hasClipboard()) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §cNo clipboard! Copy a multiblock first."),
                    true
            );
            return;
        }

        String name = nameInput.getCurrentString().trim();
        if (name.isEmpty()) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §cPlease enter a name!"),
                    true
            );
            return;
        }

        if ("Clipboard".equalsIgnoreCase(name)) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §c'Clipboard' is a reserved name!"),
                    true
            );
            return;
        }

        boolean isDuplicate = schematics.stream().anyMatch(s -> s.getName().equals(name));
        if (isDuplicate) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §cSchematic name already exists!"),
                    true
            );
            return;
        }

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketSchematicAction(CPacketSchematicAction.ActionType.SAVE, name, -1)
        );

        player.displayClientMessage(
                Component.literal("§a§l✓ §aSaved: §f" + name),
                true
        );

        new Thread(() -> {
            try {
                Thread.sleep(100);
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    reloadSchematicsFromItem();
                    refreshLeftPanel();
                    nameInput.setCurrentString("");
                    GTCEUTerminalMod.LOGGER.info("UI refreshed after save");
                });
            } catch (InterruptedException e) {
                GTCEUTerminalMod.LOGGER.error("Failed to refresh UI after save", e);
            }
        }, "SchematicUI-Refresh").start();
    }

    private void loadSchematic() {
        if (selectedIndex < 0 || selectedIndex >= schematics.size()) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §cNo schematic selected!"),
                    true
            );
            return;
        }

        SchematicData schematic = schematics.get(selectedIndex);

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketSchematicAction(CPacketSchematicAction.ActionType.LOAD,
                        schematic.getName(), selectedIndex)
        );

        player.displayClientMessage(
                Component.literal("§a§l✓ §aLoaded to clipboard: §f" + schematic.getName()),
                true
        );
    }

    private void deleteSchematic() {
        if (selectedIndex < 0 || selectedIndex >= schematics.size()) {
            player.displayClientMessage(
                    Component.literal("§c§lError: §cNo schematic selected!"),
                    true
            );
            return;
        }

        SchematicData schematic = schematics.get(selectedIndex);
        String deletedName = schematic.getName();

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketSchematicAction(CPacketSchematicAction.ActionType.DELETE,
                        deletedName, selectedIndex)
        );

        player.displayClientMessage(
                Component.literal("§c§l✗ §cDeleted: §f" + deletedName),
                true
        );

        new Thread(() -> {
            try {
                Thread.sleep(100);
                net.minecraft.client.Minecraft.getInstance().execute(() -> {
                    reloadSchematicsFromItem();
                    refreshLeftPanel();
                    GTCEUTerminalMod.LOGGER.info("UI refreshed after delete");
                });
            } catch (InterruptedException e) {
                GTCEUTerminalMod.LOGGER.error("Failed to refresh UI after delete", e);
            }
        }, "SchematicUI-Refresh").start();
    }

    private boolean hasClipboard() {
        ItemStack currentItem = holder.getTerminalItem();
        CompoundTag tag = currentItem.getTag();
        if (tag == null || !tag.contains("Clipboard")) {
            return false;
        }
        CompoundTag clipboardTag = tag.getCompound("Clipboard");
        return clipboardTag.contains("Blocks") &&
                !clipboardTag.getList("Blocks", 10).isEmpty();
    }
} // I hate this file