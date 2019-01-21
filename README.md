# openutils-log4j

[![OpenMind logo](https://www.openmindonline.it/favicon.ico)](https://www.openmindonline.it/) 

Openutils-log4j is composed by a set of utility for log4j and log4j2.
This project is supposed to support both log4j and log4j2 features so that:
  - migration to log4j2 should be easier
  - declaring log4j version is just a matter of a maven dependency declaration

# Features log4j(1)
  - AlternateSMTPAppender
  - EnhancedDailyRollingFileAppender
  - FilteredPatternLayout
  - Log4jConfigurationServlet

# Features log4j2
  - ExtendedSmtpAppender (thanks to [Thies Wellpott])

### About Unit tests

Due to maven classpath for test uniqueness, it's impossible to change log4j dependency based upon class.
**Only log4j2 tests** are currently supported. Old log4j tests will be simply ignored


[//]: # (These are reference links used in the body of this note and get stripped out when the markdown processor does its job. There is no need to format nicely because it shouldn't be seen. Thanks SO - http://stackoverflow.com/questions/4823468/store-comments-in-markdown-syntax)


   [Thies Wellpott]: <https://issues.apache.org/jira/browse/LOG4J2-1192r>