package org.kurento.tutorial.one2manycall;

import java.util.concurrent.ConcurrentHashMap;
import org.kurento.client.MediaPipeline;
import org.springframework.web.socket.WebSocketSession;

public class PipelineRegistry {

    private ConcurrentHashMap<String, MediaPipeline> usersByName = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, MediaPipeline> usersBySessionId = new ConcurrentHashMap<>();

    public void register(MediaPipeline pipeline, WebSocketSession session, String name) {
        usersByName.put(name, pipeline);
        usersBySessionId.put(session.getId(), pipeline);
    }

    public MediaPipeline getByName(String name) {
        return usersByName.get(name);
    }

    public MediaPipeline getBySession(WebSocketSession session) {
        return usersBySessionId.get(session.getId());
    }

    public boolean exists(String name) {
        return usersByName.keySet().contains(name);
    }

    public MediaPipeline removeBySession(WebSocketSession session) {
        final MediaPipeline pipeline = getBySession(session);
        if (pipeline != null) {
            usersByName.remove(pipeline.getName());
            usersBySessionId.remove(session.getId());
        }
        return pipeline;
    }

}
