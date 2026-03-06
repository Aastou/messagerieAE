package sn.messagerieae.protocol;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import lombok.Getter;
import lombok.Setter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Setter
@Getter
public class ProtocolMessage {

    // ===== Types de commandes =====
    public enum Command {
        LOGIN,
        REGISTER,
        SEND_MESSAGE,
        GET_HISTORY,
        GET_ONLINE_USERS,
        GET_ALL_MEMBERS,
        LOGOUT,

        // Réponses serveur → client
        SUCCESS,
        ERROR,
        INCOMING_MESSAGE,
        USER_LIST,
        MESSAGE_HISTORY,
        USER_CONNECTED,
        USER_DISCONNECTED
    }

    // ===== Getters / Setters =====
    private Command command;
    private String sender;
    private String receiver;
    private String content;
    private String role;
    private String extra; // données supplémentaires (JSON sérialisé pour les listes, etc.)

    // Gson avec support LocalDateTime
    private static final Gson GSON = new GsonBuilder()
        .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
        .create();

    public ProtocolMessage() {}

    public ProtocolMessage(Command command, String content) {
        this.command = command;
        this.content = content;
    }

    // ===== Sérialisation / Désérialisation =====
    public String toJson() {
        return GSON.toJson(this);
    }

    public static ProtocolMessage fromJson(String json) {
        return GSON.fromJson(json, ProtocolMessage.class);
    }

    // ===== Factory methods pour les réponses courantes =====
    public static ProtocolMessage success(String content) {
        return new ProtocolMessage(Command.SUCCESS, content);
    }

    public static ProtocolMessage error(String content) {
        return new ProtocolMessage(Command.ERROR, content);
    }

    public Gson getGson() { return GSON; }

    // ===== Adapter pour LocalDateTime =====
    private static class LocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public void write(JsonWriter out, LocalDateTime value) throws IOException {
            out.value(value != null ? value.format(FORMATTER) : null);
        }

        @Override
        public LocalDateTime read(JsonReader in) throws IOException {
            String str = in.nextString();
            return str != null ? LocalDateTime.parse(str, FORMATTER) : null;
        }
    }
}
