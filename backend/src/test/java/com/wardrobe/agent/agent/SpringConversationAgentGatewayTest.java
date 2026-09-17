package com.wardrobe.agent.agent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpringConversationAgentGatewayTest {

    @Test
    void forwardsStreamingContentInArrivalOrderAndStoresCompleteAnswer() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ChatMemory memory = mock(ChatMemory.class);
        stubRequest(builder, client, request, stream, call);
        when(stream.content()).thenReturn(Flux.just("你好", "，这是", "流式回复"));

        SpringConversationAgentGateway gateway = new SpringConversationAgentGateway(
                builder, memory, "test-model", "https://example.test/v1");
        List<String> deltas = new ArrayList<>();

        String answer = gateway.respond("memory-1", "你好", "NONE", mock(WardrobeAgentTools.class), deltas::add);

        assertThat(answer).isEqualTo("你好，这是流式回复");
        assertThat(deltas).containsExactly("你好", "，这是", "流式回复");
        verify(memory).add("memory-1", new org.springframework.ai.chat.messages.AssistantMessage(answer));
    }

    @Test
    void fallsBackToSynchronousContentWhenStreamHasNoContent() {
        ChatClient client = mock(ChatClient.class);
        ChatClient.Builder builder = mock(ChatClient.Builder.class);
        ChatClient.ChatClientRequestSpec request = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec stream = mock(ChatClient.StreamResponseSpec.class);
        ChatClient.CallResponseSpec call = mock(ChatClient.CallResponseSpec.class);
        ChatMemory memory = mock(ChatMemory.class);
        stubRequest(builder, client, request, stream, call);
        when(stream.content()).thenReturn(Flux.empty());
        when(call.content()).thenReturn("同步兜底回复");

        SpringConversationAgentGateway gateway = new SpringConversationAgentGateway(
                builder, memory, "test-model", "https://example.test/v1");
        List<String> deltas = new ArrayList<>();

        String answer = gateway.respond("memory-2", "请继续", "NONE", mock(WardrobeAgentTools.class), deltas::add);

        assertThat(answer).isEqualTo("同步兜底回复");
        assertThat(deltas).containsExactly("同步兜底回复");
        verify(call).content();
    }

    private static void stubRequest(ChatClient.Builder builder,
                                    ChatClient client,
                                    ChatClient.ChatClientRequestSpec request,
                                    ChatClient.StreamResponseSpec stream,
                                    ChatClient.CallResponseSpec call) {
        when(builder.defaultSystem(anyString())).thenReturn(builder);
        when(builder.build()).thenReturn(client);
        when(client.prompt()).thenReturn(request);
        when(request.messages(org.mockito.ArgumentMatchers.<List<org.springframework.ai.chat.messages.Message>>any()))
                .thenReturn(request);
        when(request.messages(org.mockito.ArgumentMatchers.<org.springframework.ai.chat.messages.Message[]>any()))
                .thenReturn(request);
        when(request.user(anyString())).thenReturn(request);
        when(request.tools(org.mockito.ArgumentMatchers.any(Object[].class))).thenReturn(request);
        when(request.stream()).thenReturn(stream);
        when(request.call()).thenReturn(call);
    }
}
