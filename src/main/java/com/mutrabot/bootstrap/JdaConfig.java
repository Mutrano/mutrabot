package com.mutrabot.bootstrap;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

public final class JdaConfig {

    private JdaConfig() {
    }

    public static JDA build(String token) {
        return JDABuilder.createLight(token, GatewayIntent.GUILD_VOICE_STATES)
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .enableCache(CacheFlag.VOICE_STATE)
                .build();
    }
}
