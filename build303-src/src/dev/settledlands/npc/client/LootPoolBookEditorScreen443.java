package dev.settledlands.npc.client;

import dev.settledlands.chest.CustomBook443;
import dev.settledlands.chest.CustomBookManager443;
import dev.settledlands.chest.CustomBookNet443;
import dev.settledlands.chest.LootPool436;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Build 443: in-game editor for custom colored books ({@code config/settledlands/books/<id>.txt},
 * referenced in loot pools as {@code settledlands:book/<id>}).
 *
 * <p>Provides a 16-color + hex + {@code B/I/U/S/R} formatting bar, multi-page editor, live
 * parchment book preview matching vanilla page width (114px / 14 lines), hand-book import,
 * self-give test, server delete, and one-click save into the parent loot pool.</p>
 */
public final class LootPoolBookEditorScreen443 extends Screen {

    private static final int MAX_VISIBLE_POPUP = 7;
    private static final int POPUP_ROW_H = 15;
    private static final int VANILLA_PAGE_WIDTH = 114;
    private static final int VANILLA_PAGE_LINES = 14;

    private static final String[] STYLE_LABELS = { "B", "I", "U", "S", "R" };
    private static final String[] STYLE_CODES = { "&l", "&o", "&n", "&m", "&r" };
    private static final String[] STYLE_TIPS = {
        "gui.settledlands.book_editor.fmt_bold",
        "gui.settledlands.book_editor.fmt_italic",
        "gui.settledlands.book_editor.fmt_underline",
        "gui.settledlands.book_editor.fmt_strike",
        "gui.settledlands.book_editor.fmt_reset"
    };

    static volatile LootPoolBookEditorScreen443 active;

    private final Screen parentScreen;
    private final LootPoolEditorScreen440 parentPoolScreen;
    private final int replaceRowIndex;
    private final String requestedBookId;
    private final boolean openedWithImport;

    private final List<String> pages = new ArrayList<>();
    private int currentPage;

    private String idText = "";
    private String titleText = "";
    private String authorText = "";
    private String hexText = "FFAA00";
    private String loadedServerId = "";
    private boolean fileExistsOnServer;
    private boolean querySent;
    private boolean loading;
    private boolean dirty;
    private boolean suppressSync;
    private boolean addToPoolAfterSave;
    private boolean lastFocusedTitle;
    private boolean bookPopupOpen;
    private int bookPopupScroll;
    private long deleteArmAt;
    private long closeWarnAt;
    private long switchWarnAt;
    private String pendingSwitchId = "";
    private String statusText = "";
    private int statusColor = 0xFF9AA5B8;

    private EditBox idBox;
    private EditBox titleBox;
    private EditBox authorBox;
    private EditBox hexBox;
    private MultiLineEditBox pageBox;
    private Button bookDropBtn;
    private Button newBookBtn;
    private Button deleteBtn;
    private Button closeBtn;
    private Button prevPageBtn;
    private Button nextPageBtn;
    private Button addPageBtn;
    private Button removePageBtn;
    private Button importHandBtn;
    private Button giveBtn;
    private Button saveBtn;
    private Button saveToPoolBtn;

    private int panelX, panelY, panelW, panelH;
    private int idBoxX, idBoxY, idBoxW;
    private int deleteBtnX, newBtnX, newBtnW, authBoxX;
    private int leftColX, leftColW;
    private int rightColX, rightColW;
    private int paletteY, colorStartX, styleStartX, hexBtnX;
    private int pageBarY, editorTop, editorH;
    private int previewTop, previewH;
    private int statusY, btnY;

    public LootPoolBookEditorScreen443(Screen parentScreen, LootPoolEditorScreen440 parentPoolScreen,
            int replaceRowIndex, String initialBookId, CustomBook443.BookDef prefilled) {
        super(Component.translatable("gui.settledlands.book_editor.title"));
        this.parentScreen = parentScreen;
        this.parentPoolScreen = parentPoolScreen;
        this.replaceRowIndex = replaceRowIndex;
        this.requestedBookId = CustomBook443.normalizeBookId(initialBookId);
        this.openedWithImport = prefilled != null;
        if (prefilled != null) {
            this.idText = prefilled.id;
            this.titleText = prefilled.title;
            this.authorText = prefilled.author;
            this.pages.addAll(prefilled.pages);
            this.dirty = true;
            this.statusColor = 0xFF7BE088;
            this.statusText = Component.translatable("gui.settledlands.book_editor.imported").getString();
        } else {
            this.idText = this.requestedBookId;
            this.pages.add("");
        }
        if (this.pages.isEmpty()) this.pages.add("");
    }

    public static void handleServerData(String rawPayload) {
        LootPoolBookEditorScreen443 ed = active;
        if (ed == null || rawPayload == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen != ed) return;
        ed.applyServerData(rawPayload);
    }

    public static void onBooksListUpdated() {
        LootPoolBookEditorScreen443 ed = active;
        if (ed == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen == ed) {
            ed.updateActionButtons();
        }
    }

    @Override
    protected void init() {
        active = this;
        LootPoolCard436.ensureClientListenersRegistered();

        panelW = Math.max(Math.min(width - 16, 720), Math.min(width - 8, 400));
        panelH = Math.max(Math.min(height - 14, 420), Math.min(height - 8, 250));
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;

        int headRight = panelX + panelW - 10;
        Component closeLbl = Component.translatable("gui.settledlands.loot_pool.editor.close");
        int closeW = Math.max(52, font.width(closeLbl.getString()) + 14);
        closeBtn = Button.builder(closeLbl, b -> tryCloseScreen())
                .bounds(headRight - closeW, panelY + 6, closeW, 16)
                .build();
        addRenderableWidget(closeBtn);

        Component delLbl = Component.translatable("gui.settledlands.loot_pool.editor.delete");
        Component delConfLbl = Component.translatable("gui.settledlands.loot_pool.editor.delete_confirm");
        int delW = Math.max(70, Math.max(font.width(delLbl.getString()),
                font.width(delConfLbl.getString())) + 14);
        deleteBtnX = headRight - closeW - 4 - delW;
        deleteBtn = Button.builder(delLbl, b -> onDeletePressed())
                .bounds(deleteBtnX, panelY + 6, delW, 16)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.delete_tip")))
                .build();
        addRenderableWidget(deleteBtn);

        // Row 1: ID + dropdown + New book
        int row1Y = panelY + 26;
        int idLblW = Math.max(24, font.width(
                Component.translatable("gui.settledlands.book_editor.id_lbl").getString()) + 4);
        Component newLbl = Component.translatable("gui.settledlands.book_editor.new_btn");
        newBtnW = Math.max(54, font.width(newLbl.getString()) + 12);
        int dropW = 18;
        idBoxX = panelX + 10 + idLblW;
        idBoxY = row1Y;
        int idMaxW = Math.max(90, Math.min(200, (panelW - 20) / 3));
        idBoxW = idMaxW;

        idBox = new EditBox(font, idBoxX, idBoxY, idBoxW, 18,
                Component.translatable("gui.settledlands.book_editor.id_lbl"));
        idBox.setMaxLength(CustomBook443.MAX_ID_LEN);
        idBox.setHint(Component.literal("dnevnik_1"));
        suppressSync = true;
        idBox.setValue(idText);
        suppressSync = false;
        idBox.setResponder(txt -> {
            if (suppressSync) return;
            idText = txt == null ? "" : txt;
            markDirty();
        });
        addRenderableWidget(idBox);

        bookDropBtn = Button.builder(Component.literal("v"), b -> toggleBookPopup())
                .bounds(idBoxX + idBoxW + 2, row1Y, dropW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.switch_tip")))
                .build();
        addRenderableWidget(bookDropBtn);

        newBtnX = idBoxX + idBoxW + 2 + dropW + 4;
        newBookBtn = Button.builder(newLbl, b -> startNewBook())
                .bounds(newBtnX, row1Y, newBtnW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.new_tip")))
                .build();
        addRenderableWidget(newBookBtn);

        // Row 2: Title & Author
        int row2Y = panelY + 48;
        int titleLblW = Math.max(46, font.width(
                Component.translatable("gui.settledlands.book_editor.title_lbl").getString()) + 4);
        int authLblW = Math.max(38, font.width(
                Component.translatable("gui.settledlands.book_editor.author_lbl").getString()) + 4);
        int authBoxW = Math.max(76, Math.min(130, (panelW - 24) / 4));
        int titleBoxX = panelX + 10 + titleLblW;
        int titleBoxW = Math.max(80, headRight - authBoxW - 6 - authLblW - 6 - titleBoxX);
        authBoxX = headRight - authBoxW;

        titleBox = new EditBox(font, titleBoxX, row2Y, titleBoxW, 18,
                Component.translatable("gui.settledlands.book_editor.title_lbl"));
        titleBox.setMaxLength(CustomBook443.MAX_TITLE_LEN);
        titleBox.setHint(Component.literal("&6&lКнига тайн"));
        suppressSync = true;
        titleBox.setValue(titleText);
        suppressSync = false;
        titleBox.setResponder(txt -> {
            if (suppressSync) return;
            titleText = txt == null ? "" : txt;
            lastFocusedTitle = true;
            markDirty();
        });
        addRenderableWidget(titleBox);

        authorBox = new EditBox(font, authBoxX, row2Y, authBoxW, 18,
                Component.translatable("gui.settledlands.book_editor.author_lbl"));
        authorBox.setMaxLength(CustomBook443.MAX_AUTHOR_LEN);
        authorBox.setHint(Component.literal("SettledLands"));
        suppressSync = true;
        authorBox.setValue(authorText);
        suppressSync = false;
        authorBox.setResponder(txt -> {
            if (suppressSync) return;
            authorText = txt == null ? "" : txt;
            markDirty();
        });
        addRenderableWidget(authorBox);

        // Row 3: Palette bar (16 colors + B/I/U/S/R + Hex)
        paletteY = panelY + 71;
        colorStartX = panelX + 10;
        styleStartX = colorStartX + 16 * 15 + 6;
        int hexBoxX = styleStartX + 5 * 19 + 8;
        int hexBoxW = 50;
        hexBtnX = hexBoxX + hexBoxW + 2;
        if (hexBtnX + 34 <= headRight) {
            hexBox = new EditBox(font, hexBoxX, paletteY - 1, hexBoxW, 16, Component.literal("HEX"));
            hexBox.setMaxLength(6);
            hexBox.setFilter(LootPoolBookEditorScreen443::isHexChar);
            hexBox.setValue(hexText);
            hexBox.setResponder(txt -> hexText = txt == null ? "" : txt.toUpperCase(Locale.ROOT));
            addRenderableWidget(hexBox);

            Button addHexBtn = Button.builder(Component.literal("+Hex"), b -> insertHexCode())
                    .bounds(hexBtnX, paletteY - 1, 34, 16)
                    .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.hex_tip")))
                    .build();
            addRenderableWidget(addHexBtn);
        } else {
            hexBox = null;
        }

        // Columns below palette
        int bodyTop = paletteY + 20;
        btnY = panelY + panelH - 24;
        statusY = btnY - 14;
        int bodyBottom = statusY - 4;

        int innerW = panelW - 20;
        rightColW = Math.max(138, Math.min(200, (innerW - 10) * 40 / 100));
        leftColX = panelX + 10;
        leftColW = Math.max(160, innerW - rightColW - 10);
        rightColX = leftColX + leftColW + 10;

        pageBarY = bodyTop;
        editorTop = pageBarY + 20;
        editorH = Math.max(60, bodyBottom - editorTop);
        previewTop = bodyTop;
        previewH = Math.max(96, bodyBottom - previewTop);

        // Page toolbar
        int px = leftColX;
        prevPageBtn = Button.builder(Component.literal("<"), b -> switchPage(currentPage - 1))
                .bounds(px, pageBarY, 20, 16)
                .build();
        addRenderableWidget(prevPageBtn);

        nextPageBtn = Button.builder(Component.literal(">"), b -> switchPage(currentPage + 1))
                .bounds(px + 92, pageBarY, 20, 16)
                .build();
        addRenderableWidget(nextPageBtn);

        Component addPgLbl = Component.translatable("gui.settledlands.book_editor.add_page");
        Component delPgLbl = Component.translatable("gui.settledlands.book_editor.del_page");
        int addPgW = Math.max(46, font.width(addPgLbl.getString()) + 10);
        int delPgW = Math.max(46, font.width(delPgLbl.getString()) + 10);
        addPageBtn = Button.builder(addPgLbl, b -> addNewPage())
                .bounds(leftColX + leftColW - delPgW - 3 - addPgW, pageBarY, addPgW, 16)
                .build();
        addRenderableWidget(addPageBtn);

        removePageBtn = Button.builder(delPgLbl, b -> removeCurrentPage())
                .bounds(leftColX + leftColW - delPgW, pageBarY, delPgW, 16)
                .build();
        addRenderableWidget(removePageBtn);

        // Multi-line page editor
        pageBox = new MultiLineEditBox(font, leftColX, editorTop, leftColW, editorH,
                Component.translatable("gui.settledlands.book_editor.page_hint"),
                Component.empty());
        pageBox.setCharacterLimit(CustomBook443.MAX_PAGE_CHARS);
        suppressSync = true;
        pageBox.setValue(currentPageText());
        suppressSync = false;
        pageBox.setValueListener(txt -> {
            if (suppressSync) return;
            setCurrentPageText(txt == null ? "" : txt);
            lastFocusedTitle = false;
            markDirty();
        });
        addRenderableWidget(pageBox);

        // Bottom action buttons
        Component handLbl = Component.translatable("gui.settledlands.book_editor.from_hand");
        Component giveLbl = Component.translatable("gui.settledlands.book_editor.give_self");
        Component saveLbl = Component.translatable("gui.settledlands.loot_pool.editor.save");
        Component poolLbl = Component.translatable("gui.settledlands.book_editor.save_to_pool");

        int handW = Math.max(58, font.width(handLbl.getString()) + 12);
        int giveW = Math.max(68, font.width(giveLbl.getString()) + 12);
        int saveW = Math.max(58, font.width(saveLbl.getString()) + 12);
        int poolW = Math.max(82, font.width(poolLbl.getString()) + 12);

        importHandBtn = Button.builder(handLbl, b -> importFromHand())
                .bounds(panelX + 10, btnY, handW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.from_hand_tip")))
                .build();
        addRenderableWidget(importHandBtn);

        giveBtn = Button.builder(giveLbl, b -> giveBookToSelf())
                .bounds(panelX + 14 + handW, btnY, giveW, 18)
                .tooltip(Tooltip.create(Component.translatable("gui.settledlands.book_editor.give_self_tip")))
                .build();
        addRenderableWidget(giveBtn);

        if (parentPoolScreen != null) {
            saveToPoolBtn = Button.builder(poolLbl, b -> triggerSave(true))
                    .bounds(headRight - poolW, btnY, poolW, 18)
                    .build();
            addRenderableWidget(saveToPoolBtn);

            saveBtn = Button.builder(saveLbl, b -> triggerSave(false))
                    .bounds(headRight - poolW - 4 - saveW, btnY, saveW, 18)
                    .build();
            addRenderableWidget(saveBtn);
        } else {
            saveToPoolBtn = null;
            saveBtn = Button.builder(saveLbl, b -> triggerSave(false))
                    .bounds(headRight - saveW, btnY, saveW, 18)
                    .build();
            addRenderableWidget(saveBtn);
        }

        if (!querySent) {
            querySent = true;
            if (!openedWithImport) {
                loading = true;
                statusColor = 0xFF9AA5B8;
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.loading").getString();
                CustomBookNet443.sendBookQuery(requestedBookId);
            } else {
                CustomBookNet443.sendBookQuery("");
            }
        }
        updateActionButtons();
    }

    private String currentPageText() {
        if (currentPage < 0 || currentPage >= pages.size()) return "";
        return pages.get(currentPage);
    }

    private void setCurrentPageText(String text) {
        if (currentPage >= 0 && currentPage < pages.size()) {
            pages.set(currentPage, text == null ? "" : text);
        }
    }

    private void applyServerData(String rawPayload) {
        String[] lines = rawPayload.split("\n", 2);
        String status = lines.length >= 1 ? lines[0].trim() : "";
        String body = lines.length >= 2 ? lines[1] : "";

        loading = false;

        if (status.startsWith("ERR:")) {
            String err = status.substring(4);
            statusColor = 0xFFFF7070;
            if ("noperm".equals(err)) {
                statusText = Component.translatable("message.settledlands.cfg.noperm").getString();
            } else if ("bad_id".equals(err)) {
                statusText = Component.translatable("gui.settledlands.book_editor.err_id").getString();
            } else if ("too_big".equals(err)) {
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_too_big").getString();
            } else {
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_io").getString();
            }
            updateActionButtons();
            return;
        }

        if ("GIVEN".equals(status)) {
            statusColor = 0xFF7BE088;
            statusText = Component.translatable("gui.settledlands.book_editor.given").getString();
            updateActionButtons();
            return;
        }

        if ("DELETED".equals(status)) {
            String delId = CustomBook443.normalizeBookId(body);
            loadedServerId = "";
            fileExistsOnServer = false;
            idText = CustomBook443.suggestNextBookId(CustomBook443.getClientBookIds());
            titleText = "";
            authorText = "";
            pages.clear();
            pages.add("");
            currentPage = 0;
            syncAllBoxes();
            dirty = false;
            statusColor = 0xFFF5B841;
            statusText = Component.translatable("gui.settledlands.book_editor.deleted", delId).getString();
            updateActionButtons();
            return;
        }

        if ("NEW".equals(status)) {
            if (openedWithImport && dirty) {
                updateActionButtons();
                return;
            }
            String suggested = CustomBook443.normalizeBookId(body);
            if (suggested.isEmpty()) suggested = "book_1";
            loadedServerId = "";
            fileExistsOnServer = false;
            idText = suggested;
            titleText = "&6" + suggested;
            authorText = "";
            pages.clear();
            pages.add("");
            currentPage = 0;
            syncAllBoxes();
            dirty = false;
            statusText = "";
            updateActionButtons();
            return;
        }

        if ("OK".equals(status) || "SAVED".equals(status)) {
            CustomBook443.BookDef book = CustomBook443.decodeBookWire(body);
            if (book == null) {
                updateActionButtons();
                return;
            }
            loadedServerId = book.id;
            fileExistsOnServer = true;
            idText = book.id;
            titleText = book.title;
            authorText = book.author;
            pages.clear();
            pages.addAll(book.pages);
            if (pages.isEmpty()) pages.add("");
            if (currentPage >= pages.size()) currentPage = pages.size() - 1;
            syncAllBoxes();
            dirty = false;
            if ("SAVED".equals(status)) {
                statusColor = 0xFF7BE088;
                statusText = Component.translatable("gui.settledlands.book_editor.saved", book.id).getString();
                if (addToPoolAfterSave) {
                    addToPoolAfterSave = false;
                    commitToParentPool(book.itemId());
                    return;
                }
            } else {
                statusText = "";
            }
            updateActionButtons();
        }
    }

    private void syncAllBoxes() {
        suppressSync = true;
        if (idBox != null) idBox.setValue(idText);
        if (titleBox != null) titleBox.setValue(titleText);
        if (authorBox != null) authorBox.setValue(authorText);
        if (pageBox != null) pageBox.setValue(currentPageText());
        suppressSync = false;
    }

    private void markDirty() {
        dirty = true;
        deleteArmAt = 0L;
        closeWarnAt = 0L;
        updateActionButtons();
    }

    private void updateActionButtons() {
        boolean hasId = !CustomBook443.normalizeBookId(idText).isEmpty();
        if (deleteBtn != null) {
            boolean armed = deleteArmAt > 0L && (System.currentTimeMillis() - deleteArmAt) < 4000L;
            deleteBtn.setMessage(Component.translatable(armed
                    ? "gui.settledlands.loot_pool.editor.delete_confirm"
                    : "gui.settledlands.loot_pool.editor.delete"));
            deleteBtn.active = !loading && fileExistsOnServer && !loadedServerId.isEmpty();
        }
        if (saveBtn != null) saveBtn.active = !loading && hasId;
        if (saveToPoolBtn != null) saveToPoolBtn.active = !loading && hasId;
        if (giveBtn != null) giveBtn.active = !loading && hasId;
        if (prevPageBtn != null) prevPageBtn.active = currentPage > 0;
        if (nextPageBtn != null) nextPageBtn.active = currentPage < pages.size() - 1;
        if (addPageBtn != null) addPageBtn.active = pages.size() < CustomBook443.MAX_PAGES;
        if (removePageBtn != null) removePageBtn.active = pages.size() > 1;
        if (bookDropBtn != null) bookDropBtn.active = !loading;
    }

    private void switchPage(int target) {
        int clamped = Math.max(0, Math.min(pages.size() - 1, target));
        if (clamped == currentPage) return;
        currentPage = clamped;
        suppressSync = true;
        if (pageBox != null) pageBox.setValue(currentPageText());
        suppressSync = false;
        playPageTurnSound();
        updateActionButtons();
    }

    private void addNewPage() {
        if (pages.size() >= CustomBook443.MAX_PAGES) return;
        pages.add(currentPage + 1, "");
        currentPage++;
        suppressSync = true;
        if (pageBox != null) pageBox.setValue("");
        suppressSync = false;
        playPageTurnSound();
        markDirty();
    }

    private void removeCurrentPage() {
        if (pages.size() <= 1) return;
        pages.remove(currentPage);
        if (currentPage >= pages.size()) currentPage = pages.size() - 1;
        suppressSync = true;
        if (pageBox != null) pageBox.setValue(currentPageText());
        suppressSync = false;
        playPageTurnSound();
        markDirty();
    }

    private void toggleBookPopup() {
        bookPopupOpen = !bookPopupOpen;
        bookPopupScroll = 0;
    }

    private void startNewBook() {
        bookPopupOpen = false;
        loadedServerId = "";
        fileExistsOnServer = false;
        idText = CustomBook443.suggestNextBookId(CustomBook443.getClientBookIds());
        titleText = "&6" + idText;
        authorText = "";
        pages.clear();
        pages.add("");
        currentPage = 0;
        syncAllBoxes();
        dirty = true;
        statusColor = 0xFF9AA5B8;
        statusText = Component.translatable("gui.settledlands.book_editor.new_created", idText).getString();
        updateActionButtons();
        if (titleBox != null) setFocused(titleBox);
    }

    private void importFromHand() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc == null ? null : mc.player;
        if (player == null) return;
        ItemStack stack = player.getMainHandItem();
        if (!CustomBookManager443.hasCustomBookData(stack)) {
            stack = player.getOffhandItem();
        }
        if (!CustomBookManager443.hasCustomBookData(stack)) {
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.book_editor.err_no_hand_book").getString();
            return;
        }
        String hintId = fileExistsOnServer ? idText : "";
        CustomBook443.BookDef extracted = CustomBookManager443.extractBookFromStack(stack, hintId);
        if (extracted == null) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.book_editor.err_no_hand_book").getString();
            return;
        }
        if (!fileExistsOnServer && (idText.isEmpty() || idText.startsWith("book_"))) {
            idText = extracted.id;
        }
        titleText = extracted.title;
        authorText = extracted.author;
        pages.clear();
        pages.addAll(extracted.pages);
        if (pages.isEmpty()) pages.add("");
        currentPage = 0;
        syncAllBoxes();
        markDirty();
        statusColor = 0xFF7BE088;
        statusText = Component.translatable("gui.settledlands.book_editor.imported").getString();
    }

    private CustomBook443.BookDef buildCurrentDef() {
        String normId = CustomBook443.normalizeBookId(idText);
        if (normId.isEmpty()) return null;
        return new CustomBook443.BookDef(normId, titleText, authorText, pages);
    }

    private void giveBookToSelf() {
        CustomBook443.BookDef def = buildCurrentDef();
        if (def == null) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.book_editor.err_id").getString();
            return;
        }
        if (!CustomBookNet443.sendBookAction("GIVE", def)) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_too_big").getString();
        }
    }

    private void onDeletePressed() {
        if (!fileExistsOnServer || loadedServerId.isEmpty()) return;
        long now = System.currentTimeMillis();
        if (deleteArmAt == 0L || (now - deleteArmAt) >= 4000L) {
            deleteArmAt = now;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.book_editor.delete_warn",
                    loadedServerId).getString();
            updateActionButtons();
            return;
        }
        deleteArmAt = 0L;
        loading = true;
        CustomBookNet443.sendBookAction("DELETE",
                new CustomBook443.BookDef(loadedServerId, "", "", Collections.singletonList("")));
        updateActionButtons();
    }

    private void triggerSave(boolean commitToPool) {
        CustomBook443.BookDef def = buildCurrentDef();
        if (def == null) {
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.book_editor.err_id").getString();
            if (idBox != null) setFocused(idBox);
            return;
        }
        idText = def.id;
        if (idBox != null) {
            suppressSync = true;
            idBox.setValue(idText);
            suppressSync = false;
        }
        loading = true;
        addToPoolAfterSave = commitToPool && parentPoolScreen != null;
        if (!CustomBookNet443.sendBookAction("SAVE", def)) {
            loading = false;
            addToPoolAfterSave = false;
            statusColor = 0xFFFF7070;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.err_too_big").getString();
        }
        updateActionButtons();
    }

    private void commitToParentPool(String itemId) {
        active = null;
        if (parentPoolScreen != null) {
            if (replaceRowIndex >= 0) {
                parentPoolScreen.replaceItemAt(replaceRowIndex, itemId);
            } else {
                parentPoolScreen.addItemEntry(itemId, 1);
            }
            Minecraft.getInstance().setScreen(parentPoolScreen);
        } else {
            Minecraft.getInstance().setScreen(parentScreen);
        }
    }

    private void insertHexCode() {
        String h = hexText == null ? "" : hexText.trim().toUpperCase(Locale.ROOT);
        if (h.length() != 6) {
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.book_editor.err_hex").getString();
            return;
        }
        insertFormattingCode("&#" + h);
    }

    private void insertFormattingCode(String code) {
        if (code == null || code.isEmpty()) return;
        playClick();
        boolean useTitle = (titleBox != null && titleBox.isFocused()) || lastFocusedTitle;
        if (useTitle && titleBox != null) {
            titleBox.insertText(code);
            titleText = titleBox.getValue();
            setFocused(titleBox);
        } else if (pageBox != null) {
            String cur = pageBox.getValue();
            if (cur == null) cur = "";
            if (cur.length() + code.length() <= CustomBook443.MAX_PAGE_CHARS) {
                String next = cur + code;
                pageBox.setValue(next);
                setCurrentPageText(next);
                setFocused(pageBox);
            }
        }
        markDirty();
    }

    private void tryCloseScreen() {
        if (dirty) {
            long now = System.currentTimeMillis();
            if (closeWarnAt == 0L || (now - closeWarnAt) >= 6000L) {
                closeWarnAt = now;
                statusColor = 0xFFFFB048;
                statusText = Component.translatable("gui.settledlands.loot_pool.editor.unsaved_warn")
                        .getString();
                return;
            }
        }
        active = null;
        Minecraft.getInstance().setScreen(parentScreen);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Omit vanilla Screen#renderBlurredBackground so the book editor stays crisp without blur.
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0x990A0D13);

        int accent = 0xFF000000 | LootPool436.ACCENT_COLOR;
        graphics.fill(panelX - 1, panelY - 1, panelX + panelW + 1, panelY + panelH + 1, 0xFF080A0F);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xEE141821);
        graphics.fill(panelX, panelY, panelX + panelW, panelY + 2, accent);
        graphics.renderOutline(panelX, panelY, panelW, panelH, 0xFF2D3546);

        LootPoolGearButton440.drawGearIcon(graphics, panelX + 9, panelY + 8, true, 0xEE141821);
        String heading = Component.translatable("gui.settledlands.book_editor.title").getString();
        if (dirty) heading += " *";
        graphics.drawString(font, heading, panelX + 25, panelY + 10, 0xFFF2F5FA, false);

        // Row labels
        graphics.drawString(font, Component.translatable("gui.settledlands.book_editor.id_lbl"),
                panelX + 10, panelY + 31, 0xFFB0BACB, false);
        String normId = CustomBook443.normalizeBookId(idText);
        String refPreview = normId.isEmpty() ? "---" : (CustomBook443.BOOK_ITEM_PREFIX + normId);
        int refX = newBtnX + newBtnW + 8;
        int refAvail = deleteBtnX - 6 - refX;
        if (refAvail > 40) {
            graphics.drawString(font, clip(font, refPreview, refAvail), refX, panelY + 31,
                    normId.isEmpty() ? 0xFFFF7070 : 0xFF8C98AD, false);
        }

        graphics.drawString(font, Component.translatable("gui.settledlands.book_editor.title_lbl"),
                panelX + 10, panelY + 53, 0xFFB0BACB, false);
        if (authorBox != null) {
            int authLblX = authBoxX - 4 - font.width(
                    Component.translatable("gui.settledlands.book_editor.author_lbl").getString());
            graphics.drawString(font, Component.translatable("gui.settledlands.book_editor.author_lbl"),
                    authLblX, panelY + 53, 0xFFB0BACB, false);
        }

        // Color & formatting toolbar
        Component hoverTip = null;
        for (int i = 0; i < CustomBook443.COLORS.length; i++) {
            CustomBook443.ColorDef col = CustomBook443.COLORS[i];
            int cx = colorStartX + i * 15;
            int cy = paletteY;
            boolean over = !bookPopupOpen && mouseX >= cx && mouseX < cx + 13 && mouseY >= cy && mouseY < cy + 14;
            graphics.fill(cx, cy, cx + 13, cy + 14, 0xFF000000 | col.rgb);
            graphics.renderOutline(cx, cy, 13, 14, over ? 0xFFFFFFFF : 0xFF3A4458);
            if (over) {
                hoverTip = Component.translatable("gui.settledlands.book_editor.color_tip",
                        "&" + col.code);
            }
        }
        for (int i = 0; i < STYLE_LABELS.length; i++) {
            int sx = styleStartX + i * 19;
            int sy = paletteY;
            boolean over = !bookPopupOpen && mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 14;
            graphics.fill(sx, sy, sx + 17, sy + 14, over ? 0xFF2D374B : 0xFF1B212E);
            graphics.renderOutline(sx, sy, 17, 14, over ? accent : 0xFF3A4458);
            graphics.drawCenteredString(font, STYLE_LABELS[i], sx + 9, sy + 3, 0xFFEAEFF8);
            if (over) {
                hoverTip = Component.translatable(STYLE_TIPS[i], STYLE_CODES[i]);
            }
        }

        // Page counter label
        String pageCounter = (currentPage + 1) + " / " + pages.size();
        graphics.drawCenteredString(font, pageCounter, leftColX + 56, pageBarY + 4, 0xFFEAEFF8);

        // Live parchment book preview on the right
        renderBookPreview(graphics);

        // Status text
        if (!statusText.isEmpty()) {
            graphics.drawString(font, clip(font, statusText, panelW - 20),
                    panelX + 10, statusY, statusColor, false);
        } else {
            String charsInfo = Component.translatable("gui.settledlands.book_editor.chars_hint",
                    Integer.valueOf(currentPageText().length()),
                    Integer.valueOf(CustomBook443.MAX_PAGE_CHARS)).getString();
            graphics.drawString(font, clip(font, charsInfo, panelW - 20),
                    panelX + 10, statusY, 0xFF7D899E, false);
        }

        super.render(graphics, mouseX, mouseY, partialTick);

        if (bookPopupOpen) {
            renderBookPopup(graphics, mouseX, mouseY);
        } else if (hoverTip != null) {
            setTooltipForNextRenderPass(hoverTip);
        }
    }

    private void renderBookPreview(GuiGraphics graphics) {
        graphics.fill(rightColX, previewTop, rightColX + rightColW, previewTop + previewH, 0xAA0C1018);
        graphics.renderOutline(rightColX, previewTop, rightColW, previewH, 0xFF283244);

        // Item tooltip-like header showing colored book title + author
        int headerY = previewTop + 5;
        String shownTitle = titleText.trim().isEmpty() ? idText : titleText;
        Component titleComp = CustomBookManager443.toFormattedComponent(shownTitle, true);
        graphics.drawString(font, titleComp, rightColX + 6, headerY, 0xFFFFFFFF, false);
        int nextY = headerY + 11;
        if (!authorText.trim().isEmpty()) {
            Component byComp = Component.translatable("gui.settledlands.book_editor.by_author", authorText.trim());
            graphics.drawString(font, byComp, rightColX + 6, nextY, 0xFF9AA5B8, false);
            nextY += 11;
        }

        // Parchment sheet mimicking Minecraft BookViewScreen
        int sheetPadX = Math.max(4, (rightColW - Math.min(rightColW - 8, 134)) / 2);
        int sheetX = rightColX + sheetPadX;
        int sheetW = Math.min(rightColW - 8, 134);
        int sheetY = nextY + 3;
        int sheetH = Math.max(60, previewTop + previewH - sheetY - 6);

        graphics.fill(sheetX, sheetY, sheetX + sheetW, sheetY + sheetH, 0xFFF5EBCD);
        graphics.renderOutline(sheetX, sheetY, sheetW, sheetH, 0xFF7B6342);
        graphics.renderOutline(sheetX + 2, sheetY + 2, sheetW - 4, sheetH - 4, 0xFFD9C7A1);

        int textW = Math.min(VANILLA_PAGE_WIDTH, sheetW - 14);
        int textX = sheetX + (sheetW - textW) / 2;
        int textY = sheetY + 8;

        Component pageComp = CustomBookManager443.toFormattedComponent(currentPageText(), false);
        List<FormattedCharSequence> wrapped = font.split(pageComp, textW);

        int maxDrawLines = Math.max(1, (sheetH - 20) / 9);
        for (int i = 0; i < wrapped.size() && i < maxDrawLines; i++) {
            int col = i >= VANILLA_PAGE_LINES ? 0xFFA03322 : 0xFF1F160E;
            graphics.drawString(font, wrapped.get(i), textX, textY + i * 9, col, false);
        }

        String lineInfo = wrapped.size() + " / " + VANILLA_PAGE_LINES;
        int infoCol = wrapped.size() > VANILLA_PAGE_LINES ? 0xFFB03020 : 0xFF6B5B45;
        int iw = font.width(lineInfo);
        graphics.drawString(font, lineInfo, sheetX + sheetW - iw - 6, sheetY + sheetH - 12, infoCol, false);
    }

    private void renderBookPopup(GuiGraphics graphics, int mouseX, int mouseY) {
        List<CustomBook443.BookDef> books = CustomBook443.getClientBooks();
        int px = idBoxX;
        int py = idBoxY + 19;
        int pw = Math.max(idBoxW + 50, 175);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, books.size()));
        int ph = visible * POPUP_ROW_H + 2;

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 500.0F);

        graphics.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, 0xFF000000);
        graphics.fill(px, py, px + pw, py + ph, 0xF5181C24);
        graphics.renderOutline(px - 1, py - 1, pw + 2, ph + 2, 0xFF000000 | LootPool436.ACCENT_COLOR);

        if (books.isEmpty()) {
            graphics.drawString(font,
                    clip(font, Component.translatable("gui.settledlands.book_editor.none").getString(), pw - 10),
                    px + 5, py + 4, 0xFF8A95A8, false);
        } else {
            for (int r = 0; r < visible; r++) {
                int idx = bookPopupScroll + r;
                if (idx >= books.size()) break;
                CustomBook443.BookDef b = books.get(idx);
                int ry = py + 1 + r * POPUP_ROW_H;
                boolean over = mouseX >= px && mouseX <= px + pw && mouseY >= ry && mouseY < ry + POPUP_ROW_H;
                if (over) {
                    graphics.fill(px + 1, ry, px + pw - 1, ry + POPUP_ROW_H, 0xFF2D374B);
                }
                String label = b.id + "  (" + b.plainTitle() + ")";
                int col = b.id.equals(loadedServerId) ? (0xFF000000 | LootPool436.ACCENT_COLOR) : 0xFFE2E8F2;
                graphics.drawString(font, clip(font, label, pw - 14), px + 5, ry + 3, col, false);
            }
            if (books.size() > visible) {
                int barH = Math.max(8, (visible * POPUP_ROW_H) * visible / books.size());
                int maxPScr = Math.max(1, books.size() - visible);
                int barY = py + 1 + ((visible * POPUP_ROW_H - barH) * bookPopupScroll / maxPScr);
                graphics.fill(px + pw - 4, barY, px + pw - 1, barY + barH, 0xFF6B7994);
            }
        }

        graphics.pose().popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (bookPopupOpen) {
            if (button == 0 && handlePopupClick(mouseX, mouseY)) {
                return true;
            }
            bookPopupOpen = false;
            return true;
        }
        if (button == 0) {
            if (titleBox != null && titleBox.isMouseOver(mouseX, mouseY)) {
                lastFocusedTitle = true;
            } else if ((pageBox != null && pageBox.isMouseOver(mouseX, mouseY))
                    || (idBox != null && idBox.isMouseOver(mouseX, mouseY))
                    || (authorBox != null && authorBox.isMouseOver(mouseX, mouseY))) {
                lastFocusedTitle = false;
            }
            for (int i = 0; i < CustomBook443.COLORS.length; i++) {
                int cx = colorStartX + i * 15;
                int cy = paletteY;
                if (mouseX >= cx && mouseX < cx + 13 && mouseY >= cy && mouseY < cy + 14) {
                    insertFormattingCode("&" + CustomBook443.COLORS[i].code);
                    return true;
                }
            }
            for (int i = 0; i < STYLE_CODES.length; i++) {
                int sx = styleStartX + i * 19;
                int sy = paletteY;
                if (mouseX >= sx && mouseX < sx + 17 && mouseY >= sy && mouseY < sy + 14) {
                    insertFormattingCode(STYLE_CODES[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private boolean handlePopupClick(double mouseX, double mouseY) {
        List<CustomBook443.BookDef> books = CustomBook443.getClientBooks();
        int px = idBoxX;
        int py = idBoxY + 19;
        int pw = Math.max(idBoxW + 50, 175);
        int visible = Math.max(1, Math.min(MAX_VISIBLE_POPUP, books.size()));
        int ph = visible * POPUP_ROW_H + 2;

        if (mouseX < px || mouseX > px + pw || mouseY < py || mouseY > py + ph) {
            return false;
        }
        if (books.isEmpty()) {
            bookPopupOpen = false;
            return true;
        }
        int row = (int) ((mouseY - (py + 1)) / POPUP_ROW_H);
        int idx = bookPopupScroll + row;
        if (idx < 0 || idx >= books.size()) return true;

        String chosen = books.get(idx).id;
        long now = System.currentTimeMillis();
        if (dirty && !chosen.equals(pendingSwitchId)
                && (switchWarnAt == 0L || (now - switchWarnAt) >= 5000L)) {
            pendingSwitchId = chosen;
            switchWarnAt = now;
            statusColor = 0xFFFFB048;
            statusText = Component.translatable("gui.settledlands.loot_pool.editor.switch_warn",
                    chosen).getString();
            return true;
        }
        pendingSwitchId = "";
        bookPopupOpen = false;
        loading = true;
        statusColor = 0xFF9AA5B8;
        statusText = Component.translatable("gui.settledlands.loot_pool.editor.loading").getString();
        CustomBookNet443.sendBookQuery(chosen);
        updateActionButtons();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (bookPopupOpen) {
            int step = scrollY > 0 ? -1 : (scrollY < 0 ? 1 : 0);
            if (step != 0) {
                int maxPScr = Math.max(0, CustomBook443.getClientBooks().size() - MAX_VISIBLE_POPUP);
                bookPopupScroll = Math.max(0, Math.min(maxPScr, bookPopupScroll + step));
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) {
            if (bookPopupOpen) {
                bookPopupOpen = false;
                return true;
            }
            tryCloseScreen();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        tryCloseScreen();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static boolean isHexChar(String s) {
        if (s == null || s.isEmpty()) return true;
        for (int i = 0; i < s.length(); i++) {
            char c = Character.toUpperCase(s.charAt(i));
            if (!((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F'))) return false;
        }
        return true;
    }

    private static void playClick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static void playPageTurnSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    private static String clip(Font font, String text, int maxW) {
        if (font == null || text == null || maxW <= 6) return "";
        if (font.width(text) <= maxW) return text;
        String cut = font.plainSubstrByWidth(text, Math.max(4, maxW - font.width("...")));
        return cut + "...";
    }
}
