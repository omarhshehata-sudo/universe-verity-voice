package com.universeexe.verityvoice.client.audio;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Abstraction over local microphone capture so a future Simple Voice Chat
 * adapter can supply PCM without exclusive device ownership.
 */
@OnlyIn(Dist.CLIENT)
public interface MicrophoneSource extends AutoCloseable {
    boolean open() throws Exception;

    int read(byte[] buffer, int offset, int length) throws Exception;

    void start() throws Exception;

    void stop();

    @Override
    void close();

    boolean isOpen();

    String deviceName();
}
