package com.kobe.smels.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * Garde-fou docs/08 § 3 : un module ne touche pas au `model` / `repository` d'un autre.
 * `shared` est autorisé partout ; `portal` n'a pas de model/repository.
 */
class ModuleBoundariesArchTest {

    private val modules = listOf(
        "user",
        "academic",
        "student",
        "staff",
        "grading",
        "reporting",
        "timetable",
        "notification",
        "portal",
    )

    private val classes = ClassFileImporter()
        .withImportOption(ImportOption.DoNotIncludeTests())
        .importPackages("com.kobe.smels")

    @Test
    fun `service of a module must not depend on repository of another module`() {
        for (module in modules) {
            for (other in modules) {
                if (other == module) continue
                noClasses()
                    .that().resideInAPackage("com.kobe.smels.service.$module..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.kobe.smels.repository.$other..")
                    .because("module '$module' must not use repository of '$other' (docs/08)")
                    .check(classes)
            }
        }
    }

    @Test
    fun `service of a module must not depend on model of another module except shared`() {
        for (module in modules) {
            for (other in modules) {
                if (other == module) continue
                noClasses()
                    .that().resideInAPackage("com.kobe.smels.service.$module..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.kobe.smels.model.$other..")
                    .because("module '$module' must not use model of '$other' — use service/*/api + dto (docs/08)")
                    .check(classes)
            }
        }
    }

    @Test
    fun `controller of a module must not depend on repository of another module`() {
        for (module in modules) {
            for (other in modules) {
                if (other == module) continue
                noClasses()
                    .that().resideInAPackage("com.kobe.smels.controller.$module..")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.kobe.smels.repository.$other..")
                    .because("controller '$module' must not use repository of '$other'")
                    .check(classes)
            }
        }
    }
}
