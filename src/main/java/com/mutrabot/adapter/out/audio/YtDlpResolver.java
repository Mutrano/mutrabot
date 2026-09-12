package com.mutrabot.adapter.out.audio;

import com.sedmelluq.discord.lavaplayer.tools.JsonBrowser;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class YtDlpResolver {

    public interface CommandRunner {
        Result run(List<String> command);

        record Result(int exitCode, String stdout, String stderr) {
        }
    }

    private final CommandRunner runner;
    private final List<String> baseCommand;
    private final List<String> jsRuntimeArgs;

    YtDlpResolver(CommandRunner runner, List<String> baseCommand, List<String> jsRuntimeArgs) {
        this.runner = runner;
        this.baseCommand = baseCommand;
        this.jsRuntimeArgs = jsRuntimeArgs;
    }

    public static YtDlpResolver detect(CommandRunner runner) {
        return detect(runner, System.getenv("YTDLP_PATH"));
    }

    static YtDlpResolver detect(CommandRunner runner, String configuredPath) {
        List<String> base = null;
        if (configuredPath != null && !configuredPath.isBlank()) {
            base = List.of(configuredPath);
        } else if (works(runner, List.of("yt-dlp", "--version"))) {
            base = List.of("yt-dlp");
        } else if (works(runner, List.of("python", "-m", "yt_dlp", "--version"))) {
            base = List.of("python", "-m", "yt_dlp");
        } else if (works(runner, List.of("python3", "-m", "yt_dlp", "--version"))) {
            base = List.of("python3", "-m", "yt_dlp");
        }
        List<String> jsRuntime = works(runner, List.of("node", "--version"))
                ? List.of("--js-runtimes", "node")
                : List.of();
        return new YtDlpResolver(runner, base, jsRuntime);
    }

    public static YtDlpResolver disabled() {
        return new YtDlpResolver(command -> new CommandRunner.Result(1, "", ""), null, List.of());
    }

    private static boolean works(CommandRunner runner, List<String> command) {
        try {
            return runner.run(command).exitCode() == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    public boolean available() {
        return baseCommand != null;
    }

    public Optional<YtDlpMedia> resolve(String query) {
        if (!available() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        List<String> command = new ArrayList<>(baseCommand);
        command.add("-j");
        command.add("-f");
        command.add("bestaudio");
        command.add("--no-playlist");
        command.add("--no-warnings");
        command.addAll(jsRuntimeArgs);
        command.add(query);

        CommandRunner.Result result;
        try {
            result = runner.run(command);
        } catch (RuntimeException e) {
            return Optional.empty();
        }
        if (result.exitCode() != 0 || result.stdout() == null || result.stdout().isBlank()) {
            return Optional.empty();
        }
        try {
            return parse(JsonBrowser.parse(result.stdout()));
        } catch (RuntimeException | java.io.IOException e) {
            return Optional.empty();
        }
    }

    public List<YtDlpMedia> resolvePlaylist(String url, int maxEntries) {
        if (!available() || url == null || url.isBlank() || maxEntries <= 0) {
            return List.of();
        }
        List<String> command = new ArrayList<>(baseCommand);
        command.add("-j");
        command.add("-f");
        command.add("bestaudio");
        command.add("--yes-playlist");
        command.add("--playlist-end");
        command.add(Integer.toString(maxEntries));
        command.add("--no-warnings");
        command.addAll(jsRuntimeArgs);
        command.add(url);

        CommandRunner.Result result;
        try {
            result = runner.run(command);
        } catch (RuntimeException e) {
            return List.of();
        }
        if (result.stdout() == null || result.stdout().isBlank()) {
            return List.of();
        }
        return parseAll(result.stdout());
    }

    static List<YtDlpMedia> parseAll(String stdout) {
        List<YtDlpMedia> media = new ArrayList<>();
        for (String line : stdout.split("\\R")) {
            if (line.isBlank()) {
                continue;
            }
            try {
                parse(JsonBrowser.parse(line)).ifPresent(media::add);
            } catch (RuntimeException | java.io.IOException ignored) {
                // skip lines yt-dlp could not extract
            }
        }
        return media;
    }

    static Optional<YtDlpMedia> parse(JsonBrowser json) {
        String streamUrl = json.get("url").text();
        if (streamUrl == null || streamUrl.isBlank()) {
            return Optional.empty();
        }
        String id = json.get("id").text();
        String title = json.get("title").text();
        String uploader = json.get("uploader").text();
        long durationSeconds = json.get("duration").asLong(0);
        boolean live = json.get("is_live").asBoolean(false);
        String webpage = json.get("webpage_url").text();
        return Optional.of(new YtDlpMedia(
                id == null || id.isBlank() ? streamUrl : id,
                title,
                uploader,
                live ? Duration.ZERO : Duration.ofSeconds(durationSeconds),
                webpage,
                streamUrl,
                live));
    }
}
