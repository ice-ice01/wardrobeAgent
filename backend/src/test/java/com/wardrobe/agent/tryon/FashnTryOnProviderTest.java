package com.wardrobe.agent.tryon;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FashnTryOnProviderTest {
    @Test
    void submitsV16RequestAndMapsCompletedBase64Result() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        FashnTryOnProvider provider = new FashnTryOnProvider(builder, "https://api.fashn.test", "secret-key");

        server.expect(requestTo("https://api.fashn.test/v1/run"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andExpect(jsonPath("$.model_name").value("tryon-v1.6"))
                .andExpect(jsonPath("$.inputs.model_image").value("data:image/png;base64,MODEL"))
                .andExpect(jsonPath("$.inputs.garment_image").value("data:image/png;base64,GARMENT"))
                .andExpect(jsonPath("$.inputs.category").value("tops"))
                .andExpect(jsonPath("$.inputs.return_base64").value(true))
                .andRespond(withSuccess("{\"id\":\"pred-123\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.fashn.test/v1/status/pred-123"))
                .andExpect(header("Authorization", "Bearer secret-key"))
                .andRespond(withSuccess("{\"id\":\"pred-123\",\"status\":\"completed\",\"output\":[\"data:image/jpeg;base64,RESULT\"]}",
                        MediaType.APPLICATION_JSON));

        var submission = provider.submit(new TryOnProvider.ProviderRequest(
                "data:image/png;base64,MODEL", "data:image/png;base64,GARMENT", "tops", false));
        assertThat(submission.providerTaskId()).isEqualTo("pred-123");
        var result = provider.query(submission.providerTaskId());

        assertThat(result.state()).isEqualTo(TryOnProvider.ProviderResult.State.SUCCEEDED);
        assertThat(result.outputDataUri()).isEqualTo("data:image/jpeg;base64,RESULT");
        server.verify();
    }

    @Test
    void rejectsCallsWithoutApiKeyBeforeNetworkAccess() {
        FashnTryOnProvider provider = new FashnTryOnProvider(RestClient.builder(), "https://api.fashn.test", " ");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> provider.submit(
                        new TryOnProvider.ProviderRequest("model", "garment", "tops", false)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FASHN_API_KEY");
    }
}
