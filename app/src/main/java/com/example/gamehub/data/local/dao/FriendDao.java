package com.example.gamehub.data.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.example.gamehub.data.local.entities.LocalFriend;
import java.util.List;

@Dao
public interface FriendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LocalFriend> friends);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LocalFriend friend);

    @Query("DELETE FROM Local_Friends")
    void deleteAllFriends();

    @Query("SELECT * FROM Local_Friends")
    List<LocalFriend> getAllFriends();

    @Query("UPDATE Local_Friends SET status = :status WHERE friend_uid = :uid")
    void updateStatus(String uid, String status);

    @Query("DELETE FROM Local_Friends WHERE friend_uid = :uid")
    void deleteFriend(String uid);
}