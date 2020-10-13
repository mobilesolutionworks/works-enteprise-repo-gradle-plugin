package repo;

import groovy.lang.Closure;
import org.gradle.api.artifacts.dsl.RepositoryHandler;

import java.util.HashSet;
import java.util.Set;

public class SanitizeConfiguration {

    Set<String> hostWhitelist = new HashSet<>();

    Set<String> removeLogs = new HashSet<>();

    Closure<RepositoryHandler> closure;

    public void hostWhitelist(String whitelist) {
        hostWhitelist.add(whitelist);
    }

    public Set<String> hostWhitelist() {
        return hostWhitelist;
    }

    public void setHostWhitelist(Iterable<String> whitelist) {
        for (String host : whitelist) {
            this.hostWhitelist.add(host);
        }
    }

    public void repositories(Closure<RepositoryHandler> closure) {
        this.closure = closure;
    }
}
