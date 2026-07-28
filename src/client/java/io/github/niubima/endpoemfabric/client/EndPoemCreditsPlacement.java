package io.github.niubima.endpoemfabric.client;

import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Inserts an invisible, namespaced line into the active End Poem reader.
 * The WinScreen mixin replaces that line with the configured credits block,
 * allowing Minecraft's own parser to retain its native formatting, narration,
 * and scroll-length bookkeeping.
 */
public final class EndPoemCreditsPlacement {
    public static final int PROGRESS_SCALE = 10_000;

    private static final int MAX_POEM_CHARACTERS = 8_388_608;
    private static final int MAX_PHYSICAL_LINES = 100_000;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final String INSERTION_MARKER =
            "\uE000endpoemfabric:credits-placement:6e7c09d4\uE001";
    private static final Identifier END_POEM_LOCATION =
            Identifier.withDefaultNamespace("texts/end.txt");

    private EndPoemCreditsPlacement() {
    }

    /**
     * Adds a placement marker to a resettable view of {@code original}. If the
     * poem is too large or cannot be read safely, the reader is reset and
     * returned unchanged so normal poem playback can continue.
     */
    public static Reader injectMarkerOrOriginal(
            Reader original,
            String placement,
            int insertionProgress
    ) {
        if (!isInlinePlacement(placement)) {
            return original;
        }

        Reader resettable = original.markSupported() ? original : new BufferedReader(original);
        StringBuilder buffered = new StringBuilder();
        try {
            resettable.mark(MAX_POEM_CHARACTERS + READ_BUFFER_SIZE);
            readBounded(resettable, buffered);
            String poem = buffered.toString();
            return new StringReader(injectMarker(poem, placement, insertionProgress));
        } catch (IOException | RuntimeException e) {
            try {
                resettable.reset();
                Endpoemfabric.LOGGER.warn(
                        "Failed to prepare End Poem credits placement. Using normal credits order.",
                        e
                );
                return resettable;
            } catch (IOException resetFailure) {
                e.addSuppressed(resetFailure);
            }
            Endpoemfabric.LOGGER.warn(
                    "Failed to reset the End Poem reader after placement preparation. "
                            + "Replaying the buffered prefix before the unread remainder.",
                    e
            );
            return new ReplayReader(buffered.toString(), resettable);
        }
    }

    public static boolean isInsertionMarker(String line) {
        return INSERTION_MARKER.equals(line);
    }

    public static boolean isInlinePlacement(String placement) {
        return EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM.equals(placement)
                || EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM.equals(placement);
    }

    /**
     * Reads the poem that would currently be played and returns its non-empty
     * physical lines as stable, user-selectable paragraphs.
     */
    public static List<String> readActiveParagraphs(Minecraft minecraft) throws IOException {
        String poem;
        try (Reader resourceReader = minecraft.getResourceManager().openAsReader(END_POEM_LOCATION)) {
            Reader selectedReader = CustomEndPoem.readerOrOriginal(resourceReader);
            if (selectedReader == resourceReader) {
                poem = readBounded(selectedReader);
            } else {
                try (selectedReader) {
                    poem = readBounded(selectedReader);
                }
            }
        }

        String playerName = minecraft.getUser().getName();
        List<String> paragraphs = new ArrayList<>();
        for (String line : splitLines(poem)) {
            String normalized = line.strip();
            if (!normalized.isEmpty()) {
                paragraphs.add(normalized.replace("PLAYERNAME", playerName));
            }
        }
        return List.copyOf(paragraphs);
    }

    /**
     * Maps normalized progress to a valid boundary after paragraph 1..N-1.
     * Returns zero when the poem has no valid internal boundary.
     */
    public static int insertionBoundary(int paragraphCount, int progress) {
        if (paragraphCount < 2) {
            return 0;
        }
        int safeProgress = Math.clamp(progress, 1, PROGRESS_SCALE - 1);
        int boundary = (int) Math.round(
                paragraphCount * (double) safeProgress / PROGRESS_SCALE
        );
        return Math.clamp(boundary, 1, paragraphCount - 1);
    }

    public static int progressForBoundary(int boundary, int paragraphCount) {
        if (paragraphCount < 2) {
            return PROGRESS_SCALE / 2;
        }
        int safeBoundary = Math.clamp(boundary, 1, paragraphCount - 1);
        return (int) Math.round(
                safeBoundary * (double) PROGRESS_SCALE / paragraphCount
        );
    }

    static String injectMarker(String poem, String placement, int insertionProgress) {
        if (EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM.equals(placement)) {
            return INSERTION_MARKER + "\n" + poem;
        }
        if (!EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM.equals(placement)) {
            return poem;
        }

        String[] lines = splitLines(poem);
        int paragraphCount = 0;
        for (String line : lines) {
            if (!line.isBlank()) {
                paragraphCount++;
            }
        }

        int targetBoundary = insertionBoundary(paragraphCount, insertionProgress);
        if (targetBoundary == 0) {
            return poem;
        }

        List<String> result = new ArrayList<>(lines.length + 1);
        int paragraphsSeen = 0;
        boolean inserted = false;
        for (String line : lines) {
            result.add(line);
            if (!inserted && !line.isBlank() && ++paragraphsSeen == targetBoundary) {
                result.add(INSERTION_MARKER);
                inserted = true;
            }
        }
        return String.join("\n", result);
    }

    private static String[] splitLines(String text) {
        // Match BufferedReader.readLine(): CRLF, LF, and CR are line endings;
        // Unicode NEL/LS/PS characters remain part of their physical line.
        List<String> lines = new ArrayList<>();
        int lineStart = 0;
        for (int index = 0; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character != '\r' && character != '\n') {
                continue;
            }

            addPhysicalLine(lines, text.substring(lineStart, index));
            if (character == '\r'
                    && index + 1 < text.length()
                    && text.charAt(index + 1) == '\n') {
                index++;
            }
            lineStart = index + 1;
        }
        addPhysicalLine(lines, text.substring(lineStart));
        return lines.toArray(String[]::new);
    }

    private static String readBounded(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        readBounded(reader, result);
        return result.toString();
    }

    private static void readBounded(Reader reader, StringBuilder result) throws IOException {
        char[] buffer = new char[READ_BUFFER_SIZE];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, read);
            if (result.length() > MAX_POEM_CHARACTERS) {
                throw new IOException(
                        "End Poem exceeds " + MAX_POEM_CHARACTERS + " characters."
                );
            }
        }
    }

    private static void addPhysicalLine(List<String> lines, String line) {
        if (lines.size() >= MAX_PHYSICAL_LINES) {
            throw new IllegalArgumentException(
                    "End Poem exceeds " + MAX_PHYSICAL_LINES + " physical lines."
            );
        }
        lines.add(line);
    }

    /**
     * Best-effort fallback for the unlikely case where a marked reader cannot
     * reset after partial consumption.
     */
    private static final class ReplayReader extends Reader {
        private final Reader prefix;
        private final Reader remainder;
        private boolean prefixConsumed;

        private ReplayReader(String prefix, Reader remainder) {
            this.prefix = new StringReader(prefix);
            this.remainder = remainder;
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            if (!prefixConsumed) {
                int read = prefix.read(buffer, offset, length);
                if (read >= 0) {
                    return read;
                }
                prefixConsumed = true;
            }
            return remainder.read(buffer, offset, length);
        }

        @Override
        public void close() throws IOException {
            IOException failure = null;
            try {
                prefix.close();
            } catch (IOException e) {
                failure = e;
            }
            try {
                remainder.close();
            } catch (IOException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }
    }
}
