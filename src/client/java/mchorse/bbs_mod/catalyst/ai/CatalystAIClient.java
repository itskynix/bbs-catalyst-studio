package mchorse.bbs_mod.catalyst.ai;

import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ByteType;
import mchorse.bbs_mod.data.types.DoubleType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import mchorse.bbs_mod.data.types.StringType;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Talks to the hosted models. Every request goes out on {@link HttpClient#sendAsync}, so the game
 * never waits on the network: the caller gets a future and checks on it from the render loop.
 *
 * <p>The four wire formats differ only in where the system prompt, the turns and the answer live;
 * everything else (what the model is told, what it has to answer with) is the same for all of them.</p>
 */
public class CatalystAIClient
{
    public record Turn(boolean user, String text)
    {}

    /** A failed request, with the message to show in red. */
    public static class CatalystAIException extends RuntimeException
    {
        public CatalystAIException(String message)
        {
            super(message);
        }
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build();

    private static final Duration TIMEOUT = Duration.ofSeconds(60);

    public static CompletableFuture<String> send(CatalystAIProvider provider, CatalystAIConfig config, String system, List<Turn> turns)
    {
        String key = config.getKey(provider);
        String model = config.getModel(provider);
        String url = config.getUrl(provider);
        HttpRequest request;

        try
        {
            request = switch (provider)
            {
                case GEMINI -> gemini(url, model, key, system, turns);
                case ANTHROPIC -> anthropic(url, model, key, system, turns);
                case OPENAI, CUSTOM -> openAI(url, model, key, system, turns, provider == CatalystAIProvider.OPENAI);
                default -> throw new CatalystAIException("offline");
            };
        }
        catch (IllegalArgumentException e)
        {
            return CompletableFuture.failedFuture(new CatalystAIException("URL: " + e.getMessage()));
        }

        return HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
            .thenApply((response) -> read(provider, response.statusCode(), response.body()));
    }

    /* Requests */

    private static HttpRequest gemini(String url, String model, String key, String system, List<Turn> turns)
    {
        MapType body = new MapType();
        ListType contents = new ListType();

        for (Turn turn : turns)
        {
            MapType content = new MapType();

            content.putString("role", turn.user() ? "user" : "model");
            content.put("parts", textParts(turn.text()));
            contents.add(content);
        }

        MapType instruction = new MapType();
        MapType generation = new MapType();

        instruction.put("parts", textParts(system));
        generation.putDouble("temperature", 0.6D);
        generation.putString("responseMimeType", "application/json");

        body.put("systemInstruction", instruction);
        body.put("contents", contents);
        body.put("generationConfig", generation);

        String address = url + "/models/" + URLEncoder.encode(model, StandardCharsets.UTF_8) + ":generateContent?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8);

        return post(address, body).build();
    }

    private static HttpRequest openAI(String url, String model, String key, String system, List<Turn> turns, boolean jsonMode)
    {
        MapType body = new MapType();
        ListType messages = new ListType();

        messages.add(message("system", system));

        for (Turn turn : turns)
        {
            messages.add(message(turn.user() ? "user" : "assistant", turn.text()));
        }

        body.putString("model", model);
        body.put("messages", messages);

        /* Only where it is known to be understood: an OpenAI compatible server may refuse a field it does not
         * know, and OpenAI's own reasoning models (GPT-6) refuse a temperature, so it is left at theirs. */
        if (!jsonMode)
        {
            body.putDouble("temperature", 0.6D);
        }

        if (jsonMode)
        {
            MapType format = new MapType();

            format.putString("type", "json_object");
            body.put("response_format", format);
        }

        return post(url + "/chat/completions", body)
            .header("Authorization", "Bearer " + key)
            .build();
    }

    private static HttpRequest anthropic(String url, String model, String key, String system, List<Turn> turns)
    {
        MapType body = new MapType();
        ListType messages = new ListType();

        for (Turn turn : turns)
        {
            messages.add(message(turn.user() ? "user" : "assistant", turn.text()));
        }

        body.putString("model", model);
        body.putInt("max_tokens", 1024);
        body.putString("system", system);
        body.put("messages", messages);

        return post(url + "/messages", body)
            .header("x-api-key", key)
            .header("anthropic-version", "2023-06-01")
            .build();
    }

    private static HttpRequest.Builder post(String address, MapType body)
    {
        return HttpRequest.newBuilder(URI.create(address))
            .timeout(TIMEOUT)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8));
    }

    private static ListType textParts(String text)
    {
        ListType parts = new ListType();
        MapType part = new MapType();

        part.putString("text", text);
        parts.add(part);

        return parts;
    }

    private static MapType message(String role, String content)
    {
        MapType message = new MapType();

        message.putString("role", role);
        message.putString("content", content);

        return message;
    }

    /* Responses */

    private static String read(CatalystAIProvider provider, int status, String body)
    {
        MapType data = Json.parseMap(body);

        if (status < 200 || status >= 300)
        {
            String message = null;

            if (data != null && data.has("error"))
            {
                BaseType error = data.get("error");

                message = error.isMap() ? error.asMap().getString("message", null) : String.valueOf(error);
            }

            throw new CatalystAIException("HTTP " + status + (message == null || message.isBlank() ? "" : ": " + message));
        }

        if (data == null)
        {
            throw new CatalystAIException("?");
        }

        String text = switch (provider)
        {
            case GEMINI -> {
                MapType candidate = data.getList("candidates").getMap(0, null);
                MapType content = candidate == null ? null : candidate.getMap("content", null);

                yield content == null ? null : joinText(content.getList("parts"));
            }
            case ANTHROPIC -> joinText(data.getList("content"));
            default -> {
                MapType choice = data.getList("choices").getMap(0, null);
                MapType message = choice == null ? null : choice.getMap("message", null);

                yield message == null ? null : message.getString("content", null);
            }
        };

        if (text == null || text.isBlank())
        {
            throw new CatalystAIException("?");
        }

        return text;
    }

    private static String joinText(ListType parts)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < parts.size(); i++)
        {
            MapType part = parts.getMap(i, null);

            if (part != null && part.has("text"))
            {
                builder.append(part.getString("text"));
            }
        }

        return builder.toString();
    }

    /**
     * What the model's answer says: the chat reply and the commands to run. Models are asked for
     * a JSON object, but some wrap it in prose or a code fence, so the outermost braces are found
     * first; an answer without any is a reply with no commands.
     */
    public record Answer(String reply, List<String> commands)
    {}

    public static Answer parseAnswer(String text)
    {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        MapType data = start >= 0 && end > start ? Json.parseMap(text.substring(start, end + 1)) : null;

        if (data == null)
        {
            return new Answer(text.trim(), List.of());
        }

        List<String> commands = new ArrayList<>();
        ListType list = data.getList("commands");

        for (int i = 0; i < list.size(); i++)
        {
            String command = list.getString(i, "");

            if (!command.isBlank())
            {
                commands.add(command.trim());
            }
        }

        String single = data.getString("command", "");

        if (!single.isBlank() && !commands.contains(single.trim()))
        {
            commands.add(0, single.trim());
        }

        return new Answer(data.getString("reply", ""), commands);
    }

    /**
     * Strict JSON out of BBS data: the data stringifier is close to JSON, but leaves tabs and other
     * control characters raw, which a JSON parser on the other end refuses.
     */
    static class Json
    {
        static String write(BaseType data)
        {
            StringBuilder builder = new StringBuilder();

            write(builder, data);

            return builder.toString();
        }

        private static void write(StringBuilder builder, BaseType data)
        {
            if (data == null)
            {
                builder.append("null");
            }
            else if (data.isMap())
            {
                MapType map = data.asMap();
                boolean first = true;

                builder.append('{');

                for (String key : map.keys())
                {
                    if (!first)
                    {
                        builder.append(',');
                    }

                    string(builder, key);
                    builder.append(':');
                    write(builder, map.get(key));
                    first = false;
                }

                builder.append('}');
            }
            else if (data.isList())
            {
                ListType list = data.asList();

                builder.append('[');

                for (int i = 0; i < list.size(); i++)
                {
                    if (i > 0)
                    {
                        builder.append(',');
                    }

                    write(builder, list.get(i));
                }

                builder.append(']');
            }
            else if (data.isString())
            {
                string(builder, data.asString());
            }
            else if (data.isNumeric())
            {
                builder.append(DataToString.toString(data, true));
            }
            else
            {
                builder.append(DataToString.toString(data, true));
            }
        }

        /**
         * Strict JSON in, BBS data out. The provider answers carry what BBS's own parser was never
         * meant for (exponents, {@code \t}, {@code \/}, escaped backslashes before quotes), and the
         * model's text is inside them, so it gets a reader that follows the JSON grammar exactly.
         * Null when the text is not JSON.
         */
        static BaseType parse(String text)
        {
            try
            {
                Reader reader = new Reader(text);
                BaseType value = reader.value();

                reader.skip();

                return reader.index == text.length() ? value : null;
            }
            catch (RuntimeException e)
            {
                return null;
            }
        }

        static MapType parseMap(String text)
        {
            BaseType value = text == null ? null : parse(text);

            return value != null && value.isMap() ? value.asMap() : null;
        }

        private static class Reader
        {
            private final String text;
            private int index;

            Reader(String text)
            {
                this.text = text;
            }

            void skip()
            {
                while (this.index < this.text.length() && Character.isWhitespace(this.text.charAt(this.index)))
                {
                    this.index += 1;
                }
            }

            char peek()
            {
                this.skip();

                if (this.index >= this.text.length())
                {
                    throw new IllegalStateException("end");
                }

                return this.text.charAt(this.index);
            }

            void expect(char c)
            {
                if (this.peek() != c)
                {
                    throw new IllegalStateException("expected " + c);
                }

                this.index += 1;
            }

            BaseType value()
            {
                char c = this.peek();

                if (c == '{')
                {
                    MapType map = new MapType();

                    this.index += 1;

                    if (this.peek() == '}')
                    {
                        this.index += 1;

                        return map;
                    }

                    while (true)
                    {
                        String key = this.string();

                        this.expect(':');
                        map.put(key, this.value());

                        if (this.peek() == ',')
                        {
                            this.index += 1;
                        }
                        else
                        {
                            this.expect('}');

                            return map;
                        }
                    }
                }
                else if (c == '[')
                {
                    ListType list = new ListType();

                    this.index += 1;

                    if (this.peek() == ']')
                    {
                        this.index += 1;

                        return list;
                    }

                    while (true)
                    {
                        list.add(this.value());

                        if (this.peek() == ',')
                        {
                            this.index += 1;
                        }
                        else
                        {
                            this.expect(']');

                            return list;
                        }
                    }
                }
                else if (c == '"')
                {
                    return new StringType(this.string());
                }
                else if (this.text.startsWith("true", this.index))
                {
                    this.index += 4;

                    return new ByteType(true);
                }
                else if (this.text.startsWith("false", this.index))
                {
                    this.index += 5;

                    return new ByteType(false);
                }
                else if (this.text.startsWith("null", this.index))
                {
                    this.index += 4;

                    /* Nothing in an answer that matters is ever null; an empty string reads as "not there" */
                    return new StringType("");
                }

                int start = this.index;

                while (this.index < this.text.length() && "+-0123456789.eE".indexOf(this.text.charAt(this.index)) >= 0)
                {
                    this.index += 1;
                }

                return new DoubleType(Double.parseDouble(this.text.substring(start, this.index)));
            }

            String string()
            {
                this.expect('"');

                StringBuilder builder = new StringBuilder();

                while (true)
                {
                    char c = this.text.charAt(this.index++);

                    if (c == '"')
                    {
                        return builder.toString();
                    }

                    if (c != '\\')
                    {
                        builder.append(c);

                        continue;
                    }

                    char e = this.text.charAt(this.index++);

                    switch (e)
                    {
                        case 'n' -> builder.append('\n');
                        case 't' -> builder.append('\t');
                        case 'r' -> builder.append('\r');
                        case 'b' -> builder.append('\b');
                        case 'f' -> builder.append('\f');
                        case 'u' -> {
                            builder.append((char) Integer.parseInt(this.text.substring(this.index, this.index + 4), 16));
                            this.index += 4;
                        }
                        default -> builder.append(e);
                    }
                }
            }
        }

        private static void string(StringBuilder builder, String string)
        {
            builder.append('"');

            for (int i = 0; i < string.length(); i++)
            {
                char c = string.charAt(i);

                switch (c)
                {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    case '\n' -> builder.append("\\n");
                    case '\r' -> builder.append("\\r");
                    case '\t' -> builder.append("\\t");
                    default -> {
                        if (c < 0x20)
                        {
                            builder.append(String.format("\\u%04x", (int) c));
                        }
                        else
                        {
                            builder.append(c);
                        }
                    }
                }
            }

            builder.append('"');
        }
    }
}
