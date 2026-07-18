package com.universeexe.verityvoice.client.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;

@OnlyIn(Dist.CLIENT)
public final class TargetDataLineMicrophoneSource implements MicrophoneSource {
    public static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            16000.0f,
            16,
            1,
            2,
            16000.0f,
            false
    );

    private final String configuredDevice;
    private TargetDataLine line;
    private String resolvedName = MicrophoneDeviceManager.DEFAULT;

    public TargetDataLineMicrophoneSource(String configuredDevice) {
        this.configuredDevice = configuredDevice == null ? MicrophoneDeviceManager.DEFAULT : configuredDevice;
    }

    @Override
    public boolean open() throws Exception {
        close();
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
        Mixer.Info mixerInfo = MicrophoneDeviceManager.findMixer(configuredDevice);
        if (mixerInfo != null) {
            Mixer mixer = AudioSystem.getMixer(mixerInfo);
            line = (TargetDataLine) mixer.getLine(info);
            resolvedName = mixerInfo.getName();
        } else {
            line = (TargetDataLine) AudioSystem.getLine(info);
            resolvedName = MicrophoneDeviceManager.DEFAULT;
        }
        line.open(FORMAT, 4096);
        return line.isOpen();
    }

    @Override
    public void start() {
        if (line != null && !line.isActive()) {
            line.start();
        }
    }

    @Override
    public void stop() {
        if (line != null) {
            try {
                line.stop();
                line.flush();
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws Exception {
        if (line == null || !line.isOpen()) {
            return -1;
        }
        return line.read(buffer, offset, length);
    }

    @Override
    public void close() {
        if (line != null) {
            try {
                line.stop();
            } catch (Exception ignored) {
            }
            try {
                line.close();
            } catch (Exception ignored) {
            }
            line = null;
        }
    }

    @Override
    public boolean isOpen() {
        return line != null && line.isOpen();
    }

    @Override
    public String deviceName() {
        return resolvedName;
    }
}
