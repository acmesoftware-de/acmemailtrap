package de.acmesoftware.mailtrap.auth;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AllowlistServiceTest {

    private AllowlistService svc(AuthProperties p) {
        return new AllowlistService(p);
    }

    @Test
    void githubAllowsByOrgOrUserCaseInsensitive() {
        AuthProperties p = new AuthProperties();
        p.getGithub().setAllowedOrgs(List.of("acmesoftware-de"));
        p.getGithub().setAllowedUsers(List.of("fschupp"));
        AllowlistService s = svc(p);

        assertThat(s.githubAllowed("someoneelse", Set.of("ACMESOFTWARE-DE"))).isTrue(); // org, case-insensitive
        assertThat(s.githubAllowed("FSchupp", Set.of())).isTrue();                       // user, case-insensitive
        assertThat(s.githubAllowed("stranger", Set.of("other-org"))).isFalse();
    }

    @Test
    void gitlabAllowsByGroupOrUser() {
        AuthProperties p = new AuthProperties();
        p.getGitlab().setAllowedGroups(List.of("platform/team"));
        AllowlistService s = svc(p);

        assertThat(s.gitlabAllowed("x", Set.of("platform/team"))).isTrue();
        assertThat(s.gitlabAllowed("x", Set.of("platform"))).isFalse();
    }

    @Test
    void emptyAllowlistFailsClosed() {
        AllowlistService s = svc(new AuthProperties()); // no allowlists configured
        assertThat(s.githubAllowed("anyone", Set.of("any-org"))).isFalse();
        assertThat(s.gitlabAllowed("anyone", Set.of("any-group"))).isFalse();
    }
}
