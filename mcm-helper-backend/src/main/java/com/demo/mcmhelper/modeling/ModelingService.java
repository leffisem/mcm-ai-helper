package com.demo.mcmhelper.modeling;

import com.demo.mcmhelper.modeling.guardrail.SafeInputGuardrail;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.Result;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.guardrail.InputGuardrails;
import reactor.core.publisher.Flux;

import java.util.List;

@InputGuardrails({SafeInputGuardrail.class})
public interface ModelingService {

    @SystemMessage(fromResource = "system-prompt.txt")
    String chat(String userMessage);

    @SystemMessage(fromResource = "system-prompt.txt")
    Report chatforReport(String userMessage);

    record Report(String name, List<String> suggestionList){};

    //返回封装后的结果
    @SystemMessage(fromResource = "system-prompt.txt")
    Result<String> chatWithRag(String userMessage);


    @SystemMessage(fromResource = "system-prompt.txt")
    Flux<String> chatStream(@MemoryId int memoryId,@UserMessage String message);

    // ========== 三段式专家模式 ==========
    @SystemMessage(fromResource = "modeling-prompt.txt")
    Flux<String> chatExpert(@MemoryId int memoryId, @UserMessage String userMessage);
}