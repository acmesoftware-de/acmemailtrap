package de.acmesoftware.mailtrap.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.stereotype.Component;

/**
 * Single source of build identity, branded into every piece (SMTP/IMAP banners,
 * startup logs, the API and the UI). Version comes from the Maven build
 * (build-info.properties); commit/branch from git.properties. Both are optional —
 * running from an IDE without those files falls back to dev/unknown.
 */
@Component
public class BuildInfo {

    private final String version;
    private final String commit;
    private final String branch;
    private final Long buildTime;

    public BuildInfo(ObjectProvider<BuildProperties> build, ObjectProvider<GitProperties> git) {
        BuildProperties b = build.getIfAvailable();
        GitProperties g = git.getIfAvailable();
        this.version = (b != null && b.getVersion() != null) ? b.getVersion() : "dev";
        this.commit = (g != null && g.getShortCommitId() != null) ? g.getShortCommitId() : "unknown";
        this.branch = (g != null && g.getBranch() != null) ? g.getBranch() : "";
        if (b != null && b.getTime() != null) {
            this.buildTime = b.getTime().toEpochMilli();
        } else if (g != null && g.getCommitTime() != null) {
            this.buildTime = g.getCommitTime().toEpochMilli();
        } else {
            this.buildTime = null;
        }
    }

    public String version() {
        return version;
    }

    public String commit() {
        return commit;
    }

    public String branch() {
        return branch;
    }

    public Long buildTime() {
        return buildTime;
    }

    /** Compact human label, e.g. {@code 0.1.0-SNAPSHOT (a1b2c3d)}. */
    public String label() {
        return version + " (" + commit + ")";
    }
}
