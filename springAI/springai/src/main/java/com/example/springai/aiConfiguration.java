package com.example.springai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * aiConfiguration
 * 
 * En los bean de configuracion de proveedores
 * externos como openai y antrophic
 * hay que agregar el .apiKey("###") para que funcione 
 */
@Configuration 
public class aiConfiguration {


    @Bean("openAiChatModel")
    public ChatClient openAIChatClient(@Qualifier("openAiChatModel") OpenAiChatModel model){
        return ChatClient.builder(model)
        .defaultOptions(OpenAiChatOptions.builder()
            .temperature(0.7)
            .apiKey("###")
        )
        .build();
    }

    @Bean("ollamaChatModel")
    public ChatClient ollamaChatClient(@Qualifier("ollamaChatModel") OllamaChatModel model){
        return ChatClient.builder(model)
        .defaultOptions(OllamaChatOptions.builder())
        .build();
    }
    
}