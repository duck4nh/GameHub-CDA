# Báo cáo kỹ thuật cá nhân - Module Game Quiz & Memory

## 1. Thông tin chung

### 1.1. Tên chức năng

Module được thực hiện gồm hai trò chơi trong ứng dụng GameHub:

- Game Quiz: trò chơi trả lời câu hỏi trắc nghiệm theo chủ đề, độ khó và giới hạn thời gian.
- Game Memory: trò chơi ghi nhớ vị trí thẻ, lật và ghép các cặp giống nhau theo từng level.

### 1.2. Phạm vi cá nhân thực hiện

Phạm vi cá nhân chỉ tập trung vào các phần liên quan trực tiếp đến Quiz và Memory:

- Xây dựng màn hình giao diện Game Quiz và Game Memory.
- Xây dựng dữ liệu cục bộ cho Quiz và Memory bằng SQLite/Room.
- Thiết kế trải nghiệm chơi game, trạng thái thắng/thua, hiệu ứng chuyển trạng thái và animation.
- Lưu lịch sử kết quả chơi cục bộ, hỗ trợ đồng bộ Firebase.
- Tích hợp nhận xét AI cho kết quả chơi cuối ván.

Các module không thuộc phạm vi chính như đăng nhập, hồ sơ, bạn bè, chat, leaderboard tổng thể và Sudoku chỉ được nhắc đến khi có kết nối kỹ thuật với phần Quiz/Memory.

### 1.3. Mục tiêu kỹ thuật

1. Người chơi có thể chơi Quiz và Memory khi không có mạng nhờ dữ liệu Room/SQLite cục bộ.
2. Giao diện game phản hồi theo thời gian thực: timer, điểm, combo, trạng thái đáp án, trạng thái thẻ.
3. Kết quả chơi được lưu vào lịch sử local trước, sau đó đồng bộ Firebase khi đủ điều kiện.
4. Nhận xét AI cuối ván phải dựa trên thống kê thật và nhật ký thao tác của người chơi.
5. Code được tách lớp rõ ràng: Activity chỉ render UI, ViewModel giữ trạng thái, Repository xử lý dữ liệu, DAO truy vấn Room.

## 2. Danh sách chức năng được phân công

### 2.1. Chức năng Game Quiz

#### 2.1.1. Màn hình thiết lập ván chơi

Người chơi có thể thiết lập:

- Chủ đề câu hỏi: chọn một hoặc nhiều category từ dữ liệu Room.
- Độ khó: tất cả, dễ, trung bình, khó.
- Số câu hỏi: 10, 15, 20, 25 hoặc 30 câu.

Màn hình này lấy dữ liệu category từ `QuizViewModel`, dữ liệu này được nạp từ `GameRepository.getQuizCategories()`.

#### 2.1.2. Màn hình chơi Quiz

Màn hình chơi hiển thị:

- Nội dung câu hỏi.
- Hình minh họa nếu câu hỏi có `link_image`.
- Bốn đáp án A/B/C/D.
- Bộ đếm thời gian mỗi câu.
- Tiến độ câu hiện tại trên tổng số câu.
- Điểm hiện tại.
- Combo hiện tại.
- Feedback đúng/sai/hết giờ sau mỗi câu.

#### 2.1.3. Luật chơi Quiz

Luật chơi được xử lý trong `QuizManager`:

- Mỗi câu có giới hạn thời gian `15_000 ms`.
- Trả lời đúng được cộng điểm.
- Trả lời đúng liên tiếp tăng combo.
- Trả lời sai hoặc hết giờ reset combo.
- Điểm gồm điểm nền, điểm thưởng độ khó, điểm thưởng thời gian còn lại và điểm thưởng combo.
- Thắng khi tỷ lệ đúng đạt từ `60%` trở lên.

#### 2.1.4. Kết quả và lịch sử Quiz

Sau khi hết câu hỏi:

- Hiển thị số câu đúng/tổng câu.
- Hiển thị thời gian chơi.
- Hiển thị tỷ lệ chính xác.
- Hiển thị điểm đạt được.
- Hiển thị kết quả tốt nhất trước đó.
- Lưu lịch sử vào `Local_History` với `game_name = "quiz"`.
- Tạo prompt và gọi AI nhận xét kết quả.

### 2.2. Chức năng Game Memory

#### 2.2.1. Màn hình chọn level

Người chơi có thể:

- Xem danh sách level Memory.
- Xem level đã mở khóa hoặc bị khóa.
- Xem kích thước lưới của từng level.
- Xem best time nếu level đã có thành tích.
- Chọn level đã mở khóa để bắt đầu chơi.

Level được lưu trong bảng `Memory_Levels`.

#### 2.2.2. Màn hình chơi Memory

Màn hình chơi hiển thị:

- Lưới thẻ theo số hàng/cột của level.
- Thời gian còn lại.
- Số lượt đoán cặp.
- Chuỗi ghép đúng tốt nhất.
- Animation lật thẻ.
- Âm thanh khi lật thẻ, ghép đúng, ghép sai, thắng hoặc thua.

#### 2.2.3. Luật chơi Memory

Luật chơi được xử lý trong `MemoryViewModel`:

- Một level gồm nhiều cặp thẻ.
- Mỗi cặp có cùng `identifier`.
- Lượt lật đầu tiên chỉ mở thẻ.
- Lượt lật thứ hai tăng số lần đoán.
- Nếu hai thẻ cùng `identifier`, cặp được đánh dấu `matched`.
- Nếu hai thẻ khác nhau, board bị khóa tạm thời, sau đó úp lại hai thẻ.
- Khi ghép hết toàn bộ cặp trước khi hết giờ, người chơi thắng.
- Khi hết giờ trước khi ghép hết cặp, người chơi thua.

#### 2.2.4. Kết quả và mở khóa level

Sau khi kết thúc level:

- Hiển thị điểm.
- Hiển thị số cặp đã ghép.
- Hiển thị số lượt đoán.
- Hiển thị thời gian chơi.
- Hiển thị tỷ lệ chính xác.
- Hiển thị chuỗi ghép đúng tốt nhất.
- Lưu lịch sử vào `Local_History` với `game_name = "memory"`.
- Nếu thắng, cập nhật best time.
- Nếu thắng, mở khóa level tiếp theo.
- Tạo prompt và gọi AI nhận xét kết quả.

### 2.3. Chức năng CSDL cục bộ

#### 2.3.1. Dữ liệu Quiz

Quiz sử dụng file SQLite asset:

`app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`

Khi ứng dụng cần dữ liệu Quiz:

1. `GameRepository.ensureLocalDataReady()` kiểm tra bảng `Quiz_Questions`.
2. Nếu bảng trống, `QuizAssetImporter.readQuestions(...)` copy asset DB vào cache.
3. Importer đọc bảng `Quiz_Questions` từ asset DB.
4. Danh sách `QuizQuestion` được insert vào Room bằng `QuizDao.insertAll(...)`.

#### 2.3.2. Dữ liệu Memory

Memory không dùng file asset riêng. Danh sách level được sinh tự động trong `DatabaseSeeder`:

- Tạo 30 level.
- Mỗi level có số hàng, số cột, số cặp và thời gian giới hạn.
- Level 1 được mở khóa mặc định.
- Các level sau được mở dần sau khi người chơi thắng level trước.

#### 2.3.3. Dữ liệu lịch sử

Quiz và Memory dùng chung bảng `Local_History` để lưu:

- Tên game.
- Trạng thái thắng/thua.
- Điểm.
- Thời gian chơi.
- Ngày chơi.
- Trạng thái đồng bộ Firebase.
- Thông tin chi tiết như level Memory.
- Số lượt đoán với Memory.

### 2.4. Chức năng nhận xét AI

Nhận xét AI được tích hợp ở màn hình kết quả:

- Quiz gọi `QuizActivity.ensureQuizAiReview()`.
- Memory gọi `MemoryGameActivity.ensureMemoryAiReview()`.
- Cả hai tạo prompt gồm thống kê cuối ván và log thao tác.
- `GeminiReviewService` gửi prompt đến Gemini API.
- Nếu Gemini lỗi hoặc trả nội dung không phù hợp, service dùng `AI_METRICS` để tạo nhận xét local.

## 3. Kiến trúc chi tiết hệ thống

### 3.1. Kiến trúc theo tầng

Module Quiz & Memory được chia thành các tầng sau:

| Tầng | Thành phần | Trách nhiệm |
|---|---|---|
| UI | `QuizActivity`, `MemoryGameActivity`, XML layout | Hiển thị màn hình, nhận thao tác, gọi ViewModel, chạy animation |
| State/Logic | `QuizViewModel`, `MemoryViewModel`, `QuizManager` | Quản lý trạng thái ván chơi, luật chơi, điểm, timer |
| Data Repository | `GameRepository` | Điều phối Room, seed data, lịch sử, đồng bộ Firebase |
| Local Database | `AppDatabase`, DAO, Entity | Lưu câu hỏi, level, lịch sử local |
| External Service | `FirebaseManager`, `GeminiReviewService`, `ImageLoader` | Đồng bộ Firestore, gọi Gemini API, tải ảnh câu hỏi |
| Utility | `SoundManager`, `PreferenceManager` | Âm thanh, cấu hình animation, cache thông tin người chơi |

### 3.2. Sơ đồ tổng quan

```mermaid
flowchart LR
    A["Người chơi"] --> B["QuizActivity / MemoryGameActivity"]
    B --> C["QuizViewModel / MemoryViewModel"]
    C --> D["QuizManager / Memory rules"]
    C --> E["GameRepository"]
    E --> F["Room AppDatabase"]
    F --> G["QuizDao"]
    F --> H["MemoryDao"]
    F --> I["HistoryDao"]
    G --> J["Quiz_Questions"]
    H --> K["Memory_Levels"]
    I --> L["Local_History"]
    E --> M["QuizAssetImporter"]
    E --> N["DatabaseSeeder"]
    E --> O["FirebaseManager"]
    O --> P["Firebase Auth / Firestore"]
    B --> Q["GeminiReviewService"]
    Q --> R["Gemini generateContent API"]
    B --> S["ImageLoader / SoundManager"]
```

### 3.3. Mô hình MVVM áp dụng trong module

#### Activity

Activity chỉ chịu trách nhiệm:

- Gắn view từ XML.
- Gắn listener cho button, card, pause, retry.
- Render trạng thái từ ViewModel.
- Chạy animation và âm thanh.
- Gọi AI review khi màn kết quả xuất hiện.

Activity không trực tiếp:

- Tính điểm.
- Quyết định đúng/sai.
- Truy vấn Room.
- Ghi Firebase.
- Mở khóa level.

#### ViewModel

ViewModel chịu trách nhiệm:

- Giữ toàn bộ trạng thái của ván chơi.
- Gọi repository để lấy dữ liệu.
- Gọi manager hoặc tự xử lý luật game.
- Tính trạng thái thắng/thua.
- Tạo lịch sử sau khi kết thúc ván.
- Tạo prompt AI từ số liệu thật.

#### Repository

Repository là lớp trung gian để:

- Truy cập Room DAO.
- Import dữ liệu Quiz từ SQLite asset.
- Lấy level Memory.
- Lưu lịch sử local.
- Kích hoạt đồng bộ Firebase.
- Cung cấp thông tin người chơi hiện tại cho Memory best record.

### 3.4. Luồng dữ liệu Quiz chi tiết

```mermaid
sequenceDiagram
    participant U as Người chơi
    participant A as QuizActivity
    participant V as QuizViewModel
    participant R as GameRepository
    participant Q as QuizDao
    participant M as QuizManager
    participant H as HistoryDao
    participant AI as GeminiReviewService

    U->>A: Mở màn Quiz
    A->>V: initialize()
    V->>R: ensureLocalDataReady()
    R->>Q: getCount()
    R->>Q: insertAll() nếu chưa có dữ liệu
    V->>R: getQuizCategories()
    U->>A: Chọn bộ lọc và bấm bắt đầu
    A->>V: startGame()
    V->>R: getRandomQuizQuestions(...)
    R->>Q: Query theo category/difficulty
    V->>M: new QuizManager(questions)
    A->>V: submitAnswer() hoặc timeout
    V->>M: answerCurrentQuestion(...)
    M-->>V: AnswerOutcome
    V->>V: advanceAfterFeedback()
    V->>H: insert(LocalHistory)
    A->>AI: requestReview(prompt)
    AI-->>A: Nhận xét cuối ván
```

### 3.5. Luồng dữ liệu Memory chi tiết

```mermaid
sequenceDiagram
    participant U as Người chơi
    participant A as MemoryGameActivity
    participant V as MemoryViewModel
    participant R as GameRepository
    participant D as MemoryDao
    participant H as HistoryDao
    participant AI as GeminiReviewService

    U->>A: Mở màn Memory
    A->>V: initialize()
    V->>R: ensureLocalDataReady()
    V->>R: getMemoryLevels()
    R->>D: getAllLevels()
    U->>A: Chọn level
    A->>V: startLevel(index)
    V->>V: buildDeck(level)
    U->>A: Lật thẻ
    A->>V: onCardSelected(position)
    V-->>A: TurnOutcome
    A->>A: Phát âm thanh/animation
    V->>H: insert(LocalHistory)
    V->>R: completeMemoryLevel(...)
    R->>D: updateBestTime(), unlockLevel()
    A->>AI: requestReview(prompt)
    AI-->>A: Nhận xét cuối ván
```

## 4. Thiết kế giao diện và trải nghiệm người dùng

### 4.1. Giao diện Quiz

#### 4.1.1. Setup screen

File liên quan:

- `game_quiz.xml`
- `view_quiz_setup.xml`
- `QuizActivity.renderSetup()`

Mục tiêu:

- Cho phép người chơi cấu hình ván chơi nhanh.
- Không cho bắt đầu khi dữ liệu chưa load xong.
- Hiển thị tóm tắt lựa chọn hiện tại.

Các trạng thái chính:

| Trạng thái | Cách xử lý |
---|---|
| Đang tải câu hỏi | Hiển thị text chờ, disable nút bắt đầu |
| Có category | Cho phép chọn chủ đề bằng bottom sheet |
| Không có dữ liệu phù hợp | Khi bắt đầu, ViewModel chuyển sang empty state |

#### 4.1.2. Gameplay screen

File liên quan:

- `view_quiz_gameplay.xml`
- `QuizActivity.renderGameplay()`
- `QuizActivity.renderAnswerButtons(...)`
- `QuizActivity.renderIllustration(...)`

Các thành phần chính:

- `quiz_question`: nội dung câu hỏi.
- `quiz_image`: ảnh minh họa tải từ URL.
- `quiz_option_a/b/c/d`: bốn đáp án.
- `quiz_timer`: thời gian còn lại.
- `quiz_live_score`: điểm đang có.
- `quiz_live_combo`: combo đang có.
- `quiz_feedback`: phản hồi sau khi trả lời.

Trạng thái đáp án:

| Trạng thái | Drawable |
|---|---|
| Mặc định | `bg_quiz_option_default.xml` |
| Đang chọn | `bg_quiz_option_selected.xml` |
| Đáp án đúng | `bg_quiz_option_correct.xml` |
| Đáp án sai | `bg_quiz_option_wrong.xml` |

#### 4.1.3. Pause và result screen

Pause:

- Dừng timer.
- Hiển thị overlay.
- Cho phép tiếp tục hoặc thoát.

Result:

- Hiển thị tổng câu đúng.
- Hiển thị thời gian.
- Hiển thị độ chính xác.
- Hiển thị điểm.
- Hiển thị best history.
- Hiển thị nhận xét AI.

### 4.2. Giao diện Memory

#### 4.2.1. Level setup screen

File liên quan:

- `game_memory.xml`
- `view_memory_setup.xml`
- `MemoryGameActivity.buildLevelGrid(...)`
- `MemoryGameActivity.createLevelTile(...)`

Level tile hiển thị:

- Tên level.
- Trạng thái khóa/mở khóa.
- Best time nếu có.
- Avatar và tên người chơi hiện tại nếu có record.

#### 4.2.2. Gameplay board

File liên quan:

- `view_memory_gameplay.xml`
- `item_memory_card.xml`
- `MemoryBoardAdapter`

Board dùng `RecyclerView` với `GridLayoutManager`.

Số cột lấy từ `MemoryLevel.columnCount`. Adapter tự tính kích thước thẻ để board vẫn hiển thị hợp lý trên nhiều kích thước màn hình.

#### 4.2.3. Animation Memory

Animation lật thẻ được thực hiện trong `MemoryBoardAdapter.animateFlip(...)`:

1. Xoay thẻ đến 90 độ theo trục Y.
2. Đổi mặt thẻ ở giữa animation.
3. Xoay tiếp về 0 độ.

Mismatch:

- Khi hai thẻ không khớp, `MemoryViewModel` khóa board.
- `MemoryGameActivity` chờ `MISMATCH_DELAY_MS`.
- Sau đó gọi `resolveMismatch()` để úp lại thẻ.

## 5. Thiết kế cơ sở dữ liệu

### 5.1. Room Database

File: `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java`

Database name: `gamehub.db`

Version hiện tại: `6`

Các bảng liên quan trực tiếp đến nhiệm vụ:

- `Quiz_Questions`
- `Memory_Levels`
- `Local_History`

### 5.2. Bảng `Quiz_Questions`

Entity: `QuizQuestion`

DAO: `QuizDao`

Mục đích:

- Lưu câu hỏi trắc nghiệm.
- Hỗ trợ lọc theo chủ đề và độ khó.
- Hỗ trợ lấy câu hỏi ngẫu nhiên cho mỗi ván.

| Cột | Kiểu Java | Mô tả |
|---|---|---|
| `id` | `int` | Khóa chính câu hỏi |
| `category` | `String` | Chủ đề câu hỏi |
| `question` | `String` | Nội dung câu hỏi |
| `link_image` | `String` | URL ảnh minh họa |
| `opt_a` | `String` | Đáp án A |
| `opt_b` | `String` | Đáp án B |
| `opt_c` | `String` | Đáp án C |
| `opt_d` | `String` | Đáp án D |
| `correct_ans` | `String` | Đáp án đúng |
| `difficulty` | `String` | Độ khó |

Các query chính:

- `getDistinctCategories()`: lấy danh sách chủ đề.
- `getRandomQuestions(limit)`: lấy câu hỏi ngẫu nhiên không lọc.
- `getRandomQuestionsByDifficulty(...)`: lọc theo độ khó.
- `getRandomQuestionsByCategories(...)`: lọc theo chủ đề.
- `getRandomQuestionsByCategoriesAndDifficulty(...)`: lọc đồng thời chủ đề và độ khó.

### 5.3. Bảng `Memory_Levels`

Entity: `MemoryLevel`

DAO: `MemoryDao`

Mục đích:

- Lưu cấu hình level.
- Lưu trạng thái mở khóa.
- Lưu best time cục bộ.

| Cột | Kiểu Java | Mô tả |
|---|---|---|
| `level_id` | `int` | Số level và khóa chính |
| `row_count` | `int` | Số hàng |
| `column_count` | `int` | Số cột |
| `time_limit_sec` | `long` | Thời gian giới hạn |
| `best_time_ms` | `long` | Thời gian tốt nhất |
| `is_unlocked` | `boolean` | Level đã mở khóa hay chưa |

Các query chính:

- `getAllLevels()`: lấy danh sách level để hiển thị.
- `getLevel(levelId)`: lấy một level cụ thể.
- `updateBestTime(...)`: cập nhật best time.
- `unlockLevel(...)`: mở khóa level tiếp theo.
- `clearAll()` và `insertAll(...)`: phục vụ seed lại danh sách level.

### 5.4. Bảng `Local_History`

Entity: `LocalHistory`

DAO: `HistoryDao`

Mục đích:

- Lưu kết quả từng ván.
- Tạo dữ liệu thống kê local.
- Là hàng đợi đồng bộ Firebase.

| Cột | Kiểu Java | Mô tả |
|---|---|---|
| `id` | `int` | Khóa chính tự tăng |
| `game_name` | `String` | `quiz` hoặc `memory` |
| `status` | `String` | `won`, `lost`, `completed` |
| `score` | `int` | Điểm cuối ván |
| `time_spent` | `long` | Thời gian chơi |
| `play_date` | `long` | Timestamp khi chơi |
| `is_synced` | `boolean` | Đã đồng bộ Firebase hay chưa |
| `detail` | `String` | Chi tiết bổ sung, ví dụ Memory level |
| `attempt_count` | `int` | Số lượt đoán của Memory |

Các query chính:

- `insert(...)`: lưu một ván chơi.
- `getAllNewestFirst()`: lấy lịch sử mới nhất trước.
- `getUnsyncedHistory()`: lấy các bản ghi chưa đồng bộ.
- `markSynced(...)`: đánh dấu đã đồng bộ.
- `getBestRecordForGame(...)`: lấy record tốt nhất cho result screen.

## 6. Phân tích code đáp ứng chức năng

### 6.1. Game Quiz

#### 6.1.1. `QuizActivity`

Vai trò:

- Là controller giao diện cho Quiz.
- Không chứa luật tính điểm.
- Không truy vấn Room trực tiếp.
- Nhận dữ liệu từ `QuizViewModel` và render ra UI.

Các hàm quan trọng:

| Hàm | Vai trò chi tiết |
|---|---|
| `onCreate(...)` | Khởi tạo layout, ViewModel, SoundManager, GeminiReviewService |
| `bindViews()` | Ánh xạ view cho setup, gameplay, result, pause |
| `bindActions()` | Gắn sự kiện chọn chủ đề, độ khó, số câu, đáp án, pause, retry |
| `installBackHandler()` | Xử lý nút back theo trạng thái game |
| `render()` | Render đồng bộ toàn bộ màn hình theo state hiện tại |
| `renderSetup()` | Cập nhật lựa chọn chủ đề, độ khó, số câu |
| `renderGameplay()` | Cập nhật câu hỏi, timer, đáp án, điểm, combo, ảnh |
| `renderResult()` | Cập nhật thống kê cuối ván và nhận xét AI |
| `renderIllustration(...)` | Tải ảnh câu hỏi bằng `ImageLoader` |
| `renderAnswerButtons(...)` | Tô trạng thái đáp án sau khi chọn hoặc trả lời |
| `handleOutcome(...)` | Phát âm thanh đúng/sai và chờ trước khi sang câu |
| `ensureQuizAiReview()` | Chống gọi Gemini lặp lại cho cùng một kết quả |

#### 6.1.2. `QuizViewModel`

Vai trò:

- Giữ state của ván Quiz.
- Tải dữ liệu câu hỏi từ repository.
- Điều khiển timer từng câu.
- Gửi lựa chọn cho `QuizManager`.
- Lưu lịch sử cuối ván.
- Tạo prompt AI.

Các state quan trọng:

| Biến | Ý nghĩa |
|---|---|
| `currentScreen` | SETUP, GAMEPLAY hoặc RESULT |
| `selectedCategories` | Danh sách chủ đề được chọn |
| `selectedDifficulty` | Độ khó đang chọn |
| `selectedQuestionCount` | Số câu hỏi mỗi ván |
| `remainingQuestionMs` | Thời gian còn lại của câu hiện tại |
| `elapsedSessionMs` | Tổng thời gian đã chơi |
| `selectedAnswerKey` | Đáp án người chơi chọn |
| `latestOutcome` | Kết quả trả lời gần nhất |
| `sessionLog` | Nhật ký thao tác dùng cho AI |

Các hàm quan trọng:

| Hàm | Vai trò chi tiết |
|---|---|
| `initialize()` | Đảm bảo dữ liệu local sẵn sàng và lấy category |
| `startGame()` | Lấy danh sách câu hỏi theo bộ lọc và tạo `QuizManager` |
| `tickQuestion()` | Giảm timer và phát hiện timeout |
| `selectAnswer(...)` | Lưu đáp án đang chọn |
| `submitAnswer()` | Khóa đáp án và lấy kết quả từ `QuizManager` |
| `timeoutCurrentQuestion()` | Xử lý hết giờ |
| `advanceAfterFeedback()` | Sang câu tiếp theo hoặc kết thúc ván |
| `finishGame()` | Tạo `LocalHistory`, lưu DB, kích hoạt sync |
| `buildAiReviewPrompt()` | Tạo prompt AI từ thống kê và log |

#### 6.1.3. `QuizManager`

Vai trò:

- Là engine luật chơi độc lập với Android UI.
- Nhận danh sách `QuizQuestion`.
- Tính đúng/sai, điểm, combo, win condition.

Công thức điểm:

```text
điểm = 100 + bonus_độ_khó + bonus_thời_gian + bonus_combo
```

Trong đó:

- `bonus_độ_khó = 0` với easy.
- `bonus_độ_khó = 35` với medium.
- `bonus_độ_khó = 60` với hard.
- `bonus_thời_gian = số_giây_còn_lại * 8`.
- `bonus_combo = (combo - 1) * 20`.

Điều kiện thắng:

```text
tỷ lệ đúng >= 60%
```

### 6.2. Game Memory

#### 6.2.1. `MemoryGameActivity`

Vai trò:

- Là controller giao diện cho Memory.
- Render level grid, board, pause, result.
- Chạy animation và âm thanh.
- Ghi log thao tác để đưa vào prompt AI.

Các hàm quan trọng:

| Hàm | Vai trò chi tiết |
|---|---|
| `onCreate(...)` | Khởi tạo layout, adapter, ViewModel, SoundManager, AI service |
| `bindViews()` | Ánh xạ view cho setup, gameplay, result, pause |
| `bindActions()` | Gắn sự kiện pause, resume, retry, next level |
| `renderSetup()` | Hiển thị danh sách level |
| `buildLevelGrid(...)` | Tạo các tile level bằng code |
| `createLevelTile(...)` | Tạo UI cho một level |
| `renderGameplay()` | Render board, timer, lượt đoán, streak |
| `onCardClicked(...)` | Gửi lượt click cho ViewModel và xử lý âm thanh/animation |
| `scheduleTimerTick()` | Chạy timer mỗi giây |
| `renderResult()` | Hiển thị kết quả và gọi nhận xét AI |
| `ensureMemoryAiReview()` | Chống gọi Gemini lặp cho cùng kết quả |

#### 6.2.2. `MemoryViewModel`

Vai trò:

- Quản lý toàn bộ state Memory.
- Tải level từ Room.
- Sinh deck thẻ.
- Xử lý luật match/mismatch/win/lose.
- Lưu lịch sử và mở khóa level.

Các state quan trọng:

| Biến | Ý nghĩa |
|---|---|
| `levels` | Danh sách level từ Room |
| `cards` | Danh sách thẻ của level hiện tại |
| `firstSelectedPosition` | Vị trí thẻ đầu tiên đang mở |
| `secondSelectedPosition` | Vị trí thẻ thứ hai đang mở |
| `matchedPairs` | Số cặp đã ghép đúng |
| `pairAttempts` | Số lượt đoán cặp |
| `currentStreak` | Chuỗi ghép đúng hiện tại |
| `bestStreak` | Chuỗi ghép đúng tốt nhất |
| `remainingTimeMs` | Thời gian còn lại |
| `boardLocked` | Board đang khóa khi mismatch |

Các hàm quan trọng:

| Hàm | Vai trò chi tiết |
|---|---|
| `initialize()` | Tải danh sách level từ Room |
| `startLevel(...)` | Reset state và tạo deck mới |
| `tick()` | Giảm thời gian, xử lý thua khi hết giờ |
| `onCardSelected(...)` | Xử lý một lượt lật thẻ |
| `resolveMismatch()` | Úp lại cặp thẻ không khớp |
| `finishGame(...)` | Lưu lịch sử, cập nhật best time và unlock level |
| `buildDeck(...)` | Tạo danh sách `MemoryCard` từ cấu hình level |
| `buildSmartArrangement(...)` | Xáo thẻ nhiều lần để giảm cặp giống nhau nằm cạnh nhau |

#### 6.2.3. `MemoryBoardAdapter`

Vai trò:

- Render danh sách `MemoryCard` lên `RecyclerView`.
- Tự tính kích thước thẻ theo số cột.
- Tô màu mặt trước của thẻ.
- Chạy animation lật thẻ.

Adapter không quyết định:

- Thẻ nào đúng/sai.
- Khi nào thắng/thua.
- Điểm của người chơi.

Những việc đó thuộc `MemoryViewModel`.

## 7. Tích hợp API và đồng bộ ngoài

### 7.1. Gemini AI Review

File: `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java`

Endpoint:

```text
https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

API key được đọc từ:

```text
BuildConfig.GEMINI_API_KEY
```

Giá trị này được cấu hình trong `app/build.gradle.kts` từ `local.properties`.

Quy trình gọi AI:

1. Activity tạo prompt từ ViewModel.
2. Gọi `GeminiReviewService.requestReview(...)`.
3. Service tạo request JSON có schema.
4. Service gọi Gemini API bằng `HttpURLConnection`.
5. Service parse phản hồi.
6. Service kiểm tra phản hồi có đủ 2-3 câu, đúng định dạng, không quá ngắn.
7. Nếu phản hồi không đạt, service thử prompt fallback.
8. Nếu vẫn không đạt, service tạo nhận xét local từ `AI_METRICS`.

### 7.2. Firebase Sync

File: `app/src/main/java/com/example/gamehub/data/remote/FirebaseManager.java`

Firestore collections liên quan:

- `Users`
- `Game_Records`

Quy trình đồng bộ:

1. Quiz/Memory kết thúc ván.
2. ViewModel tạo `LocalHistory`.
3. `GameRepository.saveHistory(...)` lưu vào Room.
4. Repository gọi `syncPendingHistoryNow(...)`.
5. Nếu offline hoặc chưa đăng nhập, record vẫn nằm trong `Local_History` với `is_synced = false`.
6. Khi có mạng và có user, `FirebaseManager.syncHistoryRecordDetailed(...)` ghi Firestore transaction.
7. Nếu transaction thành công, `HistoryDao.markSynced(...)` cập nhật local.

Lý do dùng transaction:

- Tránh ghi trùng `Game_Records`.
- Đảm bảo record mới và tổng điểm user được cập nhật nhất quán.
- Nếu record đã tồn tại, xem như đã đồng bộ thành công.

### 7.3. ImageLoader

File: `app/src/main/java/com/example/gamehub/utils/ImageLoader.java`

Mục đích:

- Tải ảnh minh họa cho câu hỏi Quiz.
- Chạy tải ảnh trên background thread.
- Cache ảnh bằng `LruCache`.
- Trả kết quả về main thread để cập nhật `ImageView`.

Cơ chế chống ảnh cũ ghi đè ảnh mới:

- Khi bắt đầu load, `ImageView.setTag(normalizedUrl)` được gọi.
- Khi tải xong, loader kiểm tra tag hiện tại.
- Nếu tag không còn trùng URL ban đầu, kết quả bị bỏ qua.

## 8. Hướng dẫn cài đặt và triển khai

### 8.1. Yêu cầu môi trường

- Android Studio phiên bản mới.
- JDK 11.
- Android SDK có compile SDK 36.
- Thiết bị/emulator Android API 24 trở lên.
- Firebase project đã có `google-services.json`.
- Internet nếu cần Firebase, tải ảnh URL hoặc Gemini AI.

### 8.2. Cấu hình `local.properties`

Tạo file `local.properties` ở thư mục gốc:

```properties
sdk.dir=C:\\Users\\<ten_user>\\AppData\\Local\\Android\\Sdk
GEMINI_API_KEY=<api_key_gemini>
```

Lưu ý:

- `sdk.dir` cần đúng đường dẫn Android SDK trên máy.
- `GEMINI_API_KEY` cần có nếu muốn nhận xét AI hoạt động.
- Không commit `local.properties` lên GitHub.

### 8.3. Cấu hình Firebase

File bắt buộc:

```text
app/google-services.json
```

Firebase cần bật:

- Authentication.
- Firestore Database.

Collections được dùng:

- `Users`
- `Game_Records`

### 8.4. Build debug

Lệnh build:

```powershell
.\gradlew.bat assembleDebug
```

Nếu không có `local.properties`, có thể build tạm bằng biến môi trường:

```powershell
$env:ANDROID_HOME='C:\\Users\\ducan\\AppData\\Local\\Android\\Sdk'
.\gradlew.bat assembleDebug
```

### 8.5. Chạy ứng dụng

1. Mở project bằng Android Studio.
2. Sync Gradle.
3. Chọn emulator hoặc thiết bị thật.
4. Chạy app.
5. Đăng nhập tài khoản.
6. Vào danh sách game.
7. Chọn Quiz hoặc Memory.

### 8.6. Lưu ý triển khai

1. Không xóa asset DB câu hỏi Quiz:
   `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`
2. Nếu thay đổi schema Room, cần tăng version database.
3. Project hiện dùng `fallbackToDestructiveMigration()`, nên thay đổi schema có thể làm mất dữ liệu local.
4. Nếu Gemini API không hoạt động, app vẫn có fallback nhận xét local.
5. Nếu offline, lịch sử vẫn được lưu local và chờ đồng bộ sau.
6. Nếu Firebase chưa đăng nhập, sync sẽ bị hoãn nhưng không làm mất lịch sử local.

## 9. Kiểm thử và xác nhận

### 9.1. Kiểm thử chức năng Quiz

Các kịch bản cần kiểm tra:

| Kịch bản | Kết quả mong đợi |
|---|---|
| Mở màn Quiz lần đầu | Dữ liệu câu hỏi được import nếu Room trống |
| Chọn nhiều category | Câu hỏi lấy theo các category đã chọn |
| Chọn độ khó | Câu hỏi lọc đúng theo difficulty |
| Không chọn đáp án và bấm gửi | Hiển thị thông báo yêu cầu chọn đáp án |
| Trả lời đúng | Tăng điểm, tăng combo, tô đáp án đúng |
| Trả lời sai | Reset combo, tô đáp án sai và đáp án đúng |
| Hết giờ | Khóa câu hỏi, hiển thị đáp án đúng |
| Hết ván | Lưu lịch sử và hiển thị result screen |
| Có API key Gemini | Hiển thị nhận xét AI |
| Không có API key Gemini | Hiển thị thông báo thiếu API key |

### 9.2. Kiểm thử chức năng Memory

Các kịch bản cần kiểm tra:

| Kịch bản | Kết quả mong đợi |
|---|---|
| Mở màn Memory lần đầu | Level được seed vào Room |
| Chọn level khóa | Không bắt đầu game |
| Chọn level mở khóa | Bắt đầu board đúng kích thước |
| Lật thẻ đầu tiên | Thẻ mở mặt trước |
| Lật thẻ thứ hai khớp | Cặp được giữ mở, tăng điểm |
| Lật thẻ thứ hai không khớp | Board khóa tạm, sau đó úp lại |
| Ghép hết cặp | Win, lưu lịch sử, mở khóa level tiếp theo |
| Hết giờ | Lose, lưu lịch sử |
| Màn kết quả | Hiển thị điểm, thời gian, accuracy, streak và nhận xét AI |

### 9.3. Kiểm thử build

Đã chạy:

```powershell
$env:ANDROID_HOME='C:\\Users\\ducan\\AppData\\Local\\Android\\Sdk'
.\gradlew.bat assembleDebug
```

Kết quả: build debug thành công.

Lưu ý: project trước đó có lỗi trùng resource launcher do cùng tồn tại `ic_launcher.png` và `ic_launcher.webp` trong các thư mục `mipmap-*`. Đã giữ bộ `.png` và xóa bộ `.webp` trùng tên để build được.

## 10. Rủi ro và hướng xử lý

| Rủi ro | Ảnh hưởng | Hướng xử lý |
|---|---|---|
| Asset DB Quiz bị thiếu | Quiz không có câu hỏi | Kiểm tra file trong `assets/databases` trước khi build |
| URL ảnh câu hỏi lỗi | Ảnh không hiển thị | `ImageLoader` ẩn vùng ảnh nếu tải thất bại |
| Không có mạng | Không sync Firebase, không tải ảnh, không gọi Gemini | Lưu local trước, sync sau; AI có fallback local |
| Chưa đăng nhập Firebase | Không sync lịch sử | Giữ `is_synced = false` để đồng bộ sau |
| Gemini trả phản hồi không đạt | Nhận xét kém chất lượng | Service validate và tạo fallback từ `AI_METRICS` |
| Thay đổi Room schema | Có thể mất dữ liệu local | Cần migration rõ ràng nếu triển khai thật |
| Board Memory quá lớn | UI có thể chật | Adapter tự tính kích thước thẻ theo số cột |

## 11. Danh sách file liên quan đến phần cá nhân

### 11.1. Game Quiz

- `app/src/main/java/com/example/gamehub/games/quiz/QuizActivity.java`
- `app/src/main/java/com/example/gamehub/games/quiz/QuizViewModel.java`
- `app/src/main/java/com/example/gamehub/games/quiz/QuizManager.java`
- `app/src/main/res/layout/game_quiz.xml`
- `app/src/main/res/layout/view_quiz_setup.xml`
- `app/src/main/res/layout/view_quiz_gameplay.xml`
- `app/src/main/res/layout/view_quiz_pause.xml`
- `app/src/main/res/layout/view_quiz_result.xml`
- `app/src/main/res/drawable/bg_quiz_option_default.xml`
- `app/src/main/res/drawable/bg_quiz_option_selected.xml`
- `app/src/main/res/drawable/bg_quiz_option_correct.xml`
- `app/src/main/res/drawable/bg_quiz_option_wrong.xml`

### 11.2. Game Memory

- `app/src/main/java/com/example/gamehub/games/memory/MemoryGameActivity.java`
- `app/src/main/java/com/example/gamehub/games/memory/MemoryViewModel.java`
- `app/src/main/java/com/example/gamehub/games/memory/MemoryBoardAdapter.java`
- `app/src/main/java/com/example/gamehub/games/memory/MemoryCard.java`
- `app/src/main/res/layout/game_memory.xml`
- `app/src/main/res/layout/view_memory_setup.xml`
- `app/src/main/res/layout/view_memory_gameplay.xml`
- `app/src/main/res/layout/view_memory_pause.xml`
- `app/src/main/res/layout/view_memory_result.xml`
- `app/src/main/res/layout/item_memory_card.xml`
- `app/src/main/res/drawable/bg_tile_memory_locked.xml`
- `app/src/main/res/drawable/bg_tile_memory_unlocked.xml`
- `app/src/main/res/drawable/bg_tile_selected_memory.xml`

### 11.3. CSDL, repository, API và tiện ích

- `app/src/main/java/com/example/gamehub/data/local/AppDatabase.java`
- `app/src/main/java/com/example/gamehub/data/local/DatabaseSeeder.java`
- `app/src/main/java/com/example/gamehub/data/local/QuizAssetImporter.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/QuizDao.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/MemoryDao.java`
- `app/src/main/java/com/example/gamehub/data/local/dao/HistoryDao.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/QuizQuestion.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/MemoryLevel.java`
- `app/src/main/java/com/example/gamehub/data/local/entities/LocalHistory.java`
- `app/src/main/java/com/example/gamehub/data/repository/GameRepository.java`
- `app/src/main/java/com/example/gamehub/data/remote/FirebaseManager.java`
- `app/src/main/java/com/example/gamehub/ai/GeminiReviewService.java`
- `app/src/main/java/com/example/gamehub/utils/ImageLoader.java`
- `app/src/main/assets/databases/quiz_questions_500_vi_entity_images.db`

## 12. Link GitHub

Repository:

[https://github.com/duck4nh/GameHub](https://github.com/duck4nh/GameHub)

Branch làm việc:

```text
feature/quiz
```
