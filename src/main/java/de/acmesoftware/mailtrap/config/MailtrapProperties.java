package de.acmesoftware.mailtrap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Configuration for all moving parts of ACMEmailtrap. Bound from the
 * {@code acmemailtrap.*} tree in application.yml (or env vars / CLI args).
 */
@ConfigurationProperties(prefix = "acmemailtrap")
public class MailtrapProperties {

    /** Root directory of the maildir-style store; one folder per recipient mailbox. */
    private String dataDir = "./data";

    private Smtp smtp = new Smtp();
    private Imap imap = new Imap();
    private Forward forward = new Forward();

    public String getDataDir() {
        return dataDir;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public Smtp getSmtp() {
        return smtp;
    }

    public void setSmtp(Smtp smtp) {
        this.smtp = smtp;
    }

    public Imap getImap() {
        return imap;
    }

    public void setImap(Imap imap) {
        this.imap = imap;
    }

    public Forward getForward() {
        return forward;
    }

    public void setForward(Forward forward) {
        this.forward = forward;
    }

    /** Teil 1: the catch-all SMTP listener. */
    public static class Smtp {
        private boolean enabled = true;
        private String bind = "0.0.0.0";
        private int port = 1025;
        private long maxMessageSize = 26_214_400L; // 25 MiB
        private String hostname = "acmemailtrap.local";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBind() {
            return bind;
        }

        public void setBind(String bind) {
            this.bind = bind;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public long getMaxMessageSize() {
            return maxMessageSize;
        }

        public void setMaxMessageSize(long maxMessageSize) {
            this.maxMessageSize = maxMessageSize;
        }

        public String getHostname() {
            return hostname;
        }

        public void setHostname(String hostname) {
            this.hostname = hostname;
        }
    }

    /** Teil 3: the IMAP listener that serves the stored mailboxes read-mostly. */
    public static class Imap {
        private boolean enabled = true;
        private String bind = "0.0.0.0";
        private int port = 1143;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBind() {
            return bind;
        }

        public void setBind(String bind) {
            this.bind = bind;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }
    }

    /** Teil 6: optional relay of caught mail to a real SMTP service. */
    public static class Forward {
        private boolean enabled = false;
        private String host = "";
        private int port = 587;
        private String username = "";
        private String password = "";
        private boolean starttls = true;
        /** Only forward mail addressed to these recipient domains. Empty = forward all. */
        private List<String> recipientDomains = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isStarttls() {
            return starttls;
        }

        public void setStarttls(boolean starttls) {
            this.starttls = starttls;
        }

        public List<String> getRecipientDomains() {
            return recipientDomains;
        }

        public void setRecipientDomains(List<String> recipientDomains) {
            this.recipientDomains = recipientDomains;
        }
    }
}
