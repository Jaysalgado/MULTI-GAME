package com.networks.p2;

public interface GameState {
    void onCanBuzzChanged(boolean canBuzz);
    void onCanAnswerChanged(boolean canAnswer);
    void onQuestionReceived(String[] question);
    void onGameOver(String[] results);
    void onScoreUpdated(String display);
//    void onStatusChanged(boolean status);
}
