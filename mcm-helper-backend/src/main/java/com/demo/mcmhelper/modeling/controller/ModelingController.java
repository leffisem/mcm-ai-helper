package com.demo.mcmhelper.modeling.controller;

import com.demo.mcmhelper.modeling.ModelingService;
import com.demo.mcmhelper.modeling.dto.ChatRequest;
import jakarta.annotation.Resource;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai")
public class ModelingController {

    @Resource
    private ModelingService modelingService;

    @PostMapping(value = "/chat", produces = "text/event-stream;charset=UTF-8")
    public Flux<ServerSentEvent<String>> chat(@RequestBody ChatRequest request) {
        return modelingService.chatStream(request.getMemoryId(), request.getMessage())
                .map(chunk -> ServerSentEvent.<String>builder()
                        .data(chunk)
                        .build());
    }
}