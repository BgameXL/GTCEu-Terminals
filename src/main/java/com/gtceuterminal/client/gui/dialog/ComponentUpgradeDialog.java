package com.gtceuterminal.client.gui.dialog;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.client.gui.multiblock.ComponentDetailUI;
import com.gtceuterminal.client.gui.widget.LDLMaterialListWidget;
import com.gtceuterminal.common.ae2.WirelessTerminalHandler;
import com.gtceuterminal.common.config.MaintenanceHatchConfig;
import com.gtceuterminal.common.material.ComponentUpgradeHelper;
import com.gtceuterminal.common.material.MaterialAvailability;
import com.gtceuterminal.common.material.MaterialCalculator;
import com.gtceuterminal.common.multiblock.ComponentGroup;
import com.gtceuterminal.common.multiblock.ComponentInfo;
import com.gtceuterminal.common.multiblock.ComponentType;
import com.gtceuterminal.common.multiblock.MultiblockInfo;
import com.gtceuterminal.common.network.CPacketComponentUpgrade;
import com.gtceuterminal.common.network.TerminalNetwork;
import com.gtceuterminal.common.upgrade.UniversalUpgradeCatalog;
import com.gtceuterminal.common.upgrade.UniversalUpgradeCatalogBuilder;

import com.gregtechceu.gtceu.api.GTCEuAPI;
import com.gregtechceu.gtceu.api.block.ICoilType;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;

import com.lowdragmc.lowdraglib.gui.texture.ColorBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.*;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private UniversalUpgradeCatalog universalCatalog;

    private Integer tierFilter = null; // null = ALL
    private DraggableScrollableWidgetGroup optionsScroll;
    private ComponentInfo currentRep;

    private int selectedTier = -1;

    // For component types where tier is not enough (e.g. maintenance variants, coil block variants)
    private String selectedUpgradeId = null;

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
        addWidget(new ImageWidget(0, 0, DIALOG_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(0, 0, 2, DIALOG_HEIGHT, new ColorRectTexture(COLOR_BORDER_LIGHT)));
        addWidget(new ImageWidget(DIALOG_WIDTH - 2, 0, 2, DIALOG_HEIGHT, new ColorRectTexture(COLOR_BORDER_DARK)));
        addWidget(new ImageWidget(0, DIALOG_HEIGHT - 2, DIALOG_WIDTH, 2, new ColorRectTexture(COLOR_BORDER_DARK)));

        addWidget(createHeader());
        addWidget(createInfoPanel());

        tierSelectionPanel = createTierSelection();
        addWidget(tierSelectionPanel);

        materialsPanel = createMaterialsPanel();
        addWidget(materialsPanel);

        addWidget(createButtons());

        var controller = multiblock.getController();
        if (controller instanceof MultiblockControllerMachine mmc) {
            universalCatalog = UniversalUpgradeCatalogBuilder.build(mmc, player.level());
        }
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

    // Tier selection / Option selection
    private WidgetGroup createTierSelection() {
        WidgetGroup panel = new WidgetGroup(10, 70, DIALOG_WIDTH - 20, 75);
        panel.setBackground(new ColorRectTexture(COLOR_BG_MEDIUM));

        ComponentInfo rep = group.getRepresentative();
        if (rep == null) return panel;
        if (tierFilter == null) tierFilter = rep.getTier(); // default = actual tier

        // Label
        String labelText;
        if (rep.getType() == ComponentType.MAINTENANCE || rep.getType() == ComponentType.COIL) {
            labelText = "§l§7Select Upgrade Option:";
        } else {
            labelText = "§l§7Select Target Tier:";
        }

        LabelWidget label = new LabelWidget(10, 4, labelText);
        label.setTextColor(COLOR_TEXT_WHITE);
        panel.addWidget(label);

        // Scroll area
        DraggableScrollableWidgetGroup scroll = new DraggableScrollableWidgetGroup(
                10, 18, (DIALOG_WIDTH - 20) - 20, 55
        );
        panel.addWidget(scroll);
        this.optionsScroll = scroll;
        this.currentRep = rep;

        // --- MAINTENANCE: grid of variant blocks from config ---
        if (rep.getType() == ComponentType.MAINTENANCE) {
            buildMaintenanceGrid(rep, scroll);
            return panel;
        }

        // --- COIL: grid built from GTCEuAPI.HEATING_COILS ---
        if (rep.getType() == ComponentType.COIL) {
            buildCoilGrid(rep, scroll);
            return panel;
        }

        // --- Default: universal candidates grid (por blockId real) ---
        if (!buildUniversalCandidatesGrid(rep, scroll, tierFilter)){
            // fallback al sistema viejo si no se pudo
            List<Integer> tiers = ComponentUpgradeHelper.getAvailableTiers(rep.getType());
            buildTierGrid(rep, scroll, tiers);
        }
        return panel;
    }

    private boolean buildUniversalCandidatesGrid(ComponentInfo rep,
                                                 DraggableScrollableWidgetGroup scroll,
                                                 Integer filterTier) {
        if (universalCatalog == null) return false;

        var opt = universalCatalog.get(rep.getPosition());
        if (opt == null) return false;

        var candidates = opt.candidates();
        if (candidates == null || candidates.isEmpty()) return false;

        String currentId = rep.getState().getBlock().builtInRegistryHolder().key().location().toString();

        // UI layout
        int btnWidth = 132;
        int btnHeight = 26;
        int spacing = 6;
        int perRow = 2;

        int xPos = 0;
        int yPos = 0;
        int added = 0;

        List<UniversalUpgradeCatalog.CandidatePart> list = new ArrayList<>();
        for (var c : candidates) {
            if (c == null || c.blockId() == null) continue;
            if (currentId != null && currentId.equalsIgnoreCase(c.blockId())) continue;
            if (filterTier != null && c.tier() != filterTier) continue; // <-- AQUÍ
            list.add(c);
        }

        if (list.isEmpty()) return false;

        list.sort(Comparator.comparingInt(UniversalUpgradeCatalog.CandidatePart::tier)
                .thenComparing(UniversalUpgradeCatalog.CandidatePart::blockId, String.CASE_INSENSITIVE_ORDER));

        for (var c : list) {
            if (c == null || c.blockId() == null) continue;

            // if (currentId != null && currentId.equalsIgnoreCase(c.blockId())) continue;

            if (added > 0 && added % perRow == 0) {
                xPos = 0;
                yPos += btnHeight + spacing;
            }

            // Display name
            String display = c.blockId();
            try {
                Block b = BuiltInRegistries.BLOCK.get(net.minecraft.resources.ResourceLocation.tryParse(c.blockId()));
                if (b != null) {
                    String loc = Component.translatable(b.getDescriptionId()).getString();
                    if (loc != null && !loc.isBlank()) display = loc;
                }
            } catch (Exception ignored) {}

            display = trimCommonSuffixes(display);
            if (display.length() > 22) {
                display = display.substring(0, 21) + "…";
            }

            boolean isSelected = c.blockId().equalsIgnoreCase(selectedUpgradeId);

            String text = "§f" + display + "\n§7(" + safeTierName(c.tier()) + ")";

            ButtonWidget btn = createOptionButton(
                    text,
                    isSelected,
                    xPos, yPos,
                    btnWidth, btnHeight,
                    () -> selectUpgradeOption(c.blockId(), c.tier())
            );

            scroll.addWidget(btn);

            xPos += btnWidth + spacing;
            added++;
        }

        return added > 0;
    }


    private void buildTierGrid(ComponentInfo rep, DraggableScrollableWidgetGroup scroll, List<Integer> tiers) {
        // Small buttons
        int btnWidth = 48;
        int btnHeight = 24;
        int spacing = 3;
        int buttonsPerRow = 7;

        int xPos = 0;
        int yPos = 0;
        int added = 0;

        ButtonWidget allBtn = createOptionButton("§fALL\n§7(any)", tierFilter == null,
                xPos, yPos, btnWidth, btnHeight,
                () -> onShowAllClicked());
        scroll.addWidget(allBtn);

        // Duplicate maintained to preserve order (in case of duplicates)
        java.util.LinkedHashSet<Integer> unique = new java.util.LinkedHashSet<>(tiers);

        for (int tier : unique) {
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

    private void buildMaintenanceGrid(ComponentInfo rep, DraggableScrollableWidgetGroup scroll) {
        String currentId = null;
        try {
            currentId = rep.getState().getBlock().builtInRegistryHolder().key().location().toString();
        } catch (Exception ignored) {}

        List<MaintenanceHatchConfig.MaintenanceHatchEntry> entries = new ArrayList<>(MaintenanceHatchConfig.getAllHatches());
        entries.removeIf(Objects::isNull);
        entries.sort((a, b) -> {
            int c = Integer.compare(a.tier, b.tier);
            if (c != 0) return c;
            return String.valueOf(a.displayName).compareToIgnoreCase(String.valueOf(b.displayName));
        });

        // Smaller boxes (like tier grid, but wider)
        int btnWidth = 118;
        int btnHeight = 26;
        int spacing = 4;
        int perRow = 3;

        int xPos = 0;
        int yPos = 0;
        int added = 0;

        for (var e : entries) {
            if (e == null || e.blockId == null) continue;
            if (currentId != null && currentId.equalsIgnoreCase(e.blockId)) continue;

            if (added > 0 && added % perRow == 0) {
                xPos = 0;
                yPos += btnHeight + spacing;
            }

            String name = (e.displayName != null && !e.displayName.isBlank()) ? e.displayName : e.blockId;
            name = trimCommonSuffixes(name);

            String tierTag = (e.tierName != null && !e.tierName.isBlank()) ? e.tierName : safeTierName(e.tier);

            String text = "§f" + name + "\n§7(" + tierTag + ")";

            boolean isSelected = e.blockId.equals(selectedUpgradeId);

            ButtonWidget btn = createOptionButton(
                    text,
                    isSelected,
                    xPos, yPos,
                    btnWidth, btnHeight,
                    () -> selectUpgradeOption(e.blockId, e.tier)
            );

            scroll.addWidget(btn);
            xPos += btnWidth + spacing;
            added++;
        }
    }

    private void buildCoilGrid(ComponentInfo rep, DraggableScrollableWidgetGroup scroll) {
        // Build list from GTCEuAPI.HEATING_COILS
        class CoilEntry {
            final String blockId;
            final String display;
            final int tier;

            CoilEntry(String blockId, String display, int tier) {
                this.blockId = blockId;
                this.display = display;
                this.tier = tier;
            }
        }

        List<CoilEntry> list = new ArrayList<>();
        for (var entry : GTCEuAPI.HEATING_COILS.entrySet()) {
            ICoilType coilType = entry.getKey();
            if (coilType == null) continue;

            Block block = null;
            try {
                block = entry.getValue() != null ? entry.getValue().get() : null;
            } catch (Exception ignored) {}

            if (block == null) continue;

            String blockId = BuiltInRegistries.BLOCK.getKey(block).toString();
            String name = Component.translatable(block.getDescriptionId()).getString(); // localized
            if (name == null || name.isBlank()) name = coilType.getName();

            name = trimCommonSuffixes(name);
            list.add(new CoilEntry(blockId, name, coilType.getTier()));
        }

        // Sort by tier then name
        list.sort(Comparator
                .comparingInt((CoilEntry c) -> c.tier)
                .thenComparing(c -> c.display, String.CASE_INSENSITIVE_ORDER));

        String currentId = null;
        try {
            currentId = rep.getState().getBlock().builtInRegistryHolder().key().location().toString();
        } catch (Exception ignored) {}

        int btnWidth = 118;
        int btnHeight = 26;
        int spacing = 4;
        int perRow = 3;

        int xPos = 0;
        int yPos = 0;
        int added = 0;

        for (CoilEntry e : list) {
            if (e == null || e.blockId == null) continue;
            if (currentId != null && currentId.equalsIgnoreCase(e.blockId)) continue;

            if (added > 0 && added % perRow == 0) {
                xPos = 0;
                yPos += btnHeight + spacing;
            }

            boolean isSelected = e.blockId.equals(selectedUpgradeId);

            String text = "§f" + e.display + "\n§7(" + safeTierName(e.tier) + ")";

            ButtonWidget btn = createOptionButton(
                    text,
                    isSelected,
                    xPos, yPos,
                    btnWidth, btnHeight,
                    () -> selectUpgradeOption(e.blockId, e.tier)
            );

            scroll.addWidget(btn);

            xPos += btnWidth + spacing;
            added++;
        }
    }

    private static String trimCommonSuffixes(String s) {
        if (s == null) return "";
        String out = s;
        out = out.replace(" Coil Block", "");
        out = out.replace(" Heating Coil", "");
        out = out.replace(" Maintenance Hatch", " Maintenance");
        return out.trim();
    }

    private WidgetGroup createButtons() {
        WidgetGroup buttons = new WidgetGroup(10, DIALOG_HEIGHT - 35, DIALOG_WIDTH - 20, 25);

        confirmButton = new ButtonWidget(
                0, 0, 140, 22,
                new GuiTextureGroup(
                        new ColorRectTexture(0xFF2E7D32),
                        new ColorBorderTexture(1, COLOR_SUCCESS)
                ),
                cd -> performUpgrade()
        );
        confirmButton.setButtonTexture(new TextTexture("§eConfirm Change")
                .setWidth(140)
                .setType(TextTexture.TextType.NORMAL));

        confirmButton.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(0xFF43A047),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        ButtonWidget cancel = new ButtonWidget(
                DIALOG_WIDTH - 20 - 140, 0, 140, 22,
                new GuiTextureGroup(
                        new ColorRectTexture(COLOR_BG_LIGHT),
                        new ColorBorderTexture(1, COLOR_BORDER_LIGHT)
                ),
                cd -> close()
        );
        cancel.setButtonTexture(new TextTexture("§fCancel")
                .setWidth(140)
                .setType(TextTexture.TextType.NORMAL));
        cancel.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(COLOR_BG_LIGHT),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE)
        ));

        buttons.addWidget(confirmButton);
        buttons.addWidget(cancel);

        return buttons;
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
        return "T" + tier;
    }

    private ButtonWidget createTierButton(int tier, int x, int y, int width, int height) {
        String tierName = safeTierName(tier);
        boolean isSelected = (tier == selectedTier);

        // Texto reutilizado (normal y hover) para que no desaparezca
        TextTexture text = new TextTexture("§f" + tierName)
                .setWidth(width)
                .setType(TextTexture.TextType.NORMAL);

        int bg = isSelected ? 0x6600FF00 : COLOR_BG_LIGHT; // seleccionado: verde TRANSPARENTE
        int border = isSelected ? 0xFF00FF00 : COLOR_BORDER_LIGHT;

        ButtonWidget btn = new ButtonWidget(
                x, y, width, height,
                new GuiTextureGroup(
                        new ColorRectTexture(bg),
                        new ColorBorderTexture(1, border),
                        text
                ),
                cd -> onTierButtonClicked(tier)
        );

        btn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(bg),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE),
                text
        ));

        return btn;
    }

    private ButtonWidget createOptionButton(String textLines,
                                            boolean isSelected,
                                            int x, int y,
                                            int width, int height,
                                            Runnable onClick) {

        TextTexture text = new TextTexture(textLines)
                .setWidth(width)
                .setType(TextTexture.TextType.NORMAL);

        int bg = isSelected ? 0x6600FF00 : COLOR_BG_LIGHT; // verde transparente al seleccionar
        int border = isSelected ? 0xFF00FF00 : COLOR_BORDER_LIGHT;

        ButtonWidget btn = new ButtonWidget(
                x, y, width, height,
                new GuiTextureGroup(
                        new ColorRectTexture(bg),
                        new ColorBorderTexture(1, border),
                        text
                ),
                cd -> onClick.run()
        );

        btn.setHoverTexture(new GuiTextureGroup(
                new ColorRectTexture(bg),
                new ColorBorderTexture(1, COLOR_TEXT_WHITE),
                text
        ));

        return btn;
    }

    private void refreshUpgradeOptions() {
        if (optionsScroll == null || currentRep == null) return;

        optionsScroll.clearAllWidgets();

        // 1) rebuild options list (universal o fallback) with current filter
        if (!buildUniversalCandidatesGrid(currentRep, optionsScroll, tierFilter)) {
            // Old falback
            List<Integer> tiers = ComponentUpgradeHelper.getAvailableTiers(currentRep.getType());
            buildTierGrid(currentRep, optionsScroll, tiers);
        }

        // 2) recalculate materials based on new selection (which may have been reset)
        calculateMaterials();
    }


    private void onTierButtonClicked(int tier) {
        this.selectedTier = tier;
        this.tierFilter = tier;
        this.selectedUpgradeId = null;
        refreshUpgradeOptions();
    }

    private void onShowAllClicked() {
        this.tierFilter = null;
        this.selectedUpgradeId = null;
        refreshUpgradeOptions();
    }

    private void refreshUniversalOptions() {
        if (optionsScroll == null || currentRep == null) return;
        try {
            optionsScroll.clearAllWidgets();
        } catch (Throwable t1) {
            try {
                optionsScroll.clearAllWidgets();
            } catch (Throwable t2) {
                // optionsScroll.widgets.clear();
            }
        }

        if (!buildUniversalCandidatesGrid(currentRep, optionsScroll, tierFilter)) {
            List<Integer> tiers = ComponentUpgradeHelper.getAvailableTiers(currentRep.getType());
            buildTierGrid(currentRep, optionsScroll, tiers);
        }
        calculateMaterials();
    }


    private void selectUpgradeOption(String upgradeId, int tier) {
        this.selectedUpgradeId = upgradeId;
        this.selectedTier = tier; // keep tier for validation/materials

        refreshTierSelectionPanel();
        calculateMaterials();
        refreshMaterialsPanel();

        GTCEUTerminalMod.LOGGER.info("Selected upgrade option: {} (tier {})", upgradeId, tier);
    }

    private void calculateMaterials() {
        if (selectedTier == -1) return;

        ComponentInfo rep = group.getRepresentative();
        if (rep == null) return;

        Map<Item, Integer> singleRequired = (selectedUpgradeId != null && !selectedUpgradeId.isBlank())
                ? ComponentUpgradeHelper.getUpgradeItemsForBlockId(selectedUpgradeId)
                : ComponentUpgradeHelper.getUpgradeItems(rep, selectedTier);

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

        if (!player.level().isClientSide && WirelessTerminalHandler.isLinked(getWirelessTerminal(player))) {
            LabelWidget warning = new LabelWidget(5, 16, "§e⚠ ME materials will be verified on confirmation");
            warning.setTextColor(0xFFFFAA00);
            panel.addWidget(warning);
        }

        if (player.isCreative()) {
            LabelWidget creativeLabel = new LabelWidget(5, 60, "§a§lCREATIVE MODE");
            creativeLabel.setTextColor(COLOR_SUCCESS);
            panel.addWidget(creativeLabel);

            LabelWidget infoLabel = new LabelWidget(5, 75, "§7Select an option to see materials");
            infoLabel.setTextColor(COLOR_TEXT_GRAY);
            panel.addWidget(infoLabel);
        } else if (selectedTier == -1) {
            LabelWidget placeholder = new LabelWidget(5, 70, "§7Select an option to see materials");
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

            LDLMaterialListWidget list = new LDLMaterialListWidget(
                    0, yOffset, DIALOG_WIDTH - 20, height, materials
            );
            materialsPanel.addWidget(list);
        } else {
            if (player.isCreative()) {
                LabelWidget info = new LabelWidget(5, 75, "§7Select an option to see materials");
                info.setTextColor(COLOR_TEXT_GRAY);
                materialsPanel.addWidget(info);
            } else if (selectedTier == -1) {
                LabelWidget info = new LabelWidget(5, 70, "§7Select an option to see materials");
                info.setTextColor(COLOR_TEXT_GRAY);
                materialsPanel.addWidget(info);
            }
        }

        addWidget(materialsPanel);
    }

    private ItemStack getWirelessTerminal(Player player) {
        if (player == null) return ItemStack.EMPTY;

        // manos
        ItemStack main = player.getMainHandItem();
        if (WirelessTerminalHandler.isWirelessTerminal(main)) return main;

        ItemStack off = player.getOffhandItem();
        if (WirelessTerminalHandler.isWirelessTerminal(off)) return off;

        // inventario
        for (ItemStack s : player.getInventory().items) {
            if (WirelessTerminalHandler.isWirelessTerminal(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    private void performUpgrade() {
        List<net.minecraft.core.BlockPos> positions = new java.util.ArrayList<>();
        for (ComponentInfo component : group.getComponents()) {
            positions.add(component.getPosition());
        }

        TerminalNetwork.CHANNEL.sendToServer(
                new CPacketComponentUpgrade(
                        positions,
                        selectedTier,
                        selectedUpgradeId // can be null if tier-based only
                )
        );

        player.displayClientMessage(
                Component.literal("§aChanging " + group.getCount() + " components..."),
                true
        );

        close();
        if (parentDialog != null) parentDialog.close();

    }
} // First worst file.