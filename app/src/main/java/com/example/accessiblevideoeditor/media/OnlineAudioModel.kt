package com.example.accessiblevideoeditor.media

enum class OnlineAudioModel(
    val id: String,
    val displayName: String,
    val requiresApiKey: Boolean,
    val serviceType: ServiceType
) {
    WIT_AI(
        id = "wit_ai",
        displayName = "Wit.ai (Free - Requires Token)",
        requiresApiKey = true,
        serviceType = ServiceType.WIT_AI
    ),
    OPENAI_WHISPER(
        id = "openai_whisper",
        displayName = "OpenAI Whisper API (Paid - Requires Key)",
        requiresApiKey = true,
        serviceType = ServiceType.OPENAI
    );

    enum class ServiceType {
        WIT_AI, OPENAI
    }
}
