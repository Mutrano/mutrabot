package com.mutrabot.bootstrap;

import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;

import java.time.Duration;

final class YoutubeOAuth {

    private static final Duration TIMEOUT = Duration.ofMinutes(10);

    private YoutubeOAuth() {
    }

    static void authorize() {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        YoutubeAudioSourceManager youtube = new YoutubeAudioSourceManager(true);
        manager.registerSourceManager(youtube);
        youtube.useOauth2(null, false);
        System.out.println();
        System.out.println("Siga as instrucoes acima no log (URL + codigo) e autorize com uma conta BURNER.");
        System.out.println("Aguardando autorizacao por ate " + TIMEOUT.toMinutes() + " minutos...");
        System.out.println();

        long deadline = System.currentTimeMillis() + TIMEOUT.toMillis();
        while (System.currentTimeMillis() < deadline) {
            String refreshToken = youtube.getOauth2RefreshToken();
            if (refreshToken != null && !refreshToken.isBlank()) {
                System.out.println();
                System.out.println("==================================================");
                System.out.println("Autorizado. Cole a linha abaixo no arquivo .env:");
                System.out.println("YOUTUBE_REFRESH_TOKEN=" + refreshToken);
                System.out.println("==================================================");
                manager.shutdown();
                return;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.err.println("Tempo esgotado sem autorizacao. Rode de novo.");
        manager.shutdown();
    }
}
