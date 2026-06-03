package com.homefit;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Enforces the module boundaries (core / ingestion / api). Fails the build if, e.g.,
 * api reaches into ingestion's internals or core depends on either.
 */
class ModularityTests {

    static final ApplicationModules MODULES = ApplicationModules.of(HomefitApplication.class);

    @Test
    void verifiesModularStructure() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES).writeDocumentation(); // -> target/spring-modulith-docs
    }
}
