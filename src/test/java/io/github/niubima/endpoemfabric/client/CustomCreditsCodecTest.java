package io.github.niubima.endpoemfabric.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigMigrationTest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Dependency-free regression checks executed by Gradle's {@code check} task.
 */
public final class CustomCreditsCodecTest {
    private CustomCreditsCodecTest() {
    }

    public static void main(String[] args) {
        EndpoemConfigMigrationTest.run();
        preservesContributorOrderAndUnicode();
        validatesRequiredNames();
        supportsEmptyAndOneShotPreviews();
        preservesSimplifiedCreditsComposition();
        skipsCustomCreditsAfterSuccessfulInlineInsertion();
        placesCreditsAtStablePoemBoundaries();
        rejectsMalformedInlineCreditsBeforeRendering();
    }

    private static void preservesContributorOrderAndUnicode() {
        CustomCredits.Document document = new CustomCredits.Document(
                CustomCredits.SCHEMA_VERSION,
                " 特别鸣谢 ",
                List.of(
                        new CustomCredits.Entry(" 开发 ", " Alice "),
                        new CustomCredits.Entry("开发", "Álvaro"),
                        new CustomCredits.Entry("美术", "Bob \"B\""),
                        new CustomCredits.Entry("开发", "Carol"),
                        new CustomCredits.Entry("", "丁")
                )
        );

        check(CustomCredits.validate(document).isEmpty(), "Valid Unicode document was rejected.");

        CustomCredits.preparePreview(document);
        JsonArray root = parse(CustomCredits.previewReaderOrOriginal(new StringReader("[]")));
        check(root.size() == 1, "Expected one custom section.");

        JsonObject section = root.get(0).getAsJsonObject();
        check("特别鸣谢".equals(section.get("section").getAsString()), "Section text was not normalized.");

        JsonArray titles = section.getAsJsonArray("disciplines")
                .get(0).getAsJsonObject()
                .getAsJsonArray("titles");
        check(titles.size() == 4, "Non-adjacent equal roles were incorrectly regrouped.");
        check("开发".equals(titles.get(0).getAsJsonObject().get("title").getAsString()),
                "The first role was not normalized.");
        checkNames(titles.get(0).getAsJsonObject(), "Alice", "Álvaro");
        checkNames(titles.get(1).getAsJsonObject(), "Bob \"B\"");
        checkNames(titles.get(2).getAsJsonObject(), "Carol");
        checkNames(titles.get(3).getAsJsonObject(), "丁");
    }

    private static void validatesRequiredNames() {
        CustomCredits.Document invalid = new CustomCredits.Document(
                CustomCredits.SCHEMA_VERSION,
                "Credits",
                List.of(new CustomCredits.Entry("Role", " "))
        );
        CustomCredits.ValidationError error = CustomCredits.validate(invalid).orElseThrow();
        check(error.problem() == CustomCredits.ValidationProblem.EMPTY_ENTRY_FIELD,
                "Blank-name validation reported the wrong problem.");
        check(error.entryIndex() == 0, "Blank-name validation reported the wrong row.");
    }

    private static void supportsEmptyAndOneShotPreviews() {
        CustomCredits.preparePreview(new CustomCredits.Document(
                CustomCredits.SCHEMA_VERSION,
                "Credits",
                List.of()
        ));
        JsonArray empty = parse(CustomCredits.previewReaderOrOriginal(
                new StringReader("[{\"section\":\"vanilla\"}]")
        ));
        check(empty.isEmpty(), "An empty custom-only preview should emit an empty array.");

        Reader untouched = new StringReader("[1]");
        check(CustomCredits.previewReaderOrOriginal(untouched) == untouched,
                "The preview override was not consumed exactly once.");
    }

    private static void preservesSimplifiedCreditsComposition() {
        JsonArray vanilla = JsonParser.parseString("[\"V\"]").getAsJsonArray();
        JsonArray custom = JsonParser.parseString("[\"C\"]").getAsJsonArray();

        check("[\"V\"]".equals(CustomCredits.combineCredits(
                true,
                false,
                false,
                vanilla,
                custom
        ).toString()), "Vanilla-only credits composition changed.");
        check("[\"C\",\"V\"]".equals(CustomCredits.combineCredits(
                true,
                true,
                false,
                vanilla,
                custom
        ).toString()), "Post-poem custom credits should precede vanilla credits.");
        check("[\"V\",\"C\"]".equals(CustomCredits.combineCredits(
                true,
                true,
                true,
                vanilla,
                custom
        ).toString()), "After-vanilla custom credits composition changed.");
        check("[\"C\"]".equals(CustomCredits.combineCredits(
                false,
                true,
                false,
                vanilla,
                custom
        ).toString()), "Custom-only credits composition changed.");
        check(CustomCredits.combineCredits(
                false,
                false,
                false,
                vanilla,
                custom
        ).isEmpty(), "Hiding both credits sources should produce an empty block.");
    }

    private static void skipsCustomCreditsAfterSuccessfulInlineInsertion() {
        Reader vanillaOnly = new StringReader("vanilla");
        check(CustomCredits.readerOrOriginal(
                vanillaOnly,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_OFF,
                false
        ) == vanillaOnly, "Disabled custom credits should not touch the vanilla Reader.");

        Reader vanillaAfterInline = new StringReader("vanilla");
        check(CustomCredits.readerOrOriginal(
                vanillaAfterInline,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                true
        ) == vanillaAfterInline, "Inline custom credits incorrectly suppressed vanilla credits.");

        JsonArray hiddenAfterInline = parse(CustomCredits.readerOrOriginal(
                new StringReader("vanilla"),
                false,
                EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM,
                true
        ));
        check(hiddenAfterInline.isEmpty(),
                "Inline custom-only playback left a second post-poem credits block.");
    }

    private static void placesCreditsAtStablePoemBoundaries() {
        String poem = "First paragraph\n\nSecond paragraph\nThird paragraph\nFourth paragraph\n";

        String before = EndPoemCreditsPlacement.injectMarker(
                poem,
                EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        );
        check(EndPoemCreditsPlacement.isInsertionMarker(before.split("\\R", -1)[0]),
                "Before-poem placement did not put the marker first.");
        List<String> beforeLines = new ArrayList<>(physicalLines(before));
        beforeLines.removeIf(EndPoemCreditsPlacement::isInsertionMarker);
        check(beforeLines.equals(physicalLines(poem)),
                "Before-poem placement changed the poem's physical lines.");

        String inside = EndPoemCreditsPlacement.injectMarker(
                poem,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        );
        String[] lines = inside.split("\\R", -1);
        int markerIndex = -1;
        for (int index = 0; index < lines.length; index++) {
            if (EndPoemCreditsPlacement.isInsertionMarker(lines[index])) {
                check(markerIndex < 0, "Inside-poem placement emitted more than one marker.");
                markerIndex = index;
            }
        }
        check(markerIndex > 0 && "Second paragraph".equals(lines[markerIndex - 1]),
                "The midpoint marker was not placed after the second of four paragraphs.");
        List<String> renderedLines = new ArrayList<>(physicalLines(inside));
        renderedLines.removeIf(EndPoemCreditsPlacement::isInsertionMarker);
        check(renderedLines.equals(physicalLines(poem)),
                "Removing the internal marker changed the poem's physical lines.");

        String tooShort = EndPoemCreditsPlacement.injectMarker(
                "Only one paragraph\n",
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        );
        check("Only one paragraph\n".equals(tooShort),
                "A poem without an internal boundary should remain unchanged.");
        check("\n\r\n".equals(EndPoemCreditsPlacement.injectMarker(
                "\n\r\n",
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        )), "An all-blank poem should fall back to after-poem placement.");

        Reader afterReader = new NonMarkingReader("After placement");
        check(EndPoemCreditsPlacement.injectMarkerOrOriginal(
                afterReader,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        ) == afterReader, "After-poem placement should preserve the Reader identity.");

        Reader offReader = new NonMarkingReader("Disabled placement");
        check(EndPoemCreditsPlacement.injectMarkerOrOriginal(
                offReader,
                EndpoemConfig.CREDITS_PLACEMENT_OFF,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        ) == offReader, "Disabled custom credits should preserve the Reader identity.");

        Reader afterVanillaReader = new NonMarkingReader("After vanilla placement");
        check(EndPoemCreditsPlacement.injectMarkerOrOriginal(
                afterVanillaReader,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        ) == afterVanillaReader, "After-vanilla placement should not inject a poem marker.");

        Reader nonMarkingReader = new NonMarkingReader(poem);
        String nonMarkingResult = readAll(EndPoemCreditsPlacement.injectMarkerOrOriginal(
                nonMarkingReader,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        ));
        check(nonMarkingResult.contains("Second paragraph"),
                "A non-marking poem Reader could not be wrapped safely.");

        String mixedEndings = "A\r\n\rB\nC\u2028D\r\n";
        String mixedInside = EndPoemCreditsPlacement.injectMarker(
                mixedEndings,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                EndPoemCreditsPlacement.PROGRESS_SCALE / 2
        );
        List<String> mixedLines = new ArrayList<>(physicalLines(mixedInside));
        mixedLines.removeIf(EndPoemCreditsPlacement::isInsertionMarker);
        check(mixedLines.equals(physicalLines(mixedEndings)),
                "CR/LF normalization changed BufferedReader line semantics.");

        for (int boundary = 1; boundary < 9; boundary++) {
            int progress = EndPoemCreditsPlacement.progressForBoundary(boundary, 9);
            check(EndPoemCreditsPlacement.insertionBoundary(9, progress) == boundary,
                    "Normalized insertion progress did not round-trip boundary " + boundary + ".");
        }
    }

    private static void rejectsMalformedInlineCreditsBeforeRendering() {
        String valid = """
                [{"section":"Credits","extra":"kept","disciplines":[
                  {"discipline":"","titles":[{"title":"Role","names":["Alice"]}]}
                ]}]
                """;
        check(CustomCredits.isValidVanillaCreditsJson(valid),
                "A valid vanilla credits block was rejected.");
        Reader validated = CustomCredits.validatedBlockReader(new StringReader(valid))
                .orElseThrow();
        check(JsonParser.parseReader(validated)
                        .getAsJsonArray()
                        .get(0)
                        .getAsJsonObject()
                        .has("extra"),
                "Unknown vanilla credits fields were not preserved.");

        String toleratedPrimitives = """
                [{"section":1,"disciplines":[
                  {"discipline":true,"titles":[{"title":2,"names":[3,false]}]}
                ]}]
                """;
        check(CustomCredits.isValidVanillaCreditsJson(toleratedPrimitives),
                "Values accepted by vanilla getAsString() were rejected.");

        String[] malformed = {
                "{}",
                "[null]",
                "[{\"section\":{},\"disciplines\":[]}]",
                "[{\"section\":\"Credits\"}]",
                "[{\"section\":\"Credits\",\"disciplines\":[null]}]",
                "[{\"section\":\"Credits\",\"disciplines\":[{\"discipline\":\"\"}]}]",
                """
                [{"section":"Credits","disciplines":[
                  {"discipline":"","titles":[{"title":"Role"}]}
                ]}]
                """,
                """
                [{"section":"Credits","disciplines":[
                  {"discipline":"","titles":[{"title":"Role","names":[{"bad":42}]}]}
                ]}]
                """
        };
        for (String invalid : malformed) {
            check(!CustomCredits.isValidVanillaCreditsJson(invalid),
                    "Malformed nested credits reached the rendering parser: " + invalid);
        }
    }

    private static JsonArray parse(Reader reader) {
        return JsonParser.parseReader(reader).getAsJsonArray();
    }

    private static List<String> physicalLines(String text) {
        return new BufferedReader(new StringReader(text)).lines().toList();
    }

    private static String readAll(Reader reader) {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[128];
        try {
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, read);
            }
            return result.toString();
        } catch (IOException e) {
            throw new AssertionError("Failed to read test data.", e);
        }
    }

    private static void checkNames(JsonObject title, String... expected) {
        JsonArray names = title.getAsJsonArray("names");
        check(names.size() == expected.length, "A role contains the wrong number of names.");
        for (int index = 0; index < expected.length; index++) {
            check(expected[index].equals(names.get(index).getAsString()),
                    "Contributor order changed at name index " + index + ".");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class NonMarkingReader extends Reader {
        private final StringReader delegate;

        private NonMarkingReader(String text) {
            delegate = new StringReader(text);
        }

        @Override
        public int read(char[] buffer, int offset, int length) throws IOException {
            return delegate.read(buffer, offset, length);
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
