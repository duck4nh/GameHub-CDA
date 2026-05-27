package com.example.gamehub.games.memory;

/**
 * Model runtime cho một thẻ trong ván Memory.
 *
 * Hai thẻ có cùng identifier tạo thành một cặp. ViewModel thay đổi trạng thái
 * revealed/matched khi chơi, còn adapter chỉ dùng label và toneIndex để hiển thị.
 */
public class MemoryCard {
    /** Id ổn định cho RecyclerView để thẻ animate mà không mất định danh. */
    public final long cardId;
    /** Khóa ghép cặp; hai thẻ khớp khi giá trị này bằng nhau. */
    public final int identifier;
    /** Nội dung mặt trước khi thẻ được lật lên. */
    public final String label;
    /** Chỉ số màu trong bảng màu của adapter. */
    public final int toneIndex;
    /** True khi thẻ đang tạm thời được mở. */
    public boolean revealed;
    /** True sau khi thẻ đã ghép đúng và cần giữ trạng thái mở. */
    public boolean matched;

    public MemoryCard(long cardId, int identifier, String label, int toneIndex) {
        this.cardId = cardId;
        this.identifier = identifier;
        this.label = label;
        this.toneIndex = toneIndex;
    }
}
