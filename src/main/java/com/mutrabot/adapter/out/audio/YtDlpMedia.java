package com.mutrabot.adapter.out.audio;

import java.time.Duration;

public record YtDlpMedia(
        String id,
        String title,
        String uploader,
        Duration duration,
        String webpageUrl,
        String streamUrl,
        boolean live) {
}
