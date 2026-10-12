# Spring AI: ChatClient vs ChatModel (con Ollama)

## 1. ¿Qué es `ChatModel`?

Es la **capa de bajo nivel** de Spring AI. Es una interfaz que abstrae al proveedor del LLM. Define un contrato común y cada proveedor tiene su implementación:

- `OllamaChatModel`
- `OpenAiChatModel`
- `AnthropicChatModel`
- `AzureOpenAiChatModel`

Su trabajo es **hablar con un modelo específico**: convierte tu `Prompt` al formato que entiende el proveedor, hace la llamada HTTP y traduce la respuesta a objetos de Spring AI.

> `ChatModel` no crea las implementaciones: es la interfaz que cada proveedor implementa.

## 2. ¿Qué es `ChatClient`?

Es la **capa de alto nivel**: una API fluida (estilo builder) para construir peticiones y consumir respuestas. Por debajo usa un `ChatModel`.

Ofrece:

- Sintaxis fluida: `.prompt().user("...").call().content()`
- System prompt por defecto (`defaultSystem`)
- Mapeo de la respuesta a objetos Java (`.entity(MiRecord.class)`)
- Streaming (`.stream().content()`)
- Advisors (memoria de conversación, RAG, logging)
- Tool calling y opciones por defecto

## 3. Diferencias

| | `ChatModel` | `ChatClient` |
|---|---|---|
| Nivel | Bajo | Alto |
| Rol | Se conecta a un proveedor/modelo concreto | API cómoda para usar cualquier `ChatModel` |
| Implementaciones | Una por proveedor | Una sola; cambia el `ChatModel` que recibe |
| Uso típico | Control total del request/response | Uso diario en controllers y servicios |
| Extras | Ninguno | Advisors, system prompt, entity, streaming |

Flujo completo:

```
Controller → ChatClient → ChatModel (OllamaChatModel) → Ollama (localhost:11434) → llama3.2:1b
```

## 4. Instalar Ollama y un modelo pequeño

```bash
# Linux/macOS
curl -fsSL https://ollama.com/install.sh | sh

# Descargar el modelo
ollama pull llama3.2:1b

# Probar
ollama run llama3.2:1b
```

En Windows, instala desde ollama.com. Ollama escucha en `http://localhost:11434`.

Modelos pequeños recomendados: `llama3.2:1b`, `qwen2.5:1.5b`. Si las respuestas salen flojas, prueba `llama3.2:3b` o `qwen2.5:3b`.

## 5. Dependencias (Maven)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.ai</groupId>
            <artifactId>spring-ai-bom</artifactId>
            <version>1.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.ai</groupId>
        <artifactId>spring-ai-starter-model-ollama</artifactId>
    </dependency>
</dependencies>
```

> Usa la versión de Spring AI que corresponda a tu proyecto. Antes de la 1.0 el starter se llamaba `spring-ai-ollama-spring-boot-starter`.

## 6. application.properties

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2:1b
spring.ai.ollama.chat.options.temperature=0.7
```

## 7. Primer ejemplo: crear el ChatClient desde ChatModel

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    @GetMapping
    public String chat() {
        return chatClient.prompt()
                .user("hola que tal estas?")
                .call()
                .content();
    }
}
```

## 8. Variante: inyectar ChatClient.Builder

Spring Boot autoconfigura un `ChatClient.Builder` (no un `ChatClient` ya construido). Es un bean de scope **prototype**: cada inyección recibe una instancia nueva para configurarla a gusto.

```java
@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient.Builder builder) {
        this.chatClient = builder
                .defaultSystem("Eres un asistente que responde siempre en español y de forma breve.")
                .build();
    }

    @GetMapping
    public String chat(@RequestParam(defaultValue = "Hola, ¿quién eres?") String message) {
        return chatClient.prompt()
                .user(message)
                .call()
                .content();
    }
}
```

## 9. ¿Por qué no hay un bean ChatClient listo?

No es porque sea una interfaz. Es una decisión de diseño: un `ChatClient` suele ser **específico de cada caso de uso** (distinto system prompt, advisors u opciones). Si Spring diera uno global, todos compartirían la misma configuración.

Si quieres un bean `ChatClient`, lo defines tú:

```java
@Configuration
public class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("Eres un asistente breve y en español")
                .build();
    }
}
```

## 10. ¿Cómo sabe Spring qué ChatModel inyectar? (Autoconfiguración)

1. Agregas la dependencia `spring-ai-starter-model-ollama`.
2. El starter trae una clase de autoconfiguración (`OllamaChatAutoConfiguration`).
3. Esa clase solo se activa si Ollama está en el classpath y la propiedad está habilitada.
4. Registra un bean `OllamaChatModel` con tus propiedades (`spring.ai.ollama.*`).
5. Cuando pides un `ChatModel`, Spring encuentra ese bean.

Si cambias a `spring-ai-starter-model-openai`, se registra `OpenAiChatModel` y tu controller **no cambia**, solo las propiedades.

## 11. Dos proveedores a la vez (Ollama + OpenAI)

Con ambas dependencias habrá **dos beans** `ChatModel` y Spring lanzará `NoUniqueBeanDefinitionException`. Opciones:

**a) Elegir con @Qualifier**

```java
public ChatController(@Qualifier("ollamaChatModel") ChatModel chatModel) {
    this.chatClient = ChatClient.builder(chatModel).build();
}
```

**b) Un ChatClient por proveedor**

```java
@Configuration
public class AiConfig {

    @Bean
    ChatClient ollamaClient(OllamaChatModel model) {
        return ChatClient.builder(model).build();
    }

    @Bean
    ChatClient openAiClient(OpenAiChatModel model) {
        return ChatClient.builder(model).build();
    }
}
```

```java
public ChatController(@Qualifier("ollamaClient") ChatClient chatClient) {
    this.chatClient = chatClient;
}
```

**c) Desactivar uno por propiedad**

```properties
spring.ai.model.chat=ollama
```

## 12. Siguientes pasos para practicar

- **Streaming:** usa `.stream().content()` y retorna `Flux<String>`.

```java
  @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public Flux<String> stream() {
      return chatClient.prompt().user("Cuéntame un chiste").stream().content();
  }
```

- **Salida estructurada:** mapea la respuesta a un record.

```java
  record Receta(String nombre, List<String> ingredientes) {}

  Receta receta = chatClient.prompt()
          .user("Dame una receta de arepas")
          .call()
          .entity(Receta.class);
```

- **Memoria de conversación** con advisors.
- **Dos ChatClient** con modelos distintos en la misma app.

---

# ChatOptions en Spring AI

## 1. ¿Qué es `ChatOptions`?

Es la interfaz de Spring AI que representa los **parámetros de configuración de una petición al modelo**: cuánta creatividad tiene, cuántos tokens puede generar, qué modelo usar, etc. No cambia *qué* le preguntas (eso es `system` y `user`), sino *cómo* responde el modelo.

Hay dos niveles:

- **`ChatOptions`**: interfaz común con los parámetros que casi todos los proveedores soportan (portable entre proveedores).
- **Opciones específicas del proveedor**: por ejemplo `OllamaOptions` (antes `OllamaChatOptions`), `OpenAiChatOptions`, `AnthropicChatOptions`. Incluyen parámetros propios de cada proveedor.

> Nota: los nombres exactos de clases y métodos pueden variar entre versiones de Spring AI (por ejemplo, entre 1.0.x y 1.1.x). Si algo no compila, revisa el autocompletado del IDE y la documentación de tu versión.

## 2. Parámetros más comunes

| Parámetro | Qué hace | Efecto práctico |
|---|---|---|
| `model` | Qué modelo usar | `llama3.2:1b`, `qwen2.5:3b`... |
| `temperature` | Aleatoriedad de la respuesta | Bajo (0.0–0.3) = más preciso y repetible. Alto (0.8–1.2) = más creativo y variado |
| `maxTokens` | Máximo de tokens que puede generar la respuesta | Limita la longitud de la respuesta |
| `topP` | Muestreo por probabilidad acumulada | Alternativa a temperature; normalmente se ajusta uno u otro, no ambos |
| `topK` | Considera solo los K tokens más probables | Menor valor = respuestas más conservadoras |
| `frequencyPenalty` | Penaliza repetir palabras ya usadas | Reduce repeticiones |
| `presencePenalty` | Penaliza repetir temas ya mencionados | Fomenta temas nuevos |
| `stopSequences` | Textos que hacen que el modelo deje de generar | Cortar la respuesta en un punto concreto |

Un **token** es un fragmento de texto (aprox. una palabra o parte de una). En español, 100 tokens son más o menos 60–75 palabras.

### Temperature en la práctica

- `0.0` a `0.3`: ideal para extracción de datos, salida estructurada, clasificación, código.
- `0.5` a `0.7`: equilibrio, uso general.
- `0.8` o más: escritura creativa, lluvia de ideas.

Con modelos pequeños (1B), una temperatura muy alta suele producir respuestas incoherentes. Para practicar, `0.3`–`0.7` funciona bien.

## 3. Tres formas de configurarlas

### a) Globalmente con `application.properties`

Se aplican a **todas** las peticiones que use ese `ChatModel`:

```properties
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2:1b
spring.ai.ollama.chat.options.temperature=0.7
spring.ai.ollama.chat.options.num-predict=200
```

> En Ollama, el límite de tokens de salida se llama `num-predict` (equivale a `maxTokens`). Con las opciones portables de `ChatOptions` se usa `maxTokens` y Spring AI lo traduce.

### b) Por defecto en un `ChatClient` (`defaultOptions`)

Se aplican a todas las llamadas de **ese cliente**, sin afectar a otros clientes:

```java
import org.springframework.ai.chat.prompt.ChatOptions;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel)
                .defaultSystem("Eres un asistente breve y en español")
                .defaultOptions(ChatOptions.builder()
                        .temperature(0.3)
                        .maxTokens(200)
                        .build())
                .build();
    }

    @GetMapping
    public String chat() {
        return chatClient.prompt()
                .user("Explícame qué es un bean en Spring")
                .call()
                .content();
    }
}
```

### c) Por petición (`.options(...)`)

Se aplican **solo a esa llamada** y sobrescriben las anteriores:

```java
@GetMapping("/creativo")
public String creativo() {
    return chatClient.prompt()
            .user("Inventa un eslogan para una tienda de arepas")
            .options(ChatOptions.builder()
                    .temperature(1.0)
                    .maxTokens(50)
                    .build())
            .call()
            .content();
}
```

## 4. Orden de prioridad

De menor a mayor prioridad (el último gana):

1. `application.properties` (valores por defecto del `ChatModel`)
2. `defaultOptions(...)` del `ChatClient`
3. `.options(...)` en la petición concreta

```
properties  <  defaultOptions del ChatClient  <  options() de la petición
```

## 5. Opciones específicas del proveedor (Ollama)

Cuando necesitas algo que no está en `ChatOptions`, usas la clase del proveedor:

```java
import org.springframework.ai.ollama.api.OllamaOptions;

@GetMapping("/ollama")
public String conOpcionesOllama() {
    return chatClient.prompt()
            .user("Dame un dato curioso sobre Colombia")
            .options(OllamaOptions.builder()
                    .model("qwen2.5:1.5b")   // cambiar de modelo solo en esta petición
                    .temperature(0.5)
                    .numCtx(2048)            // tamaño de la ventana de contexto
                    .build())
            .call()
            .content();
}
```

Ventajas y desventajas:

- Permiten usar todo lo que ofrece el proveedor (`numCtx`, `repeatPenalty`, `keepAlive`, etc.).
- Atan ese código a Ollama: si cambias de proveedor, hay que reescribir esa parte.

Un detalle útil: al poner `.model("otro-modelo")` en las opciones puedes **cambiar de modelo por petición** sin crear otro `ChatModel`, siempre que ese modelo esté descargado en Ollama (`ollama pull qwen2.5:1.5b`).

## 6. Relación con system y user

| Concepto | Qué controla |
|---|---|
| `system` | Cómo **debe comportarse** el modelo (rol, tono, reglas) |
| `user` | **Qué le pides** en esta petición |
| `ChatOptions` | Cómo **genera** el modelo (creatividad, longitud, modelo) |

Ejemplo con los tres juntos:

```java
String respuesta = chatClient.prompt()
        .system("Eres un profesor de Java. Responde en español.")
        .user("¿Qué es una interfaz?")
        .options(ChatOptions.builder().temperature(0.2).maxTokens(150).build())
        .call()
        .content();
```

## 7. Ejercicios para practicar

1. Haz la misma pregunta con `temperature` en `0.0` varias veces: la respuesta debería ser casi idéntica.
2. Haz la misma pregunta con `temperature` en `1.2`: cada respuesta debería variar bastante.
3. Pon `maxTokens` en `20` y observa cómo la respuesta se corta a mitad de frase.
4. Define `defaultOptions` en el cliente y sobrescribe solo `temperature` en un endpoint con `.options(...)`.
5. Cambia de modelo en una petición con `OllamaOptions.builder().model("qwen2.5:1.5b")`.

## Resumen rápido

- `ChatOptions` configura **cómo genera** el modelo, no qué le preguntas.
- Parámetros clave: `model`, `temperature`, `maxTokens`, `topP`, `topK`.
- Se configuran en 3 niveles: `application.properties` < `defaultOptions` < `.options()` por petición.
- Las opciones portables (`ChatOptions`) funcionan con cualquier proveedor; las específicas (`OllamaOptions`) dan más control pero atan el código a ese proveedor.
- Con modelos pequeños, usa temperaturas moderadas para evitar respuestas incoherentes.