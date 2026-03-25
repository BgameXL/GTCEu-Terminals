package com.gtceuterminal.client.gui.blueprint;

import com.gtceuterminal.client.blueprint.BlueprintFileManager;
import com.gtceuterminal.common.data.SchematicData;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * GUI for managing saved blueprints.
 *
 * Called from {@link com.gtceuterminal.client.gui.planner.PlannerScreen} via
 * the "Blueprints" button in the sidebar. When the player loads a blueprint,
 * {@code onLoad} is called with the loaded {@link SchematicData} so the planner
 * can add it to its schematic list.
 */
public class BlueprintLibraryScreen extends Screen {

    // ── Layout ────────────────────────────────────────────────────────────────
    private static final int W         = 280;
    private static final int H         = 220;
    private static final int ENTRY_H   = 20;
    private static final int LIST_PAD  = 6;

    // ── Colors ────────────────────────────────────────────────────────────────
    private static final int C_BG        = 0xFF1A1A1A;
    private static final int C_PANEL     = 0xFF222222;
    private static final int C_BORDER    = 0xFF2E75B6;
    private static final int C_TEXT      = 0xFFDDDDDD;
    private static final int C_DIM       = 0xFF888888;
    private static final int C_SEL       = 0x332E75B6;
    private static final int C_BTN       = 0xFF1A3A6B;
    private static final int C_BTN_BDR   = 0xFF2E75B6;
    private static final int C_BTN_DEL   = 0xFF6B1E1E;
    private static final int C_BTN_DEL_B = 0xFF8B2E2E;
    private static final int C_INPUT_BG  = 0xFF111111;
    private static final int C_INPUT_BDR = 0xFF444444;
    private static final int C_INPUT_ACT = 0xFF2E75B6;

    // ── State ─────────────────────────────────────────────────────────────────
    private final SchematicData        currentSchematic;  // the schematic open in planner (to save)
    private final Consumer<SchematicData> onLoad;         // called when user loads a blueprint
    private final Screen               parentScreen;

    private List<String> blueprintNames = new ArrayList<>();
    private int          selectedIdx    = -1;
    private int          scrollY        = 0;

    // Save-as text field
    private String  saveNameText  = "";
    private boolean saveFieldFocus = false;
    private long    cursorTimer   = 0;

    // Feedback message (shown for ~2s after save/delete)
    private String feedbackMsg  = "";
    private long   feedbackTime = 0;

    // Cached window origin
    private int wx, wy;

    // ── List area geometry (relative to wx,wy) ────────────────────────────────
    private static final int LIST_TOP  = 30;
    private static final int LIST_H    = 120;
    private static final int LIST_LEFT = LIST_PAD;
    private static final int LIST_W    = W - LIST_PAD * 2 - 90; // room for Load+Del buttons

    // ── Constructor ───────────────────────────────────────────────────────────
    public BlueprintLibraryScreen(Screen parent,
                                  SchematicData currentSchematic,
                                  Consumer<SchematicData> onLoad) {
        super(Component.translatable("gui.gtceuterminal.blueprint.library.title"));
        this.parentScreen      = parent;
        this.currentSchematic  = currentSchematic;
        this.onLoad            = onLoad;
        this.saveNameText      = currentSchematic != null
                ? BlueprintFileManager.sanitize(
                // Prefer multiblockType as default name — it's more descriptive than
                // "Clipboard" which is the generic name SchematicCopier assigns.
                currentSchematic.getMultiblockType() != null
                        && !currentSchematic.getMultiblockType().isBlank()
                        && !currentSchematic.getMultiblockType().equals("null")
                        ? currentSchematic.getMultiblockType()
                        .replaceAll(".*:", "") // strip namespace prefix e.g. "gtceu:blast_furnace" → "blast_furnace"
                        : currentSchematic.getName())
                : "";
    }

    @Override
    protected void init() {
        super.init();
        wx = (width  - W) / 2;
        wy = (height - H) / 2;
        refreshList();
    }

    private void refreshList() {
        blueprintNames = BlueprintFileManager.listBlueprintNames();
        selectedIdx = Math.min(selectedIdx, blueprintNames.size() - 1);
    }

    // ── Render ────────────────────────────────────────────────────────────────
    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // Dim background
        g.fill(0, 0, width, height, 0x88000000);

        // Panel
        g.fill(wx, wy, wx + W, wy + H, C_BG);
        g.fill(wx, wy, wx + W, wy + 1, C_BORDER);
        g.fill(wx, wy + H - 1, wx + W, wy + H, C_BORDER);
        g.fill(wx, wy, wx + 1, wy + H, C_BORDER);
        g.fill(wx + W - 1, wy, wx + W, wy + H, C_BORDER);

        // Title
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.blueprint.library.title").getString(),
                wx + 8, wy + 8, C_TEXT, false);

        // Close button [✕]
        int closeBx = wx + W - 18;
        int closeBy = wy + 4;
        renderBtn(g, closeBx, closeBy, 14, 14, C_BTN_DEL, C_BTN_DEL_B, mouseX, mouseY);
        g.drawString(font,
                Component.translatable("gui.gtceuterminal.blueprint.library.close").getString(),
                closeBx + 3, closeBy + 3, 0xFFFFFFFF, false);

        // List area background
        int lx = wx + LIST_LEFT;
        int ly = wy + LIST_TOP;
        g.fill(lx - 1, ly - 1, lx + LIST_W + 1, ly + LIST_H + 1, C_INPUT_BDR);
        g.fill(lx, ly, lx + LIST_W, ly + LIST_H, C_PANEL);

        // Entries
        int maxScroll = Math.max(0, blueprintNames.size() * ENTRY_H - LIST_H);
        scrollY = Math.max(0, Math.min(maxScroll, scrollY));

        net.minecraft.client.gui.Font f = font;
        for (int i = 0; i < blueprintNames.size(); i++) {
            String name  = blueprintNames.get(i);
            int    ey    = ly + i * ENTRY_H - scrollY;
            if (ey + ENTRY_H < ly || ey > ly + LIST_H) continue;

            boolean sel     = (i == selectedIdx);
            boolean hovered = mouseX >= lx && mouseX < lx + LIST_W
                    && mouseY >= ey && mouseY < ey + ENTRY_H;
            if (sel)     g.fill(lx, ey, lx + LIST_W, ey + ENTRY_H, C_SEL);
            if (hovered) g.fill(lx, ey, lx + LIST_W, ey + ENTRY_H, 0x18FFFFFF);

            String display = name.length() > 22 ? name.substring(0, 19) + "…" : name;
            g.drawString(f, display, lx + 4, ey + 6, C_TEXT, false);
        }

        // Load + Delete buttons (right of list)
        int btnX  = lx + LIST_W + 4;
        int btnY  = wy + LIST_TOP;
        boolean hasSelection = selectedIdx >= 0 && selectedIdx < blueprintNames.size();

        renderBtn(g, btnX, btnY,      82, 14, hasSelection ? C_BTN : 0xFF2A2A2A,
                hasSelection ? C_BTN_BDR : 0xFF444444, mouseX, mouseY);
        g.drawString(f,
                hasSelection
                        ? Component.translatable("gui.gtceuterminal.blueprint.library.load.enabled").getString()
                        : Component.translatable("gui.gtceuterminal.blueprint.library.load.disabled").getString(),
                btnX + 4, btnY + 3, 0xFFFFFFFF, false);

        renderBtn(g, btnX, btnY + 20, 82, 14, hasSelection ? C_BTN_DEL : 0xFF2A2A2A,
                hasSelection ? C_BTN_DEL_B : 0xFF444444, mouseX, mouseY);
        g.drawString(f,
                hasSelection
                        ? Component.translatable("gui.gtceuterminal.blueprint.library.delete.enabled").getString()
                        : Component.translatable("gui.gtceuterminal.blueprint.library.delete.disabled").getString(),
                btnX + 4, btnY + 23, 0xFFFFFFFF, false);

        // Save-as section
        int saveY = wy + LIST_TOP + LIST_H + 14;
        g.drawString(f,
                Component.translatable("gui.gtceuterminal.blueprint.library.save_as_label").getString(),
                wx + LIST_PAD, saveY, C_DIM, false);

        int inputY    = saveY + 12;
        int inputW    = W - LIST_PAD * 2 - 46;
        int inputBdr  = saveFieldFocus ? C_INPUT_ACT : C_INPUT_BDR;
        g.fill(wx + LIST_PAD,         inputY,     wx + LIST_PAD + inputW, inputY + 14, C_INPUT_BG);
        g.fill(wx + LIST_PAD,         inputY,     wx + LIST_PAD + inputW, inputY + 1,  inputBdr);
        g.fill(wx + LIST_PAD,         inputY + 13,wx + LIST_PAD + inputW, inputY + 14, inputBdr);
        g.fill(wx + LIST_PAD,         inputY,     wx + LIST_PAD + 1,       inputY + 14, inputBdr);
        g.fill(wx + LIST_PAD + inputW - 1, inputY, wx + LIST_PAD + inputW, inputY + 14, inputBdr);

        String displayText = saveNameText;
        boolean showCursor = saveFieldFocus && ((System.currentTimeMillis() / 500) % 2 == 0);
        g.drawString(f, displayText + (showCursor ? "|" : ""), wx + LIST_PAD + 4, inputY + 3, C_TEXT, false);

        // Save button
        int saveBtnX = wx + LIST_PAD + inputW + 4;
        boolean canSave = !saveNameText.isBlank() && currentSchematic != null;
        renderBtn(g, saveBtnX, inputY, 38, 14, canSave ? 0xFF1E6B1E : 0xFF2A2A2A,
                canSave ? 0xFF2E8B2E : 0xFF444444, mouseX, mouseY);
        g.drawString(f,
                canSave
                        ? Component.translatable("gui.gtceuterminal.blueprint.library.save.enabled").getString()
                        : Component.translatable("gui.gtceuterminal.blueprint.library.save.disabled").getString(),
                saveBtnX + 4, inputY + 3, 0xFFFFFFFF, false);

        // Feedback message
        if (!feedbackMsg.isEmpty()) {
            long age = System.currentTimeMillis() - feedbackTime;
            if (age < 2000) {
                g.drawString(f, feedbackMsg, wx + LIST_PAD, inputY + 18, 0xFFAAAAAA, false);
            } else {
                feedbackMsg = "";
            }
        }

        super.render(g, mouseX, mouseY, partialTick);
    }

    private void renderBtn(GuiGraphics g, int x, int y, int w, int h,
                           int fill, int bdr, int mx, int my) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + h;
        g.fill(x, y, x + w, y + h, hov ? brighten(fill) : fill);
        g.fill(x, y, x + w, y + 1, bdr);
        g.fill(x, y + h - 1, x + w, y + h, bdr);
        g.fill(x, y, x + 1, y + h, bdr);
        g.fill(x + w - 1, y, x + w, y + h, bdr);
    }

    private int brighten(int argb) {
        int a = (argb >> 24) & 0xFF;
        int r = Math.min(255, ((argb >> 16) & 0xFF) + 20);
        int gv= Math.min(255, ((argb >>  8) & 0xFF) + 20);
        int b = Math.min(255, ( argb        & 0xFF) + 20);
        return (a << 24) | (r << 16) | (gv << 8) | b;
    }

    // ── Input ─────────────────────────────────────────────────────────────────
    @Override
    public boolean mouseClicked(double mx, double my, int btn) {
        if (btn != 0) return super.mouseClicked(mx, my, btn);

        // Close button
        int closeBx = wx + W - 18, closeBy = wy + 4;
        if (mx >= closeBx && mx < closeBx + 14 && my >= closeBy && my < closeBy + 14) {
            onClose(); return true;
        }

        // List entries
        int lx = wx + LIST_LEFT, ly = wy + LIST_TOP;
        if (mx >= lx && mx < lx + LIST_W && my >= ly && my < ly + LIST_H) {
            int idx = ((int) my - ly + scrollY) / ENTRY_H;
            if (idx >= 0 && idx < blueprintNames.size()) {
                selectedIdx = idx;
                saveFieldFocus = false;
            }
            return true;
        }

        // Load button
        int btnX = lx + LIST_W + 4, btnY = wy + LIST_TOP;
        if (mx >= btnX && mx < btnX + 82 && my >= btnY && my < btnY + 14) {
            doLoad(); return true;
        }
        // Delete button
        if (mx >= btnX && mx < btnX + 82 && my >= btnY + 20 && my < btnY + 34) {
            doDelete(); return true;
        }

        // Save text field
        int saveY   = wy + LIST_TOP + LIST_H + 14;
        int inputY  = saveY + 12;
        int inputW  = W - LIST_PAD * 2 - 46;
        if (mx >= wx + LIST_PAD && mx < wx + LIST_PAD + inputW
                && my >= inputY && my < inputY + 14) {
            saveFieldFocus = true; return true;
        }

        // Save button
        int saveBtnX = wx + LIST_PAD + inputW + 4;
        if (mx >= saveBtnX && mx < saveBtnX + 38 && my >= inputY && my < inputY + 14) {
            doSave(); return true;
        }

        saveFieldFocus = false;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int lx = wx + LIST_LEFT, ly = wy + LIST_TOP;
        if (mx >= lx && mx < lx + LIST_W && my >= ly && my < ly + LIST_H) {
            int maxScroll = Math.max(0, blueprintNames.size() * ENTRY_H - LIST_H);
            scrollY = Math.max(0, Math.min(maxScroll, scrollY - (int)(delta * ENTRY_H)));
            return true;
        }
        return super.mouseScrolled(mx, my, delta);
    }


    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (saveFieldFocus) {
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE && !saveNameText.isEmpty()) {
                saveNameText = saveNameText.substring(0, saveNameText.length() - 1);
                return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER) {
                doSave(); return true;
            }
            if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
                saveFieldFocus = false; return true;
            }
            return true; // consume all keys while focused
        }
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            onClose(); return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (saveFieldFocus && saveNameText.length() < 40
                && (Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == ' ')) {
            saveNameText += c;
            return true;
        }
        return false;
    }

    // ── Actions ───────────────────────────────────────────────────────────────
    private void doLoad() {
        if (selectedIdx < 0 || selectedIdx >= blueprintNames.size()) return;
        String name = blueprintNames.get(selectedIdx);
        SchematicData loaded = BlueprintFileManager.load(name);
        if (loaded != null) {
            // Rename the loaded schematic to the file name so it appears correctly
            // in the PlannerScreen sidebar (otherwise everything shows as "Clipboard").
            SchematicData renamed = new SchematicData(
                    name,
                    loaded.getMultiblockType(),
                    loaded.getBlocks(),
                    loaded.getBlockEntities(),
                    loaded.getOriginalFacing());
            onLoad.accept(renamed);
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.loaded",
                    name
            ).getString());
            onClose();
        } else {
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.failed_load"
            ).getString());
        }
    }

    private void doDelete() {
        if (selectedIdx < 0 || selectedIdx >= blueprintNames.size()) return;
        String name = blueprintNames.get(selectedIdx);
        if (BlueprintFileManager.delete(name)) {
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.deleted",
                    name
            ).getString());
            refreshList();
            selectedIdx = Math.min(selectedIdx, blueprintNames.size() - 1);
        } else {
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.failed_delete"
            ).getString());
        }
    }

    private void doSave() {
        if (saveNameText.isBlank() || currentSchematic == null) return;
        if (BlueprintFileManager.save(saveNameText.trim(), currentSchematic)) {
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.saved_as",
                    saveNameText.trim()
            ).getString());
            refreshList();
        } else {
            showFeedback(Component.translatable(
                    "gui.gtceuterminal.blueprint.library.feedback.failed_save"
            ).getString());
        }
    }

    private void showFeedback(String msg) {
        feedbackMsg  = msg;
        feedbackTime = System.currentTimeMillis();
    }

    @Override
    public void onClose() {
        net.minecraft.client.Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}