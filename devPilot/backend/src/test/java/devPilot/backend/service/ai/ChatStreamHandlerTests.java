package devPilot.backend.service.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ChatStreamHandlerTests {

    @Test
    void reportsExhaustedProviderQuotaWithoutLeakingProviderDetails() {
        String message = ChatStreamHandler.userFacingError(
                new IllegalStateException("RESOURCE_EXHAUSTED: Gemini quota exceeded"));

        assertEquals(
                "Gemini's request quota is exhausted for this API project. Wait for the quota to reset or enable billing/increase the Gemini API quota, then retry.",
                message);
    }

    @Test
    void reportsRateLimitAndTemporaryUnavailableErrors() {
        assertEquals(
                "Gemini is receiving too many requests. Wait a little, then retry.",
                ChatStreamHandler.userFacingError(new IllegalStateException("HTTP 429")));
        assertEquals(
                "Gemini is temporarily unavailable. Please try again shortly.",
                ChatStreamHandler.userFacingError(new IllegalStateException("HTTP 503")));
    }

    @Test
    void reportsSafeGenericErrorForUnknownProviderFailures() {
        assertEquals(
                "The AI could not generate a reply. Please try again shortly.",
                ChatStreamHandler.userFacingError(
                        new IllegalStateException("Sensitive provider response and credentials")));
    }
}
