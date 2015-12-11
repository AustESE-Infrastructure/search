INSTALL
=======

Search is a webapp suitable for tomcat6 or tomcat7. It builds an index of multi-version documents,
for use within Ecdosis.

Search prints out progress of index-building as a series of lines 
consisting of percent complete numbers, up to and including 100.

To rebuild it do a clean rebuild within NetBeans, or produce a fresh 
jar in dist/Search.jar. Then run the war-building script:

    sudo ./build-war.sh

Finally, install the war into tomcat6:

    sudo ./install-war.sh

For Tomcat7 just replace "tomcat6" with "tomcat7" in the install-search-war.sh script.

Indices are stored in a mongo database called "calliope", in an "indices" collection.
Both should be created prior to installation.

SERVICES
========
Only GET is used.

    /search/build -- this builds the index.

Parameters
----------
*docid* - the project identifier. All descendants of this path will have their CORTEX MVD files included in the inde. e.g. english/harpur or italian/capuana/ildrago

    /search/find -- finds some text in the indes
    
Parameters
----------
*docid* - the project identifier.

*firsthit* - the number of the first hit to display. The page-size is preset to 20.

*query* -- the query string. Only literal and keyword search are currently suported. A literal query is enclosed in double quotation marks.

    /search/voffsets - get the offsets in the underlying plain text of a particular version
    
Parameters
----------
*docid* -- the *document* identifier. This should indicate a specific CORTEX document such as english/conrad/nostromo/1/1.

*selections* = a sequence of comma-separated integers indicating the MVD global offsets of text whose version-offsets are desired.

*version1* -- the vid of the version whose voffsets are desired.

    /search/list -- list the available indices. THis returns a JSON document

Parameters
----------
NONE

INIT PARAMETRERS
================
These are stored in the web.xml file.

repository 

The only admissible value is MONGO. Other databases could be supported, and 
historically COUCH was.

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

