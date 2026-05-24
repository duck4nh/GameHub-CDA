# GameHub - Technical Handover

## 1. Chức năng được phân công

- Quiz offline theo bộ câu hỏi tích hợp sẵn.
- Memory game offline theo level lưu trong Room.
- AI nhận xét cuối ván cho Quiz và Memory bằng Gemini.
- Lưu lịch sử ván chơi cục bộ và đồng bộ lên Firebase khi có mạng.

## 2. Kiến trúc chi tiết hệ thống

### Các thành phần chính

- `QuizActivity` và `MemoryGameActivity`: lớp UI, nhận thao tác người chơi, hiển thị timer, điểm, kết quả và AI review.
- `QuizViewModel` và `MemoryViewModel`: giữ state game, điều khiển luật chơi, đếm thời gian, lưu lịch sử, tạo prompt AI.
- `QuizManager`: engine luật Quiz, tính điểm, combo, đúng/sai, trạng thái thắng.
- `GeminiReviewService`: gọi Gemini, parse JSON, retry plain text, fallback local khi AI trả về nội dung không dùng được.
- `GameRepository`: facade dữ liệu, gom Room, asset import, Firebase, WorkManager.
- `AppDatabase` + DAO + entity: lưu quiz questions, memory levels, history, friends, sudoku state.
- `FirebaseManager` + `SyncWorker`: đồng bộ history lên Firestore theo nền.

### Kết nối luồng

- `Activity` -> `ViewModel` -> `Repository` -> `Room / Asset / Firebase`.
- `Activity` -> `GeminiReviewService` để tạo nhận xét AI sau khi kết thúc ván.
- `Repository` -> `WorkManager` -> `SyncWorker` khi còn bản ghi history chưa đồng bộ.
- `QuizAssetImporter` đọc file SQLite trong `assets/databases/` và nạp vào `Quiz_Questions`.
- `DatabaseSeeder` tạo và đồng bộ bảng `Memory_Levels`.

## 3. Code đáp ứng chức năng

### Quiz

- `app/src/main/java/com/example/gamehub/games/quiz/QuizActivity.java`
  - Render màn setup/gameplay/result.
  - Gọi `QuizViewModel.submitAnswer()` và `advanceAfterFeedback()`.
  - Gọi `GeminiReviewService.requestReview()` qua `ensureQuizAiReview()`.

- `app/src/main/java/com/example/gamehub/games/quiz/QuizViewModel.java`
  - Load data quiz từ repository.
  - `startGame()`, `tickQuestion()`, `submitAnswer()`, `timeoutCurrentQuestion()`.
  - `buildAiReviewPrompt()` kèm block `AI_METRICS`.
  - `FEEDBACK_DELAY_MS = 2500L`.

- `app/src/main/java/com/example/gamehub/games/quiz/QuizManager.java`
  - `answerCurrentQuestion()`: xác định đúng/sai.
  - `calculateScore()`: điểm theo độ khó, thời gian còn lại, combo.
  - `isWin()`: đạt ngưỡng đúng 60%.

### Memory

- `app/src/main/java/com/example/gamehub/games/memory/MemoryGameActivity.java`
  - Render level list, board, result, pause.
  - `onCardClicked()` ghi log thao tác và xử lý mismatch delay.
  - `buildMemoryReviewPrompt()` + `appendAiMetricsBlock()`.

- `app/src/main/java/com/example/gamehub/games/memory/MemoryViewModel.java`
  - `initialize()` load level từ Room.
  - `startLevel()` reset session.
  - `onCardSelected()` xử lý luật ghép cặp.
  - `finishGame()` lưu history và mở khóa level tiếp theo.
  - `buildSmartArrangement()` giảm xác suất cặp trùng liền kề.

### AI

- `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java`
  - Gọi API Gemini `generateContent`.
  - Parse JSON structured review.
  - Retry plain-text nếu JSON không đạt.
  - Local fallback theo `AI_METRICS`, gồm nhánh riêng cho Memory.

### Dữ liệu cục bộ

- `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java`
  - Room database `gamehub.db`.
  - Bảng: `Quiz_Questions`, `Memory_Levels`, `Local_History`, `Local_Friends`, `Sudoku_Boards`, `Sudoku_Game_State`, `Sudoku_Stats`.

- `app/src/main/java/com/example/gamehub/data/local/QuizAssetImporter.java`
  - Import câu hỏi quiz từ asset DB:
    - `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`

- `app/src/main/java/com/example/gamehub/data/local/DatabaseSeeder.java`
  - Seed bảng Memory và Sudoku.

- DAO liên quan:
  - `QuizDao`
  - `MemoryDao`
  - `HistoryDao`

## 4. Bảng trong CSDL

- `Quiz_Questions`: câu hỏi quiz offline.
- `Memory_Levels`: level, số hàng/cột, thời gian, best time, trạng thái mở khóa.
- `Local_History`: lịch sử kết quả từng ván.
- `Local_Friends`: dữ liệu bạn bè cục bộ.
- `Sudoku_Boards`: bộ đề Sudoku.
- `Sudoku_Game_State`: trạng thái ván Sudoku đang chơi.
- `Sudoku_Stats`: thống kê Sudoku.

## 5. API gọi ngoài

- Gemini:
  - Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent`
  - Dùng `BuildConfig.GEMINI_API_KEY` lấy từ `local.properties`.
- Firebase:
  - Auth
  - Firestore
  - Đồng bộ history và dữ liệu user
- WorkManager:
  - Chạy `SyncWorker` để đẩy bản ghi chưa đồng bộ lên Firebase.
- Glide:
  - Tải avatar, ảnh câu hỏi, ảnh banner, icon.

## 6. Hướng dẫn cài đặt và triển khai

1. Mở project bằng Android Studio.
2. Tạo file `local.properties` và khai báo:
   - `sdk.dir=...`
   - `GEMINI_API_KEY=...`
3. Kiểm tra `app/google-services.json` còn hợp lệ nếu dùng Firebase.
4. Sync Gradle.
5. Chạy app trên máy ảo hoặc thiết bị thật.
6. Nếu build lại seed data, giữ nguyên file asset:
   - `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`
   - `app/src/main/assets/sql/quiz_questions_500_vi_entity_images.sql`

## 7. Lưu ý khi triển khai

- Không commit `local.properties` hoặc API key thật.
- Không xóa asset DB quiz nếu muốn game chạy offline đầy đủ.
- `AppDatabase` đang dùng `fallbackToDestructiveMigration()`, nên thay đổi schema phải kiểm tra lại dữ liệu.
- `GeminiReviewService` có fallback nội bộ, nhưng vẫn cần mạng và API key để có nhận xét AI thật.
- Thay đổi nội dung AI review nên cập nhật cả prompt và local fallback để không bị rỗng ở màn kết quả.

## 8. File cá nhân thực hiện

- `app/src/main/java/com/example/gamehub/games/quiz/QuizActivity.java`
- `app/src/main/java/com/example/gamehub/games/quiz/QuizViewModel.java`
- `app/src/main/java/com/example/gamehub/games/quiz/QuizManager.java`
- `app/src/main/java/com/example/gamehub/games/memory/MemoryGameActivity.java`
- `app/src/main/java/com/example/gamehub/games/memory/MemoryViewModel.java`
- `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java`
- `app/src/main/java/com/example/gamehub/data/repository/GameRepository.java`
- `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java`
- `app/src/main/java/com/example/gamehub/data/local/DatabaseSeeder.java`
- `app/src/main/java/com/example/gamehub/data/local/QuizAssetImporter.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/QuizQuestion.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/MemoryLevel.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/LocalHistory.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/QuizDao.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/MemoryDao.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/HistoryDao.java`

