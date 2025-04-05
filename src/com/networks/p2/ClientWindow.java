package com.networks.p2;

import java.awt.Color;
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
    private JLabel question;
    private JLabel timer;
    private JLabel score;
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
        question = new JLabel("Waiting for the game to begin ..."); // represents the question
        window.add(question);
        question.setBounds(10, 5, 350, 100);;

        options = new JRadioButton[4];
        optionGroup = new ButtonGroup();
        for(int index=0; index<options.length; index++)
        {
            options[index] = new JRadioButton("Option " + (index+1));  // represents an option
            options[index].setActionCommand("Option " + (index + 1));
            // if a radio button is clicked, the event would be thrown to this class to handle
            options[index].addActionListener(this);
            options[index].setBounds(10, 110+(index*20), 350, 20);
            window.add(options[index]);
            optionGroup.add(options[index]);
            options[index].setEnabled(false);  // disable the radio buttons until the question is received
        }

        timer = new JLabel("TIMER");  // represents the countdown shown on the window
        timer.setBounds(250, 250, 100, 20);
        clock = new TimerCode(30);  // represents clocked task that should run after X seconds
        Timer t = new Timer();  // event generator
        t.schedule(clock, 0, 1000); // clock is called every second
        window.add(timer);


        score = new JLabel("SCORE"); // represents the score
        score.setBounds(50, 250, 100, 20);
        window.add(score);

        poll = new JButton("Poll");  // button that use clicks/ like a buzzer
        poll.setBounds(10, 300, 100, 20);
        poll.addActionListener(this);  // calls actionPerformed of this class
        window.add(poll);

        submit = new JButton("Submit");  // button to submit their answer
        submit.setBounds(200, 300, 100, 20);
        submit.addActionListener(this);  // calls actionPerformed of this class
        window.add(submit);
        submit.setEnabled(false);


        window.setSize(400,400);
        window.setBounds(50, 50, 400, 400);
        window.setLayout(null);
        window.setVisible(true);
        window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        window.setResizable(false);

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

                });
            }

            @Override
            public void onQuestionReceived (String[] q) {
                SwingUtilities.invokeLater(() -> {
                    question.setText(q[0]);
                    optionGroup.clearSelection();
                    int  i = 1;
                    for (JRadioButton option : options) {
                        option.setText(q[i]);
                        i++;
                    }
                });
            }
        });

    }

    // this method is called when you check/uncheck any radio button
    // this method is called when you press either of the buttons- submit/poll
    @Override
    public void actionPerformed(ActionEvent e)
    {
        System.out.println("You clicked " + e.getActionCommand());
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
            case "Poll":
                gameManager.buzz();
                break;
            case "Submit":
                if (!answer.isEmpty()) {
                    System.out.println("Submitting answeringggg: " + answer);
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

    // this class is responsible for running the timer on the window
    public class TimerCode extends TimerTask
    {
        private int duration;  // write setters and getters as you need
        public TimerCode(int duration)
        {
            this.duration = duration;
        }
        @Override
        public void run()
        {
            if(duration < 0)
            {
                timer.setText("Timer expired");
                window.repaint();
                this.cancel();  // cancel the timed task
                return;
                // you can enable/disable your buttons for poll/submit here as needed
            }

            if(duration < 6)
                timer.setForeground(Color.red);
            else
                timer.setForeground(Color.black);

            timer.setText(duration+"");
            duration--;
            window.repaint();
        }
    }

}