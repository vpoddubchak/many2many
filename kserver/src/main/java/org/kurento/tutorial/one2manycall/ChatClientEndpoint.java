package org.kurento.tutorial.one2manycall;

import java.net.URI;
import javax.net.ssl.SSLContext;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import javax.websocket.ClientEndpointConfig;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

@ClientEndpoint
public class ChatClientEndpoint{
    private Session userSession = null;
    private MessageHandler messageHandler;
    private boolean _ready = false;
    private boolean _valid = false;
    private static final Gson gson = new GsonBuilder().create();

    public ChatClientEndpoint(final URI endpointURI) {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @OnOpen
    public void onOpen(final Session userSession) {
        this.userSession = userSession;
    }

    @OnClose
    public void onClose(final Session userSession, final CloseReason reason) {
        this.userSession = null;
    }

    @OnMessage
    public void onMessage(final String message) {
        if (messageHandler != null) {
            messageHandler.handleMessage(message);
        }
        try {
            JsonObject jsonMessage = gson.fromJson(message, JsonObject.class);
            String result = jsonMessage.getAsJsonPrimitive("checkresp").getAsString();
            if (result.compareTo("valid") == 0) {
                _valid = true;
            }
            _ready = true;
        }catch(Exception e){

        }
    }

    public void addMessageHandler(final MessageHandler msgHandler) {
        messageHandler = msgHandler;
    }

    public void sendMessage(final String message) {
        userSession.getAsyncRemote().sendText(message);
    }

    public boolean isReady() {
        return _ready;
    }
    public boolean isValid() {
        return _valid;
    }

    public static interface MessageHandler {
        public void handleMessage(String message);
    }
}
