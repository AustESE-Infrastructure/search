#!/bin/bash
if [ ! -d search ]; then
  mkdir search
  if [ $? -ne 0 ] ; then
    echo "couldn't create search directory"
    exit
  fi
fi
if [ ! -d search/WEB-INF ]; then
  mkdir search/WEB-INF
  if [ $? -ne 0 ] ; then
    echo "couldn't create search/WEB-INF directory"
    exit
  fi
fi
if [ ! -d search/WEB-INF/lib ]; then
  mkdir search/WEB-INF/lib
  if [ $? -ne 0 ] ; then
    echo "couldn't create search/WEB-INF/lib directory"
    exit
  fi
fi
rm -f search/WEB-INF/lib/*.jar
cp dist/Search.jar search/WEB-INF/lib/
cp web.xml search/WEB-INF/
jar cf search.war -C search WEB-INF 
echo "NB: you MUST copy the contents of tomcat-bin to \$tomcat_home/bin"
