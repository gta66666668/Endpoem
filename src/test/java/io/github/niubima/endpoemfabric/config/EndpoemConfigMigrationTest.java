package io.github.niubima.endpoemfabric.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Regression checks for every legacy credits state and the simplified schema.
 */
public final class EndpoemConfigMigrationTest {
    private EndpoemConfigMigrationTest() {
    }

    public static void run() {
        usesUnambiguousDefaults();
        migratesEveryLegacyState();
        preservesExplicitCanonicalFields();
        foldsTransitionalVisibilityIntoPlacement();
        normalizesImpossibleSimplifiedStates();
    }

    private static void usesUnambiguousDefaults() {
        EndpoemConfig value = new EndpoemConfig();
        check(value.showVanillaCredits, "Vanilla credits should be shown by default.");
        check(EndpoemConfig.CREDITS_PLACEMENT_OFF.equals(
                        value.customCreditsPlacement
                ), "Custom credits should be disabled by default.");
    }

    private static void migratesEveryLegacyState() {
        checkLegacy(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_VANILLA,
                EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_OFF
        );
        checkLegacy(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_VANILLA,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_OFF
        );
        checkLegacy(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_VANILLA,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_OFF
        );

        checkCustomMode(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_CUSTOM_BEFORE,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM
        );
        checkCustomMode(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_CUSTOM_AFTER,
                true,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA
        );
        checkCustomMode(
                EndpoemConfigManager.LEGACY_CREDITS_MODE_CUSTOM_ONLY,
                false,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM
        );
    }

    private static void checkCustomMode(
            String mode,
            boolean expectedVanilla,
            String expectedAfterPoemPlacement
    ) {
        checkLegacy(
                mode,
                EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM,
                expectedVanilla,
                EndpoemConfig.CREDITS_PLACEMENT_BEFORE_POEM
        );
        checkLegacy(
                mode,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM,
                expectedVanilla,
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM
        );
        checkLegacy(
                mode,
                EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM,
                expectedVanilla,
                expectedAfterPoemPlacement
        );
    }

    private static void checkLegacy(
            String mode,
            String placement,
            boolean expectedVanilla,
            String expectedCustomPlacement
    ) {
        EndpoemConfig migrated = new EndpoemConfig();
        EndpoemConfigManager.migrateLegacyCredits(migrated, mode, placement);
        check(migrated.showVanillaCredits == expectedVanilla,
                "Legacy vanilla visibility changed for " + mode + "/" + placement + ".");
        check(expectedCustomPlacement.equals(migrated.customCreditsPlacement),
                "Legacy custom placement changed for " + mode + "/" + placement + ".");
    }

    private static void preservesExplicitCanonicalFields() {
        JsonObject root = JsonParser.parseString("""
                {
                  "creditsMode": "custom_only",
                  "creditsPlacement": "inside_poem",
                  "showVanillaCredits": true,
                  "customCreditsPlacement": "after_poem"
                }
                """).getAsJsonObject();
        EndpoemConfig value = new EndpoemConfig();
        value.showVanillaCredits = true;
        value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM;

        EndpoemConfigManager.applyCreditsMigration(value, root);

        check(value.showVanillaCredits,
                "An explicit canonical vanilla setting was overwritten.");
        check(EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM.equals(
                        value.customCreditsPlacement
                ), "An explicit canonical custom placement was overwritten.");

        JsonObject partial = JsonParser.parseString("""
                {
                  "creditsMode": "custom_after",
                  "creditsPlacement": "inside_poem",
                  "showVanillaCredits": false
                }
                """).getAsJsonObject();
        EndpoemConfig partialValue = new EndpoemConfig();
        partialValue.showVanillaCredits = false;
        EndpoemConfigManager.applyCreditsMigration(partialValue, partial);
        check(!partialValue.showVanillaCredits,
                "A partial canonical vanilla setting was overwritten.");
        check(EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM.equals(
                        partialValue.customCreditsPlacement
                ), "A missing canonical custom placement was not migrated.");
    }

    private static void foldsTransitionalVisibilityIntoPlacement() {
        JsonObject disabled = JsonParser.parseString("""
                {
                  "showCustomCredits": false,
                  "customCreditsPlacement": "inside_poem"
                }
                """).getAsJsonObject();
        EndpoemConfig disabledValue = new EndpoemConfig();
        disabledValue.customCreditsPlacement =
                EndpoemConfig.CREDITS_PLACEMENT_INSIDE_POEM;
        EndpoemConfigManager.applyCreditsMigration(disabledValue, disabled);
        check(EndpoemConfig.CREDITS_PLACEMENT_OFF.equals(
                        disabledValue.customCreditsPlacement
                ), "The transitional disabled state did not migrate to off.");

        JsonObject enabled = JsonParser.parseString("""
                {"showCustomCredits": true}
                """).getAsJsonObject();
        EndpoemConfig enabledValue = new EndpoemConfig();
        EndpoemConfigManager.applyCreditsMigration(enabledValue, enabled);
        check(EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM.equals(
                        enabledValue.customCreditsPlacement
                ), "The transitional enabled state lost custom playback.");
    }

    private static void normalizesImpossibleSimplifiedStates() {
        EndpoemConfig value = new EndpoemConfig();
        value.showVanillaCredits = false;
        value.customCreditsPlacement = EndpoemConfig.CREDITS_PLACEMENT_AFTER_VANILLA;
        value.creditsInsertionProgress = 20_000;

        EndpoemConfigManager.sanitize(value);

        check(EndpoemConfig.CREDITS_PLACEMENT_AFTER_POEM.equals(
                        value.customCreditsPlacement
                ), "After-vanilla remained selectable while vanilla credits were hidden.");
        check(value.creditsInsertionProgress == 9999,
                "Insertion progress was not normalized.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
