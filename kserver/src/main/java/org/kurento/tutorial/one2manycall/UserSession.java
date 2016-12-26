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

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.kurento.client.EventListener;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.kurento.client.RecorderEndpoint;
import org.kurento.client.StoppedEvent;
import org.kurento.client.ListenerSubscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.google.gson.JsonObject;

/**
 * User session.
 * 
 * @author Boni Garcia (bgarcia@gsyc.es)
 * @since 5.0.0
 */
public class UserSession {

  private static final Logger log = LoggerFactory.getLogger(UserSession.class);

  private final WebSocketSession session;
  private WebRtcEndpoint webRtcEndpoint;
  private RecorderEndpoint recorderEndpoint;
  private final String name;
  private String filePath;

  public UserSession(WebSocketSession session, String name ) {
    this.session = session;
    this.name = name;
  }

  public WebSocketSession getSession() {
    return session;
  }

  public String getName() {
    return name;
  }

  public void sendMessage(JsonObject message) throws IOException {
    log.debug("Sending message from user with session Id '{}': {}", session.getId(), message);
    session.sendMessage(new TextMessage(message.toString()));
  }

  public WebRtcEndpoint getWebRtcEndpoint() {
    return webRtcEndpoint;
  }

  public void setWebRtcEndpoint(WebRtcEndpoint webRtcEndpoint) {
    this.webRtcEndpoint = webRtcEndpoint;
  }

  public void setRecorderEndpoint(RecorderEndpoint recorderEndpoint) {
    this.recorderEndpoint = recorderEndpoint;
  }

  public void setFilePath(String sFilePath) {
    this.filePath = sFilePath;
  }

  public void addCandidate(IceCandidate candidate) {
    webRtcEndpoint.addIceCandidate(candidate);
  }

  public void stop(String sServerPath) {
    if (recorderEndpoint != null) {
      final CountDownLatch stoppedCountDown = new CountDownLatch(1);
      ListenerSubscription subscriptionId = recorderEndpoint
              .addStoppedListener(new EventListener<StoppedEvent>() {

                @Override
                public void onEvent(StoppedEvent event) {
                  stoppedCountDown.countDown();
                }
              });
      recorderEndpoint.stop();
      try {
        if (!stoppedCountDown.await(5, TimeUnit.SECONDS)) {
          log.error("Error waiting for recorder to stop");
        }
      } catch (InterruptedException e) {
        log.error("Exception while waiting for state change", e);
      }
      recorderEndpoint.removeStoppedListener(subscriptionId);
      Uploader uploader = new Uploader(filePath, sServerPath);
    }
  }

}
