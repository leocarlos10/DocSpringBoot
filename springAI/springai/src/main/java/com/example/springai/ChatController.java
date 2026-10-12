package com.example.springai;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;


/**
 * ChatController
 * 
 * ChatClient - construye un cliente de chat
 * para interactuar con el modelo pero ahora bien quien especifica
 * el modelo
 * 
 * ChatModel - representa el modelo de chat 
 * que se va a utilizar para generar respuestas de chat
 * Spring sabe que tipo de modelo utilizar por la dependencia
 * instalada en el application.yaml
 * 
 * system - Es como debe comportarse el modelo: su rol, tono, idioma
 * ,reglas y limites Lo escribes tu como desarrollador y suele
 * ser fijo para todas las conversaciones. El usuario normalmente no lo ve.
 * 
 * podemos hacer uso de un Resource para cargar el prompt del sistema desde un archivo de texto o un .st
 * 
 * user - Es lo que piedes al modelo: la pregunta o la instruccion concreta
 * de se momento. Normalmente viene del usuario final, y cambia en cada peticion.
 * 
 * defaultOptions - Es un conjunto de opciones que se aplican a todas las llamadas al modelo de chat.
 * 
 * 
 *   
 */
@RestController 
@RequestMapping ("/chats")
public class ChatController {

    private final ChatClient chatClient;
    // dependencia core.io.Resource
    private final Resource systemPrompt;

    /* 
    Utilizamos la inyeccion de dependencias para obtener el modelo de chat y el prompt del sistema
    */
    public ChatController(
        ChatModel chatModel,
        @Value("classpath:system-prompts-v1.st") 
        Resource systemPrompt
    ) {
        this.chatClient = ChatClient.builder(chatModel)
        .defaultOptions(
            OllamaChatOptions.builder()
            .temperature(0.7)
            .model(OllamaModel.LLAMA3_2_1B)
        )
        .build();
        this.systemPrompt = systemPrompt;
    }
    
    @GetMapping
    public String chat() {
        /* return chatClient.prompt()
        .system("Eres un asistente util")
        .user("hola que tal estas ?")
        .call()
        .content(); */

        return chatClient.prompt()
            .system(systemPrompt)
            .user(u -> u
                .text("hola {nombre} que tal estas ?")
                .param("nombre", "pepe")
            )
            .call().content();


    }
    
}
