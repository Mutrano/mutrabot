package com.mutrabot.application.service;

import com.mutrabot.domain.model.GuildQueue;
import com.mutrabot.domain.model.SourceKind;
import com.mutrabot.domain.model.Track;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class BotMessages {

    private BotMessages() {
    }

    public static String nowPlaying(Track track) {
        return "▶ Tocando agora: **" + track.title() + "** (" + sourceLabel(track.source())
                + ") — pedido por " + track.requestedBy().displayName();
    }

    public static String addedToQueue(Track track, int position) {
        return "➕ Adicionado à fila (#" + position + "): **" + track.title() + "** — pedido por "
                + track.requestedBy().displayName();
    }

    public static String playlistAdded(int added, int ignored, Optional<Track> startedNow) {
        StringBuilder message = new StringBuilder("➕ Playlist adicionada: **").append(added)
                .append("** faixas (ordem original)");
        if (ignored > 0) {
            message.append(", ").append(ignored).append(" ignoradas pelo limite de ")
                    .append(GuildQueue.MAX_PLAYLIST_TRACKS);
        }
        startedNow.ifPresent(track -> message.append("\n▶ Tocando agora: **").append(track.title())
                .append("** — pedido por ").append(track.requestedBy().displayName()));
        return message.toString();
    }

    public static String notFound(String query) {
        return "🔍 Nada encontrado para \"" + query + "\". Tente outro nome ou link.";
    }

    public static String loadFailed(String query, String reason) {
        return "⚠️ Não consegui carregar \"" + query + "\": " + reason + ". A fila atual foi mantida.";
    }

    public static String voiceRequired() {
        return "🔇 Entre em um canal de voz primeiro e tente de novo.";
    }

    public static String emptyQuery() {
        return "✏️ Informe um link ou o nome de uma música para eu tocar.";
    }

    public static String paused(Track track) {
        return "⏸ Pausado: **" + track.title() + "**. Use /resume para continuar.";
    }

    public static String nothingPlaying() {
        return "ℹ️ Nada tocando no momento.";
    }

    public static String resumed(Track track) {
        return "▶ Continuando: **" + track.title() + "**.";
    }

    public static String nothingPaused() {
        return "ℹ️ Nada pausado no momento.";
    }

    public static String skippedTo(Track next) {
        return "⏭ Pulado. ▶ Tocando agora: **" + next.title() + "** — pedido por "
                + next.requestedBy().displayName();
    }

    public static String queueEndedAfterSkip() {
        return "⏭ Pulado. 📭 A fila acabou.";
    }

    public static String queueEndedAnnouncement() {
        return "📭 A fila acabou.";
    }

    public static String nothingToSkip() {
        return "ℹ️ Nada para pular.";
    }

    public static String queueEmpty() {
        return "📭 A fila está vazia. Use /play para adicionar músicas.";
    }

    public static String idleDisconnected() {
        return "💤 Saí do canal de voz após 5 minutos sem atividade. Use /play para me chamar de volta.";
    }

    public static String pong(long gatewayPingMs, long restPingMs) {
        return "🏓 pong (gateway: " + gatewayPingMs + "ms, rest: " + restPingMs + "ms)";
    }

    public static String unknownCommand() {
        return "❓ Comando não reconhecido.";
    }

    public static String help() {
        return """
                🎶 **mutrabot** — comandos disponíveis:
                /play <link ou nome> — toca uma música, busca ou playlist
                /stop — pausa a reprodução atual
                /resume — retoma a reprodução pausada
                /skip — pula para a próxima faixa da fila
                /queue — mostra a fila e quem pediu cada música
                /ping — verifica se o bot está online
                /help — mostra esta ajuda""";
    }

    public static String queueView(Optional<Track> current, List<Track> upcoming, int pageSize) {
        StringBuilder message = new StringBuilder();
        current.ifPresent(track -> message.append("🎵 Tocando agora: ").append(track.title())
                .append(" — pedido por ").append(track.requestedBy().displayName())
                .append(" [").append(durationLabel(track)).append("]"));
        if (!upcoming.isEmpty()) {
            if (!message.isEmpty()) {
                message.append('\n');
            }
            message.append("📋 Próximas (").append(upcoming.size()).append("):");
            int shown = Math.min(pageSize, upcoming.size());
            for (int i = 0; i < shown; i++) {
                Track track = upcoming.get(i);
                message.append('\n').append(i + 1).append(". ").append(track.title())
                        .append(" — ").append(track.requestedBy().displayName());
            }
            if (upcoming.size() > pageSize) {
                message.append("\n... e mais ").append(upcoming.size() - pageSize);
            }
        }
        return message.toString();
    }

    public static String durationLabel(Track track) {
        if (track.isLive()) {
            return "ao vivo";
        }
        long totalSeconds = track.duration().toSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    public static String sourceLabel(SourceKind source) {
        return switch (source) {
            case YOUTUBE -> "YouTube";
            case SOUNDCLOUD -> "SoundCloud";
            case SPOTIFY -> "Spotify";
            case TIDAL -> "Tidal";
            case SEARCH_RESULT -> "busca";
            case HTTP -> "link";
        };
    }
}
