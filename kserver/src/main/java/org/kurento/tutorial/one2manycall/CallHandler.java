/*
 * (C) Copyright 2014 Kurento (http://kurento.org/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.kurento.tutorial.one2manycall;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.io.IOException;
import java.util.Enumeration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.ArrayList;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.IceCandidateFoundEvent;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.StoppedEvent;
import org.kurento.client.Composite;
import org.kurento.client.HubPort;
import org.kurento.jsonrpc.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

/*import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.sockjs.client.RestTemplateXhrTransport;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.util.concurrent.ListenableFuture;*/
import java.net.*;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;

/**
 * Protocol handler for 1 to N video call communication.
 *
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class CallHandler extends TextWebSocketHandler {

  private static final Logger log = LoggerFactory.getLogger(CallHandler.class);
  private static final Gson gson = new GsonBuilder().create();

  private final ConcurrentHashMap<String, ConcurrentHashMap<String, UserSession>> viewers = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, MediaPipeline> pipelines = new ConcurrentHashMap<>();

  @Autowired
  private KurentoClient kurento;

  @Autowired
  private UserRegistry registry;

  @Autowired
  private PipelineRegistry pipregistry;

  //private MediaPipeline pipeline;
  //private UserSession presenterUserSession;
  //private UserSession presenterUserSession2;

  private Composite _composite;
  private HubPort _hubport1;
  private HubPort _hubport2;
  private HubPort _hubport3;

  private static SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-S");

  public static String RECORDING_EXT = ".webm";

  @Override
  public void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
    JsonObject jsonMessage = gson.fromJson(message.getPayload(), JsonObject.class);
    log.debug("Incoming message from session '{}': {}", session.getId(), jsonMessage);

    switch (jsonMessage.get("id").getAsString()) {
      case "presenter":
        try {
          presenter(session, jsonMessage);
        } catch (Throwable t) {
          handleErrorResponse(t, session, "presenterResponse");
        }
        break;
      case "viewer":
        try {
          viewer(session, jsonMessage);
        } catch (Throwable t) {
          handleErrorResponse(t, session, "viewerResponse");
        }
        break;
      case "onIceCandidate": {
        JsonObject candidate = jsonMessage.get("candidate").getAsJsonObject();
        UserSession user = registry.getBySession(session);
        if (user != null) {
          IceCandidate cand =
              new IceCandidate(candidate.get("candidate").getAsString(), candidate.get("sdpMid")
                  .getAsString(), candidate.get("sdpMLineIndex").getAsInt());
          user.addCandidate(cand);
        }
        break;
      }
      case "stop":
        stop(session);
        break;
      default:
        break;
    }
  }

  private void handleErrorResponse(Throwable throwable, WebSocketSession session, String responseId)
      throws IOException {
    stop(session);
    log.error(throwable.getMessage(), throwable);
    JsonObject response = new JsonObject();
    response.addProperty("id", responseId);
    response.addProperty("response", "rejected");
    response.addProperty("message", throwable.getMessage());
    session.sendMessage(new TextMessage(response.toString()));
  }



  private void addRecordModule(JsonObject jsonMessage, WebRtcEndpoint presenterWebRtc,
                                           MediaPipeline pipeline, UserSession userSession){

    String RECORDING_PATH = "file:///tmp/" + df.format(new Date()) + "-";
    String outPath = RECORDING_PATH;

    if (jsonMessage.has("user_id") && jsonMessage.has("match_id")) {
      String user_id = jsonMessage.getAsJsonPrimitive("user_id").getAsString();
      String match_id = jsonMessage.getAsJsonPrimitive("match_id").getAsString();
      String delim = "_";
      outPath += user_id;
      outPath += delim;
      outPath += match_id;
    }

    outPath += RECORDING_EXT;
    RecorderEndpoint recorderCaller = new RecorderEndpoint.Builder(pipeline, outPath).build();
    presenterWebRtc.connect(recorderCaller);
    recorderCaller.record();
    userSession.setRecorderEndpoint(recorderCaller);
    userSession.setFilePath(outPath);
  }

  private boolean CheckToken(String tokenToCheck){
    boolean bResult = false;
    try {
      URI uri = new URI("ws://ec2-52-213-7-108.eu-west-1.compute.amazonaws.com:3000");
      ChatClientEndpoint client = new ChatClientEndpoint(uri);

      String sMessage = "{\"type\":\"tokencheck\", \"token\":\"" + tokenToCheck + "\"}";
      client.sendMessage(sMessage);

      for (int i = 0; i < 5; i++){
        Thread.sleep(100);
        if (client.isReady()){
          break;
        }
      }

      bResult = client.isValid();

    } catch (URISyntaxException e) {
      e.printStackTrace();
    } catch (InterruptedException e) {
      e.printStackTrace();
    }

    return bResult;
  }

  private synchronized void presenter(final WebSocketSession session, JsonObject jsonMessage)
      throws IOException {

    String name = jsonMessage.getAsJsonPrimitive("match_id").getAsString();

    UserSession presenterUserSession = new UserSession(session, name);

    //Check token here
    String token = jsonMessage.getAsJsonPrimitive("token").getAsString();
    if (CheckToken(token) != true){
      JsonObject resp = new JsonObject();
      resp.addProperty("id", "resgisterResponse");
      resp.addProperty("response", "Invalid token");
      presenterUserSession.sendMessage(resp);
      return;
    }

    String responseMsg = "accepted";
    if (name.isEmpty() || registry.exists(name)) {
      responseMsg = "rejected: user '" + name + "' already registered";
      JsonObject resp = new JsonObject();
      resp.addProperty("id", "resgisterResponse");
      resp.addProperty("response", responseMsg);
      presenterUserSession.sendMessage(resp);
    } else {


      MediaPipeline pipeline = kurento.createMediaPipeline();
      pipregistry.register(pipeline, session, name);

      presenterUserSession.setWebRtcEndpoint(new WebRtcEndpoint.Builder(pipeline).build());

      WebRtcEndpoint presenterWebRtc = presenterUserSession.getWebRtcEndpoint();

      presenterWebRtc.setMaxVideoRecvBandwidth(3000);
      presenterWebRtc.setMinVideoRecvBandwidth(1000);

      /*_composite = new Composite.Builder(pipeline).build();
      _hubport1 = new HubPort.Builder(_composite).build();
      _hubport2 = new HubPort.Builder(_composite).build();
      _hubport3 = new HubPort.Builder(_composite).build();

      presenterWebRtc.connect(_hubport1);*/
      //presenterWebRtc.connect(_hubport2);

      addRecordModule(jsonMessage, presenterWebRtc, pipeline, presenterUserSession);

      registry.register(presenterUserSession);
      presenterWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.debug(e.getMessage());
          }
        }
      });

      String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
      String sdpAnswer = presenterWebRtc.processOffer(sdpOffer);

      JsonObject response = new JsonObject();
      response.addProperty("id", "presenterResponse");
      response.addProperty("response", "accepted");
      response.addProperty("sdpAnswer", sdpAnswer);

      synchronized (session) {
        presenterUserSession.sendMessage(response);
      }
      presenterWebRtc.gatherCandidates();
    }

  }

  private synchronized void viewer(final WebSocketSession session, JsonObject jsonMessage)
      throws IOException {
    String name = jsonMessage.getAsJsonPrimitive("match_id").getAsString();
    UserSession presenterUserSession = registry.getByName(name);

    if (presenterUserSession == null) {
      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "rejected");
      response.addProperty("message",
          "No active sender with this name now. Become sender or . Try again later ...");
      session.sendMessage(new TextMessage(response.toString()));
    } else {
      if (viewers.containsKey(session.getId()) && viewers.get(session.getId()).containsKey(session.getId())) {
        JsonObject response = new JsonObject();
        response.addProperty("id", "viewerResponse");
        response.addProperty("response", "rejected");
        response.addProperty("message", "You are already viewing in this session. "
            + "Use a different browser to add additional viewers.");
        session.sendMessage(new TextMessage(response.toString()));
        return;
      }
      String defaultName = "viewer";
      String viewerName = defaultName;
      for ( int i = 0; registry.exists(viewerName); i++){
        viewerName = defaultName + i;
      }

      UserSession viewer = new UserSession(session, viewerName);
      registry.register(viewer);
      if (viewers.containsKey(presenterUserSession.getSession().getId())){
        viewers.get(presenterUserSession.getSession().getId()).put(session.getId(), viewer);
      }
      else{
        ConcurrentHashMap<String, UserSession> vHM = new ConcurrentHashMap<String, UserSession>();
        vHM.put(session.getId(), viewer);
        viewers.put(presenterUserSession.getSession().getId(), vHM);
      }
      //viewers.put(session.getId(), viewer);

      MediaPipeline pipeline = pipregistry.getByName(name);
      WebRtcEndpoint nextWebRtc = new WebRtcEndpoint.Builder(pipeline).build();

      nextWebRtc.setMaxVideoSendBandwidth(4000);
      nextWebRtc.setMinVideoSendBandwidth(1000);

      nextWebRtc.addIceCandidateFoundListener(new EventListener<IceCandidateFoundEvent>() {

        @Override
        public void onEvent(IceCandidateFoundEvent event) {
          JsonObject response = new JsonObject();
          response.addProperty("id", "iceCandidate");
          response.add("candidate", JsonUtils.toJsonObject(event.getCandidate()));
          try {
            synchronized (session) {
              session.sendMessage(new TextMessage(response.toString()));
            }
          } catch (IOException e) {
            log.debug(e.getMessage());
          }
        }
      });

      viewer.setWebRtcEndpoint(nextWebRtc);
      presenterUserSession.getWebRtcEndpoint().connect(nextWebRtc);
      //_hubport3.connect(nextWebRtc);
      String sdpOffer = jsonMessage.getAsJsonPrimitive("sdpOffer").getAsString();
      String sdpAnswer = nextWebRtc.processOffer(sdpOffer);

      JsonObject response = new JsonObject();
      response.addProperty("id", "viewerResponse");
      response.addProperty("response", "accepted");
      response.addProperty("sdpAnswer", sdpAnswer);

      synchronized (session) {
        viewer.sendMessage(response);
      }
      nextWebRtc.gatherCandidates();
    }
  }

  private synchronized void stop(WebSocketSession session) throws IOException {
    String sessionId = session.getId();
    UserSession presenterUserSession = registry.getBySession(session);
    if (presenterUserSession != null && presenterUserSession.getSession().getId().equals(sessionId)) {
      //Stop recording
      presenterUserSession.stop();

      if (viewers.containsKey(sessionId)){
        // It is presenter. lets stop all his viewers
        ConcurrentHashMap<String, UserSession> views = viewers.get(sessionId);
        for (UserSession viewer : views.values()) {
          JsonObject response = new JsonObject();
          response.addProperty("id", "stopCommunication");
          viewer.sendMessage(response);
        }
       } else{
        // It is viewer. Just remove ourself from list
        for (Enumeration<ConcurrentHashMap<String, UserSession>> e = viewers.elements(); e.hasMoreElements();) {
          ConcurrentHashMap<String, UserSession> views = e.nextElement();
          if (views.containsKey(sessionId)) {
            views.remove(sessionId);
            break;
          }
        }
      }

      log.info("Releasing media pipeline");
      MediaPipeline pipeline = pipregistry.getBySession(session);
      if (pipeline != null) {
        pipregistry.removeBySession(session);
        pipeline.release();
      }
      registry.removeBySession(session);
      //pipeline = null;
      //presenterUserSession = null;
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
    stop(session);
  }

}
