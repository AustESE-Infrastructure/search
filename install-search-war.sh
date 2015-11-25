#!/bin/bash
service tomcat6 stop
cp search.war /var/lib/tomcat6/webapps/
rm -rf /var/lib/tomcat6/webapps/search
rm -rf /var/lib/tomcat6/work/Catalina/localhost/
service tomcat6 start
