package com.example.gamehub.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.example.gamehub.BuildConfig;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generates final-round AI reviews for GameHub games.
 *
 * The service calls Gemini with a structured JSON schema first, then retries with
 * a plain-text prompt, and finally falls back to deterministic local text when
 * the network response is empty, boilerplate, or too short for display.
 */
public class GeminiReviewService {
    /**
     * Callback used by result screens. All callback methods are posted back to
     * the main thread so Activity code can update views directly.
     */
    public interface Callback {
        void onSuccess(String review);

        void onError(String message);
    }

    private static final String MODEL_NAME = "gemini-2.5-flash";
    private static final String MODEL_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/" + MODEL_NAME + ":generateContent";
    private static final String MISSING_KEY_ERROR = "Chưa cấu hình GEMINI_API_KEY trong local.properties.";
    private static final String GENERIC_ERROR = "Chưa thể tạo nhận xét AI lúc này. Hãy thử lại sau.";
    private static final String BUSY_ERROR = "AI đang bận do có nhiều yêu cầu cùng lúc. Hãy thử lại sau ít phút.";
    private static final String INVALID_KEY_ERROR = "API key Gemini không hợp lệ hoặc chưa được cấp quyền.";
    private static final String EMPTY_REVIEW_ERROR = "Chưa thể tạo nhận xét AI cho ván này.";
    private static final String TAG = "GeminiReviewService";
    private static final String VIETNAMESE_SYSTEM_PROMPT =
            "Bạn là người nhận xét sau trận game trong ứng dụng GameHub. " +
            "Chỉ được trả lời bằng tiếng Việt có dấu, giọng khách quan, dễ nghe, ngắn gọn. " +
            "Không dùng gạch đầu dòng, không xưng là AI, không dùng tiếng Anh trừ tên riêng hoặc thuật ngữ game. " +
            "Trả về đúng 2 đến 3 câu.";
    private static final int MIN_REVIEW_CHAR_COUNT = 28;
    private static final int MIN_REVIEW_CHAR_COUNT_STRICT = 45;
    private static final String STRUCTURED_REVIEW_REQUIREMENTS =
            "Bat buoc viet 2 hoac 3 cau hoan chinh. Cau 1 neu mot diem lam tot noi bat dua tren so lieu hoac hanh vi cu the. " +
            "Cau 2 bat buoc neu mot diem can cai thien that cu the va huong dieu chinh ngan gon. " +
            "Neu co cau 3 thi do la mot loi khuyen thuc hanh ngan. Khong duoc tra ve 1 cau duy nhat va khong duoc nhan xet chung chung.";
    private static final String STRUCTURED_REVIEW_TONE_GUIDE =
            "Use a tone that matches the result: very good results can be praised clearly, average results should sound fairly positive but still point out the gap, " +
            "and weak results should sound gentle and constructive, like tam on or can cai thien, not harsh. " +
            "If the prompt includes score versus total, accuracy, time, combo, attempts, mistakes, or level, the review should refer to those signals directly.";
    private static final String STRUCTURED_JSON_PROMPT =
            "Return valid JSON only. " +
            "Do not add any introduction, explanation, markdown fence, or trailing text. " +
            "Use this exact shape: " +
            "{\"overall_tone\":\"excellent|good|average|needs_work\",\"praise\":\"...\",\"improvement\":\"...\",\"closing\":\"...\"}. " +
            "The praise field must be sentence 1 and clearly mention what the player did well based on the real round stats. " +
            "The improvement field must be sentence 2 and must mention one concrete weakness plus one practical way to improve it. " +
            "The closing field is optional sentence 3 and should be empty if not needed. " +
            "Always write in natural Vietnamese with accents. Never use markdown or bullet points. Never return more than 3 sentences total.";

    private static GeminiReviewService instance;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    @SuppressWarnings("unused")
    private final Context appContext;

    private GeminiReviewService(Context context) {
        this.appContext = context.getApplicationContext();
    }

    public static synchronized GeminiReviewService getInstance(Context context) {
        if (instance == null) {
            instance = new GeminiReviewService(context);
        }
        return instance;
    }

    /**
     * Starts review generation for a completed game round.
     *
     * @param prompt full game summary, event log, and AI_METRICS block.
     * @param callback receives a displayable review or a user-facing error.
     */
    public void requestReview(@NonNull String prompt, @NonNull Callback callback) {
        String apiKey = BuildConfig.GEMINI_API_KEY == null ? "" : BuildConfig.GEMINI_API_KEY.trim();
        if (apiKey.isEmpty()) {
            postError(callback, MISSING_KEY_ERROR);
            return;
        }

        executor.execute(() -> {
            try {
                String review = executePrompt(apiKey, prompt);
                if (review.isEmpty()) {
                    postError(callback, EMPTY_REVIEW_ERROR);
                    return;
                }
                postSuccess(callback, review);
            } catch (ReviewException exception) {
                Log.w(TAG, "Gemini review request failed: " + exception.getMessage());
                postError(callback, exception.getMessage());
            } catch (Exception exception) {
                Log.e(TAG, "Unexpected Gemini review failure", exception);
                postError(callback, GENERIC_ERROR);
            }
        });
    }

    /**
     * Runs the Gemini review pipeline and validates that the final text is safe
     * to show in the result screen.
     */
    private String executePrompt(String apiKey, String prompt) throws Exception {
        String rawResponse = performRequest(apiKey, prompt);
        String review = normalizeCandidateReview(parseReview(rawResponse));
        if (review.isEmpty() || isBoilerplateResponse(review)) {
            Log.w(TAG, "Gemini returned unusable structured review: " + abbreviate(review));
            return buildFallbackReview(apiKey, prompt, review);
        }

        if (needsRepair(review) && !isBoilerplateResponse(review)) {
            String repaired = normalizeCandidateReview(parseReview(performRequest(apiKey, buildRecoveryPrompt(prompt, review))));
            if (isAcceptableReview(repaired) || isDisplayableReview(repaired)) {
                review = repaired;
            }
        }

        if (looksEnglish(review) && !containsVietnameseChars(review)) {
            String localizedReview = normalizeCandidateReview(parseReview(performRequest(apiKey, buildLocalizationPrompt(review))));
            if ((isAcceptableReview(localizedReview) || isDisplayableReview(localizedReview))
                    && containsVietnameseChars(localizedReview)) {
                review = localizedReview;
            }
        }
        if (!isAcceptableReview(review)) {
            String rewritten = normalizeCandidateReview(parseReview(performRequest(apiKey, buildStructurePrompt(prompt, review))));
            if (isAcceptableReview(rewritten) || isDisplayableReview(rewritten)) {
                review = rewritten;
            }
        }
        if (isAcceptableReview(review)) {
            return review;
        }
        if (isDisplayableReview(review) && !looksTruncated(review)) {
            Log.w(TAG, "Gemini review kept with relaxed validation: " + abbreviate(review));
            return review;
        }
        Log.w(TAG, "Gemini review rejected after validation: " + abbreviate(review));
        return buildFallbackReview(apiKey, prompt, review);
    }

    private String performRequest(String apiKey, String prompt) throws Exception {
        return performRequestBody(apiKey, buildRequestBody(prompt));
    }

    private String performPlainTextRequest(String apiKey, String prompt, String rejectedReview) throws Exception {
        return performRequestBody(apiKey, buildPlainTextRequestBody(prompt, rejectedReview));
    }

    /**
     * External API boundary for Gemini generateContent.
     *
     * HTTP details stay in this method so callers only handle normalized review
     * text or mapped Vietnamese error messages.
     */
    private String performRequestBody(String apiKey, String requestBody) throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(MODEL_ENDPOINT).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(20_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("x-goog-api-key", apiKey);

            byte[] body = requestBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(body);
            }

            int responseCode = connection.getResponseCode();
            InputStream responseStream = responseCode >= 200 && responseCode < 300
                    ? connection.getInputStream()
                    : connection.getErrorStream();
            String rawResponse = readFully(responseStream);
            if (responseCode < 200 || responseCode >= 300) {
                Log.w(TAG, "Gemini HTTP " + responseCode + " for " + MODEL_NAME + ": " + abbreviate(rawResponse));
                throw new ReviewException(extractErrorMessage(responseCode, rawResponse));
            }
            return rawResponse;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Fallback chain after Gemini returns unusable structured JSON.
     *
     * Plain-text Gemini is tried first. If that also fails validation, the
     * service creates a local review from AI_METRICS so the result screen still
     * has useful feedback for offline-like or degraded AI responses.
     */
    private String buildFallbackReview(String apiKey, String prompt, String rejectedReview) throws Exception {
        String plainReview = requestPlainTextReview(apiKey, prompt, rejectedReview);
        if (!plainReview.isEmpty()) {
            return plainReview;
        }

        String localReview = buildLocalFallbackReview(prompt);
        if (!localReview.isEmpty()) {
            Log.w(TAG, "Using local fallback review after unusable Gemini response.");
            return localReview;
        }
        return "";
    }

    private String requestPlainTextReview(String apiKey, String prompt, String rejectedReview) throws Exception {
        try {
            String rawResponse = performPlainTextRequest(apiKey, prompt, rejectedReview);
            String review = normalizeCandidateReview(parseReview(rawResponse));
            if (!isBoilerplateResponse(review) && (isAcceptableReview(review) || isDisplayableReview(review))) {
                return review;
            }
            Log.w(TAG, "Gemini plain-text fallback rejected after validation: " + abbreviate(review));
        } catch (ReviewException exception) {
            throw exception;
        } catch (Exception exception) {
            Log.w(TAG, "Gemini plain-text fallback failed.", exception);
        }
        return "";
    }

    /**
     * Builds the primary Gemini request with responseMimeType and JSON schema.
     * This keeps model output predictable enough to parse into praise,
     * improvement, and optional closing sentences.
     */
    private String buildRequestBody(String prompt) throws Exception {
        JSONObject root = new JSONObject();

        JSONObject systemInstruction = new JSONObject();
        JSONArray systemParts = new JSONArray();
        systemParts.put(new JSONObject().put("text",
                VIETNAMESE_SYSTEM_PROMPT + " " +
                        STRUCTURED_REVIEW_REQUIREMENTS + " " +
                        STRUCTURED_REVIEW_TONE_GUIDE + " " +
                        STRUCTURED_JSON_PROMPT));
        systemInstruction.put("parts", systemParts);
        root.put("systemInstruction", systemInstruction);

        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        parts.put(new JSONObject().put("text", prompt));
        content.put("parts", parts);
        contents.put(content);
        root.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.6);
        generationConfig.put("topP", 0.9);
        generationConfig.put("maxOutputTokens", 220);
        generationConfig.put("responseMimeType", "application/json");
        generationConfig.put("responseJsonSchema", buildStructuredReviewSchema());
        root.put("generationConfig", generationConfig);
        return root.toString();
    }

    private String buildPlainTextRequestBody(String prompt, String rejectedReview) throws Exception {
        JSONObject root = new JSONObject();

        JSONObject systemInstruction = new JSONObject();
        JSONArray systemParts = new JSONArray();
        systemParts.put(new JSONObject().put("text",
                "Bạn là người nhận xét sau trận game trong ứng dụng GameHub. " +
                        "Chỉ trả về 2 đến 3 câu tiếng Việt có dấu, không JSON, không markdown, không lời dẫn. " +
                        "Câu 1 nêu một điểm làm tốt dựa trên số liệu thật. " +
                        "Câu 2 nêu một điểm cần cải thiện và cách điều chỉnh ngắn gọn. " +
                        "Nếu có câu 3 thì là lời khuyên thực hành ngắn."));
        systemInstruction.put("parts", systemParts);
        root.put("systemInstruction", systemInstruction);

        JSONArray contents = new JSONArray();
        JSONObject content = new JSONObject();
        JSONArray parts = new JSONArray();
        StringBuilder userPrompt = new StringBuilder();
        if (rejectedReview != null && !rejectedReview.trim().isEmpty()) {
            userPrompt.append("Phản hồi trước không dùng được vì không phải nhận xét: ")
                    .append(rejectedReview.trim())
                    .append("\n\n");
        }
        userPrompt.append("Dữ liệu ván chơi:\n").append(prompt);
        parts.put(new JSONObject().put("text", userPrompt.toString()));
        content.put("parts", parts);
        contents.put(content);
        root.put("contents", contents);

        JSONObject generationConfig = new JSONObject();
        generationConfig.put("temperature", 0.55);
        generationConfig.put("topP", 0.9);
        generationConfig.put("maxOutputTokens", 180);
        root.put("generationConfig", generationConfig);
        return root.toString();
    }

    private String parseReview(String rawResponse) throws Exception {
        JSONObject response = new JSONObject(rawResponse);
        JSONArray candidates = response.optJSONArray("candidates");
        if (candidates == null || candidates.length() == 0) {
            return "";
        }

        JSONObject firstCandidate = candidates.optJSONObject(0);
        if (firstCandidate == null) {
            return "";
        }

        JSONObject content = firstCandidate.optJSONObject("content");
        if (content == null) {
            return "";
        }

        JSONArray parts = content.optJSONArray("parts");
        if (parts == null || parts.length() == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONObject part = parts.optJSONObject(i);
            if (part == null) {
                continue;
            }
            String text = part.optString("text", "");
            if (!text.trim().isEmpty()) {
                if (builder.length() > 0) {
                    builder.append(' ');
                }
                builder.append(text.trim());
            }
        }
        return parseStructuredReviewText(builder.toString());
    }

    private String sanitizeReview(String text) {
        String sanitized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        sanitized = sanitized.replaceAll("^(?i)(nhận xét|feedback|review)[:\\-\\s]*", "");
        return sanitized.trim();
    }

    private JSONObject buildStructuredReviewSchema() throws Exception {
        JSONObject schema = new JSONObject();
        schema.put("type", "object");

        JSONObject properties = new JSONObject();
        properties.put("overall_tone", new JSONObject()
                .put("type", "string")
                .put("description", "Overall tone bucket for the review.")
                .put("enum", new JSONArray()
                        .put("excellent")
                        .put("good")
                        .put("average")
                        .put("needs_work")));
        properties.put("praise", new JSONObject()
                .put("type", "string")
                .put("description", "Sentence 1. Praise a concrete strength from the real round data."));
        properties.put("improvement", new JSONObject()
                .put("type", "string")
                .put("description", "Sentence 2. Point out a concrete weakness and one practical improvement."));
        properties.put("closing", new JSONObject()
                .put("type", "string")
                .put("description", "Optional sentence 3. Use an empty string if not needed."));
        schema.put("properties", properties);
        schema.put("required", new JSONArray()
                .put("overall_tone")
                .put("praise")
                .put("improvement")
                .put("closing"));
        return schema;
    }

    private String parseStructuredReviewText(String rawText) {
        String candidate = sanitizeReview(rawText);
        if (candidate.isEmpty()) {
            return "";
        }

        String jsonObjectText = extractFirstJsonObject(candidate);
        if (!jsonObjectText.isEmpty()) {
            String structuredReview = parseStructuredJsonReview(jsonObjectText);
            if (!structuredReview.isEmpty()) {
                return structuredReview;
            }
        }

        return sanitizeReview(candidate);
    }

    private String parseStructuredJsonReview(String jsonText) {
        try {
            JSONObject root = new JSONObject(jsonText);
            String praise = normalizeStructuredSentence(root.optString("praise", ""));
            String improvement = normalizeStructuredSentence(root.optString("improvement", ""));
            String closing = normalizeStructuredSentence(root.optString("closing", ""));
            if (praise.isEmpty() || improvement.isEmpty()) {
                return "";
            }

            StringBuilder builder = new StringBuilder();
            builder.append(praise).append(' ').append(improvement);
            if (!closing.isEmpty()) {
                builder.append(' ').append(closing);
            }
            return sanitizeReview(builder.toString());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String extractFirstJsonObject(String text) {
        if (text == null || text.trim().isEmpty()) {
            return "";
        }

        int startIndex = text.indexOf('{');
        if (startIndex < 0) {
            return "";
        }

        boolean inString = false;
        boolean escaping = false;
        int depth = 0;
        for (int index = startIndex; index < text.length(); index++) {
            char current = text.charAt(index);
            if (escaping) {
                escaping = false;
                continue;
            }
            if (current == '\\' && inString) {
                escaping = true;
                continue;
            }
            if (current == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            if (current == '{') {
                depth++;
            } else if (current == '}') {
                depth--;
                if (depth == 0) {
                    return text.substring(startIndex, index + 1).trim();
                }
            }
        }
        return "";
    }

    private String normalizeStructuredSentence(String value) {
        String normalized = sanitizeReview(value);
        if (normalized.isEmpty()) {
            return "";
        }
        if (!endsWithTerminalPunctuation(normalized)) {
            normalized = normalized + ".";
        }
        return normalized;
    }

    private String normalizeCandidateReview(String review) {
        String normalized = sanitizeReview(review);
        if (normalized.isEmpty()) {
            return "";
        }
        if (!endsWithTerminalPunctuation(normalized)
                && isDisplayableReview(normalized)
                && !endsWithIncompleteToken(normalized)) {
            normalized = normalized + ".";
        }
        return normalized;
    }

    private String extractErrorMessage(int responseCode, String rawResponse) {
        try {
            JSONObject root = new JSONObject(rawResponse);
            JSONObject error = root.optJSONObject("error");
            if (error != null) {
                String message = error.optString("message", "");
                if (!message.trim().isEmpty()) {
                    return mapToVietnameseError(responseCode, message.trim());
                }
            }
        } catch (Exception ignored) {
            // Ignore parsing errors and use a localized fallback.
        }
        return mapToVietnameseError(responseCode, "");
    }

    private String mapToVietnameseError(int responseCode, String rawMessage) {
        String normalized = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.US);
        if (responseCode == 429
                || normalized.contains("high demand")
                || normalized.contains("resource exhausted")
                || normalized.contains("quota")
                || normalized.contains("rate limit")) {
            return BUSY_ERROR;
        }
        if (responseCode == 401
                || responseCode == 403
                || normalized.contains("api key not valid")
                || normalized.contains("permission denied")
                || normalized.contains("forbidden")) {
            return INVALID_KEY_ERROR;
        }
        if (normalized.contains("billing")) {
            return "Tài khoản Gemini hiện chưa sẵn sàng để gọi API.";
        }
        return GENERIC_ERROR;
    }

    private boolean containsVietnameseChars(String text) {
        String value = text == null ? "" : text;
        return value.matches(".*[ăâđêôơưáàảãạắằẳẵặấầẩẫậéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹỵĂÂĐÊÔƠƯÁÀẢÃẠẮẰẲẴẶẤẦẨẪẬÉÈẺẼẸẾỀỂỄỆÍÌỈĨỊÓÒỎÕỌỐỒỔỖỘỚỜỞỠỢÚÙỦŨỤỨỪỬỮỰÝỲỶỸỴ].*");
    }

    private boolean isAcceptableReview(String review) {
        if (review == null) {
            return false;
        }
        String normalized = review.trim();
        if (normalized.length() < MIN_REVIEW_CHAR_COUNT_STRICT) {
            return false;
        }
        if (countWords(normalized) < 10) {
            return false;
        }
        int sentenceCount = countSentences(normalized);
        if (sentenceCount < 2 || sentenceCount > 3) {
            return false;
        }
        return endsWithTerminalPunctuation(normalized);
    }

    private boolean isDisplayableReview(String review) {
        if (review == null) {
            return false;
        }
        String normalized = review.trim();
        return normalized.length() >= MIN_REVIEW_CHAR_COUNT
                && countWords(normalized) >= 8
                && countSentences(normalized) >= 2;
    }

    private boolean needsRepair(String review) {
        return !isAcceptableReview(review);
    }

    private boolean looksEnglish(String text) {
        String normalized = " " + (text == null ? "" : text).toLowerCase(Locale.US) + " ";
        int hits = 0;
        String[] englishMarkers = {
                " the ", " and ", " you ", " your ", " try ", " improve ",
                " player ", " speed ", " accuracy ", " should ", " keep ",
                " while ", " but ", " focus ", " timing ", " demand "
        };
        for (String marker : englishMarkers) {
            if (normalized.contains(marker)) {
                hits++;
            }
        }
        return hits >= 2;
    }

    private boolean isBoilerplateResponse(String text) {
        String normalized = " " + (text == null ? "" : text).trim().toLowerCase(Locale.US) + " ";
        if (normalized.trim().isEmpty()) {
            return false;
        }
        return normalized.contains("here is the json")
                || normalized.contains("json requested")
                || normalized.contains("requested json")
                || normalized.contains("valid json")
                || normalized.contains("```json")
                || normalized.matches(".*\\bjson\\b.*") && countWords(normalized) <= 8;
    }

    private int countWords(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        return normalized.split("\\s+").length;
    }

    private int countSentences(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return 0;
        }
        String[] parts = normalized.split("(?<=[.!?])\\s+");
        int count = 0;
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                count++;
            }
        }
        return count;
    }

    private boolean hasImprovementCue(String text) {
        String normalized = " " + (text == null ? "" : text).toLowerCase(Locale.US) + " ";
        String[] cues = {
                " cần ", " nên ", " hãy ", " cải thiện ", " giảm ", " tăng ", " ổn định ",
                " tập trung ", " chú ý ", " thử ", " ưu tiên ", " chậm lại ", " nhanh hơn "
        };
        for (String cue : cues) {
            if (normalized.contains(cue)) {
                return true;
            }
        }
        return false;
    }

    private boolean endsWithTerminalPunctuation(String text) {
        return text.endsWith(".") || text.endsWith("!") || text.endsWith("?");
    }

    private boolean looksTruncated(String text) {
        String normalized = text == null ? "" : text.trim();
        if (normalized.isEmpty()) {
            return true;
        }
        if (endsWithTerminalPunctuation(normalized)) {
            return false;
        }
        return normalized.length() < 40 || countWords(normalized) < 8;
    }

    private boolean endsWithIncompleteToken(String text) {
        String normalized = text == null ? "" : text.trim().toLowerCase(Locale.US);
        if (normalized.isEmpty()) {
            return true;
        }
        String[] hardCutTokens = {
                "v", "va", "và", "nhưng", "cần", "để", "khi", "với", "về", "ở",
                "là", "một", "nhờ", "do", "vì", "nên", "đã", "có", "theo", "cho"
        };
        for (String token : hardCutTokens) {
            if (normalized.endsWith(" " + token) || normalized.equals(token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds deterministic feedback from the AI_METRICS block.
     *
     * Quiz is the default format. Memory adds game_type=memory and is routed to
     * a game-specific fallback so wording and metrics stay correct.
     */
    private String buildLocalFallbackReview(String prompt) {
        String gameType = extractMetricString(prompt, "game_type");
        if ("memory".equalsIgnoreCase(gameType)) {
            return buildMemoryLocalFallbackReview(prompt);
        }

        int totalQuestions = extractMetricInt(prompt, "total_questions");
        int correctCount = extractMetricInt(prompt, "correct_count");
        int accuracyPercent = extractMetricInt(prompt, "accuracy_percent");
        int score = extractMetricInt(prompt, "score");
        int bestCombo = extractMetricInt(prompt, "best_combo");

        if (totalQuestions < 0) {
            totalQuestions = 0;
        }
        if (correctCount < 0) {
            correctCount = 0;
        }
        if (accuracyPercent < 0) {
            accuracyPercent = 0;
        }
        if (score < 0) {
            score = 0;
        }
        if (bestCombo < 0) {
            bestCombo = 0;
        }

        if (totalQuestions <= 0 && correctCount <= 0 && score <= 0) {
            return "";
        }

        String praise;
        if (accuracyPercent >= 70) {
            praise = String.format(Locale.getDefault(),
                    "Bạn giữ nhịp khá tốt với %d/%d câu đúng và đạt %d điểm.",
                    correctCount,
                    Math.max(1, totalQuestions),
                    score);
        } else if (correctCount > 0) {
            praise = String.format(Locale.getDefault(),
                    "Bạn đã xử lý đúng %d/%d câu, đây là nền tảng ổn để tiếp tục cải thiện.",
                    correctCount,
                    Math.max(1, totalQuestions));
        } else {
            praise = String.format(Locale.getDefault(),
                    "Ván này chưa có câu đúng, nhưng bạn đã hoàn thành đủ %d câu để có dữ liệu luyện tập.",
                    Math.max(1, totalQuestions));
        }

        String improvement;
        if (accuracyPercent < 50) {
            improvement = "Điểm cần cải thiện là độ chính xác, hãy đọc kỹ đáp án trước khi gửi và ưu tiên chắc câu dễ.";
        } else if (bestCombo <= 1) {
            improvement = "Bạn nên tập giữ chuỗi đúng liên tiếp bằng cách giảm tốc độ chọn ở những câu còn phân vân.";
        } else {
            improvement = "Bạn có thể tăng điểm thêm bằng cách giữ độ chính xác hiện tại nhưng trả lời nhanh hơn ở các câu quen thuộc.";
        }

        return praise + " " + improvement;
    }

    /**
     * Local fallback for Memory rounds, based on pairs, attempts, streak, score,
     * and win state. It prevents the UI from showing an empty AI review when
     * Gemini returns boilerplate or an incomplete answer.
     */
    private String buildMemoryLocalFallbackReview(String prompt) {
        int totalPairs = extractMetricInt(prompt, "total_pairs");
        int matchedPairs = extractMetricInt(prompt, "matched_pairs");
        int pairAttempts = extractMetricInt(prompt, "pair_attempts");
        int accuracyPercent = extractMetricInt(prompt, "accuracy_percent");
        int score = extractMetricInt(prompt, "score");
        int bestStreak = extractMetricInt(prompt, "best_streak");
        int elapsedMs = extractMetricInt(prompt, "elapsed_ms");
        int won = extractMetricInt(prompt, "won");

        if (totalPairs < 0) {
            totalPairs = 0;
        }
        if (matchedPairs < 0) {
            matchedPairs = 0;
        }
        if (pairAttempts < 0) {
            pairAttempts = 0;
        }
        if (accuracyPercent < 0) {
            accuracyPercent = 0;
        }
        if (score < 0) {
            score = 0;
        }
        if (bestStreak < 0) {
            bestStreak = 0;
        }
        if (elapsedMs < 0) {
            elapsedMs = 0;
        }

        if (totalPairs <= 0 && matchedPairs <= 0 && pairAttempts <= 0 && score <= 0) {
            return "";
        }

        String praise;
        if (totalPairs > 0 && matchedPairs >= totalPairs) {
            praise = String.format(Locale.getDefault(),
                    "Bạn đã ghép đúng toàn bộ %d cặp và giữ được chuỗi tốt nhất %d, đây là dấu hiệu nhớ vị trí khá chắc.",
                    totalPairs,
                    bestStreak);
        } else if (matchedPairs > 0) {
            praise = String.format(Locale.getDefault(),
                    "Bạn đã ghép đúng %d/%d cặp và giữ được chuỗi tốt nhất %d, nền tảng ghi nhớ đã có nhưng chưa thật ổn định.",
                    matchedPairs,
                    Math.max(1, totalPairs),
                    bestStreak);
        } else {
            praise = String.format(Locale.getDefault(),
                    "Ván này chưa ghép đúng cặp nào, nhưng bạn vẫn có %d lượt thử để tạo dữ liệu luyện tập.",
                    Math.max(1, pairAttempts));
        }

        String improvement;
        if (accuracyPercent < 50) {
            improvement = "Điểm cần cải thiện là độ chính xác và cách dò vị trí, hãy chậm lại một nhịp sau mỗi lượt lật để ghi nhớ tốt hơn.";
        } else if (bestStreak <= 1) {
            improvement = "Bạn nên cố giữ chuỗi ghép liên tiếp lâu hơn bằng cách ưu tiên các ô đã lộ thông tin thay vì chọn vội.";
        } else if (pairAttempts > Math.max(1, totalPairs)) {
            improvement = "Bạn có thể tiết kiệm lượt đoán bằng cách thu hẹp phạm vi các cặp khả nghi trước khi lật tiếp.";
        } else {
            improvement = "Bạn đã đi đúng hướng, chỉ cần giữ nhịp ổn định hơn ở các lượt còn phân vân để tăng điểm và tốc độ.";
        }

        String closing;
        if (won > 0) {
            closing = "Nếu giữ nhịp này, bạn sẽ cải thiện rõ ở các ván sau.";
        } else if (elapsedMs > 0) {
            closing = "Càng bình tĩnh ở những lượt đầu, bạn sẽ càng nhớ vị trí tốt hơn về sau.";
        } else {
            closing = "";
        }

        return closing.isEmpty() ? praise + " " + improvement : praise + " " + improvement + " " + closing;
    }

    private int extractMetricInt(String prompt, String key) {
        if (prompt == null || key == null || key.trim().isEmpty()) {
            return -1;
        }
        String marker = key.trim() + "=";
        int markerIndex = prompt.indexOf(marker);
        if (markerIndex < 0) {
            return -1;
        }
        int valueStart = markerIndex + marker.length();
        while (valueStart < prompt.length() && !Character.isDigit(prompt.charAt(valueStart))) {
            valueStart++;
        }
        if (valueStart >= prompt.length()) {
            return -1;
        }
        int valueEnd = valueStart;
        while (valueEnd < prompt.length() && Character.isDigit(prompt.charAt(valueEnd))) {
            valueEnd++;
        }
        try {
            return Integer.parseInt(prompt.substring(valueStart, valueEnd));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private String extractMetricString(String prompt, String key) {
        if (prompt == null || key == null || key.trim().isEmpty()) {
            return "";
        }
        String marker = key.trim() + "=";
        int markerIndex = prompt.indexOf(marker);
        if (markerIndex < 0) {
            return "";
        }
        int valueStart = markerIndex + marker.length();
        int valueEnd = valueStart;
        while (valueEnd < prompt.length()) {
            char current = prompt.charAt(valueEnd);
            if (current == '\n' || current == '\r') {
                break;
            }
            valueEnd++;
        }
        return prompt.substring(valueStart, valueEnd).trim();
    }

    private String buildLocalizationPrompt(String review) {
        return STRUCTURED_JSON_PROMPT + " " + STRUCTURED_REVIEW_REQUIREMENTS + " " + STRUCTURED_REVIEW_TONE_GUIDE + "\n\n" +
                "Rewrite the following review into natural Vietnamese with accents while keeping the same meaning:\n" +
                review;
    }

    private String buildStructurePrompt(String originalPrompt, String currentReview) {
        return STRUCTURED_JSON_PROMPT + " " + STRUCTURED_REVIEW_REQUIREMENTS + " " + STRUCTURED_REVIEW_TONE_GUIDE + "\n\n" +
                "Current review that needs to be rewritten:\n" + currentReview + "\n\n" +
                "Round context:\n" + originalPrompt;
    }

    private String buildRecoveryPrompt(String originalPrompt, String partialReview) {
        return STRUCTURED_JSON_PROMPT + " " + STRUCTURED_REVIEW_REQUIREMENTS + " " + STRUCTURED_REVIEW_TONE_GUIDE + "\n\n" +
                "The review below is cut off or too short. Rewrite it into a complete review that follows the required structure.\n\n" +
                "Broken review:\n" + partialReview + "\n\n" +
                "Round context:\n" + originalPrompt;
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 120 ? normalized : normalized.substring(0, 117) + "...";
    }

    private String readFully(InputStream inputStream) throws Exception {
        if (inputStream == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        }
        return builder.toString();
    }

    private void postSuccess(Callback callback, String review) {
        mainHandler.post(() -> callback.onSuccess(review));
    }

    private void postError(Callback callback, String message) {
        mainHandler.post(() -> callback.onError(message));
    }

    private static final class ReviewException extends Exception {
        ReviewException(String message) {
            super(message == null || message.trim().isEmpty() ? GENERIC_ERROR : message.trim());
        }
    }
}
