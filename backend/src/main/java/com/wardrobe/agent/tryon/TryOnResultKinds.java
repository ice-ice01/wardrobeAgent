package com.wardrobe.agent.tryon;

final class TryOnResultKinds {
    private TryOnResultKinds() {
    }

    static String forProvider(String provider) {
        return switch (provider) {
            case "MOCK" -> "MOCK_PREVIEW";
            case "SPRING_AI_IMAGE", "GPT_IMAGE_EDIT" -> "AI_PREVIEW";
            default -> "VIRTUAL_TRY_ON";
        };
    }
}
