package com.example.gamehub.data.local;

import com.example.gamehub.data.local.entities.MemoryLevel;
import com.example.gamehub.data.local.entities.QuizQuestion;
import com.example.gamehub.data.local.entities.SudokuBoard;

import java.util.ArrayList;
import java.util.List;

final class DatabaseSeeder {
    private DatabaseSeeder() {
    }

    static void seedIfNeeded(AppDatabase database) {
        if (database.quizDao().getCount() == 0) {
            database.quizDao().insertAll(buildQuizQuestions());
        }
        if (database.memoryDao().getCount() == 0) {
            database.memoryDao().insertAll(buildMemoryLevels());
        }
        if (database.sudokuDao().getCount() == 0) {
            database.sudokuDao().insertAll(buildSudokuBoards());
        }
    }

    private static List<QuizQuestion> buildQuizQuestions() {
        List<QuizQuestion> items = new ArrayList<>();
        items.add(new QuizQuestion(1, "Geography", "Thủ đô của Việt Nam là gì?", "", "Hà Nội", "Huế", "Đà Nẵng", "Cần Thơ", "A", "easy"));
        items.add(new QuizQuestion(2, "Science", "Hành tinh nào được gọi là Hành tinh Đỏ?", "", "Sao Kim", "Sao Hỏa", "Sao Mộc", "Sao Thổ", "B", "easy"));
        items.add(new QuizQuestion(3, "Sports", "Một đội bóng đá có bao nhiêu cầu thủ trên sân?", "", "9", "10", "11", "12", "C", "easy"));
        items.add(new QuizQuestion(4, "History", "Quốc khánh Việt Nam là ngày nào?", "", "30/4", "2/9", "1/5", "19/8", "B", "easy"));
        items.add(new QuizQuestion(5, "Technology", "HTTP là viết tắt của cụm nào?", "", "HyperText Transfer Protocol", "High Transfer Text Program", "Home Tool Transfer Protocol", "Hyperlink Text Transfer Process", "A", "medium"));
        items.add(new QuizQuestion(6, "Math", "Số nguyên tố nhỏ nhất là số nào?", "", "0", "1", "2", "3", "C", "medium"));
        items.add(new QuizQuestion(7, "Biology", "Cơ quan nào bơm máu đi khắp cơ thể?", "", "Gan", "Tim", "Phổi", "Não", "B", "medium"));
        items.add(new QuizQuestion(8, "Movies", "Bộ phim nào có nhân vật Jack và Rose?", "", "Avatar", "Titanic", "Inception", "Frozen", "B", "medium"));
        items.add(new QuizQuestion(9, "Geography", "Châu lục nào có diện tích lớn nhất?", "", "Châu Phi", "Châu Mỹ", "Châu Á", "Châu Âu", "C", "hard"));
        items.add(new QuizQuestion(10, "Science", "Nguyên tố hóa học có ký hiệu O là gì?", "", "Vàng", "Oxy", "Bạc", "Sắt", "B", "hard"));
        items.add(new QuizQuestion(11, "History", "Ai là vị vua đầu tiên của triều Nguyễn?", "", "Gia Long", "Minh Mạng", "Tự Đức", "Bảo Đại", "A", "hard"));
        items.add(new QuizQuestion(12, "Technology", "Cấu trúc dữ liệu FIFO là gì?", "", "Queue", "Stack", "Tree", "Graph", "A", "hard"));
        items.add(new QuizQuestion(13, "Science", "Kim loại nào lỏng ở nhiệt độ phòng?", "", "Sắt", "Thủy ngân", "Đồng", "Nhôm", "B", "easy"));
        items.add(new QuizQuestion(14, "Geography", "Sông nào dài nhất thế giới theo nhiều tài liệu phổ biến?", "", "Nile", "Amazon", "Mississippi", "Danube", "A", "medium"));
        items.add(new QuizQuestion(15, "Sports", "Trong bóng rổ, mỗi đội có bao nhiêu cầu thủ trên sân?", "", "4", "5", "6", "7", "B", "easy"));
        items.add(new QuizQuestion(16, "History", "Chiến thắng Điện Biên Phủ diễn ra năm nào?", "", "1954", "1945", "1968", "1975", "A", "medium"));
        items.add(new QuizQuestion(17, "Movies", "Nhân vật phù thủy chính trong Harry Potter tên là gì?", "", "Ron Weasley", "Harry Potter", "Hermione Granger", "Draco Malfoy", "B", "easy"));
        items.add(new QuizQuestion(18, "Math", "Giá trị của số Pi gần đúng là bao nhiêu?", "", "2.14", "3.14", "4.13", "3.41", "B", "easy"));
        items.add(new QuizQuestion(19, "Biology", "DNA là viết tắt của cụm nào?", "", "Deoxyribonucleic Acid", "Dynamic Nuclear Atom", "Double Neural Axis", "Digital Nucleic Array", "A", "hard"));
        items.add(new QuizQuestion(20, "Technology", "Thiết bị nào định tuyến lưu lượng mạng giữa các mạng khác nhau?", "", "Router", "Switch", "Hub", "Repeater", "A", "medium"));
        items.add(new QuizQuestion(21, "Science", "Ánh sáng truyền nhanh nhất trong môi trường nào?", "", "Nước", "Chân không", "Thủy tinh", "Không khí", "B", "hard"));
        items.add(new QuizQuestion(22, "History", "Ai đọc bản Tuyên ngôn Độc lập ngày 2/9/1945?", "", "Trường Chinh", "Võ Nguyên Giáp", "Hồ Chí Minh", "Phạm Văn Đồng", "C", "easy"));
        items.add(new QuizQuestion(23, "Sports", "Giải đấu quần vợt Wimbledon được chơi trên mặt sân nào?", "", "Đất nện", "Cỏ", "Cứng", "Thảm", "B", "hard"));
        items.add(new QuizQuestion(24, "Movies", "Bộ phim hoạt hình nào có nhân vật Elsa?", "", "Moana", "Frozen", "Encanto", "Mulan", "B", "easy"));
        return items;
    }

    private static List<MemoryLevel> buildMemoryLevels() {
        List<MemoryLevel> items = new ArrayList<>();
        items.add(new MemoryLevel(1, 4, 150, 0, true));
        items.add(new MemoryLevel(2, 6, 240, 0, true));
        items.add(new MemoryLevel(3, 8, 360, 0, false));
        return items;
    }

    private static List<SudokuBoard> buildSudokuBoards() {
        List<SudokuBoard> items = new ArrayList<>();
        items.add(new SudokuBoard(
                1,
                "easy",
                "530070000600195000098000060800060003400803001700020006060000280000419005000080079",
                "534678912672195348198342567859761423426853791713924856961537284287419635345286179"
        ));
        items.add(new SudokuBoard(
                2,
                "medium",
                "003020600900305001001806400008102900700000008006708200002609500800203009005010300",
                "483921657967345821251876493548132976729564138136798245372689514814253769695417382"
        ));
        items.add(new SudokuBoard(
                3,
                "hard",
                "000000907000420180000705026100904000050000040000507009920108000034059000507000000",
                "462831957795426183381795426173984265659312748248567319926178534834259671517643892"
        ));
        items.add(new SudokuBoard(
                4,
                "expert",
                "000900002050123400000000000030050600000308000001020090000000000006751040800004000",
                "314986752659123487287475931438259671972318564561627398145862379396751248823594116"
        ));
        return items;
    }
}
