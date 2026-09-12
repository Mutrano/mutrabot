package com.mutrabot.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class DotEnvTest {

    @TempDir
    Path tempDir;

    @Test
    void readsPlainAndQuotedValues() throws IOException {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, """
                # comentário
                MUTRABOT_TEST_PLAIN=abc

                MUTRABOT_TEST_DOUBLE="com espaço"
                MUTRABOT_TEST_SINGLE='aspas simples'
                """);

        DotEnv dotEnv = DotEnv.load(file);

        assertThat(dotEnv.get("MUTRABOT_TEST_PLAIN")).isEqualTo("abc");
        assertThat(dotEnv.get("MUTRABOT_TEST_DOUBLE")).isEqualTo("com espaço");
        assertThat(dotEnv.get("MUTRABOT_TEST_SINGLE")).isEqualTo("aspas simples");
    }

    @Test
    void ignoresMalformedLines() throws IOException {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, """
                sem-igual
                =sem-chave
                MUTRABOT_TEST_OK=1
                """);

        DotEnv dotEnv = DotEnv.load(file);

        assertThat(dotEnv.get("MUTRABOT_TEST_OK")).isEqualTo("1");
        assertThat(dotEnv.get("sem-igual")).isNull();
    }

    @Test
    void missingFileYieldsEmptyValues() {
        DotEnv dotEnv = DotEnv.load(tempDir.resolve("nao-existe.env"));

        assertThat(dotEnv.get("MUTRABOT_TEST_PLAIN")).isNull();
    }

    @Test
    void environmentVariablesWinOverFile() throws IOException {
        Path file = tempDir.resolve(".env");
        Files.writeString(file, "PATH=valor-do-arquivo\n");

        DotEnv dotEnv = DotEnv.load(file);

        assertThat(dotEnv.get("PATH")).isNotEqualTo("valor-do-arquivo");
    }
}
