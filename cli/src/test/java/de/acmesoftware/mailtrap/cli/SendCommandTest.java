package de.acmesoftware.mailtrap.cli;

import de.acmesoftware.mailtrap.cli.cmd.SendCommand;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SendCommandTest {

    @Test
    void buildsPlainTextMessageWithHeaders() {
        byte[] mime = SendCommand.buildMime("robot@acme.test", List.of("bob@kunde.test"),
                "Reset", "Click https://x/reset", null);
        String s = new String(mime, StandardCharsets.UTF_8);
        assertThat(s).contains("From: robot@acme.test\r\n");
        assertThat(s).contains("To: bob@kunde.test\r\n");
        assertThat(s).contains("Subject: Reset\r\n");
        assertThat(s).contains("Content-Type: text/plain; charset=UTF-8\r\n");
        assertThat(s).endsWith("Click https://x/reset");
        // Header/body separator present exactly once as a blank line.
        assertThat(s).contains("\r\n\r\n");
    }

    @Test
    void usesHtmlContentTypeWhenOnlyHtmlGiven() {
        byte[] mime = SendCommand.buildMime("a@b", List.of("c@d"), "Hi", null, "<h1>Hi</h1>");
        String s = new String(mime, StandardCharsets.UTF_8);
        assertThat(s).contains("Content-Type: text/html; charset=UTF-8\r\n");
        assertThat(s).endsWith("<h1>Hi</h1>");
    }

    @Test
    void textWinsWhenBothBodiesGiven() {
        byte[] mime = SendCommand.buildMime("a@b", List.of("c@d"), "Hi", "plain", "<h1>html</h1>");
        String s = new String(mime, StandardCharsets.UTF_8);
        assertThat(s).contains("Content-Type: text/plain; charset=UTF-8\r\n");
        assertThat(s).endsWith("plain");
    }

    @Test
    void joinsMultipleRecipients() {
        byte[] mime = SendCommand.buildMime("a@b", List.of("c@d", "e@f"), "Hi", "x", null);
        assertThat(new String(mime, StandardCharsets.UTF_8)).contains("To: c@d, e@f\r\n");
    }
}
