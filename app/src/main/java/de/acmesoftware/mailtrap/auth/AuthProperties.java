package de.acmesoftware.mailtrap.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Tool-login configuration ({@code acmemailtrap.auth.*}). Disabled by default: the tool
 * stays open on localhost for zero-friction development. When enabled, the web UI/API
 * require login; SMTP/IMAP are unaffected. Authorization is a local allowlist — an OAuth
 * provider with an empty allowlist admits nobody (fail closed).
 */
@ConfigurationProperties(prefix = "acmemailtrap.auth")
public class AuthProperties {

    /** Master switch. False = open (dev default). */
    private boolean enabled = false;

    private Local local = new Local();
    private Github github = new Github();
    private Gitlab gitlab = new Gitlab();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Local getLocal() {
        return local;
    }

    public void setLocal(Local local) {
        this.local = local;
    }

    public Github getGithub() {
        return github;
    }

    public void setGithub(Github github) {
        this.github = github;
    }

    public Gitlab getGitlab() {
        return gitlab;
    }

    public void setGitlab(Gitlab gitlab) {
        this.gitlab = gitlab;
    }

    /** Local dev account. */
    public static class Local {
        private boolean enabled = true;
        private String username = "admin";
        private String password = "";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
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
    }

    /** GitHub OAuth2 + allowlist. */
    public static class Github {
        private boolean enabled = false;
        private String clientId = "";
        private String clientSecret = "";
        private List<String> allowedOrgs = List.of();
        private List<String> allowedUsers = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public List<String> getAllowedOrgs() {
            return allowedOrgs;
        }

        public void setAllowedOrgs(List<String> allowedOrgs) {
            this.allowedOrgs = allowedOrgs;
        }

        public List<String> getAllowedUsers() {
            return allowedUsers;
        }

        public void setAllowedUsers(List<String> allowedUsers) {
            this.allowedUsers = allowedUsers;
        }
    }

    /** GitLab OAuth2 (self-hosted friendly) + allowlist. */
    public static class Gitlab {
        private boolean enabled = false;
        private String baseUrl = "https://gitlab.com";
        private String clientId = "";
        private String clientSecret = "";
        private List<String> allowedGroups = List.of();
        private List<String> allowedUsers = List.of();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public List<String> getAllowedGroups() {
            return allowedGroups;
        }

        public void setAllowedGroups(List<String> allowedGroups) {
            this.allowedGroups = allowedGroups;
        }

        public List<String> getAllowedUsers() {
            return allowedUsers;
        }

        public void setAllowedUsers(List<String> allowedUsers) {
            this.allowedUsers = allowedUsers;
        }
    }
}
