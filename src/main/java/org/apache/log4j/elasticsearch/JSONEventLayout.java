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

/*
Based on:
https://github.com/logstash/log4j-jsonevent-layout/blob/master/src/main/java/net/logstash/log4j/JSONEventLayoutV1.java
*/

package org.apache.log4j.elasticsearch;

import org.apache.log4j.elasticsearch.data.HostData;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.TimeZone;

public class JSONEventLayout extends Layout {

    private boolean locationInfo = false;
    private String customUserFields;

    private final boolean ignoreThrowable = false;

    private boolean activeIgnoreThrowable = ignoreThrowable;
    private final HostData hostData = new HostData();
    private String threadName;
    private long timestamp;
    // private String ndc;
    private LocationInfo info;
    private static Integer version = 1;

    private JsonObject logstashEvent;

    public static final TimeZone UTC = TimeZone.getTimeZone("UTC");
    public static final FastDateFormat ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS = FastDateFormat
            .getInstance("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", UTC);
    public static final String ADDITIONAL_DATA_PROPERTY = "net.logstash.log4j.JSONEventLayout.UserFields";

    public static String dateFormat(final long timestamp) {
        return ISO_DATETIME_TIME_ZONE_FORMAT_WITH_MILLIS.format(timestamp);
    }

    /**
     * For backwards compatibility, the default is to generate location information
     * in the log messages.
     */
    public JSONEventLayout() {
        this(true);
    }

    /**
     * Creates a layout that optionally inserts location information into log
     * messages.
     *
     * @param locationInfo whether or not to include location information in the log
     *                     messages.
     */
    public JSONEventLayout(final boolean locationInfo) {
        this.locationInfo = locationInfo;
        // logstashEvent = new JsonObject();
    }

    public String format(final LoggingEvent loggingEvent) {
        threadName = loggingEvent.getThreadName();
        timestamp = loggingEvent.timeStamp;
        // ndc = loggingEvent.getNDC();

        logstashEvent = new JsonObject();
        final String whoami = this.getClass().getSimpleName();

        /**
         * All v1 of the event format requires is "@timestamp" and "@version" Every
         * other field is arbitrary
         */
        logstashEvent.addProperty("@version", version);
        logstashEvent.addProperty("@timestamp", dateFormat(timestamp));

        final String loggerName = loggingEvent.getLoggerName();
        addEventData("log.logger", loggerName);

        /**
         * Extract and add fields from log4j config, if defined
         */
        if (getUserFields() != null) {
            final String userFlds = getUserFields();
            LogLog.debug("[" + whoami + "] Got user data from log4j property: " + userFlds);
            addUserFields(userFlds, loggerName);
        }

        /**
         * Extract fields from system properties, if defined Note that CLI props will
         * override conflicts with log4j config
         */
        if (System.getProperty(ADDITIONAL_DATA_PROPERTY) != null) {
            if (getUserFields() != null) {
                LogLog.warn("[" + whoami
                        + "] Loading UserFields from command-line. This will override any UserFields set in the log4j configuration file");
            }
            final String userFieldsProperty = System.getProperty(ADDITIONAL_DATA_PROPERTY);
            LogLog.debug("[" + whoami + "] Got user data from system property: " + userFieldsProperty);
            addUserFields(userFieldsProperty, loggerName);
        }

        /**
         * Now we start injecting our own stuff.
         */
        addEventData("message", loggingEvent.getRenderedMessage());
        addEventData("host.name", hostData.getHostName());
        addEventData("host.architecture", hostData.getSystemProperty("os.arch"));
        addEventData("host.os.name", hostData.getSystemProperty("os.name"));
        addEventData("host.os.version", hostData.getSystemProperty("os.version"));
        addEventData("process.pid", hostData.getPID());

        addEventData("java.version", hostData.getSystemProperty("java.version"));
        addEventData("java.vendor", hostData.getSystemProperty("java.vendor"));
        addEventData("java.home", hostData.getSystemProperty("java.home"));

        try {
            addEventData("java.bits", Integer.parseInt(hostData.getSystemProperty("sun.arch.data.model")));
        } catch (final NumberFormatException e) {
        }

        if (!activeIgnoreThrowable && loggingEvent.getThrowableInformation() != null) {
            final ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
            if (throwableInformation.getThrowable().getClass().getCanonicalName() != null) {
                addEventData("error.type", throwableInformation.getThrowable().getClass().getCanonicalName());
            }
            if (throwableInformation.getThrowable().getMessage() != null) {
                addEventData("error.message", throwableInformation.getThrowable().getMessage());
            }
            if (throwableInformation.getThrowableStrRep() != null) {
                final String stackTrace = StringUtils.join(throwableInformation.getThrowableStrRep(), "\n");
                addEventData("error.stack_trace", stackTrace);
            }
        }

        if (locationInfo) {
            info = loggingEvent.getLocationInformation();
            String data = info.getFileName();
            if (data != "?")
                addEventData("log.origin.file.name", data);
            data = info.getLineNumber();
            if (data != "?")
                addEventData("log.origin.file.line", data);
            data = info.getClassName();
            if (data != "?")
                addEventData("log.origin.class", data);
            data = info.getMethodName();
            if (data != "?")
                addEventData("log.origin.function", data);
        }
        
        // addEventData("ndc", ndc);
        addEventData("log.level", loggingEvent.getLevel().toString());
        addEventData("process.thread.name", threadName);

        return logstashEvent.toString() + "\n";
    }

    public boolean ignoresThrowable() {
        return ignoreThrowable;
    }

    /**
     * Query whether log messages include location information.
     *
     * @return true if location information is included in log messages, false
     *         otherwise.
     */
    public boolean getLocationInfo() {
        return locationInfo;
    }

    /**
     * Set whether log messages should include location information.
     *
     * @param locationInfo true if location information should be included, false
     *                     otherwise.
     */
    public void setLocationInfo(final boolean locationInfo) {
        this.locationInfo = locationInfo;
    }

    public String getUserFields() {
        return customUserFields;
    }

    public void setUserFields(final String userFields) {
        this.customUserFields = userFields;
    }

    public void activateOptions() {
        activeIgnoreThrowable = ignoreThrowable;
    }

    public void addUserFields(final String data, final String loggerName) {
        if (null != data) {
            final String[] pairs = data.split(",");
            for (final String pair : pairs) {
                final String[] userField = pair.split(":", 2);
                if (userField[0] != null) {
                    final String key = userField[0];
                    final String val = userField[1];
            
                    if(val.matches("\\(.*\\)")) {
                        if(loggerName.matches(val)) {
                            addEventData(key, loggerName);
                        }
                    }
                    else {
                        try {
                            addEventData(key, Integer.parseInt(val));
                        } catch (NumberFormatException e) {
                            addEventData(key, val);
                        }                      
                    }
                }
            }
        }
    }

    public String toString() {
        return logstashEvent.toString();
    }

    public void addEventData(final String keyname, final Object keyval) {
        if (null != keyval) {
            final String[] keys = keyname.split("\\.");
            JsonObject object = logstashEvent;
            int i = 0;
            for (final String key : keys) {
                if (++i < keys.length) {
                    final JsonElement innerobject = object.get(key);
                    if (innerobject != null && innerobject.isJsonObject()) {
                        object = innerobject.getAsJsonObject();
                    } else {
                        final JsonObject newObject = new JsonObject();
                        object.add(key, newObject);
                        object = newObject;
                    }
                } else {
                    final Gson gson = new Gson();
                    object.add(key, gson.toJsonTree(keyval));
                }
            }
        }
    }
}
