package com.example.rag.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 问答历史查询视图对象。
 */
@Getter
@Setter
public class QaHistoryRecordView {

    private String sessionCode;
    private String sessionName;
    private String messageCode;
    private String question;
    private String answer;
    private String modelName;
    private Integer topK;
    private Long latencyMs;
    private String retrievedChunks;
    private String sources;
    private String promptTemplate;
    private OffsetDateTime createdAt;
}
