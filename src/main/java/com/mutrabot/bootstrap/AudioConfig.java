package com.mutrabot.bootstrap;

import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.clients.Web;

public final class AudioConfig {

    private AudioConfig() {
    }

    public static AudioPlayerManager playerManager() {
        return playerManager(null, null, null);
    }

    public static AudioPlayerManager playerManager(
            String oauthRefreshToken, String poToken, String visitorData) {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_OPUS);
        manager.getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);

        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true);
        if (poToken != null && !poToken.isBlank() && visitorData != null && !visitorData.isBlank()) {
            Web.setPoTokenAndVisitorData(poToken, visitorData);
        }
        if (oauthRefreshToken != null && !oauthRefreshToken.isBlank()) {
            youtube.useOauth2(oauthRefreshToken, true);
        }

        manager.registerSourceManager(youtube);
        AudioSourceManagers.registerRemoteSources(
                manager,
                com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioSourceManager.class);
        return manager;
    }
}
