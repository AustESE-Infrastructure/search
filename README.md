INSTALL
=======

Search is a webapp suitable for tomcat6 (or by slight modification to 
the install scripts) to tomcat7. It builds a Lucene index of the MVDs,
annotations and metadata for use within Ecdosis.

Search prints out progress of index-building as a series of lines 
consisting of percent complete numbers, up to and including 100.

To rebuild it do a clean rebuild within NetBeans, or produce a fresh 
jar in dist/Search.jar. Then run the war-building script:

    sudo ./buildwar.sh

Finally, install the war into tomcat6:

    sudo ./install-war.sh

Search requires write-access to the ecdosis user account, and especially 
to the index directory as specified in the indexRoot webapp init 
parameter (see web.xml). For this purpose it is sufficient to add the tomcat6
user to the group of that directory, e.g.

    sudo usermod -G desmond tomcat6
    ls -l /home/ecdosis

The final command should output something like this:

    drwxrwxr-x 4 ecdosis desmond 4096 Jan 21 09:35 index

which is set up with:

    sudo chmod -R 775 /home/ecdosis/index

Support for tomcat7 will require some minor adjustments to the build 
scripts etc., basically by replacing "tomcat6" with "tomcat7" where 
appropriate.

INIT PARAMETRERS
================
repository 

The default is MONGO. Other databases could be supported, and 
historically COUCH was.

indexRoot

The absoute path to the root directory to write the indices, one per 
language in a separate sub-dir. Defaults to /home/ecdosis/index. This is 
probably the only setting you may need to change.

dbPort

The db port used by the database, or 27017 for Mongo

password

The database password, which is probably ignored

username

The database username, probably ignored

webRoot

Ignored by this application

host

Defaults to localhost, which is the host it will run on.

