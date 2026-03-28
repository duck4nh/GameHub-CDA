package com.example.gamehub.games.memory;

public class MemoryCard {
    public final int identifier;
    public final String label;
    public final int toneIndex;
    public boolean revealed;
    public boolean matched;

    public MemoryCard(int identifier, String label, int toneIndex) {
        this.identifier = identifier;
        this.label = label;
        this.toneIndex = toneIndex;
    }
}
