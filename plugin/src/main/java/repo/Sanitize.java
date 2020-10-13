package repo;

import groovy.lang.Closure;
import org.gradle.api.artifacts.dsl.RepositoryHandler;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.api.invocation.Gradle;

public class Sanitize {

    public static void apply(Gradle gradle, Closure<SanitizeConfiguration> closure) {
        SanitizeConfiguration fixer = new SanitizeConfiguration();
        closure.setDelegate(fixer);
        closure.call();

        gradle.afterProject(project -> {
            fixRepo(fixer, project.getBuildscript().getRepositories());
            fixRepo(fixer, project.getRepositories());
        });
        gradle.settingsEvaluated(settings -> {
            fixRepo(fixer, settings.getPluginManagement().getRepositories());
        });

        gradle.allprojects(project -> {
            fixRepo(fixer, project.getBuildscript().getRepositories());
            fixRepo(fixer, project.getRootProject().getBuildscript().getRepositories());
            fixRepo(fixer, project.getRepositories());
        });
    }

    private static void fixRepo(SanitizeConfiguration fixer, RepositoryHandler handler) {
        handler.all(repo -> {
            if (repo instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) repo;
                if (!fixer.hostWhitelist.contains(mavenRepo.getUrl().getHost())) {
                    if (!mavenRepo.getUrl().getScheme().equals("file")) {
                        handler.remove(mavenRepo);
                        fixer.removeLogs.add("Removing " + mavenRepo.getUrl() + " from ");
                    }
                }
            }
        });

        fixer.closure.setDelegate(handler);
        fixer.closure.call();

        handler.all(repo -> {
            if (repo instanceof MavenArtifactRepository) {
                MavenArtifactRepository mavenRepo = (MavenArtifactRepository) repo;
            }
        });
    }
}
