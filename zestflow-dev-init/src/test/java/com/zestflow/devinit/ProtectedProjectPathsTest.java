package com.zestflow.devinit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProtectedProjectPathsTest {

    @TempDir
    Path projectRoot;

    @Test
    void protectsApplicationYmlAndPom() {
        assertTrue(ProtectedProjectPaths.isProtected(
                projectRoot, projectRoot.resolve("zestory-admin/src/main/resources/application.yml")));
        assertTrue(ProtectedProjectPaths.isProtected(
                projectRoot, projectRoot.resolve("zestory-admin/pom.xml")));
        assertFalse(ProtectedProjectPaths.isProtected(
                projectRoot, projectRoot.resolve(".zestflow/rules/architecture.md")));
    }
}
