package com.networks.p2;

import java.util.Arrays;

public class Question {
    private final String[] questionArray;

    public Question(String[] questionArray) {
        this.questionArray = questionArray;
    }

    public String[] getQuestionArray() {
        return questionArray;
    }

    public String getText() {
        return questionArray[0];
    }

    public String[] getOptions() {
        return Arrays.copyOfRange(questionArray, 1, questionArray.length);
    }

    public String toNetworkPayload() {
        return String.join("::", questionArray);
    }

    public static Question fromNetworkPayload(String payload) {
        String[] parts = payload.split("::");
        return new Question(parts);
    }
}

