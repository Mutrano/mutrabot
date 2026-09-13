package com.mutrabot.bootstrap;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;

public final class AudioConfig {

    private AudioConfig() {
    }

    public static AudioPlayerManager playerManager() {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_OPUS);
        manager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        manager.setFrameBufferDuration(15_000);
        AudioSourceManagers.registerLocalSource(manager);
        AudioSourceManagers.registerRemoteSources(
                manager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
        return manager;
    }
}
