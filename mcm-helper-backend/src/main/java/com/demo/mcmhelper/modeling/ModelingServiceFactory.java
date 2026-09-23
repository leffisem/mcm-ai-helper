package com.demo.mcmhelper.modeling;

import com.demo.mcmhelper.modeling.tools.InterviewQuestionTool;
import dev.langchain4j.mcp.McpToolProvider;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ModelingServiceFactory {
    @Resource
    private ChatModel myQwenModeling;

    @Resource
    private ContentRetriever contentRetriever;

    @Resource
    private McpToolProvider mcpToolProvider;

    @Resource
    private StreamingChatModel myQwenStreamingModeling;

    @Bean
    public ModelingService modelingService(){
        //会话记忆
        ChatMemory chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        //构造AiService
        ModelingService modelingService =AiServices.builder(ModelingService.class)
                .chatModel(myQwenModeling)
                .streamingChatModel(myQwenStreamingModeling)
                .chatMemory(chatMemory)//会话记忆
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(10))//每个对话独立存储
                .contentRetriever(contentRetriever)//RAG 检索增强生成
                .tools(new InterviewQuestionTool()) //工具调用
                .toolProvider(mcpToolProvider)
                .build();

        return modelingService;
    }
}