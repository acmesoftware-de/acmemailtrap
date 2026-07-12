package de.acmesoftware.mailtrap.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.List;

/**
 * Tool-login security. Disabled by default: the whole app is open (dev). When
 * {@code acmemailtrap.auth.enabled=true}, the web UI/API require login via the configured
 * methods (local password, GitHub, GitLab); SMTP/IMAP are unaffected (separate servers).
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthProperties props,
                                    ObjectProvider<MembershipUserService> userService) throws Exception {
        if (!props.isEnabled()) {
            // Open on localhost — zero-friction dev default.
            http.authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
            return http.build();
        }

        http.authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/assets/**", "/favicon.ico",
                                "/login/**", "/oauth2/**", "/error", "/api/auth").permitAll()
                        .anyRequest().authenticated())
                .csrf(c -> c.disable())
                .logout(l -> l.logoutSuccessUrl("/"));

        boolean anyMethod = false;
        if (props.getLocal().isEnabled() && !props.getLocal().getPassword().isBlank()) {
            // Always land on the SPA root, not the cached XHR that triggered the redirect.
            http.formLogin(f -> f.defaultSuccessUrl("/", true));
            anyMethod = true;
        }
        if (!registrations(props).isEmpty()) {
            http.oauth2Login(o -> o
                    .defaultSuccessUrl("/", true)
                    .userInfoEndpoint(u -> u.userService(userService.getObject())));
            anyMethod = true;
        }
        if (!anyMethod) {
            log.warn("acmemailtrap.auth.enabled=true but no login method is configured "
                    + "(local password / GitHub / GitLab) — nobody can sign in.");
        }
        return http.build();
    }

    @Bean
    UserDetailsService userDetailsService(AuthProperties props, PasswordEncoder encoder) {
        AuthProperties.Local local = props.getLocal();
        if (!props.isEnabled() || !local.isEnabled() || local.getPassword().isBlank()) {
            return new InMemoryUserDetailsManager();
        }
        UserDetails user = User.withUsername(local.getUsername())
                .password(encoder.encode(local.getPassword()))
                .roles("USER")
                .build();
        return new InMemoryUserDetailsManager(user);
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    ClientRegistrationRepository clientRegistrationRepository(AuthProperties props) {
        List<ClientRegistration> regs = registrations(props);
        if (regs.isEmpty()) {
            // No OAuth configured: an empty repo that is never consulted (oauth2Login off).
            return registrationId -> null;
        }
        return new InMemoryClientRegistrationRepository(regs);
    }

    private List<ClientRegistration> registrations(AuthProperties props) {
        List<ClientRegistration> list = new ArrayList<>();
        AuthProperties.Github gh = props.getGithub();
        if (props.isEnabled() && gh.isEnabled() && !gh.getClientId().isBlank()) {
            list.add(github(gh));
        }
        AuthProperties.Gitlab gl = props.getGitlab();
        if (props.isEnabled() && gl.isEnabled() && !gl.getClientId().isBlank()) {
            list.add(gitlab(gl));
        }
        return list;
    }

    private ClientRegistration github(AuthProperties.Github g) {
        return ClientRegistration.withRegistrationId("github")
                .clientId(g.getClientId())
                .clientSecret(g.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/github")
                .scope("read:user", "read:org")
                .authorizationUri("https://github.com/login/oauth/authorize")
                .tokenUri("https://github.com/login/oauth/access_token")
                .userInfoUri("https://api.github.com/user")
                .userNameAttributeName("login")
                .clientName("GitHub")
                .build();
    }

    private ClientRegistration gitlab(AuthProperties.Gitlab gl) {
        String base = gl.getBaseUrl();
        return ClientRegistration.withRegistrationId("gitlab")
                .clientId(gl.getClientId())
                .clientSecret(gl.getClientSecret())
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/gitlab")
                .scope("read_user", "read_api")
                .authorizationUri(base + "/oauth/authorize")
                .tokenUri(base + "/oauth/token")
                .userInfoUri(base + "/api/v4/user")
                .userNameAttributeName("username")
                .clientName("GitLab")
                .build();
    }
}
