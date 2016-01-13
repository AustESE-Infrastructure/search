#!/bin/bash
service tomcat7 stop
cp search.war /var/lib/tomcat7/webapps/
rm -rf /var/lib/tomcat7/webapps/search
rm -rf /var/lib/tomcat7/work/Catalina/localhost/
service tomcat7 start
