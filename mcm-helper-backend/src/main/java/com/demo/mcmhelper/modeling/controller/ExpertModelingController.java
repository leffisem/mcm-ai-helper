package com.demo.mcmhelper.modeling.controller;

import com.demo.mcmhelper.modeling.ExpertChatRouter;
import com.demo.mcmhelper.modeling.dto.ChatRequest;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/modeling")
public class ExpertModelingController {

    @Resource
    private ExpertChatRouter expertChatRouter;

    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> modelingChat(@RequestBody ChatRequest request) {
        return expertChatRouter.chat(request.getMemoryId(), request.getMessage())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/analyze", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> analyze(
            @RequestParam("problem") String problem,
            @RequestParam(value = "memoryId", defaultValue = "0") int memoryId) {
        return expertChatRouter.analyzeCompleteProblem(memoryId, problem)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }

    @GetMapping(value = "/section", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> section(
            @RequestParam("section") String section,
            @RequestParam("question") String question,
            @RequestParam(value = "memoryId", defaultValue = "0") int memoryId) {
        return expertChatRouter.respondSpecificSection(memoryId, section, question)
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}