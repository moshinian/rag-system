package com.example.rag.ingestion.parser;

import com.example.rag.model.entity.DocumentEntity;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文本解析器接口。
 *
 * 不同文件类型使用不同实现。
 */
public interface DocumentTextParser {

    /**
     * 当前解析器是否支持该文件类型。
     */
    boolean supports(String fileType);

    /** 把原始文件解析成统一中间结构，供切块器继续处理。 */
    ParsedDocument parse(DocumentEntity document, Path path) throws IOException;
}
