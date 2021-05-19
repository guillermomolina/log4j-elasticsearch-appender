# Change log for Solaris OCI CLI

## 2021-05-19: Version 1.4

- Show less debug output

## 2020-07-16: Version 1.3

- Back to gson to avoid escaping issues
- Take out commons-io dependency
- Send in UTF-8 charset
- Changed error message sending to elasticsearch

## 2020-07-16: Version 1.2

- Added custom JSON handling, gson is no longer needed
- ElasticsearchBulkAppender buffer stores events instead of Strings
- Moved object to json string logic to the async thread
- Set maximum number of messages to store to 1024, beyond that messages are lost.

## 2020-07-10: Version 1.1

- Set default buffer size to 128 messages in ElasticsearchBulkAppender

## 2020-07-06: Version 1.0

- Initial version