package com.adria.bank.chatbot.web;

import com.adria.bank.chatbot.agent.BankAgent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final BankAgent bankAgent;

    public ChatController(BankAgent bankAgent) {
        this.bankAgent = bankAgent;
    }

    public record ChatRequest(String conversationId, String message) {
    }

    @PostMapping
    public Map<String, String> chat(@RequestBody ChatRequest request) {
        String conversationId = request.conversationId() == null ? "web" : request.conversationId();
        return Map.of("conversationId", conversationId, "reply", bankAgent.ask(conversationId, request.message()));
    }
}
