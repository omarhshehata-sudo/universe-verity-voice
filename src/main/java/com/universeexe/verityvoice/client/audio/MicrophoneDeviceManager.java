package com.universeexe.verityvoice.client.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@OnlyIn(Dist.CLIENT)
public final class MicrophoneDeviceManager {
    public static final String DEFAULT = "DEFAULT";

    private MicrophoneDeviceManager() {
    }

    public static List<String> listCaptureDevices() {
        List<String> names = new ArrayList<>();
        names.add(DEFAULT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            try {
                Mixer mixer = AudioSystem.getMixer(info);
                if (mixer.isLineSupported(new DataLine.Info(TargetDataLine.class, TargetDataLineMicrophoneSource.FORMAT))) {
                    names.add(info.getName());
                }
            } catch (Exception ignored) {
                // Skip unreadable mixers.
            }
        }
        return names;
    }

    public static Mixer.Info findMixer(String configuredName) {
        if (configuredName == null || configuredName.isBlank() || DEFAULT.equalsIgnoreCase(configuredName)) {
            return null;
        }
        String want = configuredName.toLowerCase(Locale.ROOT);
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            if (info.getName().toLowerCase(Locale.ROOT).contains(want)
                    || info.getName().equalsIgnoreCase(configuredName)) {
                return info;
            }
        }
        return null;
    }
}
