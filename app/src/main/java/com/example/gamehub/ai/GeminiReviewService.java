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

    private static final String MODEL_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";
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
                postError(callback, exception.getMessage());
            } catch (Exception exception) {
                postError(callback, GENERIC_ERROR);
            }
        });
    }

    private String executePrompt(String apiKey, String prompt) throws Exception {
        String rawResponse = performRequest(apiKey, prompt);
        String review = parseReview(rawResponse);
        if (review.isEmpty()) {
            Log.w(TAG, "Gemini returned empty review for initial prompt.");
            return "";
        }

        if (needsRepair(review)) {
            String repaired = parseReview(performRequest(apiKey, buildRecoveryPrompt(prompt, review)));
            if (isAcceptableReview(repaired)) {
                review = repaired;
            }
        }

        if (looksEnglish(review) && !containsVietnameseChars(review)) {
            String localizedReview = parseReview(performRequest(apiKey, buildLocalizationPrompt(review)));
            if (isAcceptableReview(localizedReview) && containsVietnameseChars(localizedReview)) {
                review = localizedReview;
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
        systemParts.put(new JSONObject().put("text", VIETNAMESE_SYSTEM_PROMPT));
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
        generationConfig.put("responseMimeType", "text/plain");
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
        return sanitizeReview(builder.toString());
    }

    private String sanitizeReview(String text) {
        String sanitized = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        sanitized = sanitized.replaceAll("^(?i)(nhận xét|feedback|review)[:\\-\\s]*", "");
        return sanitized.trim();
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
        if (normalized.length() < MIN_REVIEW_CHAR_COUNT) {
            return false;
        }
        if (countWords(normalized) < 6) {
            return false;
        }
        return endsWithTerminalPunctuation(normalized);
    }

    private boolean isDisplayableReview(String review) {
        if (review == null) {
            return false;
        }
        String normalized = review.trim();
        return normalized.length() >= 24 && countWords(normalized) >= 5;
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

    private String buildLocalizationPrompt(String review) {
        return "Hãy viết lại đoạn nhận xét sau hoàn toàn bằng tiếng Việt có dấu, giữ nguyên ý chính, " +
                "giữ giọng khách quan, gói gọn trong 2 đến 3 câu, không dùng gạch đầu dòng và phải kết thúc bằng dấu câu.\n\n" +
                review;
    }

    private String buildRecoveryPrompt(String originalPrompt, String partialReview) {
        return "Phần nhận xét sau đang bị cụt hoặc quá ngắn. " +
                "Hãy viết lại thành 2 đến 3 câu tiếng Việt có dấu, đủ ý, tự nhiên, khách quan, không gạch đầu dòng, " +
                "ít nhất 28 ký tự và phải kết thúc bằng dấu câu.\n\n" +
                "Nhận xét đang bị lỗi:\n" + partialReview + "\n\n" +
                "Ngữ cảnh ván chơi:\n" + originalPrompt;
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
