package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gamehub.data.local.entities.LocalFriend;
import java.util.List;

@Dao
public interface FriendDao {
    // Lệnh chèn danh sách bạn bè (Nếu trùng UID thì ghi đè cái mới nhất)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalFriend> friends);

    // Lệnh xóa sạch danh sách (Dùng khi Logout hoặc Sync lại)
    @Query("DELETE FROM Local_Friends")
    void deleteAllFriends();

    @Query("SELECT * FROM Local_Friends")
    List<LocalFriend> getAllFriends();
}