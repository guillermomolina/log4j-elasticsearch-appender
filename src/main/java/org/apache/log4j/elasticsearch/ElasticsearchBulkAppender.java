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
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LoggingEvent;

public class ElasticsearchBulkAppender extends ElasticsearchAppender {
  /**
   * The default timeout to wait before sending in milliseconds.
   */
  public static final long DEFAULT_TIMEOUT = 5000;

  private long timeout = DEFAULT_TIMEOUT;

  /**
   * The default buffer size is set to 128 events.
   */
  public static final int DEFAULT_BUFFER_SIZE = 128;

  /**
   * The default buffer size is set to 4096 events.
   */
  public static final int MAX_BUFFER_SIZE = 4096;

  /**
   * Event buffer.
   */
  private final List<LoggingEvent> buffer = new ArrayList<LoggingEvent>();

  /**
   * Buffer size.
   */
  private int bufferSize = DEFAULT_BUFFER_SIZE;

  private int removedMessages = 0;

  /**
   * Dispatcher.
   */
  private final Thread dispatcher;

  /**
   * Should location info be included in dispatched messages.
   */
  private boolean locationInfo = false;

  /**
   * Create new instance.
   */
  public ElasticsearchBulkAppender() {
    LogLog.setInternalDebugging(true);
    LogLog.setQuietMode(false);
    LogLog.debug("ElasticsearchBulkAppender constructor.");

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
    // Set the NDC and thread name for the calling thread as these
    // LoggingEvent fields were not set at event creation time.
    event.getNDC();
    event.getThreadName();
    // Get a copy of this thread's MDC.
    event.getMDCCopy();
    if (locationInfo) {
      event.getLocationInformation();
    }

    synchronized (buffer) {
      if (buffer.size() > MAX_BUFFER_SIZE) {
        removedMessages++;
        buffer.remove(0);
      }
      buffer.add(event);
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
      LogLog.error("Got an InterruptedException while waiting for the " + "dispatcher to finish.", e);
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
   * Gets whether the location of the logging request call should be captured.
   *
   * @return the current value of the <b>LocationInfo</b> option.
   */
  public boolean getLocationInfo() {
    return locationInfo;
  }

  /**
   * The <b>LocationInfo</b> option takes a boolean value. By default, it is set
   * to false which means there will be no effort to extract the location
   * information related to the event. As a result, the event that will be
   * ultimately logged will likely to contain the wrong location information (if
   * present in the log format).
   * <p/>
   * <p/>
   * Location information extraction is comparatively very slow and should be
   * avoided unless performance is not a concern.
   * </p>
   * 
   * @param flag true if location information should be extracted.
   */
  public void setLocationInfo(final boolean flag) {
    locationInfo = flag;
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
    private final List<LoggingEvent> buffer;

    private Layout layout;

    /**
     * Create new instance of dispatcher.
     *
     * @param parent parent ElasticsearchBulkAppender, may not be null.
     * @param buffer event buffer, may not be null.
     */
    public Dispatcher(final ElasticsearchBulkAppender parent, final List<LoggingEvent> buffer) {
      this.parent = parent;
      this.buffer = buffer;
      this.layout = parent.layout;
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
          LoggingEvent[] events = null;

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
              events = new LoggingEvent[bufferSize];
              buffer.toArray(events);
              buffer.clear();
              buffer.notifyAll();

              if (parent.removedMessages > 0) {
                LogLog.warn("Too many messages, " + parent.removedMessages + " have been removed");
                parent.removedMessages = 0;
              }
              if (layout == null) {
                layout = parent.layout;
              }
            }
          }

          //
          // process events after lock on buffer is released.
          //
          if (events != null && layout != null) {
            String[] docs = new String[events.length];
            for (int i = 0; i < events.length; i++) {
              final LoggingEvent event = events[i];
              final String doc = layout.format(event);
              docs[i] = doc;
            }
            try {
              postEvents(docs);
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
    public void postEvents(final String[] docs) throws IOException {
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
      for (final String doc : docs) {
        data.append(emptyJSON);
        data.append(doc);
      }
      final String dataString = data.toString();
      final OutputStream outputStream = connection.getOutputStream();
      LogLog.debug(dataString);
      outputStream.write(dataString.getBytes(UTF8_CHARSET));
      outputStream.close();
      final int responseCode = connection.getResponseCode();
      InputStream inputStream;
      if (responseCode == HttpURLConnection.HTTP_OK) {
        inputStream = connection.getInputStream();
        String result = parent.toString(inputStream);
        LogLog.debug(result);
      } else {
        inputStream = connection.getErrorStream();
        String result = parent.toString(inputStream);
        LogLog.error(result);
      }
      if (inputStream != null) {
        inputStream.close();
      }
    }
  }
}