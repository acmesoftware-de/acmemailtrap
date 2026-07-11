package de.acmesoftware.mailtrap.auth;

import de.acmesoftware.mailtrap.plugin.AuthKind;
import de.acmesoftware.mailtrap.plugin.AuthProvider;
import de.acmesoftware.mailtrap.plugin.ConfigField;
import org.springframework.stereotype.Component;

import java.util.List;

/** GitLab OAuth2 login (works against self-hosted); access gated by a group or user allowlist. */
@Component
public class GitlabAuthProvider implements AuthProvider {

    @Override
    public String id() {
        return "gitlab";
    }

    @Override
    public String displayName() {
        return "GitLab";
    }

    @Override
    public AuthKind kind() {
        return AuthKind.OAUTH;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.url("baseUrl", "GitLab-Basis-URL"),
                ConfigField.text("clientId", "Application-ID"),
                ConfigField.password("clientSecret", "Secret"),
                ConfigField.text("allowedGroups", "Erlaubte Gruppen (Komma-getrennt)"),
                ConfigField.text("allowedUsers", "Erlaubte Nutzer (Komma-getrennt)"));
    }
}
