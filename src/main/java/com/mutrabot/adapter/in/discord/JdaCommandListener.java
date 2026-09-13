package com.mutrabot.adapter.in.discord;

import com.mutrabot.application.port.in.HelpUseCase;
import com.mutrabot.application.port.in.JoinUseCase;
import com.mutrabot.application.port.in.LeaveUseCase;
import com.mutrabot.application.port.in.ListQueueUseCase;
import com.mutrabot.application.port.in.LivestreamJoinUseCase;
import com.mutrabot.application.port.in.LivestreamLeaveUseCase;
import com.mutrabot.application.port.in.PingUseCase;
import com.mutrabot.application.port.in.PlayUseCase;
import com.mutrabot.application.port.in.ResumeUseCase;
import com.mutrabot.application.port.in.SkipUseCase;
import com.mutrabot.application.port.in.StopUseCase;
import com.mutrabot.application.port.out.InteractionResponderPort;
import com.mutrabot.application.service.BotMessages;
import com.mutrabot.domain.command.BotCommand;
import com.mutrabot.domain.model.GuildId;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executor;

public final class JdaCommandListener extends ListenerAdapter {

    private final Executor executor;
    private final PlayUseCase playUseCase;
    private final JoinUseCase joinUseCase;
    private final LeaveUseCase leaveUseCase;
    private final StopUseCase stopUseCase;
    private final ResumeUseCase resumeUseCase;
    private final SkipUseCase skipUseCase;
    private final ListQueueUseCase listQueueUseCase;
    private final PingUseCase pingUseCase;
    private final HelpUseCase helpUseCase;
    private final LivestreamJoinUseCase livestreamJoinUseCase;
    private final LivestreamLeaveUseCase livestreamLeaveUseCase;
    private final JdaAnnouncer announcer;

    public JdaCommandListener(
            Executor executor,
            PlayUseCase playUseCase,
            JoinUseCase joinUseCase,
            LeaveUseCase leaveUseCase,
            StopUseCase stopUseCase,
            ResumeUseCase resumeUseCase,
            SkipUseCase skipUseCase,
            ListQueueUseCase listQueueUseCase,
            PingUseCase pingUseCase,
            HelpUseCase helpUseCase,
            LivestreamJoinUseCase livestreamJoinUseCase,
            LivestreamLeaveUseCase livestreamLeaveUseCase,
            JdaAnnouncer announcer) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.playUseCase = Objects.requireNonNull(playUseCase, "playUseCase");
        this.joinUseCase = Objects.requireNonNull(joinUseCase, "joinUseCase");
        this.leaveUseCase = Objects.requireNonNull(leaveUseCase, "leaveUseCase");
        this.stopUseCase = Objects.requireNonNull(stopUseCase, "stopUseCase");
        this.resumeUseCase = Objects.requireNonNull(resumeUseCase, "resumeUseCase");
        this.skipUseCase = Objects.requireNonNull(skipUseCase, "skipUseCase");
        this.listQueueUseCase = Objects.requireNonNull(listQueueUseCase, "listQueueUseCase");
        this.pingUseCase = Objects.requireNonNull(pingUseCase, "pingUseCase");
        this.helpUseCase = Objects.requireNonNull(helpUseCase, "helpUseCase");
        this.livestreamJoinUseCase = Objects.requireNonNull(livestreamJoinUseCase, "livestreamJoinUseCase");
        this.livestreamLeaveUseCase = Objects.requireNonNull(livestreamLeaveUseCase, "livestreamLeaveUseCase");
        this.announcer = Objects.requireNonNull(announcer, "announcer");
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Optional<BotCommand> mapped = CommandMapper.map(event);
        if (mapped.isEmpty()) {
            event.reply(BotMessages.unknownCommand()).setEphemeral(true).queue();
            return;
        }
        BotCommand command = mapped.get();
        Guild guild = event.getGuild();
        if (guild != null) {
            announcer.register(new GuildId(guild.getId()), event.getChannel());
        }
        if (command instanceof BotCommand.PlayCmd || command instanceof BotCommand.LivestreamJoinCmd) {
            event.deferReply().queue();
        }
        InteractionResponderPort responder = new JdaResponderAdapter(event);
        executor.execute(() -> dispatch(command, responder));
    }

    void dispatch(BotCommand command, InteractionResponderPort responder) {
        switch (command) {
            case BotCommand.PlayCmd play -> playUseCase.play(play, responder);
            case BotCommand.JoinCmd join -> joinUseCase.join(join, responder);
            case BotCommand.LeaveCmd leave -> leaveUseCase.leave(leave, responder);
            case BotCommand.StopCmd stop -> stopUseCase.stop(stop, responder);
            case BotCommand.ResumeCmd resume -> resumeUseCase.resume(resume, responder);
            case BotCommand.SkipCmd skip -> skipUseCase.skip(skip, responder);
            case BotCommand.QueueCmd queue -> listQueueUseCase.list(queue, responder);
            case BotCommand.PingCmd ping -> pingUseCase.ping(ping, responder);
            case BotCommand.HelpCmd help -> helpUseCase.help(help, responder);
            case BotCommand.LivestreamJoinCmd join -> livestreamJoinUseCase.join(join, responder);
            case BotCommand.LivestreamLeaveCmd leave -> livestreamLeaveUseCase.leave(leave, responder);
        }
    }
}
