package com.mutrabot.adapter.out.audio;

import com.sedmelluq.discord.lavaplayer.format.AudioDataFormat;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import net.dv8tion.jda.api.audio.AudioSendHandler;

import java.nio.ByteBuffer;
import java.util.Objects;

public final class AudioPlayerSendHandler implements AudioSendHandler {

    private final AudioPlayer player;
    private final MutableAudioFrame frame;
    private final ByteBuffer buffer;

    public AudioPlayerSendHandler(AudioPlayer player) {
        this.player = Objects.requireNonNull(player, "player");
        AudioDataFormat format = StandardAudioDataFormats.DISCORD_OPUS;
        this.frame = new MutableAudioFrame();
        this.frame.setFormat(format);
        this.buffer = ByteBuffer.allocate(format.maximumChunkSize());
    }

    @Override
    public boolean canProvide() {
        return player.provide(frame);
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        buffer.clear();
        buffer.put(frame.getData(), 0, frame.getDataLength());
        buffer.flip();
        return buffer;
    }

    @Override
    public boolean isOpus() {
        return true;
    }
}
