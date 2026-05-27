# GameHub - Module Game Quiz & Memory

README này tập trung vào phần chức năng cá nhân thực hiện trong dự án GameHub:

- Xây dựng Game Quiz & Memory.
- Xây dựng DB câu hỏi bằng SQLite/Room.
- Thiết kế giao diện chơi game, trạng thái thắng/thua và animation.
- Tích hợp nhận xét AI cho kết quả chơi.

Các chức năng khác của GameHub như đăng nhập, hồ sơ, bạn bè, chat, leaderboard hoặc Sudoku chỉ được nhắc đến khi có liên quan trực tiếp đến phần Quiz/Memory.

Tài liệu kỹ thuật chi tiết, bao gồm phân tích lớp, hàm, bảng CSDL, API gọi ngoài và các đoạn code liên quan, nằm tại:

- `docs/quiz_memory_technical_documentation.md`

## Mục lục

- [1. Tổng quan chức năng cá nhân thực hiện](#1-tổng-quan-chức-năng-cá-nhân-thực-hiện)
- [2. Kiến trúc triển khai](#2-kiến-trúc-triển-khai)
- [3. Cơ sở dữ liệu SQLite/Room](#3-cơ-sở-dữ-liệu-sqliteroom)
- [4. Thiết kế giao diện và animation](#4-thiết-kế-giao-diện-và-animation)
- [5. Tích hợp nhận xét AI](#5-tích-hợp-nhận-xét-ai)
- [6. Danh sách file liên quan đến phần cá nhân thực hiện](#6-danh-sách-file-liên-quan-đến-phần-cá-nhân-thực-hiện)
- [7. Cấu hình và chạy dự án](#7-cấu-hình-và-chạy-dự-án)
- [8. Ghi chú kỹ thuật](#8-ghi-chú-kỹ-thuật)
- [9. Thông tin GitHub](#9-thông-tin-github)

## 1. Tổng quan chức năng cá nhân thực hiện

### 1.1. Game Quiz

Game Quiz là trò chơi trả lời câu hỏi trắc nghiệm theo chủ đề, độ khó và số lượng câu hỏi do người chơi chọn.

Các chức năng chính:

- Chọn chủ đề câu hỏi từ dữ liệu Room.
- Chọn độ khó: tất cả, dễ, trung bình, khó.
- Chọn số câu hỏi trong một ván.
- Hiển thị câu hỏi, ảnh minh họa nếu có, 4 đáp án A/B/C/D.
- Đếm ngược thời gian từng câu, mặc định 15 giây.
- Chấm đúng/sai, tính điểm, combo và độ chính xác.
- Hiển thị feedback sau mỗi câu: đúng, sai hoặc hết giờ.
- Kết thúc ván, hiển thị điểm, số câu đúng, độ chính xác, thời gian chơi và thành tích tốt nhất.
- Lưu lịch sử chơi vào Room và kích hoạt đồng bộ Firebase khi đủ điều kiện.
- Gọi AI để nhận xét kết quả cuối ván.

Luật chính:

- Mỗi câu có `15_000 ms`.
- Trả lời đúng được cộng điểm nền, điểm thưởng độ khó, điểm thưởng thời gian còn lại và điểm thưởng combo.
- Trả lời sai hoặc hết giờ sẽ reset combo.
- Người chơi thắng khi tỷ lệ đúng đạt từ `60%` trở lên.

### 1.2. Game Memory

Game Memory là trò chơi ghi nhớ vị trí thẻ, lật và ghép các cặp giống nhau theo từng level.

Các chức năng chính:

- Hiển thị danh sách level Memory.
- Quản lý trạng thái level đã mở khóa hoặc bị khóa.
- Hiển thị kích thước board, best time và thông tin người chơi.
- Tạo board thẻ theo số hàng/cột của level.
- Xử lý lật thẻ, ghép đúng, ghép sai và khóa board tạm thời khi mismatch.
- Tính điểm theo số cặp ghép đúng, thời gian còn lại và streak.
- Hiển thị màn hình kết quả thắng/thua.
- Cập nhật best time và mở khóa level tiếp theo khi thắng.
- Lưu lịch sử chơi vào Room và kích hoạt đồng bộ Firebase.
- Gọi AI để nhận xét kết quả cuối ván.

Luật chính:

- Mỗi level có số cặp thẻ và thời gian giới hạn riêng.
- Lần lật đầu tiên chỉ mở thẻ.
- Lần lật thứ hai tăng số lượt đoán.
- Hai thẻ cùng `identifier` được tính là một cặp đúng.
- Hai thẻ khác nhau sẽ bị úp lại sau một khoảng delay.
- Ghép hết toàn bộ cặp trước khi hết giờ thì thắng.
- Hết giờ trước khi ghép hết cặp thì thua.

## 2. Kiến trúc triển khai

Module Quiz & Memory được tổ chức theo hướng MVVM:

| Tầng | File chính | Vai trò |
| --- | --- | --- |
| UI | `QuizActivity`, `MemoryGameActivity`, XML layout | Render giao diện, nhận thao tác người chơi, chạy animation, phát âm thanh, gọi AI review |
| State/Logic | `QuizViewModel`, `MemoryViewModel`, `QuizManager` | Quản lý trạng thái ván chơi, timer, luật chơi, điểm số, kết quả thắng/thua |
| Data | `GameRepository` | Điều phối Room, import dữ liệu, lưu lịch sử, cập nhật Memory level, đồng bộ Firebase |
| Room Database | `AppDatabase`, DAO, Entity | Lưu câu hỏi Quiz, level Memory và lịch sử chơi local |
| External/Utility | `GeminiReviewService`, `FirebaseManager`, `ImageLoader` | Gọi Gemini, đồng bộ Firestore, tải ảnh câu hỏi |

Luồng tổng quát:

```text
Activity -> ViewModel -> GameRepository -> Room DAO/Entity
Activity -> GeminiReviewService -> Gemini API
GameRepository -> FirebaseManager -> Firestore
```

## 3. Cơ sở dữ liệu SQLite/Room

Database local dùng Room, file SQLite nội bộ là `gamehub.db`.

File cấu hình chính:

- `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java`

Database hiện tại có `version = 6`. Các bảng liên quan trực tiếp đến phần cá nhân thực hiện:

| Bảng | Entity | DAO | Mục đích |
| --- | --- | --- | --- |
| `Quiz_Questions` | `QuizQuestion` | `QuizDao` | Lưu câu hỏi trắc nghiệm, đáp án, ảnh, category và difficulty |
| `Memory_Levels` | `MemoryLevel` | `MemoryDao` | Lưu cấu hình level Memory, số hàng/cột, thời gian, best time, trạng thái mở khóa |
| `Local_History` | `LocalHistory` | `HistoryDao` | Lưu kết quả chơi Quiz/Memory và trạng thái đồng bộ Firebase |

### 3.1. DB câu hỏi Quiz

Câu hỏi Quiz được đóng gói sẵn bằng SQLite asset:

- `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`
- `app/src/main/assets/sql/quiz_questions_500_vi_entity_images.sql`

Luồng import:

1. `GameRepository.ensureLocalDataReady()` kiểm tra bảng `Quiz_Questions`.
2. Nếu Room chưa có câu hỏi, `QuizAssetImporter.readQuestions(...)` copy asset DB vào cache.
3. `QuizAssetImporter` đọc bảng `Quiz_Questions` từ SQLite asset.
4. Dữ liệu được map sang entity `QuizQuestion`.
5. `QuizDao.insertAll(...)` insert danh sách câu hỏi vào Room.

Các trường dữ liệu chính của `QuizQuestion`:

- `id`: mã câu hỏi.
- `category`: chủ đề.
- `question`: nội dung câu hỏi.
- `link_image`: URL ảnh minh họa.
- `opt_a`, `opt_b`, `opt_c`, `opt_d`: bốn đáp án.
- `correct_ans`: đáp án đúng.
- `difficulty`: độ khó.

### 3.2. DB level Memory

Level Memory không dùng file asset riêng. Dữ liệu được sinh tự động trong:

- `app/src/main/java/com/example/gamehub/data/local/DatabaseSeeder.java`

`DatabaseSeeder` tạo 30 level Memory, mỗi level gồm:

- `levelId`: số level.
- `rowCount`: số hàng.
- `columnCount`: số cột.
- `timeLimitSec`: thời gian giới hạn.
- `bestTimeMs`: thời gian tốt nhất.
- `isUnlocked`: trạng thái mở khóa.

Level 1 được mở khóa mặc định. Khi người chơi thắng một level, `GameRepository.completeMemoryLevel(...)` cập nhật best time và mở khóa level tiếp theo.

### 3.3. Lịch sử chơi

Quiz và Memory dùng chung bảng `Local_History`.

Dữ liệu lưu gồm:

- Tên game: `quiz` hoặc `memory`.
- Trạng thái: `won` hoặc `lost`.
- Điểm số.
- Thời gian chơi.
- Ngày chơi.
- Trạng thái đồng bộ Firebase.
- Chi tiết level Memory nếu có.
- Số lượt đoán với Memory nếu có.

Lịch sử được lưu local trước để app vẫn hoạt động khi offline. Sau đó repository kích hoạt đồng bộ Firebase nếu có mạng và có tài khoản đăng nhập.

## 4. Thiết kế giao diện và animation

### 4.1. Giao diện Quiz

Các màn hình Quiz được tách thành nhiều layout:

- `game_quiz.xml`: layout container của màn Quiz.
- `view_quiz_setup.xml`: màn chọn chủ đề, độ khó, số câu.
- `view_quiz_gameplay.xml`: màn chơi chính.
- `view_quiz_pause.xml`: màn tạm dừng.
- `view_quiz_result.xml`: màn kết quả.

Các trạng thái đáp án được thể hiện bằng drawable:

- `bg_quiz_option_default.xml`: đáp án mặc định.
- `bg_quiz_option_selected.xml`: đáp án đang chọn.
- `bg_quiz_option_correct.xml`: đáp án đúng.
- `bg_quiz_option_wrong.xml`: đáp án sai.

`QuizActivity` chịu trách nhiệm render giao diện theo state từ `QuizViewModel`, cập nhật timer, điểm, combo, ảnh minh họa và feedback sau mỗi câu. Ảnh câu hỏi được tải qua `ImageLoader` để tránh block UI thread và có cache bằng `LruCache`.

### 4.2. Giao diện Memory

Các màn hình Memory được tách thành nhiều layout:

- `game_memory.xml`: layout container của màn Memory.
- `view_memory_setup.xml`: màn chọn level.
- `view_memory_gameplay.xml`: màn chơi chính.
- `view_memory_pause.xml`: màn tạm dừng.
- `view_memory_result.xml`: màn kết quả.
- `item_memory_card.xml`: item cho từng thẻ trong RecyclerView.

Board Memory dùng `RecyclerView` kết hợp `GridLayoutManager`. Số cột được lấy từ `MemoryLevel.columnCount`.

Animation lật thẻ nằm trong:

- `app/src/main/java/com/example/gamehub/games/memory/MemoryBoardAdapter.java`

Cơ chế animation:

1. Xoay thẻ theo trục Y đến 90 độ.
2. Đổi mặt thẻ ở giữa animation.
3. Xoay tiếp về 0 độ.

Khi hai thẻ không khớp, `MemoryViewModel` khóa board tạm thời. Sau `MISMATCH_DELAY_MS`, activity gọi `resolveMismatch()` để úp lại hai thẻ và mở khóa board.

## 5. Tích hợp nhận xét AI

Nhận xét AI được tích hợp ở màn kết quả của Quiz và Memory.

File service chính:

- `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java`

Model đang dùng:

```text
gemini-2.5-flash
```

Endpoint:

```text
https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

API key được đọc từ:

```text
BuildConfig.GEMINI_API_KEY
```

Giá trị này được cấu hình trong `app/build.gradle.kts`, lấy từ `local.properties`.

Luồng AI review:

1. Khi ván chơi kết thúc, ViewModel tạo prompt từ thống kê thật và log thao tác.
2. Activity gọi `GeminiReviewService.requestReview(...)`.
3. Service gửi request đến Gemini với JSON schema.
4. Service parse phản hồi, kiểm tra độ dài, số câu và ngôn ngữ.
5. Nếu phản hồi không đạt, service thử prompt fallback.
6. Nếu Gemini vẫn lỗi hoặc trả nội dung không dùng được, service tạo nhận xét local từ khối `AI_METRICS`.

Quiz tạo prompt trong:

- `QuizViewModel.buildAiReviewPrompt()`

Memory tạo prompt và gọi AI trong:

- `MemoryGameActivity`

`MemoryGameActivity` chịu trách nhiệm tạo/gửi prompt cho AI, còn `MemoryViewModel` cung cấp state và thống kê cuối ván như level, điểm, số cặp đúng, số lượt đoán, thời gian và trạng thái thắng/thua.

## 6. Danh sách file liên quan đến phần cá nhân thực hiện

### 6.1. Game Quiz

| File | Nội dung phụ trách |
| --- | --- |
| `app/src/main/java/com/example/gamehub/games/quiz/QuizActivity.java` | Controller UI của Game Quiz, render setup/gameplay/pause/result, xử lý click đáp án, timer, ảnh minh họa, âm thanh và AI review |
| `app/src/main/java/com/example/gamehub/games/quiz/QuizViewModel.java` | Quản lý state Quiz, tải câu hỏi, điều khiển timer, lưu lịch sử, tạo prompt AI |
| `app/src/main/java/com/example/gamehub/games/quiz/QuizManager.java` | Engine luật chơi Quiz: kiểm tra đáp án, tính điểm, combo, độ chính xác, điều kiện thắng |
| `app/src/main/res/layout/game_quiz.xml` | Container chính của màn Quiz |
| `app/src/main/res/layout/view_quiz_setup.xml` | Giao diện chọn chủ đề, độ khó, số câu |
| `app/src/main/res/layout/view_quiz_gameplay.xml` | Giao diện chơi Quiz |
| `app/src/main/res/layout/view_quiz_pause.xml` | Giao diện tạm dừng Quiz |
| `app/src/main/res/layout/view_quiz_result.xml` | Giao diện kết quả Quiz và nhận xét AI |
| `app/src/main/res/drawable/bg_quiz_option_default.xml` | Nền đáp án mặc định |
| `app/src/main/res/drawable/bg_quiz_option_selected.xml` | Nền đáp án đang chọn |
| `app/src/main/res/drawable/bg_quiz_option_correct.xml` | Nền đáp án đúng |
| `app/src/main/res/drawable/bg_quiz_option_wrong.xml` | Nền đáp án sai |

### 6.2. Game Memory

| File | Nội dung phụ trách |
| --- | --- |
| `app/src/main/java/com/example/gamehub/games/memory/MemoryGameActivity.java` | Controller UI của Game Memory, render level/gameplay/pause/result, xử lý click thẻ, timer, âm thanh, animation và AI review |
| `app/src/main/java/com/example/gamehub/games/memory/MemoryViewModel.java` | Quản lý state Memory, sinh deck, xử lý match/mismatch/win/lose, tính điểm, lưu lịch sử, mở khóa level |
| `app/src/main/java/com/example/gamehub/games/memory/MemoryBoardAdapter.java` | Adapter RecyclerView cho board Memory, render thẻ và animation lật thẻ |
| `app/src/main/java/com/example/gamehub/games/memory/MemoryCard.java` | Model runtime của từng thẻ Memory |
| `app/src/main/res/layout/game_memory.xml` | Container chính của màn Memory |
| `app/src/main/res/layout/view_memory_setup.xml` | Giao diện chọn level Memory |
| `app/src/main/res/layout/view_memory_gameplay.xml` | Giao diện board Memory |
| `app/src/main/res/layout/view_memory_pause.xml` | Giao diện tạm dừng Memory |
| `app/src/main/res/layout/view_memory_result.xml` | Giao diện kết quả Memory và nhận xét AI |
| `app/src/main/res/layout/item_memory_card.xml` | Item layout cho từng thẻ |
| `app/src/main/res/drawable/bg_tile_memory_locked.xml` | Giao diện level bị khóa |
| `app/src/main/res/drawable/bg_tile_memory_unlocked.xml` | Giao diện level đã mở khóa |
| `app/src/main/res/drawable/bg_tile_selected_memory.xml` | Giao diện level đang chọn |

### 6.3. Room, SQLite asset và repository

| File | Nội dung phụ trách |
| --- | --- |
| `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java` | Cấu hình Room database `gamehub.db`, khai báo entity và DAO liên quan |
| `app/src/main/java/com/example/gamehub/data/local/DatabaseSeeder.java` | Seed dữ liệu offline, đặc biệt là 30 level Memory |
| `app/src/main/java/com/example/gamehub/data/local/QuizAssetImporter.java` | Copy và đọc SQLite asset chứa câu hỏi Quiz |
| `app/src/main/java/com/example/gamehub/data/local/dao/QuizDao.java` | Query câu hỏi Quiz theo category, difficulty và lấy random question |
| `app/src/main/java/com/example/gamehub/data/local/dao/MemoryDao.java` | Query/cập nhật level Memory, best time và trạng thái mở khóa |
| `app/src/main/java/com/example/gamehub/data/local/dao/HistoryDao.java` | Lưu, đọc và đánh dấu đồng bộ lịch sử chơi |
| `app/src/main/java/com/example/gamehub/data/local/entities/QuizQuestion.java` | Entity bảng `Quiz_Questions` |
| `app/src/main/java/com/example/gamehub/data/local/entities/MemoryLevel.java` | Entity bảng `Memory_Levels` |
| `app/src/main/java/com/example/gamehub/data/local/entities/LocalHistory.java` | Entity bảng `Local_History` |
| `app/src/main/java/com/example/gamehub/data/repository/GameRepository.java` | Repository điều phối Room, seed/import data, lưu lịch sử, mở khóa Memory level và đồng bộ |
| `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db` | SQLite database câu hỏi Quiz đóng gói trong app |
| `app/src/main/assets/sql/quiz_questions_500_vi_entity_images.sql` | Script SQL tương ứng của bộ câu hỏi Quiz |

### 6.4. AI, Firebase và tiện ích hỗ trợ

| File | Nội dung phụ trách |
| --- | --- |
| `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java` | Gọi Gemini API, validate phản hồi và tạo nhận xét fallback local |
| `app/src/main/java/com/example/gamehub/data/remote/FirebaseManager.java` | Đồng bộ lịch sử chơi Quiz/Memory lên Firestore |
| `app/src/main/java/com/example/gamehub/utils/ImageLoader.java` | Tải và cache ảnh minh họa câu hỏi Quiz |
| `app/build.gradle.kts` | Cấu hình Room, Firebase, Glide, BuildConfig và `GEMINI_API_KEY` |
| `app/src/main/AndroidManifest.xml` | Khai báo quyền Internet và activity Quiz/Memory |

## 7. Cấu hình và chạy dự án

Yêu cầu môi trường:

- Android Studio.
- JDK 11.
- Android SDK, compile SDK 36.
- Thiết bị hoặc emulator Android API 24 trở lên.
- Firebase project có file `app/google-services.json` nếu cần đăng nhập, đồng bộ và leaderboard.
- Firebase Authentication và Firestore Database đã được bật.
- Gemini API key nếu muốn nhận xét AI hoạt động.

Tạo hoặc cập nhật `local.properties` ở thư mục gốc:

```properties
sdk.dir=C:\\Users\\<ten_user>\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=<api_key_gemini>
```

Ngoài `GEMINI_API_KEY`, project cũng hỗ trợ đọc key từ `gemini.api.key` hoặc `GOOGLE_AI_STUDIO_API_KEY` trong `local.properties`.

Build debug:

```powershell
.\gradlew.bat assembleDebug
```

Chạy app:

1. Mở project bằng Android Studio.
2. Sync Gradle.
3. Chọn emulator hoặc thiết bị thật.
4. Run app.
5. Đăng nhập.
6. Vào danh sách game.
7. Chọn Quiz hoặc Memory.

## 8. Ghi chú kỹ thuật

- Quiz vẫn có thể chơi offline vì câu hỏi được import từ SQLite asset vào Room.
- Memory vẫn có dữ liệu offline vì level được seed bằng `DatabaseSeeder`.
- Lịch sử chơi được lưu local trước, sau đó mới đồng bộ Firebase.
- Nếu không có `GEMINI_API_KEY`, phần nhận xét AI sẽ báo thiếu cấu hình.
- Nếu Gemini trả nội dung không đạt, `GeminiReviewService` có cơ chế fallback để tạo nhận xét local từ thống kê ván chơi.
- Project đang dùng `fallbackToDestructiveMigration()`, vì vậy khi đổi schema Room cần cân nhắc migration nếu muốn giữ dữ liệu local.

## 9. Thông tin GitHub

Repository:

[https://github.com/duck4nh/GameHub-CDA](https://github.com/duck4nh/GameHub-CDA)

Remote Git:

```text
https://github.com/duck4nh/GameHub-CDA.git
```

Branch làm việc:

```text
main
```
