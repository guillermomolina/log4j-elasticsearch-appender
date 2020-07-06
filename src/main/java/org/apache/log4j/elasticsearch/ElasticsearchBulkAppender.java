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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.HttpURLConnection;
import java.net.URL;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.codec.binary.Base64;

import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

public class ElasticsearchBulkAppender extends ElasticsearchAppender {
  /**
   * The default timeout to wait before sending in milliseconds.
   */
  public static final long DEFAULT_TIMEOUT = 5000;

  private long timeout = DEFAULT_TIMEOUT;

  /**
   * The default buffer size is set to 10 events.
   */
  public static final int DEFAULT_BUFFER_SIZE = 20;

  /**
   * Event buffer.
   */
  private final List<String> buffer = new ArrayList<String>();

  /**
   * Buffer size.
   */
  private int bufferSize = DEFAULT_BUFFER_SIZE;

  /**
   * Dispatcher.
   */
  private final Thread dispatcher;

  /**
   * Create new instance.
   */
  public ElasticsearchBulkAppender() {
    dispatcher = new Thread(new Dispatcher(this, buffer));

    // It is the user's responsibility to close appenders before
    // exiting.
    dispatcher.setDaemon(true);

    // set the dispatcher priority to lowest possible value
    // dispatcher.setPriority(Thread.MIN_PRIORITY);
    dispatcher.setName("Dispatcher-" + dispatcher.getName());
    dispatcher.start();
  }

  /**
   * {@inheritDoc}
   */
  public void append(final LoggingEvent event) {
    synchronized (buffer) {
      buffer.add(this.layout.format(event));
      if (buffer.size() >= bufferSize)
        buffer.notifyAll();
    }
  }

  /**
   * Close this <code>AsyncAppender</code> by interrupting the dispatcher thread
   * which will process all pending events before exiting.
   */
  public void close() {
    /**
     * Set closed flag and notify all threads to check their conditions. Should
     * result in dispatcher terminating.
     */
    synchronized (buffer) {
      closed = true;
      buffer.notifyAll();
    }

    try {
      dispatcher.join();
    } catch (final InterruptedException e) {
      Thread.currentThread().interrupt();
      org.apache.log4j.helpers.LogLog
          .error("Got an InterruptedException while waiting for the " + "dispatcher to finish.", e);
    }
  }

  /**
   * Sets the number of messages allowed in the event buffer before the calling
   * thread is blocked (if blocking is true) or until messages are summarized and
   * discarded. Changing the size will not affect messages already in the buffer.
   *
   * @param size buffer size, must be positive.
   */
  public void setBufferSize(final int size) {
    //
    // log4j 1.2 would throw exception if size was negative
    // and deadlock if size was zero.
    //
    if (size < 0) {
      throw new java.lang.NegativeArraySizeException("size");
    }

    synchronized (buffer) {
      //
      // don't let size be zero.
      //
      bufferSize = (size < 1) ? 1 : size;
      buffer.notifyAll();
    }
  }

  /**
   * Gets the current buffer size.
   * 
   * @return the current value of the <b>BufferSize</b> option.
   */
  public int getBufferSize() {
    return bufferSize;
  }


  /**
   * Set the timeout property
   */
  public void setTimeout(final long timeout) {
    this.timeout = timeout;
  }

  /**
   * return timeout
   * 
   * @return timeout
   */
  public long getTimeout() {
    return timeout;
  }


  /**
   * Event dispatcher.
   */
  private static class Dispatcher implements Runnable {
    /**
     * Parent AsyncAppender.
     */
    private final ElasticsearchBulkAppender parent;

    /**
     * Event buffer.
     */
    private final List<String> buffer;

    /**
     * Create new instance of dispatcher.
     *
     * @param parent parent ElasticsearchBulkAppender, may not be null.
     * @param buffer event buffer, may not be null.
     */
    public Dispatcher(final ElasticsearchBulkAppender parent, final List<String> buffer) {
      this.parent = parent;
      this.buffer = buffer;
    }

    /**
     * {@inheritDoc}
     */
    public void run() {
      boolean isActive = true;

      //
      // if interrupted (unlikely), end thread
      //
      try {
        //
        // loop until the ElasticsearchBulkAppender is closed.
        //
        while (isActive) {
          String[] events = null;

          //
          // extract pending events while synchronized
          // on buffer
          //
          synchronized (buffer) {
            int bufferSize = buffer.size();
            isActive = !parent.closed;

            while ((bufferSize == 0) && isActive) {
              buffer.wait(parent.timeout);
              bufferSize = buffer.size();
              isActive = !parent.closed;
            }

            if (bufferSize > 0) {
              events = new String[bufferSize];
              buffer.toArray(events);

              //
              // clear buffer and discard map
              //
              buffer.clear();

              //
              // allow blocked appends to continue
              buffer.notifyAll();
            }
          }

          //
          // process events after lock on buffer is released.
          //
          if (events != null) {
            try {
              postEvents(events);
            } catch (IOException e) {
            }
          }
        }
      } catch (final InterruptedException ex) {
        Thread.currentThread().interrupt();
      }
    }

    /**
     * POST a request to the url
     *
     * @param doc
     * @throws IOException if an I/O exception occurs while creating/writing/
     *                     reading the request
     */
    public void postEvents(final String[] events) throws IOException {
      URL url = parent.getURL();
      if (url == null)
        return;

      URL bulkURL = new URL(url, "_bulk");

      final HttpURLConnection connection = (HttpURLConnection) bulkURL.openConnection();

      final String username = parent.getUsername();
      final String password = parent.getPassword();
      if (username != null && password != null) {
        final String userpass = username + ":" + password;
        final String basicAuth = "Basic " + new String(new Base64().encode(userpass.getBytes()));
        connection.setRequestProperty("Authorization", basicAuth);
      }
      connection.setDoInput(true);
      connection.setDoOutput(true);
      connection.setRequestMethod("POST");
      connection.setRequestProperty("Content-Type", "application/x-ndjson");
      final StringBuffer data = new StringBuffer();
      final String emptyJSON = "{\"index\":{}}\n";
      for (final String doc : events) {
        data.append(emptyJSON);
        data.append(doc);
      }
      final String dataString = data.toString();
      final OutputStream outputStream = connection.getOutputStream();
      LogLog.debug(dataString);
      outputStream.write(dataString.getBytes());
      outputStream.close();
      final int responseCode = connection.getResponseCode();
      InputStream inputStream;
      if (responseCode == HttpURLConnection.HTTP_OK) {
        inputStream = connection.getInputStream();
        LogLog.debug(parent.toString(inputStream));
      } else {
        inputStream = connection.getErrorStream();
        LogLog.error(parent.toString(inputStream));
      }
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }
}