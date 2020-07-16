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

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

public class HostData {
    private String hostName;
    private int pid;
    Map<String, String> systemProperties;
    Map<String, Object> hostData;

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
        buildeHostData();
    }

    private void buildeHostData() {
        hostData = new HashMap<String,Object>();
        hostData.put("@version", 1);

        Map<String,Object> host = new HashMap<String,Object>();
        hostData.put("host", host);

        host.put("name", getHostName());
        host.put("architecture", getSystemProperty("os.arch"));

        Map<String,Object> os = new HashMap<String,Object>();
        host.put("os", os);

        os.put("name", getSystemProperty("os.name"));
        os.put("version", getSystemProperty("os.version"));


        Map<String,Object> process = new HashMap<String,Object>();
        hostData.put("process", process);
        process.put("pid", getPID());

        Map<String,Object> java = new HashMap<String,Object>();
        hostData.put("java", java);

        java.put("version", getSystemProperty("java.version"));
        java.put("vendor", getSystemProperty("java.vendor"));
        java.put("home", getSystemProperty("java.home"));

        try {
            java.put("bits", Integer.parseInt(getSystemProperty("sun.arch.data.model")));
        } catch (final NumberFormatException e) {
        }
    }

    public Map<String, Object> getCopy() {
        return new HashMap<String, Object>(hostData);
    }
}