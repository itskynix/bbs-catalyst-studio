package mchorse.bbs_mod.copilot.chat;

import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CopilotChatSession {
    public String id;
    public String title;
    public long timestamp;
    public final List<CopilotChatMessage> messages = new ArrayList<>();

    public CopilotChatSession() {
        this("Yeni Sohbet");
    }

    public CopilotChatSession(String title) {
        this.id = UUID.randomUUID().toString();
        this.title = title != null && !title.isBlank() ? title : "Yeni Sohbet";
        this.timestamp = System.currentTimeMillis();
    }

    public void addMessage(CopilotChatMessage message) {
        if (message != null) {
            this.messages.add(message);
        }
    }

    public MapType toData() {
        MapType map = new MapType();
        map.putString("id", this.id);
        map.putString("title", this.title);
        map.putLong("timestamp", this.timestamp);

        ListType list = new ListType();
        for (CopilotChatMessage msg : this.messages) {
            list.add(msg.toData());
        }
        map.put("messages", list);
        return map;
    }

    public static CopilotChatSession fromData(MapType map) {
        CopilotChatSession session = new CopilotChatSession();
        if (map.has("id")) session.id = map.getString("id");
        if (map.has("title")) session.title = map.getString("title");
        if (map.has("timestamp")) session.timestamp = map.getLong("timestamp");

        if (map.has("messages")) {
            ListType list = map.getList("messages");
            for (int i = 0; i < list.size(); i++) {
                BaseType item = list.get(i);
                if (item instanceof MapType msgMap) {
                    session.messages.add(CopilotChatMessage.fromData(msgMap));
                }
            }
        }
        return session;
    }
}
