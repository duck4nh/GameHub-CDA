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

public class GeminiReviewService {
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

    private String executePrompt(String apiKey, String prompt) throws Exception {
        String rawResponse = performRequest(apiKey, prompt);
        String review = normalizeCandidateReview(parseReview(rawResponse));
        if (review.isEmpty()) {
            Log.w(TAG, "Gemini returned empty review for initial prompt.");
            return "";
        }

        if (needsRepair(review)) {
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
        return "";
    }

    private String performRequest(String apiKey, String prompt) throws Exception {
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

            byte[] body = buildRequestBody(prompt).getBytes(StandardCharsets.UTF_8);
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

        try {
            JSONObject root = new JSONObject(candidate);
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
            return sanitizeReview(candidate);
        }
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
