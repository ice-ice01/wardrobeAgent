package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TryOnResultKindsTest {
    @Test
    void directSpringAiProviderIsAlwaysAnAiPreview() {
        assertThat(TryOnResultKinds.forProvider("SPRING_AI_IMAGE")).isEqualTo("AI_PREVIEW");
        assertThat(TryOnResultKinds.forProvider("GPT_IMAGE_EDIT")).isEqualTo("AI_PREVIEW");
        assertThat(TryOnResultKinds.forProvider("MOCK")).isEqualTo("MOCK_PREVIEW");
        assertThat(TryOnResultKinds.forProvider("FASHN_V1_6")).isEqualTo("VIRTUAL_TRY_ON");
    }
}
