package com.gtceuterminal.client.gui.blueprint;

import com.gtceuterminal.client.blueprint.BlueprintFileManager;
import com.gtceuterminal.client.blueprint.BlueprintHologramRenderer;
import com.gtceuterminal.client.blueprint.BlueprintHologramRenderer.Mode;
import com.gtceuterminal.client.blueprint.OrbitalCamera;
import com.gtceuterminal.client.gui.planner.PlannerState;
import com.gtceuterminal.client.gui.planner.PlannerState.PlacedGhost;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.util.SchematicUtils;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * 3D orbital viewer for a single schematic.
 *
 * Two sub-modes, toggled with {@code Tab}:
 *
 *   ORBIT<— camera orbits the schematic center; drag to rotate,
 *       scroll to zoom. No world context — good for inspecting the structure.
 *   PLACE — hologram is anchored to a world position; WASD moves
 *       it, R rotates 90° CW, Enter/Space confirms placement (adds ghost to
 *       planner), Escape cancels back to ORBIT.
 *
 * The screen is opened from {@link com.gtceuterminal.client.gui.planner.PlannerScreen}
 * when the player double-clicks (or uses the [3D] button on) a sidebar entry.
 */
public class BlueprintViewScreen extends Screen {

    // ── Sub-mode ──────────────────────────────────────────────────────────────
    private enum SubMode { ORBIT, PLACE }
    private SubMode subMode = SubMode.ORBIT;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_HUD    = 0xCC111111;
    private static final int C_TEXT   = 0xFFDDDDDD;
    private static final int C_DIM    = 0xFF888888;
    private static final int C_BTN    = 0xFF1A3A6B;
    private static final int C_BTN_B  = 0xFF2E75B6;
    private static final int C_OK     = 0xFF1E6B1E;
    private static final int C_OK_B   = 0xFF2E8B2E;
    private static final int C_CANCEL = 0xFF6B1E1E;
    private static final int C_CANCEL_B = 0xFF8B2E2E;

    private static final int HUD_H    = 22;
    private static final int BTN_W    = 80;
    private static final int BTN_H    = 16;

    // ── Data ──────────────────────────────────────────────────────────────────
    private final SchematicData       schematic;
    private final List<SchematicData> allSchematics;   // for returning to PlannerScreen
    private final int                 originSelectedIdx;
    private       int                 rotSteps = 0;

    // ── Camera ────────────────────────────────────────────────────────────────
    private final OrbitalCamera camera;
    private boolean dragging   = false;
    private double  lastMouseX, lastMouseY;

    // ── Placement state (PLACE mode) ──────────────────────────────────────────
    private BlockPos placementOrigin;

    // WASD movement accumulator (blocks/tick, applied each tick)
    private boolean moveNorth, moveSouth, moveEast, moveWest;

    // ── Orbit anchor (ORBIT mode): a fake origin near the player ─────────────
    private final BlockPos orbitOrigin;

    // ── Constructor ───────────────────────────────────────────────────────────

    public BlueprintViewScreen(SchematicData schematic,
                               List<SchematicData> allSchematics,
                               int selectedIdx) {
        super(Component.translatable("gui.gtceuterminal.blueprint.view.title"));
        this.schematic         = schematic;
        this.allSchematics     = allSchematics;
        this.originSelectedIdx = selectedIdx;

        // Orbit anchor: place schematic centred ~8 blocks in front of the player
        Minecraft mc = Minecraft.getInstance();
        BlockPos playerPos = mc.player != null ? mc.player.blockPosition() : BlockPos.ZERO;
        this.orbitOrigin    = playerPos.offset(-schematic.getSize().getX() / 2,
                0,
                -schematic.getSize().getZ() / 2);
        this.placementOrigin = orbitOrigin;

        Vec3 center = BlueprintHologramRenderer.computeCenter(schematic, orbitOrigin);
        this.camera = new OrbitalCamera(center);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // The hologram is rendered by the RenderLevelStageEvent in ClientEvents.
        // Here we only draw the 2D HUD overlay on top.

        renderHUD(g, mouseX, mouseY);
        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderHUD(GuiGraphics g, int mx, int my) {
        int y = height - HUD_H;
        g.fill(0, y, width, height, C_HUD);
        g.fill(0, y, width, y + 1, 0xFF333333);

        if (subMode == SubMode.ORBIT) {
            String hints = Component.translatable("gui.gtceuterminal.blueprint.view.orbit.hints").getString();
            g.drawString(font, hints, 8, y + 7, C_DIM, false);

            // Place Mode button
            int bx = width - 8 - BTN_W;
            drawBtn(g, bx, y + 3, BTN_W, BTN_H, C_BTN, C_BTN_B);
            g.drawString(font,
                    Component.translatable("gui.gtceuterminal.blueprint.view.orbit.place_mode").getString(),
                    bx + 6, y + 7, 0xFFFFFFFF, false);

            // Blueprints button
            int bx2 = bx - 6 - BTN_W;
            drawBtn(g, bx2, y + 3, BTN_W, BTN_H, C_BTN, C_BTN_B);
            g.drawString(font,
                    Component.translatable("gui.gtceuterminal.blueprint.view.orbit.blueprints").getString(),
                    bx2 + 6, y + 7, 0xFFFFFFFF, false);

        } else {
            String hints = Component.translatable("gui.gtceuterminal.blueprint.view.place.hints").getString();
            g.drawString(font, hints, 8, y + 7, C_DIM, false);

            BlockPos sz = schematic.getSize();
            int sx = rotSteps % 2 == 0 ? sz.getX() : sz.getZ();
            int sz2= rotSteps % 2 == 0 ? sz.getZ() : sz.getX();
            String info = Component.translatable(
                    "gui.gtceuterminal.blueprint.view.place.info",
                    placementOrigin.getX(),
                    placementOrigin.getY(),
                    placementOrigin.getZ(),
                    sx,
                    sz.getY(),
                    sz2,
                    rotSteps * 90
            ).getString();
            g.drawString(font, info, 8, y - 12, C_DIM, false);

            int confirmBx = width - 8 - BTN_W;
            int cancelBx  = confirmBx - 6 - BTN_W;
            drawBtn(g, confirmBx, y + 3, BTN_W, BTN_H, C_OK, C_OK_B);
            g.drawString(font,
                    Component.translatable("gui.gtceuterminal.blueprint.view.place.confirm").getString(),
                    confirmBx + 6, y + 7, 0xFFFFFFFF, false);
            drawBtn(g, cancelBx, y + 3, BTN_W, BTN_H, C_CANCEL, C_CANCEL_B);
            g.drawString(font,
                    Component.translatable("gui.gtceuterminal.blueprint.view.place.cancel").getString(),
                    cancelBx + 6, y + 7, 0xFFFFFFFF, false);
        }
    }

    private void drawBtn(GuiGraphics g, int x, int y, int w, int h, int fill, int bdr) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, bdr);
        g.fill(x, y + h - 1, x + w, y + h, bdr);
        g.fill(x, y, x + 1, y + h, bdr);
        g.fill(x + w - 1, y, x + w, y + h, bdr);
    }

    // ── Mouse ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        int hudY = height - HUD_H;

        if (btn == 0 && my >= hudY) {
            if (subMode == SubMode.ORBIT) {
                // Place Mode button
                int bx = width - 8 - BTN_W;
                if (mx >= bx && mx < bx + BTN_W) { enterPlaceMode(); return true; }
                // Blueprints button
                int bx2 = bx - 6 - BTN_W;
                if (mx >= bx2 && mx < bx2 + BTN_W) { openLibrary(); return true; }
            } else {
                int confirmBx = width - 8 - BTN_W;
                int cancelBx  = confirmBx - 6 - BTN_W;
                if (mx >= confirmBx && mx < confirmBx + BTN_W) { confirmPlacement(); return true; }
                if (mx >= cancelBx  && mx < cancelBx  + BTN_W) { cancelPlaceMode();  return true; }
            }
        }

        if (btn == 0 && my < hudY) {
            dragging   = true;
            lastMouseX = mx;
            lastMouseY = my;
            return true;
        }
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        if (btn == 0) dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging) {
            // Orbit drag works in both modes — in PLACE it lets you inspect
            // the hologram from different angles while still moving with WASD.
            camera.drag(dx, dy);
            updateCameraEntity();
            return true;
        }
        lastMouseX = mx;
        lastMouseY = my;
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        if (subMode == SubMode.ORBIT) {
            camera.scroll(delta);
            updateCameraEntity();
        }
        return true;
    }

    // ── Keyboard ──────────────────────────────────────────────────────────────
    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (subMode == SubMode.PLACE) {
            switch (key) {
                case GLFW.GLFW_KEY_W -> { moveNorth = true; return true; }
                case GLFW.GLFW_KEY_S -> { moveSouth = true; return true; }
                case GLFW.GLFW_KEY_A -> { moveWest  = true; return true; }
                case GLFW.GLFW_KEY_D -> { moveEast  = true; return true; }
                case GLFW.GLFW_KEY_R -> { rotSteps = (rotSteps + 1) & 3; snapToSurface(); return true; }
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> { confirmPlacement(); return true; }
                case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_TAB     -> { cancelPlaceMode();  return true; }
            }
        } else {
            if (key == GLFW.GLFW_KEY_TAB)    { enterPlaceMode(); return true; }
            if (key == GLFW.GLFW_KEY_ESCAPE)  { returnToPlanner(); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        switch (key) {
            case GLFW.GLFW_KEY_W -> moveNorth = false;
            case GLFW.GLFW_KEY_S -> moveSouth = false;
            case GLFW.GLFW_KEY_A -> moveWest  = false;
            case GLFW.GLFW_KEY_D -> moveEast  = false;
        }
        return super.keyReleased(key, scan, mods);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();
        if (subMode != SubMode.PLACE) return;

        // Move placement origin 1 block per tick per direction held
        int dx = (moveEast  ? 1 : 0) - (moveWest  ? 1 : 0);
        int dz = (moveSouth ? 1 : 0) - (moveNorth ? 1 : 0);
        if (dx != 0 || dz != 0) {
            placementOrigin = placementOrigin.offset(dx, 0, dz);
            snapToSurface();
            // Update orbital camera target to follow the hologram
            camera.setTarget(BlueprintHologramRenderer.computeCenter(schematic, placementOrigin));
            updateCameraEntity();
        }
    }

    // ── Sub-mode transitions ──────────────────────────────────────────────────
    private void enterPlaceMode() {
        subMode = SubMode.PLACE;
        // Start placement at orbit anchor snapped to surface
        placementOrigin = orbitOrigin;
        snapToSurface();
        camera.setTarget(BlueprintHologramRenderer.computeCenter(schematic, placementOrigin));
        updateCameraEntity();
        moveNorth = moveSouth = moveEast = moveWest = false;
    }

    private void cancelPlaceMode() {
        subMode = SubMode.ORBIT;
        camera.setTarget(BlueprintHologramRenderer.computeCenter(schematic, orbitOrigin));
        updateCameraEntity();
        moveNorth = moveSouth = moveEast = moveWest = false;
    }

    private void confirmPlacement() {
        PlannerState.ghosts.add(new PlacedGhost(schematic, placementOrigin, rotSteps));
        returnToPlanner();
    }

    private void returnToPlanner() {
        Minecraft.getInstance().setScreen(
                new com.gtceuterminal.client.gui.planner.PlannerScreen(
                        allSchematics, originSelectedIdx, rotSteps));
    }

    // ── Camera entity sync ────────────────────────────────────────────────────
    private void updateCameraEntity() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Vec3 pos = camera.getPosition();
        mc.player.moveTo(pos.x, pos.y, pos.z, camera.getLookYaw(), camera.getLookPitch());
        mc.player.setDeltaMovement(Vec3.ZERO);
    }

    @Override
    protected void init() {
        super.init();
        updateCameraEntity();
    }

    // ── Placement helpers ─────────────────────────────────────────────────────
    private void snapToSurface() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        int surfaceY = mc.level.getHeight(Heightmap.Types.WORLD_SURFACE,
                placementOrigin.getX(), placementOrigin.getZ());
        final int snap = rotSteps;
        int minRelY = schematic.getBlocks().keySet().stream()
                .mapToInt(p -> SchematicUtils.rotatePositionSteps(p, snap).getY())
                .min().orElse(0);
        placementOrigin = new BlockPos(placementOrigin.getX(),
                surfaceY - minRelY,
                placementOrigin.getZ());
    }

    // ── Expose state for ClientEvents renderer ────────────────────────────────
    public SchematicData getSchematic()        { return schematic; }
    public BlockPos      getPlacementOrigin()  { return placementOrigin; }
    public BlockPos      getOrbitOrigin()      { return orbitOrigin; }
    public int           getRotSteps()         { return rotSteps; }
    public SubMode       getSubMode()          { return subMode; }
    public boolean       isPlaceMode()         { return subMode == SubMode.PLACE; }

    // ── Library ───────────────────────────────────────────────────────────────
    private void openLibrary() {
        Minecraft.getInstance().setScreen(
                new BlueprintLibraryScreen(
                        this,
                        schematic,
                        loaded -> {
                            // When a blueprint is loaded, re-open viewer with it
                            Minecraft.getInstance().setScreen(
                                    new BlueprintViewScreen(loaded, allSchematics, originSelectedIdx));
                        }));
    }

    @Override
    public boolean isPauseScreen() { return false; }
}