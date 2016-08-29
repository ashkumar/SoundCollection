package AudioCapMaven.Capture;

import javax.swing.*;

import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.compress.compressors.CompressorOutputStream;
import org.apache.commons.compress.compressors.CompressorStreamFactory;
import org.apache.commons.compress.utils.IOUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;
import java.awt.event.*;
import java.io.*;
import javax.sound.sampled.*;
 
public class App extends JFrame {
 
  protected boolean running;
  ByteArrayOutputStream out;
  Long interval = null;
  String saveDirectory = null;
  
  private void setWriteInterval(Long interval){
	  this.interval = interval;
  }
 
  private void setSaveDirectory(String dir){
	  this.saveDirectory = dir;
  }
  
  public App() {
    super("Capture Sound Demo");
    setDefaultCloseOperation(EXIT_ON_CLOSE);
    Container content = getContentPane();
 
    final JButton capture = new JButton("Capture");
    final JButton stop = new JButton("Stop");
    final JButton play = new JButton("Play");
 
    capture.setEnabled(true);
    stop.setEnabled(false);
    play.setEnabled(false);
 
    ActionListener captureListener = 
        new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        capture.setEnabled(false);
        stop.setEnabled(true);
        play.setEnabled(false);
        captureAudio();
      }
    };
    capture.addActionListener(captureListener);
    content.add(capture, BorderLayout.NORTH);
 
    ActionListener stopListener = 
        new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        capture.setEnabled(true);
        stop.setEnabled(false);
        play.setEnabled(true);
        running = false;
      }
    };
    stop.addActionListener(stopListener);
    content.add(stop, BorderLayout.CENTER);
 
    ActionListener playListener = 
        new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        playAudio();
      }
    };
    play.addActionListener(playListener);
    content.add(play, BorderLayout.SOUTH);
  }
 
  private void captureAudio() {
    try {
      final AudioFormat format = getFormat();
      DataLine.Info info = new DataLine.Info(
        TargetDataLine.class, format);
      final TargetDataLine line = (TargetDataLine)
        AudioSystem.getLine(info);
      line.open(format);
      line.start();
      Runnable runner = new Runnable() {
        int bufferSize = (int)format.getSampleRate() 
          * format.getFrameSize();
        byte buffer[] = new byte[bufferSize];
        long start = System.nanoTime();
        public void run() {
          out = new ByteArrayOutputStream();
          running = true;
          OutputStream os = null;
          int fileindex = 0;
          try {
            while (running) {
              int count = 
                line.read(buffer, 0, buffer.length);
              if (count > 0) {
                out.write(buffer, 0, count);
                long now = System.nanoTime();
				long diff = now - start;
				System.out.println(now  + " "  + start + " "+ diff);
				if (diff > interval){
					System.out.println("Writing now");
					start = System.nanoTime();
					//time to write to file
					savetoBzip2(fileindex, out);
					out.reset();
					fileindex++;
				}
              }
            }
            out.close();
          } catch (IOException e) {
            System.err.println("I/O problems: " + e);
            System.exit(-1);
          }
        }
      };
      Thread captureThread = new Thread(runner);
      captureThread.start();
    } catch (LineUnavailableException e) {
      System.err.println("Line unavailable: " + e);
      System.exit(-2);
    }
  }
  
  private void savetoBzip2(int fileIndex, ByteArrayOutputStream input){
	  String filename = this.saveDirectory + fileIndex + ".bz2";
	  System.out.println(filename);
	  try {
		final OutputStream out = new FileOutputStream(filename);
		  CompressorOutputStream cos = new CompressorStreamFactory().createCompressorOutputStream("bzip2", out);
		  InputStream isFromFirstData = new ByteArrayInputStream(input.toByteArray()); 
		  IOUtils.copy(isFromFirstData, cos);
		  cos.close();
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (CompressorException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
  }
 
  private void playAudio() {
    try {
      byte audio[] = out.toByteArray();
      InputStream input = 
        new ByteArrayInputStream(audio);
      final AudioFormat format = getFormat();
      final AudioInputStream ais = 
        new AudioInputStream(input, format, 
        audio.length / format.getFrameSize());
      DataLine.Info info = new DataLine.Info(
        SourceDataLine.class, format);
      final SourceDataLine line = (SourceDataLine)
        AudioSystem.getLine(info);
      line.open(format);
      line.start();
 
      Runnable runner = new Runnable() {
        int bufferSize = (int) format.getSampleRate() 
          * format.getFrameSize();
        byte buffer[] = new byte[bufferSize];
  
        public void run() {
          
          try {
            int count;
            while ((count = ais.read(
                buffer, 0, buffer.length)) != -1) {
              if (count > 0) {
                line.write(buffer, 0, count);
                
              }
            }
            line.drain();
            line.close();
          } catch (IOException e) {
            System.err.println("I/O problems: " + e);
            System.exit(-3);
          }
        }
      };
      Thread playThread = new Thread(runner);
      playThread.start();
    } catch (LineUnavailableException e) {
      System.err.println("Line unavailable: " + e);
      System.exit(-4);
    } 
  }
 
  private AudioFormat getFormat() {
	JSONParser parser = new JSONParser();
	Long sampleRateL = null;
	Long sampleSizeInBitsL = null;
	Long channelsL = null;
	Long timeToSaveL = null;
	String dir = null;
	try {
		Object obj = parser.parse(new FileReader("./target/classes/AudioCapMaven/Capture/config.json"));
		JSONObject jsonObject = (JSONObject) obj;
		sampleRateL = (Long) jsonObject.get("sampleRate");
		sampleSizeInBitsL = (Long) jsonObject.get("sampleSizeInBits");
		channelsL = (Long) jsonObject.get("channels");
		timeToSaveL = (Long) jsonObject.get("timeToSave");
		dir = (String) jsonObject.get("saveDirectory");
		setWriteInterval(timeToSaveL);
		setSaveDirectory(dir);
		
	} catch (FileNotFoundException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (IOException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	} catch (ParseException e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    float sampleRate = sampleRateL.floatValue();
    int sampleSizeInBits = sampleSizeInBitsL.intValue();
    int channels = channelsL.intValue();
    boolean signed = true;
    boolean bigEndian = true;
    return new AudioFormat(sampleRate, 
      sampleSizeInBits, channels, signed, bigEndian);
  }
 
  public static void main(String args[]) {
    JFrame frame = new App();
    frame.pack();
    frame.show();
  }
}