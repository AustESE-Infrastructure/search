/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package search.handler;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.util.Version;

import javax.servlet.http.HttpServletResponse;

import java.io.File;
import org.json.simple.*;

import org.apache.lucene.analysis.core.StopAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import calliope.core.constants.JSONKeys;
import java.io.StringReader;
import calliope.core.database.Connection;
import calliope.core.database.Connector;
import calliope.core.constants.Database;
import org.json.simple.JSONValue;
import search.exception.SearchException;
import edu.luc.nmerge.mvd.MVD;
import edu.luc.nmerge.mvd.MVDFile;
import search.JettyServer;
import java.util.HashSet;
import java.util.Set;
import java.util.Iterator;
import java.io.IOException;
import java.util.HashMap;

/**
 * Index all cortex versions and metadata fields
 * @author desmond
 */

public class BuildIndex 
{
    HttpServletResponse response;
    private String[] cortexs;
    private String[] metadata;
    private String[] annotations;
    private float lastPercent;
    int nDocs;
    int done;
    private static HashSet<String> metadataKeys;
    static HashSet<String> okLanguages;
    static String[] italianStopwords = {"﻿a", "adesso", "ai", "al", "alla", 
        "allo", "allora", "altre", "altri", "altro", "anche", "ancora", 
        "avere", "aveva", "avevano", "ben", "buono", "che", "chi", "cinque", 
        "comprare", "con", "cosa", "cui", "da", 
        "del", "della", "dello", "dentro", "deve", "devo", "di", "doppio", 
        "due", "e", "ecco", "fare", "fine", "fino", "fra", "gente", "giu", 
        "ha", "hai", "hanno", "ho", "il", "indietro", "invece", "io", "la", 
        "lavoro", "le", "lei", "lo", "loro", "lui", "lungo", "ma", "me", 
        "meglio", "molta", "molti", "molto", "nei", "nella", "no", "noi", 
        "nome", "nostro", "nove", "nuovi", "nuovo", "o", "oltre", "ora", 
        "otto", "peggio", "pero", "persone", "piu", "poco", "primo", 
        "qua", "quarto", "quasi", "quattro", "quello", "questo", 
        "qui", "quindi", "quinto", "rispetto", "sara", "secondo", "sei", 
        "sembra", "sembrava", "senza", "sette", "sia", "siamo", "siete", 
        "solo", "sono", "sopra", "soprattutto", "sotto", "stati", "stato", 
        "stesso", "su", "subito", "sul", "sulla", "tanto", "te", "tempo", 
        "terzo", "tra", "tre", "triplo", "ultimo", "un", "una", "uno", "va", 
        "vai", "voi", "volte", "vostro"};
    static 
    {
        metadataKeys = new HashSet<String>();
        metadataKeys.add( "title");
        okLanguages = new HashSet<String>();
        okLanguages.add("english");
        okLanguages.add("italian");
    }
    // add other stoplists here
    BuildIndex( HttpServletResponse response )
    {
        this.response = response;
        // add other languages here
        lastPercent = 0.0f;
    }
    /**
     * Extract the language code from the docid
     * @param docid the docid should start with the language
     * @return a kosher language name or "english"
     */
    private String getLanguage( String docid )
    {
        int pos = docid.indexOf("/");
        if ( pos != -1 )
        {
            String lang = docid.substring(0,pos );
            if ( okLanguages.contains(lang) )
                return lang;
        }
        return "english";
    }
    /**
     * Get a set of all the kosher languages found in a set of docids
     * @param languages the set of languages to build
     * @param docids an array of docids from the database
     */
    private void getFoundLanguages( 
        HashMap<String,IndexWriter> languages, 
        String[] docids )
    {
        for ( int i=0;i<docids.length;i++ )
        {
            if ( docids[i] != null )
            {
                int pos = docids[i].indexOf("/");
                if ( pos != -1 )
                {
                    String lang = getLanguage(docids[i]);
                    // use null for the indexwriter for now...
                    if ( !languages.containsKey(lang) )
                        languages.put(lang,null);
                }
            }
        }
    }
    /**
     * Choose a stopword set appropriate for the language
     * @param language the language to get the set for
     * @return a stopword set
     */
    private CharArraySet getStopwordSet( String language )
    {
        if ( language.equals("english") ) 
            return StopAnalyzer.ENGLISH_STOP_WORDS_SET;
        else if ( language.equals("italian") )
        {
            CharArraySet it = new CharArraySet(
                italianStopwords.length, true);
            for ( int i=0;i<italianStopwords.length;i++ )
                it.add( italianStopwords[i] );
            return it;
        }
        else
            return CharArraySet.EMPTY_SET;
    }
    /** 
     * Index all text files under a directory. 
     * @param progress the response writer to report percentage progress
     */
    public void rebuild() throws SearchException
    {
        try
        {
            File index = new File(JettyServer.indexRoot);
            if ( !index.exists() )
                if ( !index.mkdirs() )
                    throw new IOException("Couldn't create "+JettyServer.indexRoot);
            Connection conn = Connector.getConnection();
            cortexs = conn.listCollection(Database.CORTEX);
            annotations = conn.listCollection(Database.ANNOTATIONS);
            metadata = conn.listCollection(Database.METADATA);
            nDocs = metadata.length+annotations.length+cortexs.length;
            HashMap<String,IndexWriter> languages = new HashMap<String,IndexWriter>();
            getFoundLanguages( languages, cortexs );
            getFoundLanguages( languages, metadata );
            getFoundLanguages( languages, annotations );
            Set<String> keys = languages.keySet();
            Iterator<String> iter = keys.iterator();
            while ( iter.hasNext() )
            {
                String key = iter.next();
                File childIndex = new File( index, key );
                if ( !childIndex.exists() )
                    if ( !childIndex.mkdir() )
                        throw new IOException("Couldn't create "
                            +childIndex.getAbsolutePath());
                Directory dir = FSDirectory.open(childIndex);
                CharArraySet set = getStopwordSet( key );
                Analyzer analyzer = new StandardAnalyzer( set );
                IndexWriterConfig iwc = new IndexWriterConfig(Version.LATEST, 
                    analyzer);
                iwc.setOpenMode(OpenMode.CREATE);
                IndexWriter writer = new IndexWriter(dir, iwc);
                languages.put( key, writer );
            }
            indexCorTexs(conn,languages);
            indexMetadata(conn,languages);
            indexAnnotations(conn,languages);
            iter = keys.iterator();
            // close all the open indices
            while ( iter.hasNext() )
            {
                IndexWriter writer = languages.get( iter.next() );
                writer.close();
            }
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    private void incDone() throws IOException
    {
        done++;
        if ( nDocs==done )
        {
            response.getWriter().println(100);
            response.getWriter().flush();
        }
        else if ( (float)(done*100)/(float)nDocs-lastPercent > 1.0f )
        {
            lastPercent = (float)(done*100)/(float)nDocs;
            response.getWriter().println(Math.round(lastPercent));
            response.getWriter().flush();
        }
    }
    /**
     * Index all the cortexs and all their versions
     * @param conn the database connection
     * @param map map of languages to index-writers
     * @throws SearchException 
     */
    private void indexCorTexs( Connection conn, 
        HashMap<String,IndexWriter> map ) throws SearchException
    {
        try
        {
            for ( int i=0;i<cortexs.length;i++ )
            {
                if ( cortexs[i]!=null )
                {
                    String bson = conn.getFromDb( Database.CORTEX, cortexs[i] );
                    JSONObject jDoc = (JSONObject)JSONValue.parse( bson );
                    String format = (String)jDoc.get(JSONKeys.FORMAT);
                    String body = (String)jDoc.get(JSONKeys.BODY);
                    // get the correct index for this language
                    String language = getLanguage( cortexs[i] );
                    IndexWriter writer = map.get(language);
                    if ( format != null && format.startsWith("MVD") )
                    {
                        MVD mvd = MVDFile.internalise( body );
                        int nVersions = mvd.numVersions();
                        String encoding = mvd.getEncoding();
                        for ( int vid=1;vid<=nVersions;vid++)
                        {
                            byte[] data = mvd.getVersion( vid ); 
                            String text = new String( data, encoding );
                            Document doc = new Document();
                            String vPath = mvd.getGroupPath((short)vid)
                                +"/"+mvd.getVersionShortName(vid);
                            Field docid = new StringField("docid", cortexs[i], 
                                Field.Store.YES);
                            Field versionId = new StringField("vid", vPath, 
                                Field.Store.YES);
                            doc.add( docid );
                            doc.add( versionId );
                            StringReader sr = new StringReader( text );
                            doc.add(new TextField(JSONKeys.CONTENT, sr) );
                            writer.addDocument(doc);
                        }
                    }
                    else if ( format != null )
                    {
                        Document doc = new Document();
                        Field docid = new StringField("docid", cortexs[i], 
                            Field.Store.YES);
                        doc.add(docid);
                        StringReader sr = new StringReader( body );
                        doc.add(new TextField(JSONKeys.CONTENT, sr) );
                        writer.addDocument(doc);
                    }
                }
                incDone();
            }
        }
        catch ( Exception e )
        {
            throw new SearchException( e );
        }
    }
    /**
     * Index all the metadata
     * @param conn the database connection
     * @param map map of languages to index-writers
     * @throws SearchException 
     */
    private void indexMetadata( Connection conn, 
        HashMap<String,IndexWriter> map ) throws SearchException
    {
        try
        {
            for ( int i=0;i<metadata.length;i++ )
            {
                if ( metadata[i]!=null )
                {
                    String bson = conn.getFromDb( Database.METADATA, metadata[i] );
                    JSONObject jDoc = (JSONObject)JSONValue.parse( bson );
                    Set<String> keys = jDoc.keySet();
                    Iterator<String> iter = keys.iterator();
                    Document doc = null;
                    String language = getLanguage( metadata[i] );
                    IndexWriter writer = map.get(language);
                    while ( iter.hasNext() )
                    {
                        String key = iter.next();
                        if ( metadataKeys.contains(key) )
                        {
                            if ( doc == null )
                            {
                                doc = new Document();
                                Field docid = new StringField("docid", 
                                    (String)jDoc.get(JSONKeys.DOCID), 
                                    Field.Store.YES);
                                doc.add( docid );
                            }
                            StringReader sr = new StringReader( (String)jDoc.get(key) );
                            doc.add( new TextField(key, sr) );
                        }
                    }
                    if ( doc != null )
                        writer.addDocument(doc);
                }
                incDone();
            }
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Index all the annotations
     * @param conn the database connection
     * @param map map of languages to index-writers
     * @throws SearchException 
     */
    private void indexAnnotations( Connection conn, 
        HashMap<String,IndexWriter> map ) throws SearchException
    {
        try
        {
            for ( int i=0;i<annotations.length;i++ )
            {
                if ( annotations[i] != null )
                {
                    String language = getLanguage( annotations[i] );
                    IndexWriter writer = map.get(language);
                    String bson = conn.getFromDb( Database.ANNOTATIONS, 
                        annotations[i] );
                    JSONObject jDoc = (JSONObject)JSONValue.parse( bson );
                    Document doc = new Document();
                    Field docid = new StringField("docid", 
                        (String)jDoc.get(JSONKeys.DOCID), 
                        Field.Store.YES);
                    // we can retrieve everything about the annotation from this
                    doc.add( docid );
                    StringReader sr = new StringReader( 
                        (String)jDoc.get(JSONKeys.BODY) );
                    doc.add( new TextField(JSONKeys.CONTENT, sr) );
                    writer.addDocument(doc);
                }
                incDone();
            }
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
}