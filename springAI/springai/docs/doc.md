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

---

# Streaming con `.stream()` y `Flux<String>` en Spring AI

## 1. Explicación

Con `.call()` el modelo genera toda la respuesta y recién cuando termina te la entrega completa. Con `.stream()` la respuesta llega **por pedazos (tokens) a medida que el modelo los genera**, igual que cuando ves escribir a ChatGPT.

`Flux<String>` es el tipo de **Project Reactor** que representa ese flujo: una secuencia de 0 a N valores que llegan con el tiempo. Cada elemento del `Flux` es un fragmento de texto.

```java
Flux<String> flujo = chatClient.prompt()
        .user("Cuéntame un chiste")
        .stream()
        .content();
```

> Requiere la dependencia `spring-boot-starter-webflux` o que `spring-boot-starter-web` tenga Reactor disponible. Spring AI ya trae Reactor como dependencia transitiva, así que normalmente no necesitas agregar nada.

## 2. Ejemplo sencillo

```java
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/chats")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatModel chatModel) {
        this.chatClient = ChatClient.builder(chatModel).build();
    }

    // Respuesta completa de una sola vez
    @GetMapping
    public String chat() {
        return chatClient.prompt()
                .user("Cuéntame un chiste corto")
                .call()
                .content();
    }

    // Respuesta en streaming
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream() {
        return chatClient.prompt()
                .user("Cuéntame un chiste corto")
                .stream()
                .content();
    }
}
```

Prueba en la terminal para ver los fragmentos llegar en vivo:

```bash
curl -N http://localhost:8080/chats/stream
```

(`-N` desactiva el buffer de curl para que veas los tokens al instante.)

## 3. Diferencias entre `.call()` y `.stream()`

| | `.call()` | `.stream()` |
|---|---|---|
| Retorno | `String` (o `ChatResponse`, `entity`) | `Flux<String>` (o `Flux<ChatResponse>`) |
| Cuándo llega la respuesta | Al terminar toda la generación | Token por token, mientras se genera |
| Experiencia de usuario | Espera hasta ver todo | Ve el texto aparecer de inmediato |
| Complejidad | Simple | Requiere entender `Flux` |
| Ideal para | Procesos internos, salida estructurada | Chats e interfaces interactivas |

## 4. Ventajas y desventajas del streaming

**Ventajas**
- El usuario ve respuesta casi al instante (menor tiempo hasta el primer token).
- Mejor experiencia en respuestas largas.
- No bloquea el hilo esperando toda la respuesta.

**Desventajas**
- Más complejo de consumir y de probar.
- Difícil de combinar con salida estructurada (`.entity()`), porque el JSON completo solo existe al final.
- El manejo de errores a mitad del flujo es más delicado.

## Resumen rápido

- `.call()` = respuesta completa; `.stream()` = respuesta por fragmentos.
- `Flux<String>` es el flujo de fragmentos que devuelve `.stream().content()`.
- En el controller usa `produces = MediaType.TEXT_EVENT_STREAM_VALUE` para que el cliente reciba los eventos en vivo.

---

# Opciones de retry para reintentar peticiones y descarga automatica de modelos locales

```yaml
spring:
  application:
    name: springai
  ai:
    ollama:
      chat:
        model: llama3.2:1b
      init:
        pull-model-strategy: when_missing # never - no descarga nunca, always - lo descarga siempre, when_missing - si no existe lo descarga.
    retry:
        max-attempts: 2 # maximo de reintos
        backoff:
            initial-interval: 1000ms # tiempo que tarda entre prueba y prueba
            multiplier: 5 # multiplica el initial-interval
            max-interval: 10000ms # que demore maximo 10s en hacer todo esto.

```

---

# configuracion de un proveedor especifico

antes de agregar esta configuracion asegurate de agregar la dependencia en el pom.xml

## . Dependencia (Maven)

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

```yaml
pring:
  application:
    name: springai
  ai:
    openai: # en este caso es open ai pero puede ser cualquier proveedor de ia
    api-key: ${OPEN_KEY}
    chat:
        model: ${AI_MODEL}
        base-url:
```

---

# Respuestas del LLM en formato JSON con Spring AI (Structured Output)

## 1. Explicación

Los LLM responden texto libre. Muchas veces necesitamos que la respuesta sea **JSON** para convertirla en objetos Java. Spring AI lo resuelve con los **Output Converters**, que hacen dos cosas:

1. **Antes de la llamada:** agregan al prompt unas instrucciones de formato (un JSON Schema) que le dicen al modelo cómo debe responder.
2. **Después de la llamada:** toman el texto devuelto y lo convierten (deserializan) al tipo Java que pediste.

Hay tres formas de usarlos. En todas usaremos `List<Curiosidad>` como resultado.

## 2. Objeto de ejemplo

```java
package dev.codeja.curso_spring_ai;

public record Curiosidad(
        String titulo,
        String descripcion,
        String tituloPelicula
) {
}
```

Salida JSON que esperamos del modelo:

```json
[
  {
    "titulo": "El sonido del sable",
    "descripcion": "Se creó mezclando un proyector de cine con el zumbido de un televisor.",
    "tituloPelicula": "Episodio IV: Una nueva esperanza"
  }
]
```

## 3. Opción 1: `.entity()` con `ParameterizedTypeReference` (lista directa)

Es la forma más corta y la recomendada para el día a día. `ChatClient` crea el converter por ti.

Como en Java los genéricos se borran en tiempo de ejecución (`List<Curiosidad>` pasa a ser `List`), hay que usar `ParameterizedTypeReference` para conservar el tipo.

```java
import org.springframework.core.ParameterizedTypeReference;

@GetMapping("/lista")
public List<Curiosidad> curiosidades() {
    return chatClient.prompt()
            .user("Genérame 5 curiosidades de star wars.")
            .call()
            .entity(new ParameterizedTypeReference<List<Curiosidad>>() {});
}
```

Nota: las llaves `{}` al final son necesarias; crean una clase anónima que "captura" el tipo genérico.

## 4. Opción 2: `.entity()` con un objeto contenedor (record)

Si no quieres usar `ParameterizedTypeReference`, puedes envolver la lista en un record. En este caso `.entity(Clase.class)` basta. (Esto cubre el caso de "objeto directo con entity", sin lista genérica.)

```java
public record StarWarsCuriosidades(List<Curiosidad> curiosidades) {
}
```

```java
@GetMapping("/contenedor")
public StarWarsCuriosidades contenedor() {
    return chatClient.prompt()
            .user("Genérame 5 curiosidades de star wars.")
            .call()
            .entity(StarWarsCuriosidades.class);
}
```

## 5. Opción 3: `BeanOutputConverter` (manual)

Aquí tú controlas cada paso: creas el converter, pides el formato, lo insertas en el prompt, llamas al modelo y conviertes la respuesta. Es el enfoque de tu código del curso (usa `ChatModel` directamente).

```java
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

@GetMapping
public List<Curiosidad> chat() {

    // 1. Converter para List<Curiosidad>
    BeanOutputConverter<List<Curiosidad>> outputConverter =
            new BeanOutputConverter<>(new ParameterizedTypeReference<List<Curiosidad>>() {});

    // 2. Instrucciones de formato (JSON Schema) que se envían al modelo
    String format = outputConverter.getFormat();
    log.info("Este es el formato utilizado {}", format);

    // 3. Plantilla del prompt con el placeholder {format}
    String template = """
            Genérame 5 curiosidades de star wars.
            {format}
            """;

    Prompt prompt = PromptTemplate.builder()
            .template(template)
            .variables(Map.of("format", format))
            .build()
            .create();

    // 4. Llamada al modelo
    Generation generation = chatModel.call(prompt).getResult();

    // 5. Convertir el texto de la respuesta a List<Curiosidad>
    return outputConverter.convert(generation.getOutput().getText());
}
```

Qué hace `getFormat()`: devuelve un texto con el JSON Schema de `List<Curiosidad>` y la instrucción de responder solo con JSON válido. Ese texto es lo que se pega en `{format}`.

## 6. Qué hace `.entity()` por debajo

`.entity(...)` es un atajo de la opción 3: internamente crea un `BeanOutputConverter`, agrega el formato al prompt y convierte la respuesta. Por eso las tres opciones producen el mismo resultado; cambia cuánto control tienes.

```
.entity(tipo)  →  BeanOutputConverter  →  getFormat() al prompt  →  LLM  →  convert()  →  objeto Java
```

## 7. Otros converters incluidos

| Converter | Devuelve | Cómo usarlo con `ChatClient` |
|---|---|---|
| `BeanOutputConverter<T>` | Un objeto o lista de tu clase | `.entity(Clase.class)` o `.entity(new ParameterizedTypeReference<...>() {})` |
| `MapOutputConverter` | `Map<String, Object>` | Manual con `new MapOutputConverter()` |
| `ListOutputConverter` | `List<String>` (valores simples) | Manual con `new ListOutputConverter(new DefaultConversionService())` |

## 8. Diferencias entre las opciones

| | `.entity` + `ParameterizedTypeReference` | `.entity` + record contenedor | `BeanOutputConverter` manual |
|---|---|---|---|
| Cantidad de código | Mínima | Baja (requiere un record extra) | Alta |
| Control del prompt | Bajo | Bajo | Total |
| Usa `ChatClient` | Sí | Sí | No necesariamente (sirve con `ChatModel`) |
| Ver el formato enviado | No | No | Sí (`getFormat()`) |
| Ideal para | Uso diario | Cuando quieres un envoltorio con más campos | Aprender, depurar o personalizar el prompt |

## 9. Ventajas y desventajas

**Ventajas**
- Conviertes la respuesta directo a objetos Java, sin parsear JSON a mano.
- El esquema se genera automáticamente desde tu clase o record.

**Desventajas**
- No es una garantía: el modelo puede devolver JSON inválido o con campos faltantes, sobre todo los modelos pequeños (1B). Si falla, verás una excepción de parseo.
- Agregar el esquema al prompt consume tokens.
- No funciona bien con `.stream()`, porque el JSON completo solo existe al final.

## 10. Consejos con modelos pequeños

- Baja la `temperature` (por ejemplo `0.0`–`0.3`) para que siga mejor el formato.
- Si falla seguido, prueba un modelo más grande como `llama3.2:3b` o `qwen2.5:3b`.
- Algunos proveedores y modelos soportan salida estructurada nativa (JSON mode); Spring AI puede aprovecharla, pero depende del modelo y de la versión.

## Resumen rápido

- Un Output Converter pide JSON al modelo (con un esquema) y lo convierte a objetos Java.
- Para listas: `.entity(new ParameterizedTypeReference<List<Curiosidad>>() {})`.
- Para objeto simple: `.entity(MiRecord.class)`.
- `BeanOutputConverter` es la versión manual: más control, más código; `.entity()` lo usa internamente.
- Los resultados dependen del modelo: con modelos pequeños, valida y baja la temperatura.

---

# Validación de esquema, salida nativa del proveedor y Advisors en Spring AI

## Parte 1: `useProviderStructuredOutput()` y `validateSchema()`

### 1.1 Explicación

Lo que aparece en el curso:

```java
.entity(StarWarsCuriosidades.class, entityParamSpec -> entityParamSpec
        .useProviderStructuredOutput()
        .validateSchema());
```

Es la versión de `.entity()` que permite configurar **cómo se obtiene y se valida** la salida estructurada, con un lambda de opciones.

- **`useProviderStructuredOutput()`**: en lugar de solo agregar instrucciones de formato al texto del prompt (lo que hace `.entity()` por defecto), usa la **función nativa del proveedor** para forzar JSON con un esquema (por ejemplo, el modo de salida estructurada de OpenAI o el parámetro `format` de Ollama).
- **`validateSchema()`**: después de recibir la respuesta, la **valida contra el JSON Schema** generado desde tu clase. Si no cumple (faltan campos, tipos incorrectos), lanza una excepción en vez de devolverte datos rotos.

> Nota: estas opciones son relativamente nuevas y sus nombres exactos cambian entre versiones de Spring AI. Aquí explico lo que hacen según su nombre y el uso del curso; confirma el comportamiento y la disponibilidad en la documentación de tu versión y de tu proveedor. No todos los modelos soportan salida nativa.

### 1.2 Diferencia entre las dos formas de salida estructurada

| | Por prompt (por defecto) | Nativa del proveedor |
|---|---|---|
| Cómo funciona | Se pega el esquema en el prompt y se le "pide" al modelo | El proveedor restringe la generación al esquema |
| Fiabilidad | Depende de que el modelo obedezca | Mucho mayor |
| Soporte | Cualquier modelo | Solo proveedores y modelos que lo soportan |
| Configuración | Ninguna | `useProviderStructuredOutput()` |

### 1.3 Ejemplo

```java
@GetMapping("/validado")
public StarWarsCuriosidades validado() {
    return chatClient.prompt()
            .user("Genérame 5 curiosidades de star wars.")
            .call()
            .entity(StarWarsCuriosidades.class, spec -> spec
                    .useProviderStructuredOutput()
                    .validateSchema());
}
```

```java
public record StarWarsCuriosidades(List<Curiosidad> curiosidades) {}

public record Curiosidad(String titulo, String descripcion, String tituloPelicula) {}
```

### 1.4 Ventajas y desventajas

**Ventajas**
- Menos respuestas con JSON inválido, sobre todo con modelos pequeños.
- Fallas tempranas y claras si la respuesta no cumple el esquema.

**Desventajas**
- Si el modelo o proveedor no soporta salida nativa, puede fallar o ignorarse.
- La validación lanza excepciones: conviene manejarlas (reintento o mensaje de error).
- Sigue sin combinarse bien con `.stream()`.

---

## Parte 2: Advisors

### 2.1 Explicación

Un **Advisor** es un interceptor que se ejecuta **antes y después** de cada llamada al modelo hecha con `ChatClient`. Es la misma idea que un filtro de servlets o un aspecto de Spring AOP: no cambias tu controller, pero modificas el request, la respuesta o ambos.

```
Tu código → Advisor 1 → Advisor 2 → ChatModel → LLM
          ←           ←           ←
```

Sirven para añadir comportamiento transversal:

- **Logging** de prompts y respuestas.
- **Memoria de conversación** (reenviar el historial).
- **RAG** (agregar documentos relevantes al prompt).
- **Seguridad** (bloquear palabras o temas).

### 2.2 Advisors incluidos

| Advisor | Para qué sirve |
|---|---|
| `SimpleLoggerAdvisor` | Imprime request y response en el log |
| `MessageChatMemoryAdvisor` | Memoria: inserta el historial como mensajes |
| `PromptChatMemoryAdvisor` | Memoria: inserta el historial dentro del texto del system prompt |
| `QuestionAnswerAdvisor` | RAG simple con un `VectorStore` |
| `SafeGuardAdvisor` | Bloquea peticiones con palabras sensibles |

### 2.3 Ejemplo 1: logging

```java
this.chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(new SimpleLoggerAdvisor())
        .build();
```

```properties
logging.level.org.springframework.ai.chat.client.advisor=DEBUG
```

Sin el nivel `DEBUG` no verás nada en consola.

### 2.4 Ejemplo 2: memoria de conversación

```java
ChatMemory chatMemory = MessageWindowChatMemory.builder()
        .maxMessages(10)
        .build();

this.chatClient = ChatClient.builder(chatModel)
        .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
        .build();
```

```java
@GetMapping("/memoria")
public String memoria(@RequestParam String mensaje) {
    return chatClient.prompt()
            .user(mensaje)
            .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, "usuario-1"))
            .call()
            .content();
}
```

Pruébalo así: primero "Me llamo Carlos" y luego "¿Cómo me llamo?". Con el mismo `CONVERSATION_ID`, el modelo debería recordarlo.

### 2.5 Ejemplo 3: advisor propio

```java
public class TiempoAdvisor implements CallAdvisor {

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        long inicio = System.currentTimeMillis();
        ChatClientResponse response = chain.nextCall(request);   // continúa la cadena
        System.out.println("Tardó " + (System.currentTimeMillis() - inicio) + " ms");
        return response;
    }

    @Override
    public String getName() {
        return "TiempoAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }
}
```

```java
ChatClient.builder(chatModel)
        .defaultAdvisors(new TiempoAdvisor())
        .build();
```

> Las firmas de la interfaz pueden variar entre versiones de Spring AI; si no compila, revisa los métodos que te sugiere el IDE.

### 2.6 Orden de ejecución

`getOrder()` define el orden: **menor número = se ejecuta primero** en la ida y último en la vuelta. Importa, por ejemplo, para que el logger vea el prompt ya modificado por la memoria (o antes de modificarlo, según lo que quieras).

### 2.7 Dónde se registran

| Forma | Alcance |
|---|---|
| `.defaultAdvisors(...)` en el builder | Todas las llamadas de ese `ChatClient` |
| `.advisors(...)` en la petición | Solo esa llamada |

### 2.8 Ventajas y desventajas

**Ventajas**
- Reutilizables: escribes la lógica una vez y la aplicas a cualquier cliente.
- Tu controller queda limpio.
- Se pueden encadenar y ordenar.

**Desventajas**
- El orden importa y un orden incorrecto produce errores difíciles de ver.
- Cada advisor de memoria o RAG agrega tokens al prompt (más costo y más lentitud).
- Con modelos pequeños, un historial largo puede degradar la calidad de las respuestas.

---

## Resumen rápido

- `useProviderStructuredOutput()` = usar la salida estructurada nativa del proveedor, más fiable que pedir JSON por prompt.
- `validateSchema()` = comprobar que la respuesta cumple el esquema y fallar si no.
- Un Advisor es un interceptor del `ChatClient`: logging, memoria, RAG y seguridad sin tocar el controller.
- Se registran con `defaultAdvisors` (todo el cliente) o `.advisors(...)` (una sola petición), y `getOrder()` decide el orden.