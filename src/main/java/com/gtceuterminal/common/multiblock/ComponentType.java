package com.gtceuterminal.common.multiblock;

// Types of multiblock components
public enum ComponentType {

    // ============================================
    // ENERGY
    // ============================================

    ENERGY_HATCH("Energy Hatch", "input_energy"),
    DYNAMO_HATCH("Dynamo Hatch", "output_energy"),
    SUBSTATION_INPUT_ENERGY("Substation Input Energy", "substation_input_energy"),
    SUBSTATION_OUTPUT_ENERGY("Substation Output Energy", "substation_output_energy"),

    // ============================================
    // BUSES (Item I/O)
    // ============================================

    INPUT_BUS("Input Bus", "import_items"),
    OUTPUT_BUS("Output Bus", "export_items"),
    STEAM_INPUT_BUS("Steam Input Bus", "steam_import_items"),
    STEAM_OUTPUT_BUS("Steam Output Bus", "steam_export_items"),

    // ============================================
    // HATCHES (Fluid I/O)
    // ============================================

    INPUT_HATCH("Input Hatch", "import_fluids"),
    OUTPUT_HATCH("Output Hatch", "export_fluids"),

    // Dual Hatches (1x capacity)
    INPUT_HATCH_1X("Input Hatch (1x)", "import_fluids_1x"),
    OUTPUT_HATCH_1X("Output Hatch (1x)", "export_fluids_1x"),

    // Quad Hatches (4x capacity)
    QUAD_INPUT_HATCH("Quad Input Hatch (4x)", "import_fluids_4x"),
    QUAD_OUTPUT_HATCH("Quad Output Hatch (4x)", "export_fluids_4x"),

    // Nonuple Hatches (9x capacity)
    NONUPLE_INPUT_HATCH("Nonuple Input Hatch (9x)", "import_fluids_9x"),
    NONUPLE_OUTPUT_HATCH("Nonuple Output Hatch (9x)", "export_fluids_9x"),

    // ============================================
    // SPECIAL HATCHES
    // ============================================

    MUFFLER("Muffler Hatch", "muffler"),
    MAINTENANCE("Maintenance Hatch", "maintenance"),
    ROTOR_HOLDER("Rotor Holder", "rotor_holder"),
    PUMP_FLUID_HATCH("Pump Fluid Hatch", "pump_fluid_hatch"),
    STEAM("Steam Hatch", "steam"),
    TANK_VALVE("Tank Valve", "tank_valve"),
    PASSTHROUGH_HATCH("Passthrough Hatch", "passthrough_hatch"),
    PARALLEL_HATCH("Parallel Hatch", "parallel_hatch"),

    // ============================================
    // LASER
    // ============================================

    INPUT_LASER("Input Laser Hatch", "input_laser"),
    OUTPUT_LASER("Output Laser Hatch", "output_laser"),

    // ============================================
    // DATA/COMPUTATION (Research Multiblocks)
    // ============================================

    COMPUTATION_DATA_RECEPTION("Computation Data Reception Hatch", "computation_data_reception"),
    COMPUTATION_DATA_TRANSMISSION("Computation Data Transmission Hatch", "computation_data_transmission"),
    OPTICAL_DATA_RECEPTION("Optical Data Reception Hatch", "optical_data_reception"),
    OPTICAL_DATA_TRANSMISSION("Optical Data Transmission Hatch", "optical_data_transmission"),
    DATA_ACCESS("Data Access Hatch", "data_access"),

    // ============================================
    // HPCA (High Performance Computing Array)
    // ============================================

    HPCA_COMPONENT("HPCA Component", "hpca_component"),
    OBJECT_HOLDER("Object Holder", "object_holder"),

    // ============================================
    // STRUCTURE COMPONENTS
    // ============================================

    COIL("Heating Coil", "coil"),
    CASING("Casing", "casing"),

    // ============================================
    // ADDON SUPPORT (GTNH, TecTech, etc.)
    // ============================================

    // This one is a VERY specific hatch
    MACHINE_HATCH("Machine Hatch", "machine_hatch"),

    DUAL_HATCH("Dual Hatch", "dual_hatch"),

    FILTER("Filter", "filter"),

    UNKNOWN("Unknown Component", "unknown");

    // ============================================
    // FIELDS & METHODS
    // ============================================

    private final String displayName;
    private final String abilityId;

    ComponentType(String displayName, String abilityId) {
        this.displayName = displayName;
        this.abilityId = abilityId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbilityId() {
        return abilityId;
    }

    // Check if this component is upgradeable
    public boolean isUpgradeable() {
        return this != UNKNOWN &&
                this != CASING &&
                this != STEAM &&  // Steam doesn't have normal tiers
                this != TANK_VALVE;  // Tank valve cannot be upgraded
    }

    // Check if it is a fluid component
    public boolean isFluidHandler() {
        return this == INPUT_HATCH ||
                this == OUTPUT_HATCH ||
                this == INPUT_HATCH_1X ||
                this == OUTPUT_HATCH_1X ||
                this == QUAD_INPUT_HATCH ||
                this == QUAD_OUTPUT_HATCH ||
                this == NONUPLE_INPUT_HATCH ||
                this == NONUPLE_OUTPUT_HATCH ||
                this == PUMP_FLUID_HATCH ||
                this == DUAL_HATCH;
    }

    // Check if it is an item component
    public boolean isItemHandler() {
        return this == INPUT_BUS ||
                this == OUTPUT_BUS ||
                this == STEAM_INPUT_BUS ||
                this == STEAM_OUTPUT_BUS;
    }

    // Check if it is a power component
    public boolean isEnergyHandler() {
        return this == ENERGY_HATCH ||
                this == DYNAMO_HATCH ||
                this == SUBSTATION_INPUT_ENERGY ||
                this == SUBSTATION_OUTPUT_ENERGY ||
                this == INPUT_LASER ||
                this == OUTPUT_LASER;
    }

    // Check if it is a data/computing component
    public boolean isDataHandler() {
        return this == COMPUTATION_DATA_RECEPTION ||
                this == COMPUTATION_DATA_TRANSMISSION ||
                this == OPTICAL_DATA_RECEPTION ||
                this == OPTICAL_DATA_TRANSMISSION ||
                this == DATA_ACCESS ||
                this == HPCA_COMPONENT;
    }

    // Check if it is a special component
    public boolean isSpecial() {
        return this == MAINTENANCE ||
                this == MUFFLER ||
                this == ROTOR_HOLDER ||
                this == PARALLEL_HATCH ||
                this == OBJECT_HOLDER ||
                this == FILTER;
    }

    // It obtains the capacity multiplier
    public int getCapacityMultiplier() {
        return switch (this) {
            case QUAD_INPUT_HATCH, QUAD_OUTPUT_HATCH, INPUT_HATCH_1X, OUTPUT_HATCH_1X -> 4;
            case NONUPLE_INPUT_HATCH, NONUPLE_OUTPUT_HATCH -> 9;
            default -> 1;
        };
    }

    public int getColor() {
        if (isFluidHandler()) return 0x4169E1;  // Blue (fluids)
        if (isItemHandler()) return 0xFFD700;   // Gold (items)
        if (isEnergyHandler()) return 0xFF4500; // Orange (energy)
        if (isDataHandler()) return 0x00CED1;   // Cyan (data)
        if (this == MAINTENANCE) return 0x32CD32;  // Green (maintenance)
        if (this == MUFFLER) return 0x808080;      // Gray (muffler)
        if (this == COIL) return 0xFF6347;         // Red (coil)
        return 0xFFFFFF;  // White (default)
    }
}