package com.gtceuterminal.client.gui.multiblock;

import com.gtceuterminal.client.gui.widget.WallpaperWidget;
import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.widget.SchematicPreviewWidget;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.network.CPacketSchematicAction;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.gtceuterminal.client.gui.theme.ThemeEditorDialog;
import com.gtceuterminal.common.theme.ItemTheme;
import com.gtceuterminal.client.gui.factory.SchematicItemUIFactory;

import com.lowdragmc.lowdraglib.gui.factory.HeldItemUIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
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

    // ─── Theme-driven instance colors ─────────────────────────────────────────
    private final int COLOR_BG_DARK;
    private final int COLOR_BG_MEDIUM;
    private final int COLOR_BG_LIGHT;
    private final int COLOR_BORDER_LIGHT;
    private static final int COLOR_BORDER_DARK  = 0xFF0A0A0A;
    private static final int COLOR_TEXT_WHITE   = 0xFFFFFFFF;
    private static final int COLOR_TEXT_GRAY    = 0xFFAAAAAA;
    private static final int COLOR_HOVER        = 0x40FFFFFF;
    private static final int COLOR_SUCCESS      = 0xFF4CAF50;
    private static final int COLOR_ERROR = 0xFFFF5252;
    private static final int COLOR_WARNING = 0xFFFFA726;
    private static final int COLOR_INFO = 0xFF42A5F5;

    private final IUIHolder uiHolder;
    private final ItemStack terminalItem; // the actual item stack
    private final Player player;
    private List<SchematicData> schematics = new ArrayList<>();
    private int selectedIndex = -1;
    private ModularUI gui;
    private TextFieldWidget nameInput;
    private WidgetGroup mainGroup;  // root group — needed by ThemeEditorDialog
    private ItemTheme theme;
    private WidgetGroup rightPanel;
    private DraggableScrollableWidgetGroup schematicsListWidget;

    // ── Constructor B: HeldItemUIFactory path ─────────────────────────────────
    public SchematicInterfaceUI(HeldItemUIFactory.HeldItemHolder heldHolder) {
        this.uiHolder      = heldHolder;
        this.terminalItem  = heldHolder.held;
        this.player        = heldHolder.player;
        this.theme         = ItemTheme.load(this.terminalItem);
        COLOR_BG_DARK      = theme.bgColor;
        COLOR_BG_MEDIUM    = theme.panelColor;
        COLOR_BG_LIGHT     = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        COLOR_BORDER_LIGHT = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
        loadSchematics();
    }

    // ── Constructor C: SchematicItemUIFactory path ────────────────────────────
    public SchematicInterfaceUI(SchematicItemUIFactory.Holder holder, Player player) {
        this.uiHolder      = holder;
        this.terminalItem  = holder.getTerminalItem();
        this.player        = player;
        this.theme         = ItemTheme.load(this.terminalItem);
        COLOR_BG_DARK      = theme.bgColor;
        COLOR_BG_MEDIUM    = theme.panelColor;
        COLOR_BG_LIGHT     = theme.isNativeStyle() ? 0xFF3A3A3A : theme.accent(0xAA);
        COLOR_BORDER_LIGHT = theme.isNativeStyle() ? 0xFF555555 : theme.accent(0xFF);
        loadSchematics();
    }

    private void loadSchematics() {
        this.schematics = new ArrayList<>();

        ItemStack currentItem = terminalItem;
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

        ItemStack currentItem = terminalItem;
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
        this.mainGroup = new WidgetGroup(0, 0, GUI_WIDTH, GUI_HEIGHT);
        WidgetGroup mainGroup = this.mainGroup;
        mainGroup.setBackground(new com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture(0x00000000));
        if (!theme.isNativeStyle()) { mainGroup.addWidget(new WallpaperWidget(0, 0, GUI_WIDTH, GUI_HEIGHT, () -> this.theme)); }

        mainGroup.addWidget(createBorders());
        mainGroup.addWidget(createHeader());
        mainGroup.addWidget(createLeftPanel());

        this.rightPanel = createRightPanel();
        mainGroup.addWidget(rightPanel);

        mainGroup.addWidget(createButtonSection());

        ModularUI ui = createUIWithViewport(mainGroup);
        this.gui = ui;
        return ui;
    }

    private ModularUI createUIWithViewport(WidgetGroup content) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();

            int margin = 10;
            int maxW = sw - margin * 2;
            int maxH = sh - margin * 2;

            if (GUI_WIDTH <= maxW && GUI_HEIGHT <= maxH) {
                // Cabe sin scroll
                ModularUI ui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), uiHolder, player);
                ui.widget(content);
                ui.background(theme.modularUIBackground());
                return ui;
            } else {
                // Necesita scroll
                int viewportW = Math.min(GUI_WIDTH, maxW);
                int viewportH = Math.min(GUI_HEIGHT, maxH);

                DraggableScrollableWidgetGroup viewport =
                        new DraggableScrollableWidgetGroup(0, 0, viewportW, viewportH);
                viewport.setYScrollBarWidth(8);
                viewport.setYBarStyle(
                        new ColorRectTexture(COLOR_BORDER_DARK),
                        new ColorRectTexture(COLOR_BORDER_LIGHT)
                );
                viewport.addWidget(content);

                WidgetGroup root = new WidgetGroup(0, 0, viewportW, viewportH);
                root.addWidget(viewport);

                ModularUI ui = new ModularUI(new Size(viewportW, viewportH), uiHolder, player);
                ui.widget(root);
                ui.background(theme.modularUIBackground());
                return ui;
            }
        } catch (Throwable t) {
            ModularUI ui = new ModularUI(new Size(GUI_WIDTH, GUI_HEIGHT), uiHolder, player);
            ui.widget(content);
            ui.background(theme.modularUIBackground());
            return ui;
        }
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

        LabelWidget titleLabel = new LabelWidget(12, 10,
                Component.translatable("gui.gtceuterminal.schematic_interface.title").getString());
        titleLabel.setTextColor(COLOR_TEXT_WHITE);
        header.addWidget(titleLabel);

        boolean hasClipboard = hasClipboard();
        String clipboardText = hasClipboard
                ? Component.translatable("gui.gtceuterminal.schematic_interface.clipboard.ready").getString()
                : Component.translatable("gui.gtceuterminal.schematic_interface.clipboard.empty").getString();
        LabelWidget clipboardLabel = new LabelWidget(GUI_WIDTH - 180, 10, clipboardText);
        clipboardLabel.setTextColor(hasClipboard ? COLOR_SUCCESS : COLOR_TEXT_GRAY);
        header.addWidget(clipboardLabel);

        // ⚙ Theme settings button
        ButtonWidget gearBtn = new ButtonWidget(GUI_WIDTH - 22, 8, 14, 14,
                new ColorRectTexture(0x00000000),
                cd -> ThemeEditorDialog.open(mainGroup, ItemTheme.load(terminalItem)));
        gearBtn.setButtonTexture(new TextTexture("§7⚙").setWidth(14).setType(TextTexture.TextType.NORMAL));
        gearBtn.setHoverTexture(new ColorRectTexture(COLOR_HOVER));
        gearBtn.setHoverTooltips(Component.translatable("gui.gtceuterminal.theme_settings").getString());
        header.addWidget(gearBtn);

        return header;
    }

    private WidgetGroup createLeftPanel() {
        int panelWidth = 160;
        WidgetGroup leftPanel = new WidgetGroup(10, 35, 160, GUI_HEIGHT - 45);
        leftPanel.setBackground(theme.panelTexture());

        LabelWidget nameLabel = new LabelWidget(8, 8,
                Component.translatable("gui.gtceuterminal.schematic_interface.name_label").getString());
        nameLabel.setTextColor(COLOR_TEXT_GRAY);
        leftPanel.addWidget(nameLabel);

        this.nameInput = new TextFieldWidget(8, 22, panelWidth - 16, 14, null, s -> {});
        nameInput.setMaxStringLength(32);
        nameInput.setTextColor(COLOR_TEXT_WHITE);
        nameInput.setBackground(theme.backgroundTexture());
        nameInput.setBordered(true);
        leftPanel.addWidget(nameInput);

        LabelWidget listTitle = new LabelWidget(8, 44,
                Component.translatable("gui.gtceuterminal.schematic_interface.saved_schematics_label").getString());
        listTitle.setTextColor(COLOR_TEXT_GRAY);
        leftPanel.addWidget(listTitle);

        this.schematicsListWidget = new DraggableScrollableWidgetGroup(
                8, 58, panelWidth - 16, GUI_HEIGHT - 150
        );
        schematicsListWidget.setBackground(theme.backgroundTexture());

        populateSchematicsList();

        leftPanel.addWidget(schematicsListWidget);
        return leftPanel;
    }

    private void populateSchematicsList() {
        if (schematics.isEmpty()) {
            LabelWidget emptyLabel = new LabelWidget(10, 40,
                    Component.translatable("gui.gtceuterminal.schematic_interface.no_schematics_saved").getString());
            schematicsListWidget.addWidget(emptyLabel);

            LabelWidget hintLabel = new LabelWidget(10, 55,
                    Component.translatable("gui.gtceuterminal.schematic_interface.hint_formed_1").getString());
            schematicsListWidget.addWidget(hintLabel);

            LabelWidget hintLabel2 = new LabelWidget(10, 65,
                    Component.translatable("gui.gtceuterminal.schematic_interface.hint_formed_2").getString());
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
                                Component.translatable(
                                        "gui.gtceuterminal.schematic_interface.selected",
                                        schematic.getName()
                                ),
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
        String sizeInfo = Component.translatable(
                "gui.gtceuterminal.schematic_interface.entry.blocks",
                blockCount
        ).getString();
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
        int panelX = 180;
        int panelWidth = GUI_WIDTH - panelX - 4;
        WidgetGroup rightPanel = new WidgetGroup(panelX, 38, panelWidth, GUI_HEIGHT - 82);
        rightPanel.setBackground(theme.panelTexture());

        int previewSize = panelWidth - 16;
        int previewHeight = GUI_HEIGHT - 82 - 16;
        WidgetGroup previewArea = new WidgetGroup(8, 8, previewSize, previewHeight);
        previewArea.setBackground(theme.backgroundTexture());

        GTCEUTerminalMod.LOGGER.info("Creating right panel - selectedIndex: {}, schematics.size(): {}",
                selectedIndex, schematics.size());

        if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
            SchematicData selected = schematics.get(selectedIndex);
            GTCEUTerminalMod.LOGGER.info("Adding preview for selected schematic: {}", selected.getName());
            addPreviewContent(previewArea, selected, previewSize);
        } else if (hasClipboard()) {
            GTCEUTerminalMod.LOGGER.info("No selection, showing clipboard hint");
            LabelWidget clipboardInfo = new LabelWidget(10, 10,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.clipboard_content").getString());
            previewArea.addWidget(clipboardInfo);

            LabelWidget hint = new LabelWidget(10, 30,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.save_to_see").getString());
            previewArea.addWidget(hint);
        } else {
            GTCEUTerminalMod.LOGGER.info("No selection and no clipboard");
            LabelWidget noPreview = new LabelWidget(previewSize/2 - 40, previewHeight/2,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.no_preview").getString());
            previewArea.addWidget(noPreview);
        }

        rightPanel.addWidget(previewArea);
        return rightPanel;
    }

    private void addPreviewContent(WidgetGroup area, SchematicData schematic, int size) {
        GTCEUTerminalMod.LOGGER.info("Creating preview for schematic: {} with {} blocks",
                schematic.getName(), schematic.getBlocks().size());

        int actualWidth = area.getSize() != null ? area.getSize().width : size;
        int actualHeight = area.getSize() != null ? area.getSize().height : size;
        int previewHeight = actualHeight - 90;

        // Preview 3D
        if (player.level().isClientSide) {
            SchematicPreviewWidget previewWidget = new SchematicPreviewWidget(
                    5, 5, actualWidth - 10, previewHeight, schematic
            );
            previewWidget.setBackground(new ColorRectTexture(0xFF0A0A0A));
            area.addWidget(previewWidget);
        }

        int textY = 5 + previewHeight + 5;

        // Name label
        String displayName = getMultiblockName(schematic);
        if (displayName.length() > 25) displayName = displayName.substring(0, 22) + "...";

        LabelWidget nameLabel = new LabelWidget(10, textY, "§f§l" + displayName);
        nameLabel.setTextColor(COLOR_TEXT_WHITE);
        area.addWidget(nameLabel);
        textY += 14;

        // Info label
        BlockPos size1 = schematic.getSize();
        String infoText = Component.translatable(
                        "gui.gtceuterminal.schematic_interface.preview.info",
                        schematic.getBlocks().size(),
                        size1.getX(), size1.getY(), size1.getZ()
                ).getString();

        LabelWidget infoLabel = new LabelWidget(10, textY, infoText);
        infoLabel.setTextColor(COLOR_TEXT_GRAY);
        area.addWidget(infoLabel);

        textY += 12;
        LabelWidget zoomHint = new LabelWidget(10, textY,
                Component.translatable("gui.gtceuterminal.schematic_interface.preview.zoom_hint").getString());
        zoomHint.setTextColor(0xFF666666);
        area.addWidget(zoomHint);

        textY += 12;
        LabelWidget rotation = new LabelWidget(10, textY,
                Component.translatable("gui.gtceuterminal.schematic_interface.preview.rotation_hint").getString());
        rotation.setTextColor(0xFF666666);
        area.addWidget(rotation);
    }

    private WidgetGroup createButtonSection() {
        WidgetGroup buttonSection = new WidgetGroup(4, GUI_HEIGHT - 40, GUI_WIDTH - 8, 36);
        buttonSection.setBackground(theme.panelTexture());

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
        saveButton.setButtonTexture(new TextTexture(
                        Component.translatable("gui.gtceuterminal.schematic_interface.button.save").getString())
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
        loadButton.setButtonTexture(new TextTexture(
                        Component.translatable("gui.gtceuterminal.schematic_interface.button.load").getString())
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
        deleteButton.setButtonTexture(new TextTexture(
                        Component.translatable("gui.gtceuterminal.schematic_interface.button.delete").getString())
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
        closeButton.setButtonTexture(new TextTexture(
                        Component.translatable("gui.gtceuterminal.schematic_interface.button.close").getString())
                .setWidth(90)
                .setType(TextTexture.TextType.NORMAL));
        closeButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_DARK),
                new ColorBorderTexture(2, COLOR_TEXT_WHITE)
        ));
        buttonSection.addWidget(closeButton);

        return buttonSection;
    }

    /** ── Open Planner (Descoment this if you want to use the Planner mode. It is not implemented but it will) ────────
     ButtonWidget plannerButton = new ButtonWidget(310, 6, 52, buttonHeight,
     new GuiTextureGroup(
     new ColorRectTexture(0xFF1A2A3A),
     new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
     ),
     cd -> {
     // Use reflection so SchematicInterfaceUI can be loaded on the dedicated
     // server without triggering classloading of PlannerScreen → Screen.
     final java.util.List<SchematicData> snap = new java.util.ArrayList<>(schematics);
     gui.entityPlayer.closeContainer();
     net.minecraft.client.Minecraft.getInstance().tell(() -> {
     try {
     Class<?> cls = Class.forName(
     "com.gtceuterminal.client.gui.planner.PlannerScreen");
     net.minecraft.client.gui.screens.Screen screen =
     (net.minecraft.client.gui.screens.Screen)
     cls.getConstructor(java.util.List.class).newInstance(snap);
     net.minecraft.client.Minecraft.getInstance().setScreen(screen);
     } catch (Exception e) {
     GTCEUTerminalMod.LOGGER.error("Failed to open PlannerScreen", e);
     }
     });
     });
     plannerButton.setButtonTexture(new TextTexture("§9⊞ Planner")
     .setWidth(52)
     .setType(TextTexture.TextType.NORMAL));
     plannerButton.setHoverTexture(new GuiTextureGroup(
     new ColorRectTexture(0xFF243040),
     new ColorBorderTexture(2, COLOR_TEXT_WHITE)
     ));
     plannerButton.setHoverTooltips("Open 2D placement planner");
     buttonSection.addWidget(plannerButton);

     return buttonSection;
     **/

    // Helper methods
    private String getMultiblockName(SchematicData schematic) {
        if (schematic == null || schematic.getBlocks().isEmpty()) {
            return Component.translatable("gui.gtceuterminal.schematic_interface.fallback.multiblock_structure").getString();
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

        return Component.translatable("gui.gtceuterminal.schematic_interface.fallback.multiblock_structure").getString();
    }

    private void refreshLeftPanel() {
        if (schematicsListWidget == null) return;

        GTCEUTerminalMod.LOGGER.info("Refreshing left panel - {} schematics, selectedIndex: {}",
                schematics.size(), selectedIndex);

        schematicsListWidget.clearAllWidgets();
        populateSchematicsList();

        refreshRightPanel();
    }

    // Checks if the clipboard content is present in the current item stack.
    private void refreshRightPanel() {
        if (rightPanel == null) return;

        GTCEUTerminalMod.LOGGER.info("Refreshing right panel - selectedIndex: {}", selectedIndex);

        rightPanel.clearAllWidgets();

        int panelX = 180;
        int panelWidth = GUI_WIDTH - panelX - 4;
        int previewSize = panelWidth - 16;
        int previewHeight = GUI_HEIGHT - 82 - 16;

        WidgetGroup previewArea = new WidgetGroup(8, 8, previewSize, previewHeight);
        previewArea.setBackground(theme.backgroundTexture());

        if (selectedIndex >= 0 && selectedIndex < schematics.size()) {
            SchematicData selected = schematics.get(selectedIndex);
            GTCEUTerminalMod.LOGGER.info("Adding preview for: {}", selected.getName());
            addPreviewContent(previewArea, selected, previewSize);
        } else if (hasClipboard()) {
            LabelWidget clipboardInfo = new LabelWidget(10, 10,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.clipboard_content").getString());
            previewArea.addWidget(clipboardInfo);

            LabelWidget hint = new LabelWidget(10, 30,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.save_to_see").getString());
            previewArea.addWidget(hint);
        } else {
            LabelWidget noPreview = new LabelWidget(previewSize/2 - 40, previewHeight/2,
                    Component.translatable("gui.gtceuterminal.schematic_interface.preview.no_preview").getString());
            previewArea.addWidget(noPreview);
        }

        rightPanel.addWidget(previewArea);
    }

    // Action methods
    private void saveSchematic() {
        if (!hasClipboard()) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.no_clipboard"),
                    true
            );
            return;
        }

        String name = nameInput.getCurrentString().trim();
        if (name.isEmpty()) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.enter_name"),
                    true
            );
            return;
        }

        if ("Clipboard".equalsIgnoreCase(name)) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.reserved_name"),
                    true
            );
            return;
        }

        // Check against the local client list only — the server also validates independently.
        boolean isDuplicate = schematics.stream().anyMatch(s -> s.getName().equalsIgnoreCase(name));
        if (isDuplicate) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.name_exists"),
                    true
            );
            return;
        }

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketSchematicAction(CPacketSchematicAction.ActionType.SAVE, name, -1)
        );

        // Build a local copy of the clipboard data so we can add it to the client list immediately,
        ItemStack currentItem = terminalItem;
        CompoundTag itemTag = currentItem.getTag();
        if (itemTag != null && itemTag.contains("Clipboard")) {
            try {
                SchematicData clipData = SchematicData.fromNBT(
                        itemTag.getCompound("Clipboard"),
                        player.level().registryAccess()
                );
                SchematicData saved = new SchematicData(
                        name,
                        clipData.getMultiblockType(),
                        clipData.getBlocks(),
                        clipData.getBlockEntities(),
                        clipData.getOriginalFacing()
                );
                schematics.add(saved);
                selectedIndex = schematics.size() - 1;
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Failed to build local schematic copy after save", e);
            }
        }

        nameInput.setCurrentString("");
        refreshLeftPanel();

        player.displayClientMessage(
                Component.translatable("gui.gtceuterminal.schematic_interface.chat.success.saved", name),
                true
        );
    }

    private void loadSchematic() {
        if (selectedIndex < 0 || selectedIndex >= schematics.size()) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.no_schematic_selected"),
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
                Component.translatable(
                        "gui.gtceuterminal.schematic_interface.chat.success.loaded_to_clipboard",
                        schematic.getName()
                ),
                true
        );
    }

    private void deleteSchematic() {
        if (selectedIndex < 0 || selectedIndex >= schematics.size()) {
            player.displayClientMessage(
                    Component.translatable("gui.gtceuterminal.schematic_interface.chat.error.no_schematic_selected"),
                    true
            );
            return;
        }

        SchematicData schematic = schematics.get(selectedIndex);
        String deletedName = schematic.getName();

        // Tell the server to delete by name — NOT by index. This is important because the client index might be out of sync with the server if there were any changes.
        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketSchematicAction(CPacketSchematicAction.ActionType.DELETE,
                        deletedName, -1)
        );

        // Update the client list immediately instead of waiting for the server to echo back.
        schematics.remove(selectedIndex);
        if (selectedIndex >= schematics.size()) {
            selectedIndex = schematics.size() - 1;
        }

        refreshLeftPanel();

        player.displayClientMessage(
                Component.translatable(
                        "gui.gtceuterminal.schematic_interface.chat.success.deleted",
                        deletedName
                ),
                true
        );
    }


    private boolean hasClipboard() {
        ItemStack currentItem = terminalItem;
        CompoundTag tag = currentItem.getTag();
        if (tag == null || !tag.contains("Clipboard")) {
            return false;
        }
        CompoundTag clipboardTag = tag.getCompound("Clipboard");
        return clipboardTag.contains("Blocks") &&
                !clipboardTag.getList("Blocks", 10).isEmpty();
    }

    // ── Static factory methods ────────────────────────────────────────────────
    public static ModularUI create(HeldItemUIFactory.HeldItemHolder heldHolder) {
        return new SchematicInterfaceUI(heldHolder).createUI();
    }

    public static ModularUI create(SchematicItemUIFactory.Holder holder, Player player) {
        return new SchematicInterfaceUI(holder, player).createUI();
    }
} // I hate this file