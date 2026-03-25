package com.gtceuterminal.client.gui.planner;

import com.gtceuterminal.client.gui.planner.PlannerState.PlacedGhost;
import com.gtceuterminal.common.data.SchematicData;
import com.gtceuterminal.common.util.SchematicUtils;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;

import java.util.*;

/**
 * 2D top-down placement planner — Satisfactory/Factorio style.
 *
 * Controls:
 *   Left-click map      → place schematic ghost
 *   Right-click ghost   → remove it
 *   Middle-drag / R-drag → pan camera
 *   Scroll              → zoom in/out
 *   Shift + Scroll      → tilt camera up/down
 *   R key               → rotate selected schematic 90° CW
 *   Escape              → close (ghosts stay in world)
 */
public class PlannerScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int SIDEBAR_W   = 160;
    private static final int ENTRY_H     = 40;
    private static final int HUD_H       = 22;

    // ── Camera defaults ───────────────────────────────────────────────────────
    private static final float ZOOM_DEFAULT  = 8f;   // px per block
    private static final float ZOOM_MIN      = 3f;
    private static final float ZOOM_MAX      = 28f;
    private static final float TILT_DEFAULT  = 72f;  // degrees; 90=top-down, lower=isometric
    private static final float TILT_MIN      = 25f;
    private static final float TILT_MAX      = 90f;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG          = 0xFF0D0D0D;
    private static final int C_SIDEBAR     = 0xFF1A1A1A;
    private static final int C_SIDEBAR_BDR = 0xFF2E75B6;
    private static final int C_SEL         = 0x332E75B6;
    private static final int C_SEL_BDR     = 0xFF2E75B6;
    private static final int C_TEXT        = 0xFFDDDDDD;
    private static final int C_DIM         = 0xFF888888;
    private static final int C_GRID        = 0x18FFFFFF;
    private static final int C_GRID_CHUNK  = 0x30FFFFFF;
    private static final int C_PLAYER      = 0xFFFFCC00;
    private static final int C_GHOST_PLACE = 0x8000E676;  // green — cursor footprint
    private static final int C_GHOST_SAVED = 0x804FC3FF;  // blue  — placed ghosts
    private static final int C_GHOST_BDR   = 0xFF00E676;
    private static final int C_HUD         = 0xCC111111;

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<SchematicData> schematics;
    private int   selectedIdx  = 0;
    private int   rotSteps     = 0;  // 0-3 clockwise 90° steps

    // Camera
    private double camX, camZ;       // world pos at screen center
    private float  zoom  = ZOOM_DEFAULT;
    private float  tilt  = TILT_DEFAULT;

    // Panning (middle-click or right-drag)
    private boolean panning   = false;
    private double  panStartX, panStartY;
    private double  panCamX,   panCamZ;

    // Sidebar scroll
    private int sidebarScrollY = 0;
    private static final int SIDEBAR_SCROLL_STEP = ENTRY_H;

    // Max world queries (getHeight + getBlockState) allowed per frame.
    // Prevents lag when many uncached tiles are visible at low zoom.
    private static final int MAX_QUERIES_PER_FRAME = 64;
    private int queriesThisFrame = 0;

    // Tile color cache: packed long (wx<<32|wz) → ARGB
    private final HashMap<Long, Integer> tileCache = new HashMap<>(4096);

    // Footprint cache: "name:rot" → list of [relX, relZ] pairs
    private final HashMap<String, List<int[]>> footprintCache = new HashMap<>();

    // ─────────────────────────────────────────────────────────────────────────
    public PlannerScreen(List<SchematicData> schematics) {
        super(Component.translatable("gui.gtceuterminal.planner.title"));
        this.schematics = schematics;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            this.camX = mc.player.getX();
            this.camZ = mc.player.getZ();
        }
    }

    // Constructor used when returning from 3D free-cam mode — restores selection and rotation
    public PlannerScreen(List<SchematicData> schematics, int selectedIdx, int rotSteps) {
        this(schematics);
        this.selectedIdx = Math.max(0, Math.min(selectedIdx, schematics.size() - 1));
        this.rotSteps    = rotSteps;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Reset per-frame world-query budget and evict stale cache entries.
        queriesThisFrame = 0;
        evictTileCache();

        // Background
        g.fill(0, 0, width, height, C_BG);

        // Map area (right of sidebar)
        enableMapScissor(g);
        renderWorldTiles(g);
        renderGrid(g);
        renderPlacedGhosts(g);
        renderCursorFootprint(g, mouseX, mouseY);
        renderPlayerMarker(g);
        disableScissor();

        // UI layers
        renderSidebar(g, mouseX, mouseY);
        renderHUD(g);

        super.render(g, mouseX, mouseY, partialTick);
    }

    // ── World tiles ───────────────────────────────────────────────────────────
    private void renderWorldTiles(GuiGraphics g) {
        Level level = Minecraft.getInstance().level;
        if (level == null) return;

        float tiltRad = (float) Math.toRadians(tilt);
        float sinT    = (float) Math.sin(tiltRad);
        int   cellW   = Math.max(1, (int) zoom);
        int   cellH   = Math.max(1, (int)(zoom * sinT));

        // Compute visible block range
        int viewW   = width - SIDEBAR_W;
        int viewH   = height;
        // Cap tile count so low-zoom views don't flood the frame with uncached queries.
        // At zoom=3 a 1920px viewport would otherwise need 640+ tiles per row.
        int rawBlocksX = viewW / cellW + 4;
        int rawBlocksZ = viewH / cellH + 4;
        int blocksX = Math.min(rawBlocksX, 256);
        int blocksZ = Math.min(rawBlocksZ, 256);
        int startX  = (int)(camX - blocksX / 2.0);
        int startZ  = (int)(camZ - blocksZ / 2.0);

        for (int dz = 0; dz <= blocksZ; dz++) {
            for (int dx = 0; dx <= blocksX; dx++) {
                int wx = startX + dx;
                int wz = startZ + dz;

                int[] sc = worldToScreen(wx, wz);
                if (sc[0] + cellW < SIDEBAR_W || sc[0] > width) continue;
                if (sc[1] + cellH < 0          || sc[1] > height) continue;

                int color = getTileColor(level, wx, wz);
                g.fill(sc[0], sc[1], sc[0] + cellW, sc[1] + cellH, color);
            }
        }
    }

    private int getTileColor(Level level, int wx, int wz) {
        long key = ((long) wx << 32) | (wz & 0xFFFFFFFFL);
        Integer cached = tileCache.get(key);
        if (cached != null) return cached;

        // Budget guard: if we've already spent all world queries this frame,
        // return the default dark color so the tile renders but doesn't stall.
        if (queriesThisFrame >= MAX_QUERIES_PER_FRAME) return 0xFF1A1A1A;

        int color = 0xFF1A1A1A;
        try {
            queriesThisFrame++;
            int y = level.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
            if (y >= level.getMinBuildHeight()) {
                BlockState state = level.getBlockState(new BlockPos(wx, y, wz));
                MapColor mc = state.getMapColor(level, new BlockPos(wx, y, wz));
                if (mc != MapColor.NONE) {
                    int r = (mc.col >> 16) & 0xFF;
                    int gv = (mc.col >> 8)  & 0xFF;
                    int b  =  mc.col        & 0xFF;
                    color = 0xFF000000 | ((r * 3 / 4) << 16) | ((gv * 3 / 4) << 8) | (b * 3 / 4);
                }
            }
        } catch (Exception ignored) {}

        tileCache.put(key, color);
        return color;
    }

    // Evict cache entries outside the current visible area. Called once per frame.
    private void evictTileCache() {
        if (tileCache.size() <= 40_000) return;

        // Compute retention area using ZOOM_MIN (maximum visible area) so that
        // zooming back in never evicts tiles that would immediately be needed again.
        // This trades memory for smoothness — the cache grows a bit larger but
        // zoom-in/out cycles no longer cause a repaint storm.
        float tiltRad  = (float) Math.toRadians(tilt);
        float sinT     = Math.max(0.01f, (float) Math.sin(tiltRad));
        int   cellW    = Math.max(1, (int) ZOOM_MIN);
        int   cellH    = Math.max(1, (int)(ZOOM_MIN * sinT));
        int   margin   = 64;
        int   halfBX   = (width  / cellW) / 2 + margin;
        int   halfBZ   = (height / cellH) / 2 + margin;
        int   minX     = (int) camX - halfBX;
        int   maxX     = (int) camX + halfBX;
        int   minZ     = (int) camZ - halfBZ;
        int   maxZ     = (int) camZ + halfBZ;

        tileCache.keySet().removeIf(k -> {
            int kx = (int)(k >> 32);
            int kz = (int)(k & 0xFFFFFFFFL);
            return kx < minX || kx > maxX || kz < minZ || kz > maxZ;
        });
    }

    // ── Grid ──────────────────────────────────────────────────────────────────
    private void renderGrid(GuiGraphics g) {
        if (zoom < 4f) return;  // too zoomed out — grid becomes noise

        float tiltRad = (float) Math.toRadians(tilt);
        float sinT    = (float) Math.sin(tiltRad);
        int   cellW   = (int) zoom;
        int   cellH   = Math.max(1, (int)(zoom * sinT));

        int viewW   = width - SIDEBAR_W;
        int viewH   = height;
        int blocksX = viewW  / cellW + 4;
        int blocksZ = viewH  / cellH + 4;
        int startX  = (int)(camX - blocksX / 2.0);
        int startZ  = (int)(camZ - blocksZ / 2.0);

        for (int dx = 0; dx <= blocksX; dx++) {
            int wx = startX + dx;
            int sx = worldToScreen(wx, (int) camZ)[0];
            if (sx < SIDEBAR_W || sx > width) continue;
            boolean isChunk = (wx % 16 == 0);
            g.fill(sx, 0, sx + 1, height, isChunk ? C_GRID_CHUNK : C_GRID);
        }
        for (int dz = 0; dz <= blocksZ; dz++) {
            int wz = startZ + dz;
            int sy = worldToScreen((int) camX, wz)[1];
            if (sy < 0 || sy > height) continue;
            boolean isChunk = (wz % 16 == 0);
            g.fill(SIDEBAR_W, sy, width, sy + 1, isChunk ? C_GRID_CHUNK : C_GRID);
        }
    }

    // ── Footprints ────────────────────────────────────────────────────────────
    private void renderPlacedGhosts(GuiGraphics g) {
        for (PlacedGhost ghost : PlannerState.ghosts) {
            renderFootprint(g, ghost.schematic, ghost.origin.getX(), ghost.origin.getZ(),
                    ghost.rotSteps, C_GHOST_SAVED, 0xFF4FC3FF);
        }
    }

    private void renderCursorFootprint(GuiGraphics g, int mouseX, int mouseY) {
        if (mouseX <= SIDEBAR_W) return;
        if (selectedIdx < 0 || selectedIdx >= schematics.size()) return;
        int[] world = screenToWorld(mouseX, mouseY);
        renderFootprint(g, schematics.get(selectedIdx), world[0], world[1],
                rotSteps, C_GHOST_PLACE, C_GHOST_BDR);
    }

    private void renderFootprint(GuiGraphics g, SchematicData schematic,
                                 int originX, int originZ, int rot,
                                 int fillColor, int borderColor) {
        List<int[]> cells = getFootprint(schematic, rot);
        int cellW = Math.max(1, (int) zoom);
        int cellH = Math.max(1, (int)(zoom * Math.sin(Math.toRadians(tilt))));

        for (int[] cell : cells) {
            int wx = originX + cell[0];
            int wz = originZ + cell[1];
            int[] sc = worldToScreen(wx, wz);
            int sx = sc[0], sy = sc[1];

            if (sx + cellW < SIDEBAR_W || sx > width) continue;
            if (sy + cellH < 0 || sy > height) continue;

            // Fill
            g.fill(sx, sy, sx + cellW, sy + cellH, fillColor);
            // Border (1px inset)
            if (zoom >= 5f) {
                g.fill(sx,             sy,             sx + cellW, sy + 1,     borderColor);
                g.fill(sx,             sy + cellH - 1, sx + cellW, sy + cellH, borderColor);
                g.fill(sx,             sy,             sx + 1,     sy + cellH, borderColor);
                g.fill(sx + cellW - 1, sy,             sx + cellW, sy + cellH, borderColor);
            }
        }
    }

    // Returns list of [relX, relZ] footprint cells for a schematic at a given rotation.
    private List<int[]> getFootprint(SchematicData schematic, int rot) {
        // Fix 4: use identity hash instead of getName() so two different SchematicData
        // objects that happen to share the same display name don't collide in the cache.
        String key = System.identityHashCode(schematic) + ":" + rot;
        return footprintCache.computeIfAbsent(key, k -> {
            Set<Long> seen = new HashSet<>();
            List<int[]> cells = new ArrayList<>();
            for (Map.Entry<BlockPos, BlockState> e : schematic.getBlocks().entrySet()) {
                if (e.getValue().isAir()) continue;
                BlockPos rotated = SchematicUtils.rotatePositionSteps(e.getKey(), rot);
                long packed = ((long) rotated.getX() << 32) | (rotated.getZ() & 0xFFFFFFFFL);
                if (seen.add(packed)) {
                    cells.add(new int[]{ rotated.getX(), rotated.getZ() });
                }
            }
            return cells;
        });
    }

    // ── Player marker ─────────────────────────────────────────────────────────
    private void renderPlayerMarker(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int[] sc = worldToScreen((int) mc.player.getX(), (int) mc.player.getZ());
        int r = Math.max(3, (int)(zoom / 2.5f));
        g.fill(sc[0] - r, sc[1] - r, sc[0] + r, sc[1] + r, C_PLAYER);
        // Cross hair
        g.fill(sc[0] - r - 2, sc[1] - 1, sc[0] + r + 2, sc[1] + 1, C_PLAYER);
        g.fill(sc[0] - 1, sc[1] - r - 2, sc[0] + 1, sc[1] + r + 2, C_PLAYER);
    }

    // ── Sidebar ───────────────────────────────────────────────────────────────
    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, SIDEBAR_W, height, C_SIDEBAR);
        g.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, height, C_SIDEBAR_BDR);

        // Title
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.planner.title").getString(),
                8, 8, C_TEXT, false);
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.planner.sidebar.schematics").getString(),
                8, 22, C_DIM, false);
        g.fill(0, 32, SIDEBAR_W - 1, 33, 0xFF333333);

        // Schematic list (scrollable)
        int listTop = 34;
        int listH   = height - listTop - HUD_H - 10;
        enableSidebarScissor(g, listTop, listH);

        for (int i = 0; i < schematics.size(); i++) {
            SchematicData s = schematics.get(i);
            int ey = listTop + i * ENTRY_H - sidebarScrollY;
            if (ey + ENTRY_H < listTop || ey > listTop + listH) continue;

            boolean sel     = (i == selectedIdx);
            boolean hovered = mouseX < SIDEBAR_W && mouseY >= ey && mouseY < ey + ENTRY_H;

            // Entry bg
            if (sel) {
                g.fill(0, ey, SIDEBAR_W - 1, ey + ENTRY_H, C_SEL);
                g.fill(0, ey, 3, ey + ENTRY_H, C_SIDEBAR_BDR);
            } else if (hovered) {
                g.fill(0, ey, SIDEBAR_W - 1, ey + ENTRY_H, 0x18FFFFFF);
            }

            // Name (truncate)
            String name = s.getName();
            if (name.length() > 18) name = name.substring(0, 15) + "…";
            g.drawString(font, "§f" + name, 10, ey + 7, C_TEXT, false);

            // Size info
            BlockPos sz = s.getSize();
            String info = String.format("§8%dx%dx%d", sz.getX(), sz.getY(), sz.getZ());
            g.drawString(font, info, 10, ey + 21, C_DIM, false);

            // Divider
            g.fill(4, ey + ENTRY_H - 1, SIDEBAR_W - 5, ey + ENTRY_H, 0xFF222222);
        }

        disableScissor();

        // Rotation badge
        int badgeY = height - HUD_H - 42;
        String rotLabel = Component.translatable(
                "gui.gtceuterminal.planner.hud.rotation", rotSteps * 90
        ).getString();
        g.drawString(font, rotLabel, 8, badgeY, C_TEXT, false);

        // Ghost count
        String ghostLabel = Component.translatable(
                "gui.gtceuterminal.planner.hud.placed", PlannerState.ghosts.size()
        ).getString();
        g.drawString(font, ghostLabel, 8, badgeY + 10, C_TEXT, false);

        // [3D View] button — opens BlueprintViewScreen for the selected schematic
        boolean hasSelected = selectedIdx >= 0 && selectedIdx < schematics.size();
        int viewBtnY = height - HUD_H - 28;
        int viewBtnW = SIDEBAR_W - 16;
        int viewFill = hasSelected ? 0xFF1A3A6B : 0xFF2A2A2A;
        int viewBdr  = hasSelected ? 0xFF2E75B6 : 0xFF444444;
        drawSidebarBtn(g, 8, viewBtnY, viewBtnW, 12, viewFill, viewBdr);
        g.drawString(font,
                hasSelected
                        ? Component.translatable("gui.gtceuterminal.planner.sidebar.view3d.enabled").getString()
                        : Component.translatable("gui.gtceuterminal.planner.sidebar.view3d.disabled").getString(),
                12, viewBtnY + 2, 0xFFFFFFFF, false);
        lastViewBtnY = viewBtnY;
        lastViewBtnW = viewBtnW;

        // [Blueprints] button
        int bpBtnY = height - HUD_H - 14;
        drawSidebarBtn(g, 8, bpBtnY, viewBtnW, 12, 0xFF1A3A6B, 0xFF2E75B6);
        g.drawString(font, Component.translatable("gui.gtceuterminal.planner.sidebar.blueprints").getString(),
                12, bpBtnY + 2, 0xFFFFFFFF, false);
        lastBpBtnY = bpBtnY;
    }

    // Sidebar button bounds for click detection
    private int lastViewBtnY, lastViewBtnW, lastBpBtnY;

    private void drawSidebarBtn(GuiGraphics g, int x, int y, int w, int h, int fill, int bdr) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, bdr);
        g.fill(x, y + h - 1, x + w, y + h, bdr);
        g.fill(x, y, x + 1, y + h, bdr);
        g.fill(x + w - 1, y, x + w, y + h, bdr);
    }

    // ── HUD ───────────────────────────────────────────────────────────────────
    // Cached button bounds updated each frame by renderHUD
    private int lastBuildBx, lastClearBx, lastModeBx, lastHudY;
    private static final int BTN_W      = 90;
    private static final int BTN_W_MODE = 44;
    private static final int BTN_H      = 16;

    private void renderHUD(GuiGraphics g) {
        int y = height - HUD_H;
        g.fill(0, y, width, height, C_HUD);
        g.fill(0, y, width, y + 1, 0xFF333333);

        String hints = Component.translatable("gui.gtceuterminal.planner.sidebar.hints").getString();
        g.drawString(font, hints, SIDEBAR_W + 6, y + 7, C_DIM, false);

        boolean hasGhosts    = !PlannerState.ghosts.isEmpty();

        // Right-anchored buttons: [3D] [Clear All] [Build All]
        int modeBx   = width - 8 - BTN_W_MODE;
        int clearBx  = modeBx  - 6 - BTN_W;
        int buildBx  = clearBx - 6 - BTN_W;
        lastModeBx   = modeBx;
        lastClearBx  = clearBx;
        lastBuildBx  = buildBx;
        lastHudY     = y;

        // ── 3D mode button ─────────────────────────────────────────────────
        drawHudBtn(g, modeBx, y + 3, BTN_W_MODE, BTN_H, 0xFF1A3A6B, 0xFF2E75B6);
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.planner.hud.mode_3d").getString(),
                modeBx + 14, y + 7, 0xFFFFFFFF, false);

        // ── Build All ──────────────────────────────────────────────────────
        int buildFill = hasGhosts ? 0xFF1E6B1E : 0xFF2A2A2A;
        int buildBdr  = hasGhosts ? 0xFF2E8B2E : 0xFF444444;
        drawHudBtn(g, buildBx, y + 3, BTN_W, BTN_H, buildFill, buildBdr);
        String buildLbl = hasGhosts
                ? Component.translatable(
                        "gui.gtceuterminal.planner.hud.build_all.enabled",
                        PlannerState.ghosts.size()
                ).getString()
                : Component.translatable("gui.gtceuterminal.planner.hud.build_all.disabled").getString();
        g.drawString(font, buildLbl, buildBx + 6, y + 7, 0xFFFFFFFF, false);

        // ── Clear All ──────────────────────────────────────────────────────
        int clearFill = hasGhosts ? 0xFF6B1E1E : 0xFF2A2A2A;
        int clearBdr  = hasGhosts ? 0xFF8B2E2E : 0xFF444444;
        drawHudBtn(g, clearBx, y + 3, BTN_W, BTN_H, clearFill, clearBdr);
        String clearLbl = hasGhosts
                ? Component.translatable("gui.gtceuterminal.planner.hud.clear_all.enabled").getString()
                : Component.translatable("gui.gtceuterminal.planner.hud.clear_all.disabled").getString();
        g.drawString(font, clearLbl, clearBx + 6, y + 7, 0xFFFFFFFF, false);
    }

    private void drawHudBtn(GuiGraphics g, int x, int y, int w, int h, int fill, int bdr) {
        g.fill(x, y, x + w, y + h, fill);
        g.fill(x, y, x + w, y + 1, bdr);
        g.fill(x, y + h - 1, x + w, y + h, bdr);
        g.fill(x, y, x + 1, y + h, bdr);
        g.fill(x + w - 1, y, x + w, y + h, bdr);
    }

    // Sends all placed ghosts to the server and clears them client-side.
    private void buildAll() {
        if (PlannerState.ghosts.isEmpty()) return;
        List<com.gtceuterminal.common.network.CPacketPlacePlannerGhosts.GhostEntry> entries =
                new ArrayList<>();
        for (PlannerState.PlacedGhost g : PlannerState.ghosts) {
            entries.add(new com.gtceuterminal.common.network.CPacketPlacePlannerGhosts.GhostEntry(
                    g.schematic.toNBT(), g.origin, g.rotSteps));
        }
        com.gtceuterminal.common.network.TerminalNetwork.sendToServer(
                new com.gtceuterminal.common.network.CPacketPlacePlannerGhosts(entries));
        PlannerState.ghosts.clear();
    }

    // Double-click detection for sidebar entries → open 3D view
    private int  lastClickedIdx  = -1;
    private long lastClickTimeMs = 0;
    private static final long DOUBLE_CLICK_MS = 400;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Sidebar button clicks
        if (button == 0 && mouseX < SIDEBAR_W) {
            // [3D View] button
            if (mouseY >= lastViewBtnY && mouseY < lastViewBtnY + 12
                    && selectedIdx >= 0 && selectedIdx < schematics.size()) {
                Minecraft.getInstance().setScreen(
                        new com.gtceuterminal.client.gui.blueprint.BlueprintViewScreen(
                                schematics.get(selectedIdx), schematics, selectedIdx));
                return true;
            }
            // [Blueprints] button
            if (mouseY >= lastBpBtnY && mouseY < lastBpBtnY + 12) {
                SchematicData current = (selectedIdx >= 0 && selectedIdx < schematics.size())
                        ? schematics.get(selectedIdx) : null;
                Minecraft.getInstance().setScreen(
                        new com.gtceuterminal.client.gui.blueprint.BlueprintLibraryScreen(
                                this, current, loaded -> schematics.add(loaded)));
                return true;
            }
        }

        if (button == 0 && mouseY >= lastHudY) {
            if (mouseX >= lastModeBx && mouseX < lastModeBx + BTN_W_MODE) {
                // Enter 3D free-cam mode (FreeCamScreen captures mouse, stops turnPlayer)
                com.gtceuterminal.client.ClientEvents.enterFreeCamScreen(
                        net.minecraft.client.Minecraft.getInstance(), schematics, selectedIdx, rotSteps);
                return true;
            }
            if (mouseX >= lastBuildBx && mouseX < lastBuildBx + BTN_W) {
                buildAll();
                return true;
            }
            if (mouseX >= lastClearBx && mouseX < lastClearBx + BTN_W) {
                PlannerState.ghosts.clear();
                return true;
            }
        }

        // Sidebar click → select schematic
        if (mouseX < SIDEBAR_W) {
            int listTop = 34;
            int i = (int)((mouseY - listTop + sidebarScrollY) / ENTRY_H);
            if (i >= 0 && i < schematics.size()) {
                long now = System.currentTimeMillis();
                if (i == lastClickedIdx && (now - lastClickTimeMs) < DOUBLE_CLICK_MS) {
                    // Double-click → open 3D view
                    Minecraft.getInstance().setScreen(
                            new com.gtceuterminal.client.gui.blueprint.BlueprintViewScreen(
                                    schematics.get(i), schematics, i));
                    return true;
                }
                lastClickedIdx  = i;
                lastClickTimeMs = now;
                selectedIdx = i;
            }
            return true;
        }

        // Map area — ignore HUD zone
        if (mouseY >= lastHudY) return true;

        if (button == 0) {
            // Left-click → place ghost
            if (selectedIdx >= 0 && selectedIdx < schematics.size()) {
                int[] world = screenToWorld(mouseX, mouseY);
                Minecraft mc = Minecraft.getInstance();
                int surfaceY = mc.level != null
                        ? mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                        world[0], world[1])
                        : (mc.player != null ? (int) mc.player.getY() : 64);

                // Adjust Y so the lowest block of the schematic sits on the surface,
                // matching the same logic used by SchematicPaster.
                SchematicData selectedSchematic = schematics.get(selectedIdx);
                final int snapRotSteps = rotSteps;
                int minRelY = selectedSchematic.getBlocks().keySet().stream()
                        .mapToInt(pos -> SchematicUtils.rotatePositionSteps(pos, snapRotSteps).getY())
                        .min().orElse(0);
                int anchorY = surfaceY - minRelY;

                // Fix 2: guard against placing on top of an existing ghost.
                // isOccupied uses the footprint-aware bounds from PlannerState.
                if (!PlannerState.isOccupied(world[0], world[1], rotSteps, selectedSchematic)) {
                    PlannerState.ghosts.add(new PlacedGhost(
                            selectedSchematic,
                            new BlockPos(world[0], anchorY, world[1]),
                            rotSteps));
                }
            }
            return true;
        }

        if (button == 1) {
            // Right-click → remove ghost under cursor
            int[] world = screenToWorld(mouseX, mouseY);
            if (!PlannerState.tryRemoveAt(world[0], world[1])) {
                // Start pan if nothing to remove
                panning    = true;
                panStartX  = mouseX;
                panStartY  = mouseY;
                panCamX    = camX;
                panCamZ    = camZ;
            }
            return true;
        }

        if (button == 2) {
            // Middle-click → start pan
            panning   = true;
            panStartX = mouseX;
            panStartY = mouseY;
            panCamX   = camX;
            panCamZ   = camZ;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 1 || button == 2) panning = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (panning) {
            float tiltRad = (float) Math.toRadians(tilt);
            camX = panCamX - (mouseX - panStartX) / zoom;
            camZ = panCamZ - (mouseY - panStartY) / (zoom * Math.sin(tiltRad));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (hasShiftDown()) {
            // Shift+Scroll → tilt
            tilt = Math.max(TILT_MIN, Math.min(TILT_MAX, tilt + (float)(delta * 3f)));
        } else if (mouseX < SIDEBAR_W) {
            // Scroll in sidebar → scroll list
            int maxScroll = Math.max(0, schematics.size() * ENTRY_H - (height - 34 - HUD_H - 10));
            sidebarScrollY = Math.max(0, Math.min(maxScroll, sidebarScrollY - (int)(delta * SIDEBAR_SCROLL_STEP)));
        } else {
            // Scroll on map → zoom (pivot around mouse)
            double beforeX = (mouseX - screenCenterX()) / zoom;
            double beforeZ = (mouseY - screenCenterZ()) / (zoom * Math.sin(Math.toRadians(tilt)));
            zoom = Math.max(ZOOM_MIN, Math.min(ZOOM_MAX, zoom + (float)(delta * 1.5f)));
            double afterX = (mouseX - screenCenterX()) / zoom;
            double afterZ = (mouseY - screenCenterZ()) / (zoom * Math.sin(Math.toRadians(tilt)));
            camX -= afterX - beforeX;
            camZ -= afterZ - beforeZ;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // R → rotate schematic 90° CW
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_R) {
            rotSteps = (rotSteps + 1) & 3;
            return true;
        }
        // Tab → switch to 3D free-cam mode (FreeCamScreen captures mouse)
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_TAB) {
            com.gtceuterminal.client.ClientEvents.enterFreeCamScreen(
                    net.minecraft.client.Minecraft.getInstance(), schematics, selectedIdx, rotSteps);
            return true;
        }
        // C → clear all ghosts
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && hasControlDown()) {
            PlannerState.ghosts.clear();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ── Coordinate transforms ─────────────────────────────────────────────────
    private int[] worldToScreen(double worldX, double worldZ) {
        float tiltRad = (float) Math.toRadians(tilt);
        int sx = (int)(screenCenterX() + (worldX - camX) * zoom);
        int sy = (int)(screenCenterZ() + (worldZ - camZ) * zoom * Math.sin(tiltRad));
        return new int[]{ sx, sy };
    }

    private int[] screenToWorld(double screenX, double screenY) {
        float tiltRad = (float) Math.toRadians(tilt);
        int wx = (int) Math.floor(camX + (screenX - screenCenterX()) / zoom);
        int wz = (int) Math.floor(camZ + (screenY - screenCenterZ()) / (zoom * Math.sin(tiltRad)));
        return new int[]{ wx, wz };
    }

    private double screenCenterX() { return SIDEBAR_W + (width - SIDEBAR_W) / 2.0; }
    private double screenCenterZ() { return height / 2.0; }

    // ── Scissor helpers ───────────────────────────────────────────────────────
    private void enableMapScissor(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        double scale  = mc.getWindow().getGuiScale();
        int screenH   = mc.getWindow().getHeight(); // physical pixels, Y=0 at bottom in GL
        int hudPx     = (int) Math.round(HUD_H * scale);
        int mapHeightPx = (int) Math.round(height * scale) - hudPx;
        // GL scissor: x/y are bottom-left in physical pixels; y=0 is the bottom of the screen.
        RenderSystem.enableScissor(
                (int) Math.round(SIDEBAR_W * scale),
                hudPx,                              // bottom edge = HUD height from bottom
                (int) Math.round((width - SIDEBAR_W) * scale),
                mapHeightPx);                       // height excludes the HUD strip
    }

    private void enableSidebarScissor(GuiGraphics g, int top, int listH) {
        Minecraft mc = Minecraft.getInstance();
        double scale = mc.getWindow().getGuiScale();
        int screenH  = mc.getWindow().getHeight();
        RenderSystem.enableScissor(
                0,
                (int) Math.round(screenH - (top + listH) * scale),
                (int) Math.round(SIDEBAR_W * scale),
                (int) Math.round(listH * scale));
    }

    private void disableScissor() {
        RenderSystem.disableScissor();
    }

}