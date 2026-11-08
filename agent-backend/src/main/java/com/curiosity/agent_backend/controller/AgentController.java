package com.curiosity.agent_backend.controller;

import com.curiosity.agent_backend.service.AgentClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentClient agentClient;

    public AgentController(AgentClient agentClient) {
        this.agentClient = agentClient;
    }

    @GetMapping("/ping")
    public Map<String, Object> ping() {
        return agentClient.ping();
    }
}