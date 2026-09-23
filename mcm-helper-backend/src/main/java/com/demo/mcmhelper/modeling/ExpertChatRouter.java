package com.demo.mcmhelper.modeling;

import com.demo.mcmhelper.modeling.IntentClassifier.IntentType;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 专家模式路由：在 Java 层完成意图识别，将路由指令嵌入用户消息，
 * 再调用 ModelingService（AI 服务代理）进行流式输出。
 *
 * 意图判断由 Java 代码完成，不依赖 AI 模型决策，无 Token 额外开销。
 *
 * 上下文隔离：专家模式使用 memoryId + MEMORY_OFFSET 作为实际记忆 key，
 * 与普通模式（/ai/chat）的 ChatMemory 完全隔离，避免提示词冲突。
 */
@Component
@Slf4j
public class ExpertChatRouter {

    /** 专家模式 memoryId 偏移量，与普通模式隔离 */
    private static final int MEMORY_OFFSET = 10000;

    private final IntentClassifier classifier = new IntentClassifier();

    @Resource
    private ModelingService modelingService;

    /**
     * 统一入口：根据意图自动路由三段式策略
     *
     * @param memoryId 会话ID
     * @param message  用户消息
     * @return SSE 流式输出
     */
    public Flux<String> chat(int memoryId, String message) {
        IntentType intent = classifier.classify(message, null);
        log.info("专家模式路由：意图={}, memoryId={}", intent, memoryId);

        String routedMessage = switch (intent) {
            case COMPLETE_PROBLEM -> "【策略A：完整赛题分析】\n" + message;
            case SPECIFIC_SECTION -> {
                String section = classifier.extractSection(message);
                yield "【策略B：单一环节求助 - " + (section != null ? section : "审题") + "】\n" + message;
            }
            case VAGUE_QUERY -> "【策略C：模糊追问】\n" + message;
        };

        return modelingService.chatExpert(memoryId + MEMORY_OFFSET, routedMessage);
    }

    /**
     * 强制走策略A：完整赛题分析
     */
    public Flux<String> analyzeCompleteProblem(int memoryId, String problem) {
        String routedMessage = "【策略A：完整赛题分析】\n" + problem;
        return modelingService.chatExpert(memoryId + MEMORY_OFFSET, routedMessage);
    }

    /**
     * 强制走策略B：单一环节求助
     */
    public Flux<String> respondSpecificSection(int memoryId, String section, String question) {
        String routedMessage = "【策略B：单一环节求助 - " + section + "】\n" + question;
        return modelingService.chatExpert(memoryId + MEMORY_OFFSET, routedMessage);
    }
}