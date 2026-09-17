package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GptImageEditTryOnProviderTest {
    @Test
    void submitsTwoImagePartsAndMapsBase64Result() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GptImageEditTryOnProvider(builder, "https://images.test", "secret-key",
                "gpt-image-test", "1024x1024", "low");

        server.expect(requestTo("https://images.test/v1/images/edits"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.MULTIPART_FORM_DATA))
                .andExpect(request -> {
                    String body = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString();
                    assertThat(body).contains("name=\"model\"").contains("gpt-image-test")
                            .contains("name=\"image[]\"").contains("filename=\"model.png\"")
                            .contains("filename=\"garment.png\"").contains("MODEL").contains("GARMENT")
                            .contains("name=\"quality\"").contains("low");
                })
                .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"UkVTVUxU\"}]}", MediaType.APPLICATION_JSON));

        var submission = provider.submit(new TryOnProvider.ProviderRequest(
                "data:image/png;base64,TU9ERUw=", "data:image/png;base64,R0FSTUVOVA==",
                "tops", "白色衬衫", "INNER_TOP", false));

        assertThat(provider.available()).isTrue();
        assertThat(submission.immediateResult().state()).isEqualTo(TryOnProvider.ProviderResult.State.SUCCEEDED);
        assertThat(submission.immediateResult().outputDataUri()).isEqualTo("data:image/png;base64,UkVTVUxU");
        server.verify();
    }

    @Test
    void rejectsMissingKeyAndInvalidImageBeforeNetworkAccess() {
        var missing = new GptImageEditTryOnProvider(RestClient.builder(), "https://images.test", " ",
                "gpt-image-test", "1024x1024", "low");
        assertThat(missing.available()).isFalse();
        assertThatThrownBy(() -> missing.submit(request("model", "garment")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("not configured");

        var configured = new GptImageEditTryOnProvider(RestClient.builder(), "https://images.test", "key",
                "gpt-image-test", "1024x1024", "low");
        assertThatThrownBy(() -> configured.submit(request("not-a-data-uri", "garment")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Data URI");
    }

    @Test
    void acceptsOpenAiCompatibleBaseUrlWithV1PathWithoutDuplicatingVersion() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GptImageEditTryOnProvider(builder, "https://images.test/v1/", "key",
                "gpt-image-test", "1024x1024", "low");
        server.expect(requestTo("https://images.test/v1/images/edits"))
                .andRespond(withSuccess("{\"data\":[{\"b64_json\":\"UkVTVUxU\"}]}", MediaType.APPLICATION_JSON));

        provider.submit(request("data:image/png;base64,TU9ERUw=", "data:image/png;base64,R0FSTUVOVA=="));
        server.verify();
    }

    @Test
    void downloadsTemporaryUrlWhenGatewayOmitsBase64() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GptImageEditTryOnProvider(builder, "https://images.test", "key",
                "gpt-image-test", "1024x1024", "low");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

        server.expect(requestTo("https://images.test/v1/images/edits"))
                .andRespond(withSuccess("{\"data\":[{\"url\":\"https://cdn.test/result.png\"}]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://cdn.test/result.png"))
                .andRespond(withSuccess(png, MediaType.IMAGE_PNG));

        var submission = provider.submit(request("data:image/png;base64,TU9ERUw=", "data:image/png;base64,R0FSTUVOVA=="));

        assertThat(submission.immediateResult().state()).isEqualTo(TryOnProvider.ProviderResult.State.SUCCEEDED);
        assertThat(submission.immediateResult().outputDataUri()).startsWith("data:image/png;base64,");
        server.verify();
    }

    @Test
    void acceptsCamelCaseBase64AliasFromCompatibleGateway() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        var provider = new GptImageEditTryOnProvider(builder, "https://images.test", "key",
                "gpt-image-test", "1024x1024", "low");
        server.expect(requestTo("https://images.test/v1/images/edits"))
                .andRespond(withSuccess("{\"data\":[{\"b64Json\":\"UkVTVUxU\"}]}", MediaType.APPLICATION_JSON));

        assertThat(provider.submit(request("data:image/png;base64,TU9ERUw=", "data:image/png;base64,R0FSTUVOVA=="))
                .immediateResult().outputDataUri()).isEqualTo("data:image/png;base64,UkVTVUxU");
        server.verify();
    }

    private TryOnProvider.ProviderRequest request(String model, String garment) {
        return new TryOnProvider.ProviderRequest(model, garment, "tops", "衬衫", "INNER_TOP", false);
    }
}
