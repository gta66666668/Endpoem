package io.github.niubima.endpoemfabric.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import io.github.niubima.endpoemfabric.Endpoemfabric;
import io.github.niubima.endpoemfabric.config.EndpoemConfig;
import io.github.niubima.endpoemfabric.config.EndpoemConfigManager;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Owns the user-editable contributor list and converts it to Minecraft's
 * ordered {@code texts/credits.json} format at playback time.
 */
public final class CustomCredits {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_ENTRIES = 512;
    public static final int MAX_SECTION_LENGTH = 64;
    public static final int MAX_FIELD_LENGTH = 64;

    private static final long MAX_FILE_SIZE_BYTES = 1_048_576L;
    private static final int MAX_ORIGINAL_CREDITS_CHARACTERS = 8_388_608;
    private static final int READ_BUFFER_SIZE = 8192;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE_NAME = "contributors.json";
    private static Document previewDocument;

    private CustomCredits() {
    }

    public static void initialize() {
        ensureFileExists();
    }

    /**
     * Builds the normal post-poem credits stage. Custom credits already
     * inserted before or inside the poem are omitted here; if inline insertion
     * failed, they safely fall back to the post-poem position.
     */
    public static Reader readerOrOriginal(Reader original) {
        return readerOrOriginal(original, false);
    }

    public static Reader readerOrOriginal(
            Reader original,
            boolean customCreditsInsertedInline
    ) {
        EndpoemConfig config = EndpoemConfigManager.get();
        return readerOrOriginal(
                original,
                config.showVanillaCredits,
                config.customCreditsPlacement,
                customCreditsInsertedInline
        );
    }

    public static Reader readerOrOriginal(
            Reader original,
            boolean showVanillaCredits,
            String customCreditsPlacement,
            boolean customCreditsInsertedInline
    ) {
        boolean includeVanilla = showVanillaCredits;
        boolean includeCustom = !EndpoemConfig.CREDITS_PLACEMENT_OFF.equals(
                customCreditsPlacement
        ) && !customCreditsInsertedInline;

        if (!includeCustom) {
            return includeVanilla ? original : new StringReader("[]");
        }

        Document document;
        try {
            document = read();
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to load custom contributors from {}. Using the available vanilla credits.",
                    getPath(),
                    e
            );
            return includeVanilla ? original : new StringReader("[]");
        }

        JsonArray customCredits = toVanillaCredits(document);
        if (!includeVanilla) {
            return new StringReader(GSON.toJson(customCredits));
        }

        String originalText;
        try {
            originalText = bufferOriginalCredits(original);
        } catch (IOException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to buffer vanilla credits. Using vanilla credits only.",
                    e
            );
            return original;
        }

        try {
            JsonArray vanillaCredits = parseAndValidateVanillaCredits(originalText);
            boolean customAfterVanilla =
                    EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA.equals(
                            customCreditsPlacement
                    );
            JsonArray combined = combineCredits(
                    true,
                    true,
                    customAfterVanilla,
                    vanillaCredits,
                    customCredits
            );
            return new StringReader(GSON.toJson(combined));
        } catch (JsonParseException | IllegalStateException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to merge custom and vanilla credits. Using vanilla credits.",
                    e
            );
            return new StringReader(originalText);
        }
    }

    /**
     * Produces a fully buffered and structurally validated credits block for
     * insertion inside the poem. Validation happens before WinScreen mutates
     * any of its line lists, preventing a malformed resource-pack credits file
     * from leaving a partially inserted block behind.
     */
    public static Optional<Reader> validatedBlockReader(Reader original) {
        Reader configured = readerOrOriginal(original);
        try {
            String text = bufferOriginalCredits(configured);
            parseAndValidateVanillaCredits(text);
            return Optional.of(new StringReader(text));
        } catch (IOException | JsonParseException | IllegalStateException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to validate the credits block for in-poem placement.",
                    e
            );
            return Optional.empty();
        }
    }

    /**
     * Prepares custom-only credits without opening or reading the vanilla
     * credits resource. This keeps the custom-only playback mode independent
     * from resource-pack credits availability.
     */
    public static Optional<Reader> validatedCustomCreditsReader() {
        try {
            Document document = read();
            String text = GSON.toJson(toVanillaCredits(document));
            parseAndValidateVanillaCredits(text);
            return Optional.of(new StringReader(text));
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn(
                    "Failed to prepare custom credits for inline placement.",
                    e
            );
            return Optional.empty();
        }
    }

    static boolean isValidVanillaCreditsJson(String text) {
        try {
            parseAndValidateVanillaCredits(text);
            return true;
        } catch (JsonParseException | IllegalStateException e) {
            return false;
        }
    }

    static JsonArray combineCredits(
            boolean includeVanilla,
            boolean includeCustom,
            boolean customAfterVanilla,
            JsonArray vanilla,
            JsonArray custom
    ) {
        JsonArray combined = new JsonArray();
        if (includeCustom && !customAfterVanilla) {
            appendAll(combined, custom);
        }
        if (includeVanilla) {
            appendAll(combined, vanilla);
        }
        if (includeCustom && customAfterVanilla) {
            appendAll(combined, custom);
        }
        return combined;
    }

    /**
     * Arms a one-shot custom-only preview for a non-poem {@code WinScreen}.
     * Normal title-screen credits remain untouched.
     */
    public static synchronized void preparePreview(Document document) {
        Document normalized = normalize(document);
        Optional<ValidationError> error = validate(normalized);
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid contributor preview: " + error.get());
        }
        previewDocument = normalized;
    }

    public static synchronized Reader previewReaderOrOriginal(Reader original) {
        if (previewDocument == null) {
            return original;
        }
        Document document = previewDocument;
        previewDocument = null;
        return new StringReader(GSON.toJson(toVanillaCredits(document)));
    }

    public static Document read() throws IOException {
        ensureFileExists();
        Path path = getPath();
        if (Files.size(path) > MAX_FILE_SIZE_BYTES) {
            throw new IOException("Contributor file exceeds " + MAX_FILE_SIZE_BYTES + " bytes.");
        }

        Document document;
        try {
            document = GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Document.class);
        } catch (RuntimeException e) {
            throw new IOException("Contributor file is not valid JSON.", e);
        }

        Document normalized = normalize(document);
        Optional<ValidationError> error = validate(normalized);
        if (error.isPresent()) {
            throw new IOException("Invalid contributor document: " + error.get());
        }
        return normalized;
    }

    public static void write(Document document) throws IOException {
        Document normalized = normalize(document);
        Optional<ValidationError> error = validate(normalized);
        if (error.isPresent()) {
            throw new IllegalArgumentException("Invalid contributor document: " + error.get());
        }

        Path path = getPath();
        Files.createDirectories(path.getParent());
        Path temporary = Files.createTempFile(path.getParent(), "contributors-", ".tmp");
        try {
            Files.writeString(temporary, GSON.toJson(normalized), StandardCharsets.UTF_8);
            try {
                Files.move(
                        temporary,
                        path,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public static Optional<ValidationError> validate(Document document) {
        if (document == null) {
            return Optional.of(new ValidationError(ValidationProblem.MISSING_DOCUMENT, -1));
        }
        if (document.schemaVersion() != SCHEMA_VERSION) {
            return Optional.of(new ValidationError(ValidationProblem.UNSUPPORTED_SCHEMA, -1));
        }
        if (document.section().isBlank()) {
            return Optional.of(new ValidationError(ValidationProblem.EMPTY_SECTION, -1));
        }
        if (document.section().length() > MAX_SECTION_LENGTH) {
            return Optional.of(new ValidationError(ValidationProblem.SECTION_TOO_LONG, -1));
        }
        if (hasControlCharacter(document.section())) {
            return Optional.of(new ValidationError(ValidationProblem.CONTROL_CHARACTER, -1));
        }
        if (document.entries().size() > MAX_ENTRIES) {
            return Optional.of(new ValidationError(ValidationProblem.TOO_MANY_ENTRIES, -1));
        }

        for (int index = 0; index < document.entries().size(); index++) {
            Entry entry = document.entries().get(index);
            if (entry.name().isBlank()) {
                return Optional.of(new ValidationError(ValidationProblem.EMPTY_ENTRY_FIELD, index));
            }
            if (entry.role().length() > MAX_FIELD_LENGTH || entry.name().length() > MAX_FIELD_LENGTH) {
                return Optional.of(new ValidationError(ValidationProblem.ENTRY_FIELD_TOO_LONG, index));
            }
            if (hasControlCharacter(entry.role()) || hasControlCharacter(entry.name())) {
                return Optional.of(new ValidationError(ValidationProblem.CONTROL_CHARACTER, index));
            }
        }
        return Optional.empty();
    }

    public static Document defaultDocument() {
        return new Document(
                SCHEMA_VERSION,
                "Endpoem",
                List.of(new Entry("Mod Author", "gta66666668"))
        );
    }

    public static Path getPath() {
        return FabricLoader.getInstance()
                .getConfigDir()
                .resolve(Endpoemfabric.MODID)
                .resolve(FILE_NAME);
    }

    private static void ensureFileExists() {
        Path path = getPath();
        if (Files.exists(path)) {
            return;
        }

        try {
            write(defaultDocument());
            Endpoemfabric.LOGGER.info("Created contributor template at {}", path);
        } catch (IOException | RuntimeException e) {
            Endpoemfabric.LOGGER.warn("Failed to create contributor template at {}", path, e);
        }
    }

    private static Document normalize(Document document) {
        if (document == null) {
            return null;
        }

        List<Entry> entries = new ArrayList<>(document.entries().size());
        for (Entry entry : document.entries()) {
            entries.add(new Entry(entry.role().trim(), entry.name().trim()));
        }
        return new Document(document.schemaVersion(), document.section().trim(), entries);
    }

    private static JsonArray toVanillaCredits(Document document) {
        JsonArray root = new JsonArray();
        if (document.entries().isEmpty()) {
            return root;
        }

        JsonObject section = new JsonObject();
        section.addProperty("section", document.section());

        JsonArray disciplines = new JsonArray();
        JsonObject discipline = new JsonObject();
        discipline.addProperty("discipline", "");

        JsonArray titles = new JsonArray();
        int index = 0;
        while (index < document.entries().size()) {
            Entry first = document.entries().get(index);
            JsonObject title = new JsonObject();
            title.addProperty("title", first.role());

            JsonArray names = new JsonArray();
            names.add(first.name());
            index++;
            while (index < document.entries().size()
                    && first.role().equals(document.entries().get(index).role())) {
                names.add(document.entries().get(index).name());
                index++;
            }

            title.add("names", names);
            titles.add(title);
        }

        discipline.add("titles", titles);
        disciplines.add(discipline);
        section.add("disciplines", disciplines);
        root.add(section);
        return root;
    }

    private static void appendAll(JsonArray destination, JsonArray source) {
        for (JsonElement element : source) {
            destination.add(element);
        }
    }

    private static JsonArray parseAndValidateVanillaCredits(String text) {
        JsonElement parsed = JsonParser.parseString(text);
        if (!parsed.isJsonArray()) {
            throw new JsonParseException("Credits root is not an array.");
        }
        JsonArray root = parsed.getAsJsonArray();
        validateVanillaCredits(root);
        return root;
    }

    private static void validateVanillaCredits(JsonArray root) {
        for (JsonElement sectionElement : root) {
            JsonObject section = requireObject(sectionElement, "credits section");
            requirePrimitive(section, "section");
            JsonArray disciplines = requireArray(section, "disciplines");

            for (JsonElement disciplineElement : disciplines) {
                JsonObject discipline = requireObject(
                        disciplineElement,
                        "credits discipline"
                );
                requirePrimitive(discipline, "discipline");
                JsonArray titles = requireArray(discipline, "titles");

                for (JsonElement titleElement : titles) {
                    JsonObject title = requireObject(titleElement, "credits title");
                    requirePrimitive(title, "title");
                    JsonArray names = requireArray(title, "names");
                    for (JsonElement name : names) {
                        if (!name.isJsonPrimitive()) {
                            throw new JsonParseException(
                                    "Credits name must be a primitive value."
                            );
                        }
                    }
                }
            }
        }
    }

    private static JsonObject requireObject(JsonElement element, String description) {
        if (element == null || !element.isJsonObject()) {
            throw new JsonParseException(description + " must be an object.");
        }
        return element.getAsJsonObject();
    }

    private static JsonArray requireArray(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonArray()) {
            throw new JsonParseException(
                    "Credits member '" + member + "' must be an array."
            );
        }
        return element.getAsJsonArray();
    }

    private static void requirePrimitive(JsonObject object, String member) {
        JsonElement element = object.get(member);
        if (element == null || !element.isJsonPrimitive()) {
            throw new JsonParseException(
                    "Credits member '" + member + "' must be a primitive value."
            );
        }
    }

    /**
     * Buffers a resource-pack credits file while retaining a safe reset point
     * if an I/O or size-limit failure occurs partway through the read.
     */
    private static String bufferOriginalCredits(Reader reader) throws IOException {
        if (!reader.markSupported()) {
            throw new IOException("The vanilla credits reader does not support reset.");
        }

        reader.mark(MAX_ORIGINAL_CREDITS_CHARACTERS + READ_BUFFER_SIZE);
        try {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[READ_BUFFER_SIZE];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                result.append(buffer, 0, read);
                if (result.length() > MAX_ORIGINAL_CREDITS_CHARACTERS) {
                    throw new IOException(
                            "Vanilla credits exceed " + MAX_ORIGINAL_CREDITS_CHARACTERS + " characters."
                    );
                }
            }
            return result.toString();
        } catch (IOException e) {
            try {
                reader.reset();
            } catch (IOException resetFailure) {
                e.addSuppressed(resetFailure);
            }
            throw e;
        }
    }

    private static boolean hasControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    public record Document(int schemaVersion, String section, List<Entry> entries) {
        public Document {
            section = section == null ? "" : section;
            List<Entry> safeEntries = new ArrayList<>();
            if (entries != null) {
                for (Entry entry : entries) {
                    safeEntries.add(entry == null ? new Entry("", "") : entry);
                }
            }
            entries = List.copyOf(safeEntries);
        }
    }

    public record Entry(String role, String name) {
        public Entry {
            role = role == null ? "" : role;
            name = name == null ? "" : name;
        }
    }

    public record ValidationError(ValidationProblem problem, int entryIndex) {
    }

    public enum ValidationProblem {
        MISSING_DOCUMENT,
        UNSUPPORTED_SCHEMA,
        EMPTY_SECTION,
        SECTION_TOO_LONG,
        TOO_MANY_ENTRIES,
        EMPTY_ENTRY_FIELD,
        ENTRY_FIELD_TOO_LONG,
        CONTROL_CHARACTER
    }
}
