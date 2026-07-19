package io.github.niubima.endpoemfabric.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractTextAreaWidget;
import net.minecraft.client.gui.font.TextFieldHelper;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A multi-line editor which deliberately treats every character as text.
 *
 * <p>The vanilla multi-line editor draws Strings directly, which gives the
 * legacy formatting marker ({@code \u00a7}) its in-game meaning. This widget renders
 * a {@link FormattedCharSequence} with an empty style instead, so the marker is
 * visible and remains a normal editable character.</p>
 */
public final class PlainTextEditBox extends AbstractTextAreaWidget {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int CURSOR_COLOR = 0xFFCCCCCC;

    private final Font font;
    private final TextFieldHelper textField;
    private final List<Line> lines = new ArrayList<>();
    private Consumer<String> valueListener = value -> {
    };
    private String value = "";
    private int characterLimit = Integer.MAX_VALUE;
    private int lineLimit = Integer.MAX_VALUE;
    private long focusedTime = Util.getMillis();

    public PlainTextEditBox(Font font, int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message, AbstractScrollArea.defaultSettings(font.lineHeight / 2), true, true);
        this.font = font;
        this.textField = new TextFieldHelper(
                () -> value,
                this::applyTextFieldValue,
                TextFieldHelper.createClipboardGetter(Minecraft.getInstance()),
                TextFieldHelper.createClipboardSetter(Minecraft.getInstance()),
                this::isAllowedValue
        );
        rebuildLines();
    }

    public void setCharacterLimit(int characterLimit) {
        this.characterLimit = Math.max(0, characterLimit);
    }

    public void setLineLimit(int lineLimit) {
        this.lineLimit = Math.max(1, lineLimit);
    }

    public void setValueListener(Consumer<String> valueListener) {
        this.valueListener = valueListener == null ? value -> {
        } : valueListener;
    }

    public void setValue(String value) {
        this.value = value == null ? "" : value;
        textField.setCursorToEnd();
        textField.setSelectionPos(textField.getCursorPos());
        rebuildLines();
    }

    public String getValue() {
        return value;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (!visible || !isFocused() || !event.isAllowedChatCharacter()) {
            return false;
        }

        boolean handled = textField.charTyped(event);
        if (handled) {
            afterTextInput();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!visible || !isFocused()) {
            return false;
        }

        boolean select = event.hasShiftDown();
        switch (event.key()) {
            case GLFW.GLFW_KEY_UP -> moveCursorVertically(-1, select);
            case GLFW.GLFW_KEY_DOWN -> moveCursorVertically(1, select);
            case GLFW.GLFW_KEY_PAGE_UP -> moveCursorVertically(-Math.max(1, height / font.lineHeight - 1), select);
            case GLFW.GLFW_KEY_PAGE_DOWN -> moveCursorVertically(Math.max(1, height / font.lineHeight - 1), select);
            case GLFW.GLFW_KEY_HOME -> moveToLineEdge(false, event.hasControlDown(), select);
            case GLFW.GLFW_KEY_END -> moveToLineEdge(true, event.hasControlDown(), select);
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                textField.insertText("\n");
                afterTextInput();
            }
            case GLFW.GLFW_KEY_TAB -> {
                textField.insertText("\t");
                afterTextInput();
            }
            default -> {
                boolean handled = textField.keyPressed(event);
                if (handled) {
                    afterTextInput();
                }
                return handled;
            }
        }
        return true;
    }

    @Override
    public void onClick(MouseButtonEvent event, boolean doubleClick) {
        int cursor = cursorForPoint(event.x(), event.y());
        if (doubleClick) {
            selectWordAt(cursor);
        } else {
            textField.setCursorPos(cursor, event.hasShiftDown());
        }
        scrollToCursor();
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double mouseX, double mouseY) {
        textField.setCursorPos(cursorForPoint(mouseX, mouseY), true);
        scrollToCursor();
    }

    @Override
    public void setFocused(boolean focused) {
        super.setFocused(focused);
        if (focused) {
            focusedTime = Util.getMillis();
        }
        Minecraft.getInstance().onTextInputFocusChange(this, focused);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float delta) {
        int selectionStart = Math.min(textField.getCursorPos(), textField.getSelectionPos());
        int selectionEnd = Math.max(textField.getCursorPos(), textField.getSelectionPos());
        int cursor = textField.getCursorPos();
        int currentLine = lineForPosition(cursor);
        int x = getInnerLeft();
        int y = getInnerTop();

        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            String lineText = value.substring(line.start(), line.end());
            if (selectionStart < line.end() && selectionEnd > line.start()) {
                int highlightedStart = Math.max(selectionStart, line.start());
                int highlightedEnd = Math.min(selectionEnd, line.end());
                int highlightX = x + rawWidth(line.start(), highlightedStart);
                int highlightEndX = x + rawWidth(line.start(), highlightedEnd);
                graphics.textHighlight(highlightX, y, highlightEndX, y + font.lineHeight, true);
            }

            graphics.text(font, rawSequence(lineText), x, y, TEXT_COLOR, true);
            if (index == currentLine && isCursorVisible()) {
                int cursorX = x + rawWidth(line.start(), Math.min(cursor, line.end()));
                graphics.verticalLine(cursorX, y, y + font.lineHeight - 1, CURSOR_COLOR);
            }
            y += font.lineHeight;
        }
    }

    @Override
    protected int getInnerHeight() {
        return Math.max(font.lineHeight, lines.size() * font.lineHeight);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), value));
    }

    private void applyTextFieldValue(String newValue) {
        value = newValue;
        rebuildLines();
        valueListener.accept(value);
    }

    private boolean isAllowedValue(String candidate) {
        return candidate.length() <= characterLimit && countLines(candidate) <= lineLimit;
    }

    private void afterTextInput() {
        rebuildLines();
        scrollToCursor();
    }

    private void moveCursorVertically(int lineOffset, boolean select) {
        Line current = lines.get(lineForPosition(textField.getCursorPos()));
        int targetLineIndex = Math.clamp(lineForPosition(textField.getCursorPos()) + lineOffset, 0, lines.size() - 1);
        Line target = lines.get(targetLineIndex);
        int x = rawWidth(current.start(), Math.min(textField.getCursorPos(), current.end()));
        textField.setCursorPos(indexAtWidth(target, x), select);
        scrollToCursor();
    }

    private void moveToLineEdge(boolean end, boolean documentEdge, boolean select) {
        int target;
        if (documentEdge) {
            target = end ? value.length() : 0;
        } else {
            Line line = lines.get(lineForPosition(textField.getCursorPos()));
            target = end ? line.end() : line.start();
        }
        textField.setCursorPos(target, select);
        scrollToCursor();
    }

    private void selectWordAt(int position) {
        if (value.isEmpty()) {
            textField.setCursorPos(0, false);
            return;
        }

        int start = Math.clamp(position, 0, value.length());
        if (start == value.length()) {
            start--;
        }
        if (Character.isWhitespace(value.charAt(start))) {
            textField.setCursorPos(start, false);
            textField.setSelectionPos(Math.min(value.length(), start + 1));
            return;
        }

        int wordStart = start;
        int wordEnd = start + 1;
        while (wordStart > 0 && !Character.isWhitespace(value.charAt(wordStart - 1))) {
            wordStart--;
        }
        while (wordEnd < value.length() && !Character.isWhitespace(value.charAt(wordEnd))) {
            wordEnd++;
        }
        textField.setCursorPos(wordStart, false);
        textField.setSelectionPos(wordEnd);
    }

    private int cursorForPoint(double mouseX, double mouseY) {
        int lineIndex = Math.clamp(
                (int) ((mouseY - getInnerTop() + scrollAmount()) / font.lineHeight),
                0,
                lines.size() - 1
        );
        return indexAtWidth(lines.get(lineIndex), Math.max(0, (int) mouseX - getInnerLeft()));
    }

    private int indexAtWidth(Line line, int targetWidth) {
        int index = line.start();
        int width = 0;
        while (index < line.end()) {
            int next = value.offsetByCodePoints(index, 1);
            int nextWidth = rawWidth(line.start(), next);
            if (targetWidth < width + (nextWidth - width) / 2) {
                break;
            }
            width = nextWidth;
            index = next;
        }
        return index;
    }

    private int lineForPosition(int position) {
        for (int index = 0; index < lines.size(); index++) {
            Line line = lines.get(index);
            if (position >= line.start() && position <= line.end()) {
                return index;
            }
        }
        return lines.size() - 1;
    }

    private void rebuildLines() {
        lines.clear();
        int paragraphStart = 0;
        for (int index = 0; index <= value.length(); index++) {
            if (index == value.length() || value.charAt(index) == '\n') {
                addWrappedLines(paragraphStart, index);
                paragraphStart = index + 1;
            }
        }
        if (lines.isEmpty()) {
            lines.add(new Line(0, 0));
        }
        refreshScrollAmount();
    }

    private void addWrappedLines(int start, int end) {
        if (start == end) {
            lines.add(new Line(start, end));
            return;
        }

        int width = Math.max(1, getWidth() - totalInnerPadding());
        int lineStart = start;
        while (lineStart < end) {
            int lineEnd = indexAtWidth(lineStart, end, width);
            lines.add(new Line(lineStart, lineEnd));
            lineStart = lineEnd;
        }
    }

    private int indexAtWidth(int start, int end, int maxWidth) {
        int index = start;
        while (index < end) {
            int next = value.offsetByCodePoints(index, 1);
            if (rawWidth(start, next) > maxWidth) {
                return index == start ? next : index;
            }
            index = next;
        }
        return end;
    }

    private int rawWidth(int start, int end) {
        return font.width(rawSequence(value.substring(start, end)));
    }

    private static FormattedCharSequence rawSequence(String text) {
        return FormattedCharSequence.forward(text, Style.EMPTY);
    }

    private void scrollToCursor() {
        int cursorTop = lineForPosition(textField.getCursorPos()) * font.lineHeight;
        int visibleHeight = Math.max(font.lineHeight, getHeight() - totalInnerPadding());
        if (cursorTop < scrollAmount()) {
            setScrollAmount(cursorTop);
        } else if (cursorTop + font.lineHeight > scrollAmount() + visibleHeight) {
            setScrollAmount(cursorTop + font.lineHeight - visibleHeight);
        }
    }

    private boolean isCursorVisible() {
        return isFocused() && (Util.getMillis() - focusedTime) / 500L % 2L == 0L;
    }

    private static int countLines(String text) {
        int count = 1;
        for (int index = 0; index < text.length(); index++) {
            if (text.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private record Line(int start, int end) {
    }
}
