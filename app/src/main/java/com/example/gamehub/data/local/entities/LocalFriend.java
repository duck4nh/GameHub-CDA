package com.example.gamehub.data.local.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(tableName = "Local_Friends")
public class LocalFriend {
    @PrimaryKey
    @NonNull
    public String friend_uid;
}
