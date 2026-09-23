package com.demo.mcmhelper.modeling.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.loader.FileSystemDocumentLoader;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static dev.langchain4j.store.embedding.filter.MetadataFilterBuilder.metadataKey;

@Configuration
@Slf4j
public class RagConfig {

    private static final String DOC_DIR = "D:/langchain4j_ai_program/mcm-helper/src/main/resources/doc";
    private static final String STORE_DIR = "D:/langchain4j_ai_program/mcm-helper/embedding-store";
    private static final Path STORE_FILE = Paths.get(STORE_DIR, "embedding-store.json");
    private static final Path METADATA_FILE = Paths.get(STORE_DIR, "metadata.json");

    @Resource
    private EmbeddingModel qwenEmbeddingModel;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    @Bean
    public ContentRetriever contentRetriever() {
        new File(STORE_DIR).mkdirs();

        InMemoryEmbeddingStore<TextSegment> store;

        if (STORE_FILE.toFile().exists()) {
            store = incrementalLoad();
        } else {
            store = fullBuild();
        }

        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(qwenEmbeddingModel)
                .maxResults(5)
                .minScore(0.75)
                .build();
    }

    // ==================== 首次启动：全量建库 ====================

    private InMemoryEmbeddingStore<TextSegment> fullBuild() {
        File docDir = new File(DOC_DIR);
        if (!docDir.exists() || docDir.listFiles() == null || Objects.requireNonNull(docDir.listFiles()).length == 0) {
            log.info("doc/ 目录为空，跳过入库，返回空 ContentRetriever");
            return new InMemoryEmbeddingStore<>();
        }

        List<Document> documents = FileSystemDocumentLoader.loadDocuments(DOC_DIR);
        log.info("doc/ 目录有 {} 个文档，首次建库...", documents.size());
        log.info("正在全量向量化所有文档（{} 个文件），此操作将消耗向量模型 Token...", documents.size());

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(1000, 200);
        List<TextSegment> allSegments = new ArrayList<>();
        Map<String, FileFingerprint> metadata = new LinkedHashMap<>();

        for (Document doc : documents) {
            String fileName = doc.metadata().getString("file_name");
            File file = new File(DOC_DIR, fileName);
            metadata.put(fileName, new FileFingerprint(file.lastModified(), file.length()));

            List<TextSegment> segments = splitter.split(doc);
            // 添加文件名前缀，便于溯源
            segments = segments.stream()
                    .map(seg -> TextSegment.from(fileName + "\n" + seg.text(), seg.metadata()))
                    .collect(Collectors.toList());
            allSegments.addAll(segments);
        }

        InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
        store.addAll(qwenEmbeddingModel.embedAll(allSegments).content(), allSegments);
        store.serializeToFile(STORE_FILE);
        saveMetadata(metadata);

        log.info("向量库入库完成，共 {} 个片段，已持久化到 {}", allSegments.size(), STORE_FILE);
        log.info("文件指纹已记录到 {}", METADATA_FILE);

        return store;
    }

    // ==================== 后续启动：增量加载 ====================

    private InMemoryEmbeddingStore<TextSegment> incrementalLoad() {
        // 加载已有向量库
        InMemoryEmbeddingStore<TextSegment> store = InMemoryEmbeddingStore.fromFile(STORE_FILE);
        log.info("从 {} 加载已有向量库", STORE_FILE);

        // 读取旧指纹 + 扫描当前文件
        Map<String, FileFingerprint> oldMetadata = loadMetadata();
        Map<String, FileFingerprint> currentFiles = scanDocDirectory();

        if (currentFiles.isEmpty() && oldMetadata.isEmpty()) {
            log.info("doc/ 目录为空，跳过入库");
            return store;
        }

        // 检测变更
        List<String> newFiles = new ArrayList<>();
        List<String> modifiedFiles = new ArrayList<>();
        List<String> deletedFiles = new ArrayList<>();

        for (String fileName : currentFiles.keySet()) {
            if (!oldMetadata.containsKey(fileName)) {
                newFiles.add(fileName);
            } else {
                FileFingerprint oldFp = oldMetadata.get(fileName);
                FileFingerprint currentFp = currentFiles.get(fileName);
                if (oldFp.lastModified != currentFp.lastModified || oldFp.size != currentFp.size) {
                    modifiedFiles.add(fileName);
                }
            }
        }

        for (String fileName : oldMetadata.keySet()) {
            if (!currentFiles.containsKey(fileName)) {
                deletedFiles.add(fileName);
            }
        }

        // 无变更：直接返回（不消耗 Token）
        if (newFiles.isEmpty() && modifiedFiles.isEmpty() && deletedFiles.isEmpty()) {
            int segmentCount = countSegments(store);
            log.info("doc/ 目录无变化，加载已有向量库（{} 个片段），本次不消耗向量模型 Token", segmentCount);
            return store;
        }

        log.info("检测到 {} 个新增文件，{} 个修改文件，{} 个删除文件",
                newFiles.size(), modifiedFiles.size(), deletedFiles.size());

        DocumentByParagraphSplitter splitter = new DocumentByParagraphSplitter(1000, 200);
        Map<String, FileFingerprint> updatedMetadata = new LinkedHashMap<>(oldMetadata);

        // 1. 删除文件
        if (!deletedFiles.isEmpty()) {
            log.info("检测到 {} 个删除文件，正在从向量库移除...", deletedFiles.size());
            for (String fileName : deletedFiles) {
                removeFileFromStore(store, fileName);
                updatedMetadata.remove(fileName);
            }
            log.info("删除处理完成，不消耗向量模型 Token");
        }

        // 2. 修改文件：先删旧片段，再向量化新版本
        if (!modifiedFiles.isEmpty()) {
            log.info("检测到 {} 个修改文件，正在更新...", modifiedFiles.size());
            for (String fileName : modifiedFiles) {
                removeFileFromStore(store, fileName);
            }
            List<Document> docs = FileSystemDocumentLoader.loadDocuments(DOC_DIR);
            for (Document doc : docs) {
                String fileName = doc.metadata().getString("file_name");
                if (modifiedFiles.contains(fileName)) {
                    int added = processSingleFile(store, doc, fileName, splitter);
                    File file = new File(DOC_DIR, fileName);
                    updatedMetadata.put(fileName, new FileFingerprint(file.lastModified(), file.length()));
                    log.info("修改文件 {} 已更新，新增 {} 个片段（消耗向量模型 Token）", fileName, added);
                }
            }
        }

        // 3. 新增文件
        if (!newFiles.isEmpty()) {
            log.info("检测到 {} 个新增文件，仅向量化新增文件...", newFiles.size());
            List<Document> docs = FileSystemDocumentLoader.loadDocuments(DOC_DIR);
            for (Document doc : docs) {
                String fileName = doc.metadata().getString("file_name");
                if (newFiles.contains(fileName)) {
                    int added = processSingleFile(store, doc, fileName, splitter);
                    File file = new File(DOC_DIR, fileName);
                    updatedMetadata.put(fileName, new FileFingerprint(file.lastModified(), file.length()));
                    log.info("新增文件 {} 已入库，新增 {} 个片段（消耗向量模型 Token）", fileName, added);
                }
            }
        }

        // 保存更新后的向量库和元数据
        store.serializeToFile(STORE_FILE);
        saveMetadata(updatedMetadata);
        log.info("增量更新完成，向量库已持久化到 {}", STORE_FILE);

        return store;
    }

    // ==================== 辅助方法 ====================

    private void removeFileFromStore(InMemoryEmbeddingStore<TextSegment> store, String fileName) {
        Filter filter = metadataKey("file_name").isEqualTo(fileName);
        store.removeAll(filter);
        log.info("已从向量库移除文件：{}", fileName);
    }

    private int processSingleFile(InMemoryEmbeddingStore<TextSegment> store, Document doc,
                                  String fileName, DocumentByParagraphSplitter splitter) {
        List<TextSegment> segments = splitter.split(doc);
        segments = segments.stream()
                .map(seg -> TextSegment.from(fileName + "\n" + seg.text(), seg.metadata()))
                .collect(Collectors.toList());

        if (!segments.isEmpty()) {
            store.addAll(qwenEmbeddingModel.embedAll(segments).content(), segments);
        }
        return segments.size();
    }

    private int countSegments(InMemoryEmbeddingStore<TextSegment> store) {
        try {
            String json = store.serializeToJson();
            int count = json.split("\"textSegment\"").length - 1;
            return Math.max(count, 0);
        } catch (Exception e) {
            return 0;
        }
    }

    private Map<String, FileFingerprint> loadMetadata() {
        File file = METADATA_FILE.toFile();
        if (!file.exists()) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.readValue(file, new TypeReference<Map<String, FileFingerprint>>() {});
        } catch (IOException e) {
            log.warn("读取 metadata.json 失败: {}", e.getMessage());
            return new LinkedHashMap<>();
        }
    }

    private void saveMetadata(Map<String, FileFingerprint> metadata) {
        try {
            objectMapper.writeValue(METADATA_FILE.toFile(), metadata);
        } catch (IOException e) {
            log.warn("保存 metadata.json 失败: {}", e.getMessage());
        }
    }

    private Map<String, FileFingerprint> scanDocDirectory() {
        Map<String, FileFingerprint> result = new LinkedHashMap<>();
        File docDir = new File(DOC_DIR);
        if (!docDir.exists()) {
            return result;
        }
        File[] files = docDir.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.isFile()) {
                result.put(file.getName(), new FileFingerprint(file.lastModified(), file.length()));
            }
        }
        return result;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileFingerprint {
        private long lastModified;
        private long size;
    }
}