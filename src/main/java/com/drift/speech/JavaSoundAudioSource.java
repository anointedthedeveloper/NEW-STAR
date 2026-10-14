package com.drift.speech;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import org.springframework.stereotype.Component;

/** Real microphone capture through javax.sound. Opening a line is the only moment audio capture begins. */
@Component
public class JavaSoundAudioSource implements AudioSource.Factory {
    static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);

    @Override
    public List<String> devices() {
        List<String> out = new ArrayList<>();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
            try {
                if (AudioSystem.getMixer(mi).isLineSupported(info)) out.add(mi.getName());
            } catch (RuntimeException ignored) {
                // device vanished while enumerating
            }
        }
        return out;
    }

    @Override
    public AudioSource open(String deviceName) throws IOException {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        try {
            TargetDataLine line = null;
            if (deviceName != null && !deviceName.isBlank()) {
                for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                    if (mi.getName().equals(deviceName) && AudioSystem.getMixer(mi).isLineSupported(info)) {
                        line = (TargetDataLine) AudioSystem.getMixer(mi).getLine(info);
                        break;
                    }
                }
                if (line == null) throw new IOException("Microphone \"" + deviceName + "\" is not available");
            } else {
                if (!AudioSystem.isLineSupported(info)) throw new IOException("No microphone supporting 16 kHz mono capture was found");
                line = (TargetDataLine) AudioSystem.getLine(info);
            }
            line.open(FORMAT, 8192);
            line.start();
            TargetDataLine opened = line;
            return new AudioSource() {
                @Override public int read(byte[] buffer) { return opened.read(buffer, 0, buffer.length); }
                @Override public void close() { opened.stop(); opened.close(); }
            };
        } catch (LineUnavailableException | SecurityException | IllegalArgumentException e) {
            throw new IOException("Cannot open the microphone: " + e.getMessage()
                    + ". On Windows check Settings > Privacy & security > Microphone and allow desktop apps.", e);
        }
    }
}
