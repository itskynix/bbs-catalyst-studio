package mchorse.bbs_mod.copilot.chat;

import mchorse.bbs_mod.data.DataParser;
import mchorse.bbs_mod.data.DataToString;
import mchorse.bbs_mod.data.types.BaseType;
import mchorse.bbs_mod.data.types.ListType;
import mchorse.bbs_mod.data.types.MapType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class CopilotChatManager {
    private static final CopilotChatManager INSTANCE = new CopilotChatManager();

    private final List<CopilotChatSession> sessions = new ArrayList<>();
    private CopilotChatSession currentSession;

    public static CopilotChatManager getInstance() {
        return INSTANCE;
    }

    private CopilotChatManager() {
        this.load();
    }

    public static File getStorageFile() {
        File runDir;
        try {
            runDir = FabricLoader.getInstance().getGameDir().toFile();
        } catch (Throwable t) {
            runDir = MinecraftClient.getInstance() != null ? MinecraftClient.getInstance().runDirectory : new File(".");
        }
        File copilotDir = new File(new File(runDir != null ? runDir : new File("."), "bbs"), "copilot");
        if (!copilotDir.exists()) {
            copilotDir.mkdirs();
        }
        return new File(copilotDir, "chats.json");
    }

    public synchronized void load() {
        this.sessions.clear();
        File file = getStorageFile();
        if (!file.exists()) {
            this.createSession("Yeni Sohbet");
            return;
        }

        try {
            String json = Files.readString(file.toPath(), StandardCharsets.UTF_8);
            BaseType data = DataParser.parse(json);
            if (data instanceof ListType list) {
                for (int i = 0; i < list.size(); i++) {
                    BaseType item = list.get(i);
                    if (item instanceof MapType map) {
                        this.sessions.add(CopilotChatSession.fromData(map));
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[BBS Copilot] Failed to load chats.json: " + e.getMessage());
        }

        if (this.sessions.isEmpty()) {
            this.createSession("Yeni Sohbet");
        } else {
            this.currentSession = this.sessions.get(0);
        }
    }

    public synchronized void save() {
        File file = getStorageFile();
        try {
            ListType list = new ListType();
            for (CopilotChatSession session : this.sessions) {
                list.add(session.toData());
            }
            String json = DataToString.toString(list, true);
            Files.writeString(file.toPath(), json, StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("[BBS Copilot] Failed to save chats.json: " + e.getMessage());
        }
    }

    public List<CopilotChatSession> getSessions() {
        return this.sessions;
    }

    public CopilotChatSession getCurrentSession() {
        if (this.currentSession == null && !this.sessions.isEmpty()) {
            this.currentSession = this.sessions.get(0);
        }
        return this.currentSession;
    }

    public void setCurrentSession(CopilotChatSession session) {
        this.currentSession = session;
    }

    public CopilotChatSession createSession(String title) {
        CopilotChatSession session = new CopilotChatSession(title);
        this.sessions.add(0, session);
        this.currentSession = session;
        this.save();
        return session;
    }

    public void deleteSession(String id) {
        if (id == null) return;
        this.sessions.removeIf(s -> id.equals(s.id));
        if (this.currentSession != null && id.equals(this.currentSession.id)) {
            this.currentSession = this.sessions.isEmpty() ? null : this.sessions.get(0);
        }
        if (this.sessions.isEmpty()) {
            this.createSession("Yeni Sohbet");
        } else {
            this.save();
        }
    }

    public void renameSession(String id, String newTitle) {
        for (CopilotChatSession session : this.sessions) {
            if (session.id.equals(id)) {
                session.title = newTitle;
                this.save();
                break;
            }
        }
    }

    public void clearAllSessions() {
        this.sessions.clear();
        this.createSession("Yeni Sohbet");
        this.save();
    }
}
