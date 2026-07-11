package de.acmesoftware.mailtrap.auth;

import de.acmesoftware.mailtrap.plugin.AuthKind;
import de.acmesoftware.mailtrap.plugin.AuthProvider;
import de.acmesoftware.mailtrap.plugin.ConfigField;
import org.springframework.stereotype.Component;

import java.util.List;

/** GitHub OAuth2 login; access gated by an org/team or explicit user allowlist. */
@Component
public class GithubAuthProvider implements AuthProvider {

    @Override
    public String id() {
        return "github";
    }

    @Override
    public String displayName() {
        return "GitHub";
    }

    @Override
    public AuthKind kind() {
        return AuthKind.OAUTH;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("clientId", "Client-ID"),
                ConfigField.password("clientSecret", "Client-Secret"),
                ConfigField.text("allowedOrgs", "Erlaubte Orgs (Komma-getrennt)"),
                ConfigField.text("allowedUsers", "Erlaubte Nutzer (Komma-getrennt)"));
    }
}
