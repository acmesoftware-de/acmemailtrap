package de.acmesoftware.mailtrap.auth;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the OAuth2 user, then fetches org/group membership and enforces the local
 * allowlist ({@link AllowlistService}). A user who is not allowed is rejected — the login
 * fails rather than being granted with no access.
 */
@Component
public class MembershipUserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

    private final DefaultOAuth2UserService delegate = new DefaultOAuth2UserService();
    private final AllowlistService allowlist;
    private final AuthProperties props;
    private final RestClient http = RestClient.create();

    public MembershipUserService(AllowlistService allowlist, AuthProperties props) {
        this.allowlist = allowlist;
        this.props = props;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest request) throws OAuth2AuthenticationException {
        OAuth2User user = delegate.loadUser(request);
        String registrationId = request.getClientRegistration().getRegistrationId();
        String token = request.getAccessToken().getTokenValue();

        boolean allowed = switch (registrationId) {
            case "github" -> allowlist.githubAllowed(user.getAttribute("login"), githubOrgs(token));
            case "gitlab" -> allowlist.gitlabAllowed(user.getAttribute("username"),
                    gitlabGroups(token, props.getGitlab().getBaseUrl()));
            default -> false;
        };
        if (!allowed) {
            throw new OAuth2AuthenticationException(new OAuth2Error("access_denied"),
                    "User is not on the ACMEmailtrap allowlist");
        }
        return user;
    }

    private Set<String> githubOrgs(String token) {
        List<Map<String, Object>> orgs = http.get()
                .uri("https://api.github.com/user/orgs")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        return orgs == null ? Set.of()
                : orgs.stream().map(o -> String.valueOf(o.get("login"))).collect(Collectors.toSet());
    }

    private Set<String> gitlabGroups(String token, String baseUrl) {
        List<Map<String, Object>> groups = http.get()
                .uri(baseUrl + "/api/v4/groups?min_access_level=10&per_page=100")
                .header("Authorization", "Bearer " + token)
                .retrieve()
                .body(new ParameterizedTypeReference<>() {
                });
        if (groups == null) {
            return Set.of();
        }
        return groups.stream()
                .flatMap(g -> java.util.stream.Stream.of(
                        String.valueOf(g.get("path")), String.valueOf(g.get("full_path"))))
                .collect(Collectors.toSet());
    }
}
