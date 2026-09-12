package com.mutrabot.bootstrap;

import com.mutrabot.adapter.in.discord.JdaAnnouncer;
import com.mutrabot.adapter.in.discord.JdaCommandListener;
import com.mutrabot.adapter.out.audio.LavaplayerPlaybackAdapter;
import com.mutrabot.adapter.out.audio.LavaplayerResolverAdapter;
import com.mutrabot.adapter.out.audio.OEmbedMetadataLookup;
import com.mutrabot.adapter.out.audio.TrackAudioRegistry;
import com.mutrabot.adapter.out.audio.YtDlpProcessRunner;
import com.mutrabot.adapter.out.audio.YtDlpResolver;
import com.mutrabot.adapter.out.persistence.InMemoryQueueAdapter;
import com.mutrabot.application.service.HelpCommandService;
import com.mutrabot.application.service.IdleDisconnectService;
import com.mutrabot.application.service.ListQueueService;
import com.mutrabot.application.service.PingCommandService;
import com.mutrabot.application.service.PlaybackFailureService;
import com.mutrabot.application.service.PlayCommandService;
import com.mutrabot.application.service.ResumeCommandService;
import com.mutrabot.application.service.SkipCommandService;
import com.mutrabot.application.service.StopCommandService;
import com.mutrabot.application.service.TrackFinishedService;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;

public final class BotApplication {

    private BotApplication() {
    }

    public static void main(String[] args) {
        if (args.length > 0 && "--youtube-oauth".equals(args[0])) {
            YoutubeOAuth.authorize();
            return;
        }
        DotEnv dotEnv = DotEnv.load();
        String token = dotEnv.get("DISCORD_TOKEN");
        if (token == null || token.isBlank()) {
            System.err.println("Defina DISCORD_TOKEN no arquivo .env ou em uma variável de ambiente.");
            return;
        }
        JDA jda = JdaConfig.build(token);
        wire(jda, dotEnv);
        Runtime.getRuntime().addShutdownHook(new Thread(ExecutorConfig::shutdown, "shutdown"));
    }

    static void wire(JDA jda, DotEnv dotEnv) {
        AudioPlayerManager manager = AudioConfig.playerManager(
                dotEnv.get("YOUTUBE_REFRESH_TOKEN"),
                dotEnv.get("YOUTUBE_PO_TOKEN"),
                dotEnv.get("YOUTUBE_VISITOR_DATA"));
        TrackAudioRegistry registry = new TrackAudioRegistry();
        InMemoryQueueAdapter queues = new InMemoryQueueAdapter();
        LavaplayerResolverAdapter resolver = new LavaplayerResolverAdapter(
                manager,
                registry,
                new OEmbedMetadataLookup(),
                YtDlpResolver.detect(new YtDlpProcessRunner()));
        LavaplayerPlaybackAdapter playback = new LavaplayerPlaybackAdapter(manager, () -> jda, registry);
        JdaAnnouncer announcer = new JdaAnnouncer();

        IdleDisconnectService idleDisconnect = new IdleDisconnectService(
                ExecutorConfig.idleScheduler(), queues, playback, announcer);

        PlayCommandService play = new PlayCommandService(resolver, playback, queues, idleDisconnect);
        StopCommandService stop = new StopCommandService(queues, playback);
        ResumeCommandService resume = new ResumeCommandService(queues, playback);
        SkipCommandService skip = new SkipCommandService(queues, playback, idleDisconnect);
        ListQueueService listQueue = new ListQueueService(queues);
        PingCommandService ping = new PingCommandService(
                jda::getGatewayPing, () -> jda.getRestPing().complete());
        HelpCommandService help = new HelpCommandService();

        TrackFinishedService trackFinished = new TrackFinishedService(queues, playback, announcer, idleDisconnect);
        playback.setTrackFinishedListener(trackFinished::onTrackFinished);

        PlaybackFailureService playbackFailure = new PlaybackFailureService(
                queues, playback, announcer, idleDisconnect);
        playback.setTrackFailureListener(playbackFailure::onTrackFailed);

        JdaCommandListener listener = new JdaCommandListener(
                ExecutorConfig.commandExecutor(), play, stop, resume, skip, listQueue, ping, help, announcer);
        jda.addEventListener(listener);
        registerCommands(jda, dotEnv.get("DISCORD_GUILD_ID"));
    }

    static void registerCommands(JDA jda, String devGuildId) {
        List<CommandData> commands = List.of(
                Commands.slash("play", "Toca uma música por link, busca ou playlist")
                        .addOptions(new OptionData(OptionType.STRING, "query", "Link ou nome da música", true)),
                Commands.slash("stop", "Pausa a reprodução atual"),
                Commands.slash("resume", "Retoma a reprodução pausada"),
                Commands.slash("skip", "Pula para a próxima faixa da fila"),
                Commands.slash("queue", "Mostra a fila de reprodução"),
                Commands.slash("ping", "Verifica se o bot está online"),
                Commands.slash("help", "Mostra os comandos disponíveis"));
        if (devGuildId != null && !devGuildId.isBlank()) {
            Guild guild = jda.getGuildById(devGuildId);
            if (guild != null) {
                guild.updateCommands().addCommands(commands).queue();
                return;
            }
        }
        jda.updateCommands().addCommands(commands).queue();
    }
}
