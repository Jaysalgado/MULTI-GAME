package com.networks.p2;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.security.SecureRandom;
import java.util.TimerTask;
import java.util.Timer;
import javax.swing.*;

public class ClientWindow implements ActionListener
{
    private JButton poll;
    private JButton submit;
    private JRadioButton options[];
    private ButtonGroup optionGroup;
    private JTextArea question;
    private JLabel timer;
    private JLabel score;
    private JLabel feedbackLabel;

    private TimerTask clock;
    private String answer = "";

    private JFrame window;

    private static SecureRandom random = new SecureRandom();

    private ClientControl gameManager;

    // write setters and getters as you need

    public ClientWindow()
    {
//        JOptionPane.showMessageDialog(window, "This is a trivia game");
        gameManager = new ClientControl();

        window = new JFrame("Trivia");
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setSize(500, 500);
        window.setLocationRelativeTo(null);
        window.setResizable(false);

        // Main container
        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        mainPanel.setBackground(new Color(14, 52, 160));

        // Question
        question = new JTextArea("Waiting for the game to begin...");
        question.setLineWrap(true);
        question.setWrapStyleWord(true);
        question.setEditable(false);
        question.setOpaque(false); // transparent, like a label
        question.setForeground(Color.WHITE);
        question.setFont(new Font("Arial", Font.BOLD, 18));
        question.setFocusable(false); // no cursor when clicked
        question.setMaximumSize(new Dimension(450, 80));
        question.setPreferredSize(new Dimension(450, 80));

// Wrap question in a panel to center it
        JPanel questionPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
        questionPanel.setBackground(mainPanel.getBackground());
        questionPanel.add(question);

// Add to main panel
        mainPanel.add(questionPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));


        // Options
        JPanel optionsPanel = new JPanel();
        optionsPanel.setLayout(new GridLayout(4, 1, 10, 10));
        optionsPanel.setBackground(mainPanel.getBackground());
        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();

        for (int i = 0; i < options.length; i++) {
            options[i] = new JRadioButton("Option " + (i + 1));
            options[i].setActionCommand("Option " + (i + 1));
            options[i].addActionListener(this);
            options[i].setEnabled(false);
            options[i].setFont(new Font("Arial", Font.PLAIN, 14));
            options[i].setBackground(mainPanel.getBackground());
            options[i].setForeground(Color.LIGHT_GRAY);
            optionGroup.add(options[i]);
            optionsPanel.add(options[i]);
        }

        mainPanel.add(optionsPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Timer and Score Panel
        JPanel infoPanel = new JPanel(new BorderLayout());
        infoPanel.setBackground(mainPanel.getBackground());

        score = new JLabel("SCORE: " + ClientControl.getScore());
        score.setForeground(Color.GREEN);
        score.setFont(new Font("Arial", Font.BOLD, 14));
        infoPanel.add(score, BorderLayout.WEST);

        timer = new JLabel("TIMER: 30s");
        timer.setForeground(Color.RED);
        timer.setFont(new Font("Arial", Font.BOLD, 14));
        infoPanel.add(timer, BorderLayout.EAST);

        mainPanel.add(infoPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        feedbackLabel = new JLabel(""); // start empty
        feedbackLabel.setFont(new Font("Arial", Font.BOLD, 20));
        feedbackLabel.setForeground(Color.YELLOW);
        feedbackLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        feedbackLabel.setHorizontalAlignment(SwingConstants.CENTER);
        feedbackLabel.setMaximumSize(new Dimension(400, 30));
        mainPanel.add(feedbackLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        // Buttons
        JPanel buttonPanel = new JPanel();
        buttonPanel.setBackground(mainPanel.getBackground());

        poll = new JButton("Buzz");
        poll.setFont(new Font("Arial", Font.BOLD, 18)); // Larger text
        poll.setPreferredSize(new Dimension(200, 50));  // Wider and taller
        poll.addActionListener(this);
        poll.setBackground(Color.RED);
        poll.setEnabled(false);
        buttonPanel.add(poll);

        submit = new JButton("Submit");
        submit.setFont(new Font("Arial", Font.BOLD, 18));
        submit.setPreferredSize(new Dimension(200, 50));
        submit.addActionListener(this);
        submit.setEnabled(false);
        buttonPanel.add(submit);

        mainPanel.add(buttonPanel);


        window.add(mainPanel);
        window.setVisible(true);


        ClientControl.setGameStateListener(new GameState() {
            @Override
            public void onCanBuzzChanged(boolean canBuzz) {
                SwingUtilities.invokeLater(() ->
                        poll.setEnabled(canBuzz)
                );
            }

            @Override
            public void onCanAnswerChanged(boolean canAnswer) {
                SwingUtilities.invokeLater(() -> {

                    submit.setEnabled(canAnswer);

                    for (JRadioButton option : options) {
                        option.setEnabled(canAnswer);
                    }
                    if (canAnswer) {
                        startPhaseTimer(10, "ANSWER");
                    }

                });
            }

            @Override
            public void onQuestionReceived (String[] q) {
                SwingUtilities.invokeLater(() -> {
                    score.setText("SCORE: " + ClientControl.getScore());
                    question.setText(q[0]);
                    optionGroup.clearSelection();
                    int  i = 1;
                    for (JRadioButton option : options) {
                        option.setText(q[i]);
                        i++;
                    }
                    startPhaseTimer(15, "BUZZ");
                });
            }
        });

    }

    // this method is called when you check/uncheck any radio button
    // this method is called when you press either of the buttons- submit/poll
    @Override
    public void actionPerformed(ActionEvent e)
    {
//        System.out.println("You clicked " + e.getActionCommand());
        // input refers to the radio button you selected or button you clicked
        String input = e.getActionCommand();
        switch(input)
        {
            case "Option 1":
                answer = "0";
                break;
            case "Option 2":
                answer = "1";
                break;
            case "Option 3":
                answer = "2";
                break;
            case "Option 4":
                answer = "3";
                break;
            case "Buzz":
                gameManager.buzz();
                break;
            case "Submit":
                if (!answer.isEmpty()) {
                    gameManager.sendAnswer(answer);
                    answer = "";
                } else
                    System.out.println("Please select an option before submitting");
                break;
            default:
                System.out.println("Button error");
        }

        // test code below to demo enable/disable components
        // DELETE THE CODE BELOW FROM HERE***
//        if(poll.isEnabled())
//        {
////          poll.setEnabled(false);
//            gameManager.buzz();
//            submit.setEnabled(true);
//        }
//        else
//        {
//            poll.setEnabled(true);
//            submit.setEnabled(false);
//        }


        // you can also enable disable radio buttons
//		options[random.nextInt(4)].setEnabled(false);
//		options[random.nextInt(4)].setEnabled(true);
        // TILL HERE ***

    }

    public void startPhaseTimer(int duration, String phaseName) {
        if (clock != null) {
            clock.cancel();
        }

        clock = new TimerTask() {
            int timeLeft = duration;

            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (timeLeft < 0) {
                        timer.setText(phaseName + " phase ended.");
                        poll.setEnabled(false);
                        submit.setEnabled(false);
                        for (JRadioButton option : options) option.setEnabled(false);
                        this.cancel();
                        return;
                    }

                    timer.setText(phaseName + ": " + timeLeft + "s");
                    timer.setFont(new Font("Arial", Font.BOLD, 14));
                    timer.setForeground(timeLeft < 6 ? Color.RED : Color.GREEN);
                    window.repaint();
                    timeLeft--;
                });
            }
        };

        Timer t = new Timer();
        t.schedule(clock, 0, 1000);
    }

}