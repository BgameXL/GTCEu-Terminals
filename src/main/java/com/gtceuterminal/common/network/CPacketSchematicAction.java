package com.gtceuterminal.common.network;

import com.gtceuterminal.GTCEUTerminalMod;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class CPacketSchematicAction {
    private final ActionType actionType;
    private final String schematicName;
    private final int schematicIndex;

    public CPacketSchematicAction(ActionType actionType, String schematicName, int schematicIndex) {
        this.actionType = actionType;
        this.schematicName = schematicName;
        this.schematicIndex = schematicIndex;
    }

    public CPacketSchematicAction(FriendlyByteBuf buf) {
        this.actionType = buf.readEnum(ActionType.class);
        this.schematicName = buf.readUtf();
        this.schematicIndex = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeEnum(this.actionType);
        buf.writeUtf(this.schematicName);
        buf.writeInt(this.schematicIndex);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) {
                return;
            }

            ItemStack stack = findSchematicInterface(player);
            if (stack.isEmpty()) {
                return;
            }

            CompoundTag stackTag = stack.getOrCreateTag();

            switch (this.actionType) {
                case SAVE:
                    if (!stackTag.contains("Clipboard")) {
                        player.displayClientMessage(Component.literal("§eNo clipboard to save!"), true);
                        return;
                    }

                    CompoundTag clipboardTag = stackTag.getCompound("Clipboard");
                    if (!clipboardTag.contains("Blocks") || clipboardTag.getList("Blocks", 10).isEmpty()) {
                        player.displayClientMessage(Component.literal("§eClipboard is empty!"), true);
                        return;
                    }

                    SchematicData clipboard = SchematicData.fromNBT(clipboardTag, player.level().registryAccess());

                    List<SchematicData> existingSchematics = loadSchematics(stack, player);
                    boolean isDuplicate = existingSchematics.stream()
                            .anyMatch(s -> s.getName().equals(this.schematicName));

                    if (isDuplicate) {
                        player.displayClientMessage(Component.literal("§cSchematic name already exists!"), true);
                        return;
                    }

                    SchematicData namedSchematic = new SchematicData(
                            this.schematicName,
                            clipboard.getMultiblockType(),
                            clipboard.getBlocks(),
                            clipboard.getBlockEntities(),
                            clipboard.getOriginalFacing()
                    );

                    existingSchematics.add(namedSchematic);
                    saveSchematics(stack, existingSchematics);

                    GTCEUTerminalMod.LOGGER.info("Saved schematic '{}' with originalFacing: {}",
                            this.schematicName, clipboard.getOriginalFacing());

                    player.displayClientMessage(
                            Component.literal("§aSaved schematic: " + this.schematicName),
                            true
                    );
                    break;

                case LOAD:
                    List<SchematicData> schematics = loadSchematics(stack, player);
                    if (this.schematicIndex >= 0 && this.schematicIndex < schematics.size()) {
                        SchematicData schematic = schematics.get(this.schematicIndex);

                        stackTag.put("Clipboard", schematic.toNBT());

                        GTCEUTerminalMod.LOGGER.info("Loaded schematic '{}' with originalFacing: {}",
                                schematic.getName(), schematic.getOriginalFacing());

                        player.displayClientMessage(
                                Component.literal("§aLoaded schematic: " + schematic.getName()),
                                true
                        );
                    }
                    break;

                case DELETE:
                    List<SchematicData> allSchematics = loadSchematics(stack, player);
                    if (this.schematicIndex >= 0 && this.schematicIndex < allSchematics.size()) {
                        SchematicData removed = allSchematics.remove(this.schematicIndex);
                        saveSchematics(stack, allSchematics);
                        player.displayClientMessage(
                                Component.literal("§eDeleted schematic: " + removed.getName()),
                                true
                        );
                    }
                    break;
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private ItemStack findSchematicInterface(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem().toString().contains("schematic_interface")) {
            return mainHand;
        }

        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem().toString().contains("schematic_interface")) {
            return offHand;
        }

        return ItemStack.EMPTY;
    }

    private List<SchematicData> loadSchematics(ItemStack stack, ServerPlayer player) {
        List<SchematicData> schematics = new ArrayList<>();

        CompoundTag stackTag = stack.getTag();
        if (stackTag == null || !stackTag.contains("SavedSchematics")) {
            return schematics;
        }

        ListTag savedList = stackTag.getList("SavedSchematics", 10);
        for (int i = 0; i < savedList.size(); i++) {
            try {
                CompoundTag schematicTag = savedList.getCompound(i);
                schematics.add(SchematicData.fromNBT(schematicTag, player.level().registryAccess()));
            } catch (Exception e) {
                GTCEUTerminalMod.LOGGER.error("Error loading schematic {}: {}", i, e.getMessage());
            }
        }

        return schematics;
    }

    private void saveSchematics(ItemStack stack, List<SchematicData> schematics) {
        CompoundTag stackTag = stack.getOrCreateTag();

        ListTag savedList = new ListTag();
        for (SchematicData schematic : schematics) {
            savedList.add(schematic.toNBT());
        }

        stackTag.put("SavedSchematics", savedList);
    }

    public enum ActionType {
        SAVE,
        LOAD,
        DELETE
    }
}