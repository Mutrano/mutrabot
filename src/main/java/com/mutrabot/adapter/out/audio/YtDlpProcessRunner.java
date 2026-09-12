package com.mutrabot.adapter.out.audio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class YtDlpProcessRunner implements YtDlpResolver.CommandRunner {

    private static final long TIMEOUT_SECONDS = 300;

    @Override
    public Result run(List<String> command) {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            StringBuilder errorOutput = new StringBuilder();
            Thread errorReader = Thread.ofVirtual().start(() -> {
                try {
                    errorOutput.append(new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
                } catch (IOException ignored) {
                }
            });
            String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(-1, stdout, "tempo esgotado");
            }
            errorReader.join(1000);
            return new Result(process.exitValue(), stdout, errorOutput.toString());
        } catch (IOException e) {
            return new Result(-1, "", e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, "", e.getMessage());
        }
    }
}
