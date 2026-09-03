package com.mykyta.rag;

import com.mykyta.model.KnowledgeSourceType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

/** Classifies repository files from stable path/name rules rather than model output. */
@Component
public class KnowledgeSourceClassifier {
    private static final Set<String> CONFIG_EXTENSIONS = Set.of("yml", "yaml", "properties");

    public KnowledgeSourceType classify(Path root, Path file) {
        String path = root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/")
                .toLowerCase(Locale.ROOT);
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        String extension = extension(name);

        if (path.contains("/mock/") || path.contains("/mocks/") || path.contains("/fixtures/")
                || name.startsWith("mock") || name.contains("mockdeployment")) return KnowledgeSourceType.MOCK_RUNTIME;
        if (path.contains("/src/test/") || path.startsWith("src/test/") || path.contains("/__tests__/")
                || name.contains(".test.") || name.contains(".spec.")) return KnowledgeSourceType.TEST;
        if (path.contains("knowledge/runbooks/") || name.contains("runbook")
                || path.contains("troubleshooting/") || path.contains("operations/")) return KnowledgeSourceType.RUNBOOK;
        if (CONFIG_EXTENSIONS.contains(extension)) return KnowledgeSourceType.CONFIGURATION;
        if (extension.equals("md") || extension.equals("markdown")) return KnowledgeSourceType.DOCUMENTATION;
        return KnowledgeSourceType.SOURCE_CODE;
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }
}
