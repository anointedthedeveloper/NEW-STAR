package com.drift.dev;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** The only commands developer mode can run. Arguments are fixed; speech never contributes to the command line. */
@Component
public class DevTemplates {
    public record Template(String id, String description, List<String> mavenArgs, boolean requiresConfirmation) {}

    private static final Map<String, Template> TEMPLATES = Map.of(
            "maven-test", new Template("maven-test", "Run Maven tests", List.of("-B", "test"), false),
            "maven-package", new Template("maven-package", "Build with Maven (package)", List.of("-B", "package"), false),
            "maven-clean", new Template("maven-clean", "Maven clean (deletes the target folder)", List.of("-B", "clean"), true));

    public Optional<Template> find(String id) { return Optional.ofNullable(TEMPLATES.get(id)); }

    public List<Template> all() { return List.copyOf(TEMPLATES.values()); }

    /** Builds the exact argument list for a template in a project directory, preferring the project's own Maven wrapper. */
    public List<String> commandLine(Template t, Path projectDir, boolean windows) {
        List<String> cmd = new java.util.ArrayList<>();
        if (windows) {
            cmd.addAll(List.of("cmd.exe", "/c", Files.isRegularFile(projectDir.resolve("mvnw.cmd")) ? "mvnw.cmd" : "mvn.cmd"));
        } else {
            cmd.add(Files.isExecutable(projectDir.resolve("mvnw")) ? projectDir.resolve("mvnw").toString() : "mvn");
        }
        cmd.addAll(t.mavenArgs());
        return cmd;
    }

    public static boolean isMavenProject(Path dir) { return Files.isRegularFile(dir.resolve("pom.xml")); }
}
