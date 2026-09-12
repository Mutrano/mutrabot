package com.mutrabot.adapter.out.audio;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

class YtDlpResolverTest {

    private static final String MEDIA_JSON = """
            {"id":"abc","title":"Meu Vídeo","uploader":"Canal","duration":125,\
            "webpage_url":"https://www.youtube.com/watch?v=abc",\
            "url":"https://rr1.googlevideo.com/videoplayback?x=1","is_live":false}""";

    private final List<List<String>> commands = new ArrayList<>();

    private YtDlpResolver.CommandRunner runner(Function<List<String>, YtDlpResolver.CommandRunner.Result> handler) {
        return command -> {
            commands.add(List.copyOf(command));
            return handler.apply(command);
        };
    }

    private YtDlpResolver availableResolver(String stdout) {
        return new YtDlpResolver(
                runner(command -> new YtDlpResolver.CommandRunner.Result(0, stdout, "")),
                List.of("yt-dlp"),
                List.of());
    }

    @Test
    void detectsGlobalYtDlpAndNodeRuntime() {
        YtDlpResolver resolver = YtDlpResolver.detect(runner(command ->
                new YtDlpResolver.CommandRunner.Result(command.contains("--version") ? 0 : 1, "", "")));

        assertThat(resolver.available()).isTrue();
        assertThat(commands).contains(List.of("yt-dlp", "--version"), List.of("node", "--version"));
    }

    @Test
    void fallsBackToPythonModuleWhenBinaryMissing() {
        YtDlpResolver resolver = YtDlpResolver.detect(runner(command -> {
            if (command.equals(List.of("yt-dlp", "--version"))) {
                return new YtDlpResolver.CommandRunner.Result(1, "", "not found");
            }
            if (command.equals(List.of("python", "-m", "yt_dlp", "--version"))) {
                return new YtDlpResolver.CommandRunner.Result(0, "2026.01.01", "");
            }
            return new YtDlpResolver.CommandRunner.Result(1, "", "");
        }));

        assertThat(resolver.available()).isTrue();
        assertThat(commands).contains(List.of("python", "-m", "yt_dlp", "--version"));
    }

    @Test
    void disabledWhenNoRunnerWorks() {
        YtDlpResolver resolver = YtDlpResolver.detect(runner(command ->
                new YtDlpResolver.CommandRunner.Result(1, "", "")));

        assertThat(resolver.available()).isFalse();
        assertThat(resolver.resolve("ytsearch1:teste")).isEmpty();
    }

    @Test
    void exceptionDuringDetectionDisablesResolver() {
        YtDlpResolver resolver = YtDlpResolver.detect(command -> {
            throw new IllegalStateException("sem processo");
        });

        assertThat(resolver.available()).isFalse();
    }

    @Test
    void parsesMediaMetadata() {
        YtDlpResolver resolver = availableResolver(MEDIA_JSON);

        Optional<YtDlpMedia> media = resolver.resolve("https://www.youtube.com/watch?v=abc");

        assertThat(media).isPresent();
        YtDlpMedia parsed = media.get();
        assertThat(parsed.id()).isEqualTo("abc");
        assertThat(parsed.title()).isEqualTo("Meu Vídeo");
        assertThat(parsed.uploader()).isEqualTo("Canal");
        assertThat(parsed.duration()).isEqualTo(java.time.Duration.ofSeconds(125));
        assertThat(parsed.webpageUrl()).isEqualTo("https://www.youtube.com/watch?v=abc");
        assertThat(parsed.streamUrl()).startsWith("https://rr1.googlevideo.com");
        assertThat(parsed.live()).isFalse();
        assertThat(commands.get(0)).contains("-j", "-f", "bestaudio", "--no-playlist");
        assertThat(commands.get(0).get(commands.get(0).size() - 1))
                .isEqualTo("https://www.youtube.com/watch?v=abc");
    }

    @Test
    void parsesPlaylistEntriesFromMultipleLines() {
        String first = "{\"id\":\"a\",\"title\":\"A\",\"url\":\"https://x/a\",\"webpage_url\":\"https://y/a\"}";
        String second = "{\"id\":\"b\",\"title\":\"B\",\"url\":\"https://x/b\",\"webpage_url\":\"https://y/b\"}";
        YtDlpResolver resolver = availableResolver(first + "\n" + second + "\n");

        List<YtDlpMedia> media = resolver.resolvePlaylist("https://www.youtube.com/playlist?list=PL1", 100);

        assertThat(media).extracting(YtDlpMedia::id).containsExactly("a", "b");
        List<String> playlistCommand = commands.get(0);
        assertThat(playlistCommand).contains("-j", "--yes-playlist", "--playlist-end", "100");
        assertThat(playlistCommand.get(playlistCommand.size() - 1))
                .isEqualTo("https://www.youtube.com/playlist?list=PL1");
    }

    @Test
    void playlistSkipsLinesYtDlpCouldNotExtract() {
        String valid = "{\"id\":\"a\",\"title\":\"A\",\"url\":\"https://x/a\"}";
        YtDlpResolver resolver = availableResolver("WARNING: nope\n" + valid + "\nnot json");

        List<YtDlpMedia> media = resolver.resolvePlaylist("https://www.youtube.com/playlist?list=PL1", 50);

        assertThat(media).extracting(YtDlpMedia::id).containsExactly("a");
    }

    @Test
    void playlistReturnsEmptyForBlankInputDisabledResolverOrFailure() {
        YtDlpResolver failing = new YtDlpResolver(
                runner(command -> new YtDlpResolver.CommandRunner.Result(1, "", "erro")),
                List.of("yt-dlp"),
                List.of());
        YtDlpResolver disabled = YtDlpResolver.disabled();

        assertThat(availableResolver("").resolvePlaylist("https://x", 10)).isEmpty();
        assertThat(availableResolver(MEDIA_JSON).resolvePlaylist(null, 10)).isEmpty();
        assertThat(availableResolver(MEDIA_JSON).resolvePlaylist("https://x", 0)).isEmpty();
        assertThat(failing.resolvePlaylist("https://x", 10)).isEmpty();
        assertThat(disabled.resolvePlaylist("https://x", 10)).isEmpty();
    }

    @Test
    void liveStreamsHaveUnknownDuration() {        YtDlpResolver resolver = availableResolver(
                "{\"id\":\"live\",\"title\":\"Ao vivo\",\"duration\":0,\"url\":\"https://x/y\",\"is_live\":true}");

        YtDlpMedia media = resolver.resolve("https://youtu.be/live").orElseThrow();

        assertThat(media.live()).isTrue();
        assertThat(media.duration()).isEqualTo(java.time.Duration.ZERO);
    }

    @Test
    void missingWebpageFallsBackToStreamUrlForId() {
        YtDlpResolver resolver = availableResolver(
                "{\"title\":\"Sem id\",\"url\":\"https://x/y\"}");

        YtDlpMedia media = resolver.resolve("busca").orElseThrow();

        assertThat(media.id()).isEqualTo("https://x/y");
        assertThat(media.uploader()).isNull();
        assertThat(media.webpageUrl()).isNull();
    }

    @Test
    void emptyResultsWhenProcessFailsOrOutputInvalid() {
        YtDlpResolver failing = new YtDlpResolver(
                runner(command -> new YtDlpResolver.CommandRunner.Result(1, "", "erro")),
                List.of("yt-dlp"),
                List.of());
        YtDlpResolver garbage = availableResolver("not json");
        YtDlpResolver noUrl = availableResolver("{\"title\":\"Sem url\"}");

        assertThat(failing.resolve("q")).isEmpty();
        assertThat(garbage.resolve("q")).isEmpty();
        assertThat(noUrl.resolve("q")).isEmpty();
    }

    @Test
    void blankQueryReturnsEmpty() {
        YtDlpResolver resolver = availableResolver(MEDIA_JSON);

        assertThat(resolver.resolve("  ")).isEmpty();
        assertThat(resolver.resolve(null)).isEmpty();
        assertThat(commands).isEmpty();
    }

    @Test
    void usesConfiguredPath() {
        YtDlpResolver resolver = YtDlpResolver.detect(
                runner(command -> new YtDlpResolver.CommandRunner.Result(0, MEDIA_JSON, "")),
                "C:/tools/yt-dlp.exe");

        assertThat(resolver.available()).isTrue();
        assertThat(resolver.resolve("q")).isPresent();
        assertThat(commands).anyMatch(command -> command.contains("-j") && command.get(0).equals("C:/tools/yt-dlp.exe"));
    }

    @Test
    void configuredPathSkipsAvailableProbe() {
        YtDlpResolver resolver = YtDlpResolver.detect(command -> {
            throw new IllegalStateException("nao deveria rodar " + command);
        }, "C:/tools/yt-dlp.exe");

        assertThat(resolver.available()).isTrue();
    }
}
