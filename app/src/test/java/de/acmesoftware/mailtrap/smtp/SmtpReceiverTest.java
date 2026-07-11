package de.acmesoftware.mailtrap.smtp;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SmtpReceiverTest {

    @Test
    void extractsAddressFromAngleBrackets() {
        assertThat(SmtpReceiver.extractAddress(" <bob@kunde.test>")).isEqualTo("bob@kunde.test");
        assertThat(SmtpReceiver.extractAddress("<bob@kunde.test> SIZE=100")).isEqualTo("bob@kunde.test");
    }

    @Test
    void extractsBareAddress() {
        assertThat(SmtpReceiver.extractAddress(" bob@kunde.test")).isEqualTo("bob@kunde.test");
        assertThat(SmtpReceiver.extractAddress("bob@kunde.test PARAM")).isEqualTo("bob@kunde.test");
    }
}
