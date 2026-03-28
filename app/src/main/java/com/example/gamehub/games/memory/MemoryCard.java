package com.example.gamehub.games.memory;

public class MemoryCard {
    public final int identifier;
    public boolean revealed;
    public boolean matched;

    public MemoryCard(int identifier) {
        this.identifier = identifier;
    }
}
