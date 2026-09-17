package com.wardrobe.agent.tryon;

import java.util.Set;

public interface TryOnProvider {
    String code();
    ProviderCapabilities capabilities();
    ProviderSubmission submit(ProviderRequest request);
    ProviderResult query(String providerTaskId);
    default boolean available() { return true; }

    record ProviderCapabilities(String displayName, boolean real, Set<String> supportedSlots,
                                int estimatedMinSeconds, int estimatedMaxSeconds) {}
    record ProviderRequest(String modelImage, String garmentImage, String category, String itemName,
                           String slot, boolean simulateFailure) {
        public ProviderRequest(String modelImage, String garmentImage, String category, boolean simulateFailure) {
            this(modelImage, garmentImage, category, category, category, simulateFailure);
        }
    }
    record ProviderSubmission(String providerTaskId, String providerStatus, ProviderResult immediateResult) {
        public ProviderSubmission(String providerTaskId, String providerStatus) {
            this(providerTaskId, providerStatus, null);
        }
    }
    record ProviderResult(State state, String providerStatus, String outputDataUri, String outputUrl,
                          String errorCode, String errorMessage) {
        public enum State { PROCESSING, SUCCEEDED, FAILED }
    }
}
