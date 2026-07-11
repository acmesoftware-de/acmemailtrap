package de.acmesoftware.mailtrap.auth;

import de.acmesoftware.mailtrap.plugin.AuthKind;
import de.acmesoftware.mailtrap.plugin.AuthProvider;
import de.acmesoftware.mailtrap.plugin.ConfigField;
import org.springframework.stereotype.Component;

import java.util.List;

/** A single local dev account (username + password), for protected standalone instances. */
@Component
public class LocalAuthProvider implements AuthProvider {

    @Override
    public String id() {
        return "local";
    }

    @Override
    public String displayName() {
        return "Lokales Passwort";
    }

    @Override
    public AuthKind kind() {
        return AuthKind.LOCAL;
    }

    @Override
    public List<ConfigField> configSchema() {
        return List.of(
                ConfigField.text("username", "Benutzername"),
                ConfigField.password("password", "Passwort"));
    }
}
