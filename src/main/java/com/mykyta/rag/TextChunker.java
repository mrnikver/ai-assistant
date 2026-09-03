package com.mykyta.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class TextChunker {

    public List<String> chunk(String document) {

        log.debug("Document chunking started: characterCount={}", document.length());

        List<String> paragraphs = Arrays.stream(
                        document.split("\\R\\s*\\R")
                )
                .map(String::trim)
                .filter(paragraph -> !paragraph.isBlank())
                .toList();

        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < paragraphs.size(); i++) {

            String current = paragraphs.get(i);

            if (current.endsWith(":")
                    && i + 1 < paragraphs.size()) {

                current = current
                        + "\n\n"
                        + paragraphs.get(++i);
            }

            chunks.add(current);
        }

        log.debug("Document chunking completed: paragraphCount={}, chunkCount={}", paragraphs.size(), chunks.size());
        return chunks;
    }
}
