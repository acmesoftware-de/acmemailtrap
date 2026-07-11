package de.acmesoftware.mailtrap.web;

import de.acmesoftware.mailtrap.auth.AuthProperties;
import de.acmesoftware.mailtrap.plugin.AuthKind;
import de.acmesoftware.mailtrap.plugin.AuthProvider;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Login state for the frontend: whether auth is on, who is signed in, and the enabled
 * login methods (with their start URLs). Always permitted so the SPA can render a login
 * screen before authenticating.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthProperties props;
    private final List<AuthProvider> providers;

    public AuthController(AuthProperties props, List<AuthProvider> providers) {
        this.props = props;
        this.providers = providers;
    }

    public record ProviderView(String id, String displayName, String kind, String loginUrl) {
    }

    public record AuthState(boolean enabled, boolean authenticated, String user, List<ProviderView> providers) {
    }

    @GetMapping
    public AuthState state(Authentication authentication) {
        boolean signedIn = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        // When auth is off the app is open, so treat as authenticated.
        boolean authenticated = !props.isEnabled() || signedIn;
        String user = signedIn ? authentication.getName() : null;

        List<ProviderView> enabled = providers.stream()
                .filter(p -> isEnabled(p.id()))
                .map(p -> new ProviderView(p.id(), p.displayName(), p.kind().name(), loginUrl(p)))
                .toList();

        return new AuthState(props.isEnabled(), authenticated, user, enabled);
    }

    private boolean isEnabled(String id) {
        return switch (id) {
            case "local" -> props.getLocal().isEnabled() && !props.getLocal().getPassword().isBlank();
            case "github" -> props.getGithub().isEnabled() && !props.getGithub().getClientId().isBlank();
            case "gitlab" -> props.getGitlab().isEnabled() && !props.getGitlab().getClientId().isBlank();
            default -> false;
        };
    }

    private String loginUrl(AuthProvider p) {
        return p.kind() == AuthKind.OAUTH ? "/oauth2/authorization/" + p.id() : "/login";
    }
}
