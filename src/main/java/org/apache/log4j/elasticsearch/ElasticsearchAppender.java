/*
 * Copyright 2020-2020 The Apache Software Foundation.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// Author: Guillermo Adrián Molina <guillermoadrianmolina@hotmail.com>

package org.apache.log4j.elasticsearch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.commons.codec.binary.Base64;

import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

public class ElasticsearchAppender extends AppenderSkeleton {
  public static final String DEFAULT_PROTOCOL = "http";
  public static final String DEFAULT_SERVER = "localhost";
  public static final int DEFAULT_PORT = 9200;
  public static final String DEFAULT_INDEX = "jboss";
  public static final String DEFAULT_DOC_TYPE = "_doc";

  protected String protocol = DEFAULT_PROTOCOL;
  protected String server = DEFAULT_SERVER;
  protected int port = DEFAULT_PORT;
  protected String index = DEFAULT_INDEX;
  protected String docType = DEFAULT_DOC_TYPE;
  protected String username;
  protected String password;

  /**
   * Set the server property
   */
  public void setServer(String server) {
    this.server = server;
  }

  /**
   * return logUrl
   * 
   * @return logUrl
   */
  public String getServer() {
    return server;
  }

  /**
   * Set the protocol property
   */
  public void setProtocol(String protocol) {
    this.protocol = protocol;
  }

  /**
   * return logUrl
   * 
   * @return logUrl
   */
  public String getProtocol() {
    return protocol;
  }

  /**
   * Set the port property
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * return logUrl
   * 
   * @return logUrl
   */
  public int getPort() {
    return port;
  }

  /**
   * Set the username property
   */
  public void setUsername(String username) {
    this.username = username;
  }

  /**
   * return username
   * 
   * @return username
   */
  public String getUsername() {
    return username;
  }

  /**
   * Set the password property
   */
  public void setPassword(String password) {
    this.password = password;
  }

  /**
   * return password
   * 
   * @return password
   */
  public String getPassword() {
    return password;
  }

  /**
   * Set the index property
   */
  public void setIndex(String index) {
    this.index = index;
  }

  /**
   * return index
   * 
   * @return index
   */
  public String getIndex() {
    return index;
  }

  /**
   * Set the docType property
   */
  public void setDocType(String docType) {
    this.docType = docType;
  }

  /**
   * return docType
   * 
   * @return docType
   */
  public String getDocType() {
    return docType;
  }


  /**
   * the http/s URL to which the log is sent
   */
  protected URL url;

  public URL getURL() {
    return url;
  }


  @Override
  protected void append(LoggingEvent event) {
    if (url == null)
      return;
     
    try {
      sendRequest(event); // Send it
    } catch (IOException ioe) {
      String errMsg = "An exception: " + ioe + " was thrown trying to send the resquest to the server URL: " + url.toString();
      LogLog.error(errMsg);
      // throw new IllegalStateException(errMsg);
    }
  }

  @Override
  public void close() {
  }

  @Override
  public boolean requiresLayout() {
    return true;
  }

  /**
   * overridden as per superclass description
   */
  @Override
  public void activateOptions() {
    try {
      url = new URL(protocol, server, port, "/" + index + "/" + docType + "/");
    } catch (MalformedURLException e) {
      LogLog.error(e.getMessage());
    }
    super.activateOptions();
  }

  /**
   * sends the request
   * 
   * @param event
   */
  private void sendRequest(LoggingEvent event) throws IOException {
    if (event == null)
      return;

    postItem(this.layout.format(event));
  }

  /**
   * POST a request to the url
   *
   * @param doc
   * @throws IOException if an I/O exception occurs while creating/writing/
   *                     reading the request
   */
  public void postItem(final String doc) throws IOException {
    final HttpURLConnection connection = (HttpURLConnection) url.openConnection();

    if (username != null && password != null) {
      final String userpass = username + ":" + password;
      final String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
      connection.setRequestProperty("Authorization", basicAuth);
    }
    connection.setDoInput(true);
    connection.setDoOutput(true);
    connection.setRequestMethod("POST");
    connection.setRequestProperty("Content-Type", "application/json");
    final OutputStream outputStream = connection.getOutputStream();
    outputStream.write(doc.getBytes());
    outputStream.close();
    final int responseCode = connection.getResponseCode();
    InputStream inputStream;
    if (responseCode == HttpURLConnection.HTTP_CREATED) {
      inputStream = connection.getInputStream();
      LogLog.debug(toString(inputStream));
    } else {
      inputStream = connection.getErrorStream();
      LogLog.error(toString(inputStream));
    }
    if (inputStream != null) {
      inputStream.close();
    }
  }

  /**
   * helper to write the response from the server
   * 
   * @param inputStream
   * @return
   * @throws IOException
   */
  public String toString(InputStream inputStream) throws IOException {
    String string;
    StringBuilder outputBuilder = new StringBuilder();
    if (inputStream != null) {
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      while (null != (string = reader.readLine())) {
        outputBuilder.append(string).append('\n');
      }
    }
    return outputBuilder.toString();
  }
}
