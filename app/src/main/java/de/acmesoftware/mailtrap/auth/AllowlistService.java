package de.acmesoftware.mailtrap.auth;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Local access decision for OAuth logins. Fail closed: a provider whose allowlists are
 * all empty admits nobody. Matching is case-insensitive. The role/authorization is never
 * derived from the provider — only this allowlist grants access.
 */
@Component
public class AllowlistService {

    private final AuthProperties props;

    public AllowlistService(AuthProperties props) {
        this.props = props;
    }

    /** GitHub: allowed if the login is listed, or the user is in an allowed org. */
    public boolean githubAllowed(String login, Collection<String> orgs) {
        AuthProperties.Github g = props.getGithub();
        return contains(g.getAllowedUsers(), login) || intersects(g.getAllowedOrgs(), orgs);
    }

    /** GitLab: allowed if the username is listed, or the user is in an allowed group. */
    public boolean gitlabAllowed(String username, Collection<String> groups) {
        AuthProperties.Gitlab gl = props.getGitlab();
        return contains(gl.getAllowedUsers(), username) || intersects(gl.getAllowedGroups(), groups);
    }

    private static boolean contains(List<String> allow, String value) {
        if (allow == null || value == null) {
            return false;
        }
        return allow.stream().anyMatch(a -> a.equalsIgnoreCase(value.trim()));
    }

    private static boolean intersects(List<String> allow, Collection<String> have) {
        if (allow == null || have == null || allow.isEmpty() || have.isEmpty()) {
            return false;
        }
        return have.stream().filter(java.util.Objects::nonNull)
                .map(s -> s.toLowerCase(Locale.ROOT))
                .anyMatch(h -> allow.stream().anyMatch(a -> a.toLowerCase(Locale.ROOT).equals(h)));
    }
}
