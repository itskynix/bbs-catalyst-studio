package mchorse.bbs_mod.copilot.chat;

import mchorse.bbs_mod.data.types.MapType;

public class CopilotChatMessage {
    public String role; // "user" or "model"
    public String text;
    public String imageBase64; // nullable

    public CopilotChatMessage(String role, String text) {
        this(role, text, null);
    }

    public CopilotChatMessage(String role, String text, String imageBase64) {
        this.role = role != null ? role : "user";
        this.text = text != null ? text : "";
        this.imageBase64 = imageBase64;
    }

    public boolean isUser() {
        return "user".equalsIgnoreCase(this.role);
    }

    public boolean hasImage() {
        return this.imageBase64 != null && !this.imageBase64.isEmpty();
    }

    public boolean containsKeyframes() {
        if (this.text == null || this.isUser()) {
            return false;
        }
        return this.text.contains("\"keyframes\"") || (this.text.contains("\"tick\"") && this.text.contains("\"x\"") && this.text.contains("\"y\""));
    }

    public MapType toData() {
        MapType map = new MapType();
        map.putString("role", this.role);
        map.putString("text", this.text);
        if (this.hasImage()) {
            map.putString("imageBase64", this.imageBase64);
        }
        return map;
    }

    public static CopilotChatMessage fromData(MapType map) {
        String role = map.getString("role");
        String text = map.getString("text");
        String imageBase64 = map.has("imageBase64") ? map.getString("imageBase64") : null;
        return new CopilotChatMessage(role, text, imageBase64);
    }
}
