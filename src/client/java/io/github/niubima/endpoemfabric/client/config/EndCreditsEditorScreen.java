package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.CustomCredits;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.WinScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Structured editor for the ordered custom contributor list.
 */
public final class EndCreditsEditorScreen extends Screen {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SECONDARY_TEXT_COLOR = 0xFFAAAAAA;
    private static final int SUCCESS_COLOR = 0xFF55FF55;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final int CONTROL_HEIGHT = 20;
    private static final int GAP = 4;

    private final Screen parent;
    private final List<CustomCredits.Entry> entries = new ArrayList<>();

    private boolean loaded;
    private boolean dirty;
    private boolean loadingFields;
    private String section = "";
    private int selectedIndex = -1;
    private int contentLeft;
    private int contentWidth;
    private int formY;
    private Component status = Component.empty();
    private int statusColor = TEXT_COLOR;

    private CreditsList creditsList;
    private EditBox sectionBox;
    private EditBox nameBox;
    private EditBox roleBox;
    private Button deleteButton;
    private Button moveUpButton;
    private Button moveDownButton;

    public EndCreditsEditorScreen(Screen parent) {
        super(Component.translatable("text.endpoemfabric.credits_editor.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadIfNeeded();

        contentWidth = Math.max(120, Math.min(700, width - 24));
        contentLeft = (width - contentWidth) / 2;

        int sectionLabelWidth = font.width(Component.translatable(
                "text.endpoemfabric.credits_editor.section"
        )) + 8;
        sectionBox = new EditBox(
                font,
                contentLeft + sectionLabelWidth,
                26,
                Math.max(80, contentWidth - sectionLabelWidth),
                CONTROL_HEIGHT,
                Component.translatable("text.endpoemfabric.credits_editor.section")
        );
        sectionBox.setMaxLength(CustomCredits.MAX_SECTION_LENGTH);
        sectionBox.setValue(section);
        sectionBox.setResponder(value -> {
            if (!loadingFields) {
                section = value;
                markDirty();
            }
        });
        sectionBox.setHint(Component.translatable("text.endpoemfabric.credits_editor.section_hint"));
        addRenderableWidget(sectionBox);

        int listTop = 64;
        int listBottom = Math.max(listTop + 34, height - 126);
        creditsList = new CreditsList(minecraft, contentWidth, listBottom - listTop, contentLeft, listTop);
        addRenderableWidget(creditsList);

        formY = listBottom + 16;
        int fieldWidth = (contentWidth - GAP) / 2;
        nameBox = new EditBox(
                font,
                contentLeft,
                formY,
                fieldWidth,
                CONTROL_HEIGHT,
                Component.translatable("text.endpoemfabric.credits_editor.name")
        );
        nameBox.setMaxLength(CustomCredits.MAX_FIELD_LENGTH);
        nameBox.setHint(Component.translatable("text.endpoemfabric.credits_editor.name_hint"));
        nameBox.setResponder(value -> updateSelectedEntry(value, null));
        addRenderableWidget(nameBox);

        roleBox = new EditBox(
                font,
                contentLeft + fieldWidth + GAP,
                formY,
                contentWidth - fieldWidth - GAP,
                CONTROL_HEIGHT,
                Component.translatable("text.endpoemfabric.credits_editor.role")
        );
        roleBox.setMaxLength(CustomCredits.MAX_FIELD_LENGTH);
        roleBox.setHint(Component.translatable("text.endpoemfabric.credits_editor.role_hint"));
        roleBox.setResponder(value -> updateSelectedEntry(null, value));
        addRenderableWidget(roleBox);

        int actionY = formY + CONTROL_HEIGHT + 5;
        int actionWidth = (contentWidth - GAP * 3) / 4;
        addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.add"),
                        button -> addEntry()
                )
                .bounds(contentLeft, actionY, actionWidth, CONTROL_HEIGHT)
                .build());

        deleteButton = addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.delete"),
                        button -> deleteSelectedEntry()
                )
                .bounds(contentLeft + (actionWidth + GAP), actionY, actionWidth, CONTROL_HEIGHT)
                .build());

        moveUpButton = addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.move_up"),
                        button -> moveSelectedEntry(-1)
                )
                .bounds(contentLeft + (actionWidth + GAP) * 2, actionY, actionWidth, CONTROL_HEIGHT)
                .build());

        moveDownButton = addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.move_down"),
                        button -> moveSelectedEntry(1)
                )
                .bounds(
                        contentLeft + (actionWidth + GAP) * 3,
                        actionY,
                        contentWidth - (actionWidth + GAP) * 3,
                        CONTROL_HEIGHT
                )
                .build());

        int bottomY = height - 28;
        int bottomWidth = (contentWidth - GAP * 2) / 3;
        addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.save"),
                        button -> save()
                )
                .bounds(contentLeft, bottomY, bottomWidth, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_editor.preview"),
                        button -> preview()
                )
                .bounds(contentLeft + bottomWidth + GAP, bottomY, bottomWidth, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.done"),
                        button -> saveAndClose()
                )
                .bounds(
                        contentLeft + (bottomWidth + GAP) * 2,
                        bottomY,
                        contentWidth - (bottomWidth + GAP) * 2,
                        CONTROL_HEIGHT
                )
                .build());

        creditsList.refresh(selectedIndex);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.centeredText(font, title, width / 2, 12, TEXT_COLOR);
        graphics.text(
                font,
                Component.translatable("text.endpoemfabric.credits_editor.section"),
                contentLeft,
                32,
                TEXT_COLOR,
                true
        );
        graphics.text(
                font,
                Component.translatable("text.endpoemfabric.credits_editor.name"),
                contentLeft,
                formY - 10,
                SECONDARY_TEXT_COLOR,
                true
        );
        graphics.text(
                font,
                Component.translatable("text.endpoemfabric.credits_editor.role"),
                contentLeft + (contentWidth - GAP) / 2 + GAP,
                formY - 10,
                SECONDARY_TEXT_COLOR,
                true
        );

        if (!status.getString().isEmpty()) {
            graphics.centeredText(font, status, width / 2, 51, statusColor);
        }
        super.extractRenderState(graphics, mouseX, mouseY, delta);
        if (entries.isEmpty()) {
            graphics.centeredText(
                    font,
                    Component.translatable("text.endpoemfabric.credits_editor.empty"),
                    width / 2,
                    78,
                    SECONDARY_TEXT_COLOR
            );
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (event.hasAltDown() && event.key() == GLFW.GLFW_KEY_UP) {
            moveSelectedEntry(-1);
            return true;
        }
        if (event.hasAltDown() && event.key() == GLFW.GLFW_KEY_DOWN) {
            moveSelectedEntry(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public Component getNarrationMessage() {
        if (entries.isEmpty()) {
            return Component.translatable("text.endpoemfabric.credits_editor.empty");
        }
        return super.getNarrationMessage();
    }

    @Override
    public void onClose() {
        if (minecraft == null) {
            return;
        }
        if (!dirty) {
            minecraft.gui.setScreen(parent);
            return;
        }

        minecraft.gui.setScreen(new ConfirmScreen(
                discard -> minecraft.gui.setScreen(discard ? parent : this),
                Component.translatable("text.endpoemfabric.credits_editor.discard_title"),
                Component.translatable("text.endpoemfabric.credits_editor.discard_message"),
                Component.translatable("text.endpoemfabric.credits_editor.discard"),
                Component.translatable("gui.cancel")
        ));
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;

        try {
            applyDocument(CustomCredits.read());
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn("Failed to load contributors for the in-game editor.", e);
            applyDocument(CustomCredits.defaultDocument());
            status = Component.translatable("text.endpoemfabric.credits_editor.load_failed");
            statusColor = ERROR_COLOR;
        }
    }

    private void applyDocument(CustomCredits.Document document) {
        section = document.section();
        entries.clear();
        entries.addAll(document.entries());
        selectedIndex = entries.isEmpty() ? -1 : Math.clamp(selectedIndex, 0, entries.size() - 1);
    }

    private void selectEntry(int index) {
        selectedIndex = index >= 0 && index < entries.size() ? index : -1;
        loadingFields = true;
        try {
            if (selectedIndex >= 0) {
                CustomCredits.Entry entry = entries.get(selectedIndex);
                nameBox.setValue(entry.name());
                roleBox.setValue(entry.role());
            } else {
                nameBox.setValue("");
                roleBox.setValue("");
            }
        } finally {
            loadingFields = false;
        }

        boolean hasSelection = selectedIndex >= 0;
        nameBox.setEditable(hasSelection);
        roleBox.setEditable(hasSelection);
        nameBox.active = hasSelection;
        roleBox.active = hasSelection;
        updateActionButtons();
    }

    private void updateSelectedEntry(String name, String role) {
        if (loadingFields || selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }

        CustomCredits.Entry current = entries.get(selectedIndex);
        entries.set(selectedIndex, new CustomCredits.Entry(
                role == null ? current.role() : role,
                name == null ? current.name() : name
        ));
        markDirty();
    }

    private void addEntry() {
        if (entries.size() >= CustomCredits.MAX_ENTRIES) {
            status = Component.translatable(
                    "text.endpoemfabric.credits_editor.too_many",
                    CustomCredits.MAX_ENTRIES
            );
            statusColor = ERROR_COLOR;
            return;
        }

        entries.add(new CustomCredits.Entry("", ""));
        selectedIndex = entries.size() - 1;
        markDirty();
        creditsList.refresh(selectedIndex);
        setInitialFocus(nameBox);
    }

    private void deleteSelectedEntry() {
        if (selectedIndex < 0 || selectedIndex >= entries.size()) {
            return;
        }

        entries.remove(selectedIndex);
        selectedIndex = entries.isEmpty() ? -1 : Math.min(selectedIndex, entries.size() - 1);
        markDirty();
        creditsList.refresh(selectedIndex);
    }

    private void moveSelectedEntry(int direction) {
        int target = selectedIndex + direction;
        if (selectedIndex < 0 || target < 0 || target >= entries.size()) {
            return;
        }

        Collections.swap(entries, selectedIndex, target);
        selectedIndex = target;
        markDirty();
        creditsList.refresh(selectedIndex);
    }

    private void updateActionButtons() {
        if (deleteButton == null) {
            return;
        }
        deleteButton.active = selectedIndex >= 0;
        moveUpButton.active = selectedIndex > 0;
        moveDownButton.active = selectedIndex >= 0 && selectedIndex < entries.size() - 1;
    }

    private boolean save() {
        CustomCredits.Document document = snapshot();
        Optional<CustomCredits.ValidationError> error = CustomCredits.validate(document);
        if (error.isPresent()) {
            showValidationError(error.get());
            return false;
        }

        try {
            CustomCredits.write(document);
            applyDocument(CustomCredits.read());
            loadingFields = true;
            try {
                sectionBox.setValue(section);
            } finally {
                loadingFields = false;
            }
            dirty = false;
            status = Component.translatable("text.endpoemfabric.credits_editor.saved");
            statusColor = SUCCESS_COLOR;
            creditsList.refresh(selectedIndex);
            return true;
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn("Failed to save custom contributors.", e);
            status = Component.translatable("text.endpoemfabric.credits_editor.save_failed");
            statusColor = ERROR_COLOR;
            return false;
        }
    }

    private CustomCredits.Document snapshot() {
        return new CustomCredits.Document(
                CustomCredits.SCHEMA_VERSION,
                section,
                List.copyOf(entries)
        );
    }

    private void showValidationError(CustomCredits.ValidationError error) {
        statusColor = ERROR_COLOR;
        switch (error.problem()) {
            case EMPTY_SECTION -> {
                status = Component.translatable("text.endpoemfabric.credits_editor.section_required");
                setInitialFocus(sectionBox);
            }
            case EMPTY_ENTRY_FIELD -> {
                selectedIndex = error.entryIndex();
                creditsList.refresh(selectedIndex);
                status = Component.translatable(
                        "text.endpoemfabric.credits_editor.name_required",
                        selectedIndex + 1
                );
                setInitialFocus(nameBox);
            }
            case TOO_MANY_ENTRIES -> status = Component.translatable(
                    "text.endpoemfabric.credits_editor.too_many",
                    CustomCredits.MAX_ENTRIES
            );
            default -> status = Component.translatable(
                    "text.endpoemfabric.credits_editor.invalid"
            );
        }
    }

    private void preview() {
        CustomCredits.Document document = snapshot();
        Optional<CustomCredits.ValidationError> error = CustomCredits.validate(document);
        if (error.isPresent()) {
            showValidationError(error.get());
            return;
        }
        if (minecraft == null) {
            return;
        }

        CustomCredits.preparePreview(document);
        Minecraft client = minecraft;
        client.gui.setScreen(new WinScreen(false, () -> client.gui.setScreen(this)));
    }

    private void saveAndClose() {
        if (dirty && !save()) {
            return;
        }
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private void markDirty() {
        dirty = true;
        status = Component.empty();
        statusColor = TEXT_COLOR;
    }

    private final class CreditsList extends ObjectSelectionList<CreditRow> {
        private boolean refreshing;

        private CreditsList(Minecraft minecraft, int width, int height, int x, int y) {
            super(minecraft, width, height, y, 24);
            updateSizeAndPosition(width, height, x, y);
            centerListVertically = false;
        }

        private void refresh(int preferredIndex) {
            refreshing = true;
            try {
                List<CreditRow> rows = new ArrayList<>(entries.size());
                for (int index = 0; index < entries.size(); index++) {
                    rows.add(new CreditRow(index));
                }
                replaceEntries(rows);

                CreditRow selected = preferredIndex >= 0 && preferredIndex < rows.size()
                        ? rows.get(preferredIndex)
                        : null;
                super.setSelected(selected);
            } finally {
                refreshing = false;
            }
            selectEntry(preferredIndex);
        }

        @Override
        public void setSelected(CreditRow entry) {
            super.setSelected(entry);
            if (!refreshing) {
                selectEntry(entry == null ? -1 : entry.index);
            }
        }

        @Override
        public int getRowWidth() {
            return Math.max(80, getWidth() - 12);
        }
    }

    private final class CreditRow extends ObjectSelectionList.Entry<CreditRow> {
        private final int index;

        private CreditRow(int index) {
            this.index = index;
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                boolean hovered,
                float delta
        ) {
            if (index < 0 || index >= entries.size()) {
                return;
            }

            CustomCredits.Entry entry = entries.get(index);
            int x = getContentX() + 4;
            int y = getContentYMiddle() - font.lineHeight / 2;
            int available = Math.max(20, getContentWidth() - 8);
            int roleWidth = available / 3;
            String prefix = (index + 1) + ". ";
            int nameWidth = Math.max(10, available - roleWidth - font.width(prefix) - 8);
            String name = entry.name().isEmpty()
                    ? Component.translatable("text.endpoemfabric.credits_editor.unnamed").getString()
                    : entry.name();

            graphics.text(font, prefix, x, y, SECONDARY_TEXT_COLOR, true);
            graphics.text(
                    font,
                    font.plainSubstrByWidth(name, nameWidth),
                    x + font.width(prefix),
                    y,
                    TEXT_COLOR,
                    true
            );
            if (!entry.role().isEmpty()) {
                graphics.text(
                        font,
                        font.plainSubstrByWidth(entry.role(), roleWidth),
                        getContentRight() - roleWidth - 4,
                        y,
                        SECONDARY_TEXT_COLOR,
                        true
                );
            }
        }

        @Override
        public Component getNarration() {
            if (index < 0 || index >= entries.size()) {
                return Component.empty();
            }
            CustomCredits.Entry entry = entries.get(index);
            if (entry.role().isBlank()) {
                return Component.translatable(
                        "text.endpoemfabric.credits_editor.row_narration_no_role",
                        index + 1,
                        entry.name()
                );
            }
            return Component.translatable(
                    "text.endpoemfabric.credits_editor.row_narration",
                    index + 1,
                    entry.name(),
                    entry.role()
            );
        }
    }
}
