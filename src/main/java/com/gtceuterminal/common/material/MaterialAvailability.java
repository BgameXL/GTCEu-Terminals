package com.gtceuterminal.common.material;

import net.minecraft.world.item.Item;

public class MaterialAvailability {
    private final Item item;
    private final int required;
    private int inInventory;
    private int inNearbyChests;
    private long inMENetwork;

    public MaterialAvailability(Item item, int required) {
        this.item = item;
        this.required = required;
        this.inInventory = 0;
        this.inNearbyChests = 0;
        this.inMENetwork = 0;
    }

    public Item getItem() {
        return item;
    }

    public int getRequired() {
        return required;
    }

    public int getInInventory() {
        return inInventory;
    }

    public void setInInventory(int amount) {
        this.inInventory = amount;
    }

    public int getInNearbyChests() {
        return inNearbyChests;
    }

    public void setInNearbyChests(int amount) {
        this.inNearbyChests = amount;
    }

    public long getInMENetwork() {
        return inMENetwork;
    }

    public void setInMENetwork(long amount) {
        this.inMENetwork = amount;
    }

    public long getTotalAvailable() {
        return inInventory + inNearbyChests + inMENetwork;
    }

    public boolean hasEnough() {
        return getTotalAvailable() >= required;
    }

    public int getMissing() {
        return Math.max(0, required - (int)getTotalAvailable());
    }

    public int getAvailabilityPercent() {
        if (required == 0) return 100;
        return Math.min(100, (int)((getTotalAvailable() * 100) / required));
    }

    public int getColor() {
        int percent = getAvailabilityPercent();
        if (percent >= 100) {
            return 0x00FF00; // Green - Full availability
        } else if (percent >= 50) {
            return 0xFFFF00; // Yellow - Partial availability
        } else if (percent > 0) {
            return 0xFF8800; // Orange - Low availability
        } else {
            return 0xFF0000; // Red - No availability
        }
    }


    // Get detailed display string with all sources
    public String getDisplayString() {
        StringBuilder sb = new StringBuilder();

        sb.append(item.getDescription().getString());
        sb.append(" x").append(required);

        // Show inventory
        if (inInventory > 0) {
            sb.append(" §7[Inv: ").append(inInventory).append("]");
        }

        // Show chests
        if (inNearbyChests > 0) {
            sb.append(" §7[Chests: ").append(inNearbyChests).append("]");
        }

        // Show ME with special formatting
        if (inMENetwork > 0) {
            sb.append(" §a[ME: ").append(inMENetwork).append("]");
        }

        return sb.toString();
    }


    // Get compact display string for GUI
    public String getCompactDisplayString() {
        String itemName = item.getDescription().getString();
        long total = getTotalAvailable();

        if (hasEnough()) {
            // Show where items are coming from
            if (inMENetwork >= required) {
                return "§a✓ " + itemName + " §7(ME: " + inMENetwork + ")";
            } else if (inInventory >= required) {
                return "§a✓ " + itemName + " §7(Inv: " + inInventory + ")";
            } else {
                // Split across sources
                return "§a✓ " + itemName + " §7(" + formatSources() + ")";
            }
        } else {
            return "§c✗ " + itemName + " §7(need " + getMissing() + " more)";
        }
    }


    // Format sources compactly
    private String formatSources() {
        StringBuilder sb = new StringBuilder();
        boolean first = true;

        if (inInventory > 0) {
            sb.append("Inv:").append(inInventory);
            first = false;
        }

        if (inNearbyChests > 0) {
            if (!first) sb.append(", ");
            sb.append("Chest:").append(inNearbyChests);
            first = false;
        }

        if (inMENetwork > 0) {
            if (!first) sb.append(", ");
            sb.append("ME:").append(inMENetwork);
        }

        return sb.toString();
    }


    // Get tooltip lines for GUI hover
    public String[] getTooltipLines() {
        return new String[] {
                "§f" + item.getDescription().getString(),
                "§7Required: §f" + required,
                "§7Available: " + (hasEnough() ? "§a" : "§c") + getTotalAvailable(),
                "",
                "§7Sources:",
                inInventory > 0 ? "  §7Inventory: §f" + inInventory : "  §8Inventory: 0",
                inNearbyChests > 0 ? "  §7Chests: §f" + inNearbyChests : "  §8Chests: 0",
                inMENetwork > 0 ? "  §aME Network: §f" + inMENetwork : "  §8ME Network: 0",
                "",
                hasEnough() ? "§a✓ Sufficient materials" : "§c✗ Missing " + getMissing() + " items"
        };
    }

    public String getItemName() {
        return item.getDescription().getString();
    }

    public long getAvailable() {
        return getTotalAvailable();
    }

    public String getShortString() {
        if (hasEnough()) {
            return "✓ " + item.getDescription().getString();
        } else {
            return "✗ " + item.getDescription().getString() + " (need " + getMissing() + " more)";
        }
    }


    //Get status indicator for GUI
    public String getStatusIndicator() {
        if (hasEnough()) {
            return "§a●"; // Green dot
        } else if (getTotalAvailable() > 0) {
            return "§e●"; // Yellow dot
        } else {
            return "§c●"; // Red dot
        }
    }


    // Get primary source (where most items will come from)
    public String getPrimarySource() {
        if (inMENetwork >= required) {
            return "ME Network";
        } else if (inInventory >= required) {
            return "Inventory";
        } else if (inNearbyChests >= required) {
            return "Chests";
        } else if (inMENetwork > 0) {
            return "ME + Others";
        } else if (inInventory > 0) {
            return "Inventory + Others";
        } else {
            return "None";
        }
    }
}