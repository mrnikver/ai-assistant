package com.mykyta.rag;

import com.mykyta.model.KnowledgeChunk;
import com.mykyta.model.KnowledgeSourceType;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Splits source documents on language-level boundaries while retaining line locations. */
@Component
public class ProjectDocumentChunker {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(.+?)\\s*$");
    private static final Pattern JAVA_DECLARATION = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|abstract|synchronized|native|default|sealed|non-sealed)\\s+)*(?:(?:class|interface|enum|record)\\s+([A-Za-z_$][\\w$]*)|(?:[\\w$.<>?,\\[\\]]+\\s+)+([A-Za-z_$][\\w$]*)\\s*\\([^;]*\\)\\s*(?:throws\\s+[^{]+)?\\{?)\\s*$");
    private static final Pattern TYPESCRIPT_DECLARATION = Pattern.compile(
            "^\\s*(?:export\\s+)?(?:default\\s+)?(?:async\\s+)?(?:class|interface|type|enum|function|const|let|var)\\s+([A-Za-z_$][\\w$]*).*$");
    private static final Pattern NO_DECLARATION = Pattern.compile("a^");

    public List<KnowledgeChunk> chunk(Path root, Path file, String content, int maxCharacters,
                                      KnowledgeSourceType sourceType) {
        String extension = extension(file);
        return switch (extension) {
            case "md", "markdown" -> chunkMarkdown(root, file, content, maxCharacters, sourceType);
            case "java" -> chunkCode(root, file, content, maxCharacters, "java", JAVA_DECLARATION, sourceType);
            case "ts", "tsx" -> chunkCode(root, file, content, maxCharacters, "typescript", TYPESCRIPT_DECLARATION, sourceType);
            case "yml", "yaml", "properties" -> chunkCode(
                    root, file, content, maxCharacters, "configuration", NO_DECLARATION, sourceType);
            default -> List.of();
        };
    }

    private List<KnowledgeChunk> chunkMarkdown(Path root, Path file, String content, int maxCharacters,
                                                KnowledgeSourceType sourceType) {
        String[] lines = content.split("\\R", -1);
        List<Section> sections = new ArrayList<>();
        List<String> hierarchy = new ArrayList<>();
        int start = 1;
        String context = file.getFileName().toString();

        for (int index = 0; index < lines.length; index++) {
            Matcher heading = MARKDOWN_HEADING.matcher(lines[index]);
            if (!heading.matches()) {
                continue;
            }
            if (index + 1 > start) {
                sections.add(new Section(start, index, context));
            }
            int level = heading.group(1).length();
            while (hierarchy.size() >= level) {
                hierarchy.remove(hierarchy.size() - 1);
            }
            hierarchy.add(heading.group(2).trim());
            context = String.join(" > ", hierarchy);
            start = index + 1;
        }
        if (start <= lines.length) {
            sections.add(new Section(start, lines.length, context));
        }
        return materialize(root, file, lines, sections, maxCharacters, "markdown", sourceType);
    }

    private List<KnowledgeChunk> chunkCode(
            Path root, Path file, String content, int maxCharacters, String language, Pattern declarationPattern,
            KnowledgeSourceType sourceType) {
        String[] lines = content.split("\\R", -1);
        List<Section> sections = new ArrayList<>();
        int start = 1;
        String context = file.getFileName().toString();
        for (int index = 0; index < lines.length; index++) {
            Matcher declaration = declarationPattern.matcher(lines[index]);
            if (!declaration.matches()) {
                continue;
            }
            if (index + 1 > start) {
                sections.add(new Section(start, index, context));
            }
            context = firstCapturedGroup(declaration);
            start = index + 1;
        }
        if (start <= lines.length) {
            sections.add(new Section(start, lines.length, context));
        }
        return materialize(root, file, lines, sections, maxCharacters, language, sourceType);
    }

    private String firstCapturedGroup(Matcher matcher) {
        for (int group = 1; group <= matcher.groupCount(); group++) {
            if (matcher.group(group) != null) {
                return matcher.group(group);
            }
        }
        throw new IllegalStateException("Declaration pattern matched without a symbol name");
    }

    private List<KnowledgeChunk> materialize(
            Path root, Path file, String[] lines, List<Section> sections, int maxCharacters, String language,
            KnowledgeSourceType sourceType) {
        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            int partStart = section.startLine();
            StringBuilder text = new StringBuilder();
            for (int lineNumber = section.startLine(); lineNumber <= section.endLine(); lineNumber++) {
                String line = lines[lineNumber - 1];
                if (!text.isEmpty() && text.length() + line.length() + 1 > maxCharacters) {
                    addChunk(chunks, root, file, language, sourceType, section.context(), partStart, lineNumber - 1, text);
                    text.setLength(0);
                    partStart = lineNumber;
                }
                text.append(line).append('\n');
            }
            addChunk(chunks, root, file, language, sourceType, section.context(), partStart, section.endLine(), text);
        }
        return chunks;
    }

    private void addChunk(List<KnowledgeChunk> chunks, Path root, Path file, String language,
                          KnowledgeSourceType sourceType,
                          String context, int startLine, int endLine, StringBuilder text) {
        String value = text.toString().trim();
        if (value.isBlank()) {
            return;
        }
        chunks.add(new KnowledgeChunk(
                value,
                root.getFileName().toString(),
                root.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/"),
                sourceType,
                language,
                context,
                startLine,
                endLine,
                chunks.size()
        ));
    }

    private String extension(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private record Section(int startLine, int endLine, String context) {}
}
