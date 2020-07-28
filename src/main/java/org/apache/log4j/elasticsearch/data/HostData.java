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
https://github.com/logstash/log4j-jsonevent-layout/blob/master/src/main/java/net/logstash/log4j/data/HostData.java
*/

package org.apache.log4j.elasticsearch.data;

import com.google.gson.JsonObject;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class HostData {
    private String hostName;
    private int pid;
    Map<String, String> systemProperties;
    private JsonObject hostData;

    public String getSystemProperty(final String property) {
        return systemProperties.get(property);
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(final String hostName) {
        this.hostName = hostName;
    }

    public int getPID() {
        return pid;
    }

    public void setPID(final int pid) {
        this.pid = pid;
    }

    public HostData() {
        try {
            this.hostName = java.net.InetAddress.getLocalHost().getHostName();
        } catch (final UnknownHostException e) {
            setHostName("unknown-host");
        }
        final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
        if (bean != null) {
            this.systemProperties = bean.getSystemProperties();

            final String[] name = bean.getName().split("@");
            if (name.length == 2) {
                try {
                    this.pid = Integer.parseInt(name[0]);
                } catch (final NumberFormatException e) {
                    setPID(-1);
                }
            }    
        }
        if (this.systemProperties == null)
            this.systemProperties = new HashMap<String,String>();
        buildHostData();
    }

    private void buildHostData() {
        hostData = new JsonObject();
        hostData.addProperty("@version", 1);

        final JsonObject host = new JsonObject();
        hostData.add("host", host);

        host.addProperty("name", getHostName());
        host.addProperty("architecture", getSystemProperty("os.arch"));

        final JsonObject os = new JsonObject();
        host.add("os", os);

        os.addProperty("name", getSystemProperty("os.name"));
        os.addProperty("version", getSystemProperty("os.version"));


        final JsonObject process = new JsonObject();
        hostData.add("process", process);
        process.addProperty("pid", getPID());

        final JsonObject java = new JsonObject();
        hostData.add("java", java);

        java.addProperty("version", getSystemProperty("java.version"));
        java.addProperty("vendor", getSystemProperty("java.vendor"));
        java.addProperty("home", getSystemProperty("java.home"));

        try {
            java.addProperty("bits", Integer.parseInt(getSystemProperty("sun.arch.data.model")));
        } catch (final NumberFormatException e) {
        }
    }

    public JsonObject getCopy() {
        return hostData.deepCopy();
    }
}