package com.mykyta.rag;

import com.mykyta.model.QdrantSearchResult;
import com.mykyta.model.KnowledgeSourceType;

import java.util.List;
import java.util.Set;

/**
 * Searches the application's vector-backed knowledge base.
 *
 * <p>This service-level abstraction owns retrieval only. Callers decide when
 * retrieval is appropriate and how returned chunks should be used.</p>
 */
public interface KnowledgeRetriever {

    /**
     * Finds the most relevant stored chunks for a semantic query.
     *
     * @param query natural-language search query
     * @param limit maximum number of chunks to return
     * @param sourceTypes application-approved evidence categories to include
     * @return matching chunks ordered by vector-store relevance
     */
    List<QdrantSearchResult> retrieve(String query, int limit, Set<KnowledgeSourceType> sourceTypes);
}
