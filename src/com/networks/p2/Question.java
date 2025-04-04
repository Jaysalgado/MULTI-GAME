package com.networks.p2;

public class Question {
    private final String[] questionArray;
    private final String text;
    private final String[] options;
    private final int correctIndex;

    public Question(String[] questionArray, int correctIndex) {
        this.questionArray = questionArray;
        this.text = questionArray[0];
        this.options = new String[] {
                questionArray[1], questionArray[2], questionArray[3], questionArray[4]
        };
        this.correctIndex = correctIndex;
    }

    public String getText() {
        return text;
    }

    public String[] getOptions() {
        return options;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public String getCorrectAnswerText() {
        return options[correctIndex];
    }

    public String[] getQuestionArray() {
        return questionArray;
    }

    public String toNetworkString() {
        StringBuilder sb = new StringBuilder();
        sb.append(text).append("\n");
        for (int i = 0; i < options.length; i++) {
            sb.append((char) ('A' + i)).append(". ").append(options[i]).append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    public String toString() {
        return "Q: " + text + " (Answer: " + getCorrectAnswerText() + ")";
    }
}
