package io.github.niubima.endpoemfabric.client.config;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.client.EndPoemCreditsPlacement;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lets the player choose the End Poem paragraph after which the configured
 * credits block should be inserted.
 */
public final class EndCreditsPlacementScreen extends Screen {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int SECONDARY_TEXT_COLOR = 0xFFAAAAAA;
    private static final int ERROR_COLOR = 0xFFFF5555;
    private static final int CONTROL_HEIGHT = 20;
    private static final int ROW_HEIGHT = 32;
    private static final int GAP = 4;
    private static final int MAX_INSTRUCTION_LINES = 2;

    private final Screen parent;
    private final List<String> paragraphs = new ArrayList<>();

    private boolean loaded;
    private boolean loadFailed;
    private int selectedBoundary = -1;
    private int contentLeft;
    private int contentWidth;
    private int listTop;
    private int listBottom;
    private List<FormattedCharSequence> instructionLines = List.of();

    private ParagraphList paragraphList;
    private Button useButton;

    public EndCreditsPlacementScreen(Screen parent) {
        super(Component.translatable("text.endpoemfabric.credits_placement.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        loadIfNeeded();

        contentWidth = Math.max(120, Math.min(700, width - 24));
        contentLeft = (width - contentWidth) / 2;

        List<FormattedCharSequence> wrappedInstruction = font.split(
                Component.translatable("text.endpoemfabric.credits_placement.instruction"),
                contentWidth
        );
        instructionLines = List.copyOf(wrappedInstruction.subList(
                0,
                Math.min(MAX_INSTRUCTION_LINES, wrappedInstruction.size())
        ));

        int instructionY = 29;
        listTop = instructionY + instructionLines.size() * font.lineHeight + 6;
        int bottomY = height - 28;
        listBottom = Math.max(listTop + ROW_HEIGHT, bottomY - 8);

        paragraphList = new ParagraphList(
                minecraft,
                contentWidth,
                listBottom - listTop,
                contentLeft,
                listTop
        );
        addRenderableWidget(paragraphList);

        int buttonWidth = (contentWidth - GAP) / 2;
        Button cancelButton = addRenderableWidget(Button.builder(
                        Component.translatable("gui.cancel"),
                        button -> onClose()
                )
                .bounds(contentLeft, bottomY, buttonWidth, CONTROL_HEIGHT)
                .build());
        useButton = addRenderableWidget(Button.builder(
                        Component.translatable("text.endpoemfabric.credits_placement.use"),
                        button -> useSelection()
                )
                .bounds(
                        contentLeft + buttonWidth + GAP,
                        bottomY,
                        contentWidth - buttonWidth - GAP,
                        CONTROL_HEIGHT
                )
                .build());

        paragraphList.refresh(selectedBoundary);
        updateUseButton();
        if (hasUsableSelection()) {
            setInitialFocus(paragraphList);
        } else {
            setInitialFocus(cancelButton);
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics,
            int mouseX,
            int mouseY,
            float delta
    ) {
        graphics.fill(0, 0, width, height, 0xC0101010);
        graphics.centeredText(font, title, width / 2, 12, TEXT_COLOR);

        int instructionY = 29;
        for (int index = 0; index < instructionLines.size(); index++) {
            graphics.centeredText(
                    font,
                    instructionLines.get(index),
                    width / 2,
                    instructionY + index * font.lineHeight,
                    SECONDARY_TEXT_COLOR
            );
        }

        super.extractRenderState(graphics, mouseX, mouseY, delta);

        Component unavailable = unavailableMessage();
        if (unavailable != null) {
            int messageY = listTop + Math.max(0, (listBottom - listTop - font.lineHeight) / 2);
            graphics.centeredText(
                    font,
                    unavailable,
                    width / 2,
                    messageY,
                    loadFailed ? ERROR_COLOR : SECONDARY_TEXT_COLOR
            );
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if ((event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER)
                && getFocused() == paragraphList
                && hasUsableSelection()) {
            useSelection();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public Component getNarrationMessage() {
        Component unavailable = unavailableMessage();
        if (unavailable != null) {
            return CommonComponents.joinForNarration(title, unavailable);
        }
        return CommonComponents.joinForNarration(
                title,
                Component.translatable("text.endpoemfabric.credits_placement.instruction")
        );
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }

    private void loadIfNeeded() {
        if (loaded) {
            return;
        }
        loaded = true;

        if (minecraft == null) {
            loadFailed = true;
            return;
        }

        try {
            paragraphs.addAll(EndPoemCreditsPlacement.readActiveParagraphs(minecraft));
            if (paragraphs.size() >= 2) {
                selectedBoundary = EndPoemCreditsPlacement.insertionBoundary(
                        paragraphs.size(),
                        EndpoemConfigManager.get().creditsInsertionProgress
                );
            }
        } catch (IOException | RuntimeException e) {
            paragraphs.clear();
            selectedBoundary = -1;
            loadFailed = true;
            Endpoemfabric.LOGGER.warn(
                    "Failed to load End Poem paragraphs for the credits placement screen.",
                    e
            );
        }
    }

    private Component unavailableMessage() {
        if (loadFailed) {
            return Component.translatable(
                    "text.endpoemfabric.credits_placement.load_failed"
            );
        }
        if (paragraphs.size() < 2) {
            return Component.translatable(
                    "text.endpoemfabric.credits_placement.unavailable"
            );
        }
        return null;
    }

    private boolean hasUsableSelection() {
        return !loadFailed
                && selectedBoundary >= 1
                && selectedBoundary < paragraphs.size();
    }

    private void updateUseButton() {
        if (useButton != null) {
            useButton.active = hasUsableSelection();
        }
    }

    private void useSelection() {
        if (minecraft == null || !hasUsableSelection()) {
            return;
        }

        int paragraphCount = paragraphs.size();
        int progress = Math.clamp(
                EndPoemCreditsPlacement.progressForBoundary(
                        selectedBoundary,
                        paragraphCount
                ),
                1,
                EndPoemCreditsPlacement.PROGRESS_SCALE - 1
        );
        EndpoemConfigManager.update(config -> {
            config.customCreditsPlacement =
                    EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM;
            config.creditsInsertionProgress = progress;
        });
        minecraft.gui.setScreen(parent);
    }

    private final class ParagraphList extends ObjectSelectionList<ParagraphRow> {
        private ParagraphList(Minecraft minecraft, int width, int height, int x, int y) {
            super(minecraft, width, height, y, ROW_HEIGHT);
            updateSizeAndPosition(width, height, x, y);
            centerListVertically = false;
        }

        private void refresh(int preferredBoundary) {
            List<ParagraphRow> rows = new ArrayList<>(Math.max(0, paragraphs.size() - 1));
            for (int index = 0; index < paragraphs.size() - 1; index++) {
                rows.add(new ParagraphRow(index));
            }
            replaceEntries(rows);

            int selectedIndex = preferredBoundary - 1;
            ParagraphRow selected = selectedIndex >= 0 && selectedIndex < rows.size()
                    ? rows.get(selectedIndex)
                    : null;
            super.setSelected(selected);
            selectedBoundary = selected == null ? -1 : selected.boundary();
            if (selected != null) {
                centerScrollOn(selected);
            }
        }

        @Override
        public void setSelected(ParagraphRow entry) {
            super.setSelected(entry);
            selectedBoundary = entry == null ? -1 : entry.boundary();
            updateUseButton();
        }

        @Override
        public int getRowWidth() {
            return Math.max(80, getWidth() - 12);
        }
    }

    private final class ParagraphRow extends ObjectSelectionList.Entry<ParagraphRow> {
        private final int index;

        private ParagraphRow(int index) {
            this.index = index;
        }

        private int boundary() {
            return index + 1;
        }

        @Override
        public void extractContent(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                boolean hovered,
                float delta
        ) {
            if (index < 0 || index >= paragraphs.size() - 1) {
                return;
            }

            int x = getContentX() + 4;
            int y = getContentY() + 3;
            int availableWidth = Math.max(20, getContentWidth() - 8);
            graphics.text(
                    font,
                    Component.translatable(
                            "text.endpoemfabric.credits_placement.insert_after",
                            boundary()
                    ),
                    x,
                    y,
                    TEXT_COLOR,
                    true
            );
            graphics.text(
                    font,
                    font.plainSubstrByWidth(paragraphs.get(index), availableWidth),
                    x,
                    y + font.lineHeight + 2,
                    SECONDARY_TEXT_COLOR,
                    true
            );
        }

        @Override
        public Component getNarration() {
            if (index < 0 || index >= paragraphs.size() - 1) {
                return Component.empty();
            }
            return Component.translatable(
                    "text.endpoemfabric.credits_placement.row_narration",
                    boundary(),
                    ChatFormatting.stripFormatting(paragraphs.get(index))
            );
        }
    }
}
