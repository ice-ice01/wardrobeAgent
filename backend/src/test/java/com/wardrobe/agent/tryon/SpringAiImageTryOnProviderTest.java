package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SpringAiImageTryOnProviderTest {
    @Test
    void mapsSpringAiBase64ResultToImmediatePreview() {
        AtomicReference<ImagePrompt> captured = new AtomicReference<>();
        ImageModel model = prompt -> {
            captured.set(prompt);
            return new ImageResponse(List.of(new ImageGeneration(new Image(null, "aW1hZ2U="))));
        };
        var beans = new StaticListableBeanFactory();
        beans.addBean("imageModel", model);
        var provider = new SpringAiImageTryOnProvider(beans.getBeanProvider(ImageModel.class), "gpt-image-test", "test-key");

        var submission = provider.submit(new TryOnProvider.ProviderRequest(
                "data:image/png;base64,bW9kZWw=", "data:image/png;base64,Z2FybWVudA==",
                "tops", "白色衬衫", "INNER_TOP", false));

        assertThat(provider.available()).isTrue();
        assertThat(submission.immediateResult().state()).isEqualTo(TryOnProvider.ProviderResult.State.SUCCEEDED);
        assertThat(submission.immediateResult().outputDataUri()).isEqualTo("data:image/png;base64,aW1hZ2U=");
        assertThat(captured.get().getInstructions().getFirst().getText()).contains("白色衬衫").contains("not an identity-preserving");
        assertThat(captured.get().getOptions().getModel()).isEqualTo("gpt-image-test");
    }

    @Test
    void reportsUnavailableWhenNoImageModelExists() {
        var beans = new StaticListableBeanFactory();
        var provider = new SpringAiImageTryOnProvider(beans.getBeanProvider(ImageModel.class), "gpt-image-test", "missing");

        assertThat(provider.available()).isFalse();
        assertThatThrownBy(() -> provider.submit(new TryOnProvider.ProviderRequest(
                "model", "garment", "tops", "衬衫", "INNER_TOP", false)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("ImageModel");
    }
}
