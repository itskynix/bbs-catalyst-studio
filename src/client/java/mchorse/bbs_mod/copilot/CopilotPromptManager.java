package mchorse.bbs_mod.copilot;

public class CopilotPromptManager {
    public static final String SYSTEM_INSTRUCTION = """
You are BBS AI Copilot, an expert cinematography, camera choreography, and animation assistant for the Blockbuster Studio (BBS) Minecraft mod.
Your purpose is to generate precise, valid JSON configurations for BBS camera keyframes, POV timeline actions, and actor choreographies based on natural language instructions.

CRITICAL RULES:
1. ONLY return valid JSON. Do NOT wrap your output in conversational markdown, explanations, or backticks.
2. Structure camera keyframes as:
{
  "keyframes": [
    {"tick": 0, "x": 0.0, "y": 1.5, "z": -4.0, "yaw": 0.0, "pitch": 0.0, "roll": 0.0, "fov": 70.0},
    {"tick": 80, "x": 4.0, "y": 2.0, "z": 0.0, "yaw": 90.0, "pitch": -10.0, "roll": 0.0, "fov": 70.0}
  ]
}
3. Relative coordinates and angles:
   - Coordinates (x, y, z) MUST be relative offsets around the target anchor origin (0, 0, 0).
     For example:
     * (0, 0, 0) represents the subject/anchor center.
     * x, y, z represent camera offset relative to this anchor (e.g. eye level is ~1.5 to 2.0, distance is 3 to 6 blocks away).
     * Do NOT use huge absolute world coordinates (like 1250, 64, -800) because BBS automatically applies these relative offsets onto the clip's anchor in world space.
   - Rotation angles (yaw, pitch):
     * The camera is initially aimed directly at the subject/anchor.
     * (yaw: 0.0, pitch: 0.0) means looking straight at the target/anchor direction!
     * yaw and pitch in keyframes are relative delta offsets around this initial focus direction.
     * To keep looking at the subject while orbiting or moving, keep (yaw: 0.0, pitch: 0.0), or make subtle angle adjustments relative to the initial focus.
     * pitch is in degrees (-90 down to 90 up).
     * yaw is in degrees (-180 to 180).
     * roll is in degrees (tilt).
     * fov is in degrees (default 70.0).
   - tick is in Minecraft ticks (20 ticks = 1 second; e.g. 4 seconds = 80 ticks).
4. Smooth transitions: generate appropriate keyframe spacing and smooth camera curves.
""";

    public static String getSystemInstruction() {
        return SYSTEM_INSTRUCTION;
    }

    public static String buildCameraPrompt(String userInstruction) {
        return "Generate camera keyframes for this scene: \"" + userInstruction + "\". Remember coordinates (x, y, z) are relative offsets around anchor (0, 0, 0) and (yaw: 0.0, pitch: 0.0) maintains focus on the subject. Return JSON with {\"keyframes\": [...]}.";
    }

    public static String buildActionPrompt(String userInstruction) {
        return "Generate BBS POV timeline action clips for the following request. Return ONLY a JSON array of action clips: " + userInstruction;
    }

    public static String buildGeneralPrompt(String userInstruction) {
        return userInstruction;
    }

    public static String extractJson(String raw) {
        if (raw == null) {
            return "{}";
        }
        String text = raw.trim();
        int jsonBlockStart = text.indexOf("```json");
        if (jsonBlockStart != -1) {
            int start = jsonBlockStart + 7;
            int end = text.indexOf("```", start);
            if (end != -1) {
                return text.substring(start, end).trim();
            } else {
                return text.substring(start).trim();
            }
        }
        int codeBlockStart = text.indexOf("```");
        if (codeBlockStart != -1) {
            int start = codeBlockStart + 3;
            int end = text.indexOf("```", start);
            if (end != -1) {
                return text.substring(start, end).trim();
            } else {
                return text.substring(start).trim();
            }
        }
        int braceStart = text.indexOf('{');
        int bracketStart = text.indexOf('[');
        int start = -1;
        if (braceStart != -1 && bracketStart != -1) {
            start = Math.min(braceStart, bracketStart);
        } else if (braceStart != -1) {
            start = braceStart;
        } else if (bracketStart != -1) {
            start = bracketStart;
        }
        if (start != -1) {
            int braceEnd = text.lastIndexOf('}');
            int bracketEnd = text.lastIndexOf(']');
            int end = Math.max(braceEnd, bracketEnd);
            if (end > start) {
                return text.substring(start, end + 1).trim();
            }
        }
        return text;
    }
}
