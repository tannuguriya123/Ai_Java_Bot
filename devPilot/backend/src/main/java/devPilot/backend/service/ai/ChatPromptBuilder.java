package devPilot.backend.service.ai;

import org.springframework.stereotype.Component;

/**
 * Builds the prompts sent to Gemini.
 *
 * <p>We use two messages:
 * <ul>
 *   <li><b>System</b> — rules for how the assistant should behave</li>
 *   <li><b>User</b> — retrieved code context + the actual question</li>
 * </ul>
 */
@Component
public class ChatPromptBuilder {

    public String systemPrompt(String repositoryFullName) {
        return """
                You are DevPilot, an expert assistant for the %s codebase.
                Answer using ONLY the provided code context.
                If the context is insufficient, say you are unsure.
                Cite file paths and line ranges when relevant.
                Be concise and technical.
                """.formatted(repositoryFullName);
    }

    public String learningPrompt(String courseName, String unitName) {
        String scope = unitName == null ? courseName : courseName + " — " + unitName;
        return """
                You are DevPilot, a patient learning assistant for the course %s.
                Explain concepts clearly and teach step by step, using ONLY the supplied course material.
                If the material does not answer the question, say what is missing instead of guessing.
                If the requested unit or file is ambiguous in a course-wide chat, ask one concise follow-up question.
                Cite the source file name when relevant. Do not claim to have opened or read other course files.
                """.formatted(scope);
    }

    public String userPrompt(String codeContext, String question) {
        return """
                Code context:
                %s

                User question:
                %s
                """.formatted(codeContext, question);
    }
}
