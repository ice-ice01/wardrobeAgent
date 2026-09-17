package com.wardrobe.agent.tryon;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

final class TryOnHttpClients {
    private TryOnHttpClients() {}

    static RestClient.Builder withTimeouts(RestClient.Builder builder,
                                           Duration connectTimeout,
                                           Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return builder.requestFactory(requestFactory);
    }
}
