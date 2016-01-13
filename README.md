## INSTALLATION

Search is a webapp suitable for tomcat6 or tomcat7. It builds an index of multi-version documents,
for use within Ecdosis. Search is normally used by three front-end Drupal modules: find, index and mvdsingle. Find allows the user to search the index. Index rebuilds it and mvdsingle displays MVDs and search hits.

To rebuild it do a clean rebuild within NetBeans, or produce a fresh 
jar in dist/Search.jar. Then run the war-building script:

    sudo ./build-war.sh

Finally, install the war into tomcat6:

    sudo ./install-war.sh

For Tomcat7 just replace "tomcat6" with "tomcat7" in the install-search-war.sh script. This script assumes you are using Ubuntu. If that is not true you will have ti change the install path.

Indices are stored in a mongo database called "calliope", in an "indices" collection.
Both should be created prior to installation. And of course Mongo needs to be installed. The calliope database is also used to store the Cortexs and Corcodes, so it has to be there.

## SERVICES
Only GET is used.

### /search/build
This builds the index, printing out the progress of index-building as a series of lines 
consisting of percent complete numbers, up to and including 100.

#### Parameters
*docid* - the project identifier. All descendants of this path will have their CORTEX MVD files included in the inde. e.g. english/harpur or italian/capuana/ildrago

### /search/find
Finds some text in the index.
    
#### Parameters
*docid* - the project identifier.

*firsthit* - the number of the first hit to display. The page-size is preset to 20.

*query* -- the query string. Only literal and keyword search are currently suported. A literal query is enclosed in double quotation marks.

### /search/voffsets
Get the offsets in the underlying plain text of a particular version
    
#### Parameters
*docid* -- the *document* identifier. This should indicate a specific CORTEX document such as english/conrad/nostromo/1/1.

*selections* = a sequence of comma-separated integers indicating the MVD global offsets of text whose version-offsets are desired.

*version1* -- the vid of the version whose voffsets are desired.

### /search/list
List the available indices. This returns a JSON document whose format can be read by running this query.

#### Parameters
NONE

## INIT PARAMETERS

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

