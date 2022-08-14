# Log4j Elasticsearch Appender and JSON layout

## What is it?
This project adds the possibility to send log4j (version 1.12+) logs to Elasticsearch.

It is composed of the appenders:

- ElasticsearchAppender
- ElasticsearchBulkAppender
- SocketAppender

And the ECS (Elasticsearch Common Schema) json layout:

- JSONEventLayout

## The appenders
SocketAppender is a hack to the original log4j appender of the same name, allowing layed out messages to be sent (not just a serialization).
ElasticsearchAppender and ElasticsearchBulkAppender can talk to an Elasticsearch API. The conversation is pure HTTP so it is not tied to any specific version of Elasticsearch. The ElasticsearchAppender sends messages one at a time, and ElasticsearchBulkAppender sends many messages at once with the bulk API. 

# Usage
This is just a quick snippit of a `log4j.properties` file:

```
log4j.rootCategory=WARN, ElasticLog
log4j.appender.ElasticLog=org.apache.log4j.elasticsearch.ElasticsearchAppender
log4j.appender.ElasticLog.Server=myelasticsearch.local
log4j.appender.ElasticLog.Index=elasticsearch_log
log4j.appender.ElasticLog.layout=org.apache.log4j.elasticsearch.JSONEventLayout
```

You must use a JSON Layout or it will fail. 

# Sample XML configuration
If you use the XML format for your log4j configuration (and there are valid reasons thanks to AsyncAppender - fml), changing your layout class for your appender would look like this

```xml
   <appender name="ELASTIC" class="org.apache.log4j.elasticsearch.ElasticsearchAppender">
     <param name="Server" value="myelasticsearch.local" />
     <param name="Index" value="elasticsearch_log" />
     <layout class="org.apache.log4j.elasticsearch.JSONEventLayout" />
   </appender>
```

# Configuration for both ElasticsearchAppender and ElasticsearchBulkAppender

Parameter | Type | Default | Description
---|---|---|---
Protocol | String | http | Protocol of the API, http or https
Server | String | localhost | Elasticsearch server
Port | Integer | 9200 | Port of the server 
Index | String | jboss | Elasticsearch destination index 
DocType | String | _doc | Document type (Must be set to _doc for Elasticsearch >= 8)
Username | String | | Username for basic authentication (if required)
Password | String | | Password for basic authentication (if required)

# Configuration for ElasticsearchBulkAppender

This parameters are also available

Parameter | Type | Default | Description
---|---|---|---
BufferSize | Integer | 20 | Maximum number of messages to receive before sending together
Timeout | Integer | 5000 | Timeout to force the sending of messages in milliseconds


## The layout
JSONEventLayout is heavily based on [log4j-jsonevent-layout](https://github.com/logstash/log4j-jsonevent-layout), but the output is ECS compliant. As it is a separate log4j Layout, it can be used in the other appenders that support it. 

# Usage
This is just a quick snippet of a `log4j.properties` file:

```
log4j.rootCategory=WARN, RollingLog
log4j.appender.RollingLog=org.apache.log4j.DailyRollingFileAppender
log4j.appender.RollingLog.Threshold=TRACE
log4j.appender.RollingLog.File=api.log
log4j.appender.RollingLog.DatePattern=.yyyy-MM-dd
log4j.appender.RollingLog.layout=org.apache.log4j.elasticsearch.JSONEventLayout
```

# Sample XML configuration
If you use the XML format for your log4j configuration (and there are valid reasons thanks to AsyncAppender - fml), changing your layout class for your appender would look like this

```xml
   <appender name="Console" class="org.apache.log4j.ConsoleAppender">
     <param name="Threshold" value="TRACE" />
     <layout class="org.apache.log4j.elasticsearch.JSONEventLayout" />
   </appender>
```

## log4j config

Parameter | Type | Default | Description
---|---|---|---
LocationInfo | String | False | Adds source code location info (it is a resource consuming operation)
UserFields | String | | Add user defined fields to the output, a comma separated list of variables


```xml
<layout class="org.apache.log4j.elasticsearch.JSONEventLayout" >
  <param name="UserFields" value="foo:bar,baz.output:qux" />
</layout>
```

or

```
log4j.appender.RollingLog.layout=org.apache.log4j.elasticsearch.JSONEventLayout
log4j.appender.RollingLog.layout.UserFields=foo:bar,baz.output:qux
```

### User fields

UserFields is a comma separated list of <key>:<value> pairs, that will be added to the output.

<key> can be a simple property: 

"foo:bar"
```json
{
    "foo": "bar"
}
```

Or a more complex property:

"foo.ec:bar"
```json
{
    "foo": {
        "ec": "bar"
    }
}
```

If the value is in between parentheses it is matched against the logger name as a regular expression, and if it matches, the property is added with the logger name as the value:

"foo:((.*)\.jboss)\..*"
```json
{
    "foo": "org.jboss.web.tomcat.service.TomcatDeployer"
}
```

### MDC Properties

MDCProperties is a comma separated list of <key>:<mdc_key> pairs, that will be added to the output.

The <key> element has the same structure and usage as in the user fiedls. 
The <mdc_key> element is an MDC property such as "IdUser" or "IdSession"


## Command-line
*Note that the command-line version will OVERRIDE any values specified in the config file should there be a key conflict!*

`java -Dorg.apache.log4j.elasticsearch.JSONEventLayout="field3:prop3,field4:prop4" -jar .....`

A warning will be logged should you attempt to set values in both places.

# Pull Requests
Pull requests are welcome for any and all things - documentation, bug fixes...whatever.