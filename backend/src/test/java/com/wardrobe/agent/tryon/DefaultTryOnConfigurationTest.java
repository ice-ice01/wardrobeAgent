package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultTryOnConfigurationTest {
    @Test
    void localStartupDefaultsToGptImageEditWhileTestsExplicitlyUseMock() throws Exception {
        var source = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"))
                .getFirst();

        assertThat(source.getProperty("app.tryon.provider"))
                .isEqualTo("${TRYON_PROVIDER:GPT_IMAGE_EDIT}");
        assertThat(source.getProperty("app.tryon.real-enabled"))
                .isEqualTo("${TRYON_REAL_ENABLED:true}");
        assertThat(Files.readString(Path.of("scripts", "start-backend.ps1")))
                .contains("[string]$TryOnProvider = 'GPT_IMAGE_EDIT'");
    }
}
