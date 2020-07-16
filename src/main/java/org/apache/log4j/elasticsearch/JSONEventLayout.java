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

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Layout;
import org.apache.log4j.helpers.LogLog;
import org.apache.log4j.spi.LocationInfo;
import org.apache.log4j.spi.LoggingEvent;
import org.apache.log4j.spi.ThrowableInformation;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

public class JSONEventLayout extends Layout {
    private boolean locationInfo = false;
    private String customUserFields;

    private final boolean ignoreThrowable = false;

    private boolean activeIgnoreThrowable = ignoreThrowable;
    private final HostData hostData = new HostData();
    // private String ndc;

    // private JsonObject logstashEvent;
    private Map<String, Object> jsonEvent;

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
    }

    public String format(final LoggingEvent loggingEvent) {
        final Long timestamp = loggingEvent.timeStamp;
        final String loggerName = loggingEvent.getLoggerName();
        final String whoami = this.getClass().getSimpleName();

        jsonEvent = hostData.getCopy();

        jsonEvent.put("@timestamp", timestamp);
        jsonEvent.put("message", loggingEvent.getRenderedMessage());

        if (!activeIgnoreThrowable && loggingEvent.getThrowableInformation() != null) {
            final ThrowableInformation throwableInformation = loggingEvent.getThrowableInformation();
            final String type = throwableInformation.getThrowable().getClass().getCanonicalName();
            final String message = throwableInformation.getThrowable().getMessage();
            final String[] stackTrace = throwableInformation.getThrowableStrRep();

            if (type != null || message != null || stackTrace != null) {
                Map<String, Object> error = new HashMap<String, Object>();
                jsonEvent.put("error", error);

                if (type != null)
                    error.put("type", type);
                if (message != null)
                    error.put("message", message);
                if (stackTrace != null)
                    error.put("stack_trace", StringUtils.join(stackTrace, "\n"));
            }
        }

        Map<String, Object> log = new HashMap<String, Object>();
        jsonEvent.put("log", log);
        log.put("logger", loggerName);
        log.put("level", loggingEvent.getLevel().toString());
        
        if (locationInfo) {
            final LocationInfo info = loggingEvent.getLocationInformation();
            final String file_name = info.getFileName();
            final String line_number = info.getLineNumber();
            final String class_name = info.getClassName();
            final String method_name = info.getMethodName();

            if (file_name != "?" && line_number != "?" && class_name != "?" && method_name != "?") {
                Map<String, Object> origin = new HashMap<String, Object>();
                log.put("origin", origin);
                origin.put("class", class_name);
                origin.put("function", method_name);
                Map<String, Object> file = new HashMap<String, Object>();
                origin.put("file", file);
                file.put("name", file_name);
                try {
                    file.put("line", Integer.parseInt(line_number));
                } catch (final NumberFormatException e) {
                }

            }
        }

        addEventData("process.thread.name", loggingEvent.getThreadName());

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

        return toString() + "\n";
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

                    if (val.matches("\\(.*\\)")) {
                        if (loggerName.matches(val)) {
                            addEventData(key, loggerName);
                        }
                    } else {
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

    /*
     * public String toString() { return logstashEvent.toString(); }
     */
    public String toString() {
        String result = jsonToString(jsonEvent);
        return result;
    }

    public String jsonToString(Map<String, Object> object) {
        final StringBuilder str = new StringBuilder("{");
        boolean first = true;
        for (final Map.Entry<String, Object> entry : object.entrySet()) {
            final String key = entry.getKey();
            final Object value = entry.getValue();
            if (value != null) {
                if (!first)
                    str.append(",");
                else
                    first = false;
                str.append("\"");
                str.append(key);
                str.append("\":");
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> valueObject = (Map<String, Object>) value;
                    str.append(jsonToString(valueObject));
                } else if ((value instanceof Integer) || (value instanceof Long) || (value instanceof Float)) {
                    str.append(value.toString());
                } else {
                    str.append("\"");
                    str.append(value.toString());
                    str.append("\"");
                }
            }
        }
        str.append("}");
        return str.toString();
    }

    public void addEventData(final String keyname, final Object keyval) {
        if (null != keyval) {
            final String[] keys = keyname.split("\\.");
            Map<String, Object> object = jsonEvent;
            int i = 0;
            for (final String key : keys) {
                if (++i < keys.length) {
                    final Object innerobject = object.get(key);
                    if (innerobject instanceof Map) {
                        @SuppressWarnings("unchecked")
                        final Map<String, Object> _innerObject = (Map<String, Object>) innerobject;
                        object = _innerObject;
                    } else {
                        Map<String, Object> newObject = new HashMap<String, Object>();
                        object.put(key, newObject);
                        object = newObject;
                    }
                } else {
                    object.put(key, keyval);
                }
            }
        }
    }
}
