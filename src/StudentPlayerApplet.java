import javax.sound.sampled.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.*;
import java.io.*;

class Player extends Panel implements Runnable {
    private static final long serialVersionUID = 1L;
    private TextField textfield;
    private TextArea textarea;
    private Font font;
    private String filename;

    Thread producer;
    Thread consumer;

    private SourceDataLine line;
    private byte[] audioBuffer;
    private AudioInputStream s;
    private int bytesRead;
    private AudioFormat format;
    private DataLine.Info info;
    private boolean ready = false;
    private int length;
    private int oneSecond;
    private BoundedBuffer buffer;

    private boolean paused = false;

    public Player(String filename){

        font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        textfield = new TextField();
        textarea = new TextArea();
        textarea.setFont(font);
        textfield.setFont(font);
        setLayout(new BorderLayout());
        add(BorderLayout.SOUTH, textfield);
        add(BorderLayout.CENTER, textarea);

        textfield.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {

                if(e.getActionCommand().toString().equals("x"))
                {
                    textarea.append("Shutting Down.. \n");
                    textfield.setText("");
                    //TODO GRACEFULLY SHUT DOWN PROGRAMME
                }
                if(e.getActionCommand().toString().equals("p"))
                {

                    //TODO Tidy Pause Method
                    if(!paused)
                    {
                        textarea.append("Pausing Audio \n");
                        textfield.setText("");
                        paused = true;
                    }

                }
                else if(e.getActionCommand().toString().equals("r"))
                {
                    //TODO Tidy Resume Method
                    if(paused)
                    {
                        textarea.append("Resumed Audio \n");
                        textfield.setText("");
                        resume();
                    }

                }
                else if(e.getActionCommand().toString().equals("q"))
                {
                    textarea.append("Raised Volume \n");
                    textfield.setText("");
                    //TODO Raise Volume
                }
                else if(e.getActionCommand().toString().equals("a"))
                {
                    textarea.append("Lowered Volume \n");
                    textfield.setText("");
                    //TODO Lower Volume
                }
                else if(e.getActionCommand().toString().equals("m"))
                {
                    textarea.append("Muted Audio \n");
                    textfield.setText("");
                    //TODO Mute Playback
                }
                else if(e.getActionCommand().toString().equals("u"))
                {
                    textarea.append("Unmuted Audio \n");
                    textfield.setText("");
                    //TODO Unmute Playback
                }
                else
                {
                    textarea.append("Invalid command \n");
                    textfield.setText("");
                }

            }
        });

        this.filename = filename;
        new Thread(this).start();
    }

    //Require Synchronized method to notify threads of change
    public synchronized void resume()
    {
        try
        {
            paused = false;
            notifyAll();
        }
        catch (Exception e)
        {

        }
    }

    public void run() {

        try {
            File file = new File(filename);
            length = (int)file.length();
            s = AudioSystem.getAudioInputStream(file);
            format = s.getFormat();
            System.out.println("Audio format: " + format.toString());

            info = new DataLine.Info(SourceDataLine.class, format);
            if (!AudioSystem.isLineSupported(info)) {
                throw new UnsupportedAudioFileException();
            }

            oneSecond = (int) (format.getChannels() * format.getSampleRate() *
                    format.getSampleSizeInBits() / 8);
            buffer = new BoundedBuffer(oneSecond * 10);
            audioBuffer = new byte[oneSecond];

            //Once Audio format, readers and writers setup start the threads
            producer = new Thread(new Producer());
            consumer = new Thread(new Consumer());
            producer.start();
            consumer.start();

            producer.join();
            consumer.join();

        } catch (UnsupportedAudioFileException e ) {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }  catch (IOException e) {
            System.out.println("Player initialisation failed");
            e.printStackTrace();
            System.exit(1);
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    public synchronized void readAudio()
    {
        try
        {
            //TODO allow the readAudio to do something in the background
            while(ready)
                wait();

            bytesRead = s.read(audioBuffer);
            //Once we get this we're done playing audio
            if(bytesRead == -1)
            {
                return;
            }

            buffer.insertChunk(bytesRead);

            ready = true;
            notifyAll();
            return;
        }

        catch (InterruptedException e)
        {
            System.out.println("Thread Interupted Exception");
            e.printStackTrace();
            System.exit(1);
        }

        catch (Exception e) {}
    }

    public synchronized void writeAudio()
    {
        try
        {
            //TODO allow the writeAudio to do something in the background
            while (!ready)
                wait();

            while(paused)
                wait();

            //Once we get this we're done reading audio
            if(bytesRead == -1)
            {
                return;
            }

            line.write(audioBuffer, 0, buffer.removeChunk());

            ready = false;
            notifyAll();
            return;
        }
        catch (InterruptedException e)
        {
            System.out.println("Thread Interrupted Exception");
            e.printStackTrace();
            System.exit(1);
        }
    }

    private class Producer extends Thread
    {
        synchronized public void run()
        {
            for(int i = 0; i < length; i += oneSecond) {
                readAudio();
            }
            System.out.println("Done reading from file");
        }
    }

    private class Consumer extends Thread
    {
        synchronized public void run()
        {
            try
            {
                line = (SourceDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                for(int i = 0; i < length; i += oneSecond) {
                    writeAudio();
                }
                System.out.println("Done writing to device");

                line.drain();
                line.stop();
                line.close();
            }
            catch (LineUnavailableException e)
            {
                System.out.println("Player initialisation failed");
                e.printStackTrace();
                System.exit(1);
            }
        }
    }

    private class BoundedBuffer
    {
        int nextIn = 0;
        int nextOut = 0;
        int size;
        int occupied = 0;
        int ins;
        int outs;
        boolean dataAvailable;
        boolean roomAvailable;
        int [] bufferArray;

        BoundedBuffer(int size)
        {
            bufferArray = new int[10];
            this.size = size;
        }

        synchronized void insertChunk(int data)
        {
            System.out.println("Inserted at " + nextIn + " and occupied is " + occupied);
            try {
                while (occupied == 10) wait();

                bufferArray[nextIn] = data;
                nextIn++;
                if(nextIn == 10) nextIn %= 10;
                occupied++;
                notifyAll();
            }
            catch (InterruptedException e)
            {
                System.out.println("Thread Interrupted Exception");
                e.printStackTrace();
                System.exit(1);
            }


        }

        synchronized int removeChunk()
        {
            outs = 0;
            System.out.println("Removed at " + nextOut + " and occupied is " + occupied);
            try
            {
                while (occupied == 0) wait();

                outs = bufferArray[nextOut];
                nextOut++;
                if(nextOut == 10) nextOut %= 10;
                occupied--;
                notifyAll();
            }

            catch (InterruptedException e)
            {
                System.out.println("Thread Interrupted Exception");
                e.printStackTrace();
                System.exit(1);
            }
            return outs;
        }
    }
}

public class StudentPlayerApplet extends Applet
{
    private static final long serialVersionUID = 1L;
    public void init() {
        setLayout(new BorderLayout());
        add(BorderLayout.CENTER, new Player(getParameter("file")));
    }
}

