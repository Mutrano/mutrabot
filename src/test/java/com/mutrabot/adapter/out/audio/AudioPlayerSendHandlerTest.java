package com.mutrabot.adapter.out.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AudioPlayerSendHandlerTest {

    private final AudioPlayer player = mock(AudioPlayer.class);

    private static byte[] bytesOf(ByteBuffer buffer) {
        byte[] out = new byte[buffer.remaining()];
        buffer.get(out);
        return out;
    }

    @Test
    void returnsExactlyTheFrameBytesProducedByPlayer() {
        when(player.provide(any(MutableAudioFrame.class))).thenAnswer(invocation -> {
            MutableAudioFrame frame = invocation.getArgument(0);
            frame.store(new byte[] {1, 2, 3, 4}, 0, 4);
            return true;
        });
        AudioPlayerSendHandler handler = new AudioPlayerSendHandler(player);

        boolean provided = handler.canProvide();

        assertThat(provided).isTrue();
        assertThat(bytesOf(handler.provide20MsAudio())).containsExactly(1, 2, 3, 4);
    }

    @Test
    void reusesBufferForDifferentFrameSizes() {
        AtomicInteger calls = new AtomicInteger();
        when(player.provide(any(MutableAudioFrame.class))).thenAnswer(invocation -> {
            MutableAudioFrame frame = invocation.getArgument(0);
            byte[] data = calls.incrementAndGet() == 1
                    ? new byte[] {10, 11, 12}
                    : new byte[] {20, 21, 22, 23, 24};
            frame.store(data, 0, data.length);
            return true;
        });
        AudioPlayerSendHandler handler = new AudioPlayerSendHandler(player);

        handler.canProvide();
        assertThat(bytesOf(handler.provide20MsAudio())).containsExactly(10, 11, 12);

        handler.canProvide();
        assertThat(bytesOf(handler.provide20MsAudio())).containsExactly(20, 21, 22, 23, 24);
    }

    @Test
    void declaresOpusOutput() {
        AudioPlayerSendHandler handler = new AudioPlayerSendHandler(player);

        assertThat(handler.isOpus()).isTrue();
    }
}
