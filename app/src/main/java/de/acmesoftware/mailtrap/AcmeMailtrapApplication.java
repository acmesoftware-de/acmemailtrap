package de.acmesoftware.mailtrap;

import de.acmesoftware.mailtrap.auth.AuthProperties;
import de.acmesoftware.mailtrap.config.MailtrapProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * ACMEmailtrap: catches SMTP, stores every message per recipient mailbox on disk,
 * serves the mailboxes over IMAP and a web UI, and can forward to a real mail service.
 *
 * <p>Purpose: exercise the mail flows of ACMEsuite without touching real email.
 */
@SpringBootApplication
@EnableConfigurationProperties({MailtrapProperties.class, AuthProperties.class})
public class AcmeMailtrapApplication {

    public static void main(String[] args) {
        SpringApplication.run(AcmeMailtrapApplication.class, args);
    }
}
