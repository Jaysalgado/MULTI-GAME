package com.networks.p2;

public interface GameState {
    void onCanBuzzChanged(boolean canBuzz);
    void onCanAnswerChanged(boolean canAnswer);
    void onQuestionReceived(String[] question);
//    void onStatusChanged(boolean status);
}
