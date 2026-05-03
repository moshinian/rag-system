package com.example.rag.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * pgvector 检索返回的 chunk 候选结果。
 */
@Getter
@Setter
public class RetrievedChunkCandidate {

    private Long id;
    private Long documentId;
    private String documentCode;
    private String documentName;
    private Integer chunkIndex;
    private String chunkType;
    private String content;
    private Integer startOffset;
    private Integer endOffset;
    private String embeddingModel;
    private Double score;
}
