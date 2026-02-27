package com.gtceuterminal.common.multiblock;

public enum MachineState {
    RUNNING,
    IDLE_NO_RECIPE,
    IDLE_NO_POWER,
    IDLE_MACHINE_OFF,
    IDLE_NOT_FORMED,
    IDLE_CHUNK_UNLOADED,
    UNKNOWN
}