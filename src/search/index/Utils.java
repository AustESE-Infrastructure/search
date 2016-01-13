/*
 * This file is part of Search.
 *
 *  Search is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  earch is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Search.  If not, see <http://www.gnu.org/licenses/>.
 *  (c) copyright Desmond Schmidt 2015
 */
package search.index;

import java.util.HashMap;
import java.util.HashSet;
import search.exception.*;

/**
 * Some utilities to help search and query
 * @author desmond
 */
public class Utils {
    static String plainQuotes = "'\"";
    static String leadingQuotes = "‛“‘";
    static String trailingQuotes = "’”";
    static String[] italianStopWords = {"﻿a", "adesso", "ai", "al", "alla", 
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
        "vai", "voi", "volte", "vostro" };
    static String[] englishStopWords = { "a", "about", "above", "after", 
        "again", "against", "all", "am", "an", "and", "any", "are", "aren't", 
        "as", "at", "be", "because", "been", "before", "being", "below", 
        "between", "both", "but", "by", "can't", "cannot", "could", "couldn't", 
        "did", "didn't", "do", "does", "doesn't", "doing", "don't", "down", 
        "during", "each", "few", "for", "from", "further", "had", "hadn't", 
        "has", "hasn't", "have", "haven't", "having", "he", "he'd", "he'll", 
        "he's", "her", "here", "here's", "hers", "herself", "him", "himself", 
        "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", 
        "into", "is", "isn't", "it", "it's", "its", "itself", "let's", "me", 
        "more", "most", "mustn't", "my", "myself", "no", "nor", "not", "of", 
        "off", "on", "once", "only", "or", "other", "ought", "our", "ours", 
        "ourselves", "out", "over", "own", "same", "shan't", "she", "she'd", 
        "she'll", "she's", "should", "shouldn't", "so", "some", "such", "than", 
        "that", "that's", "the", "their", "theirs", "them", "themselves", 
        "then", "there", "there's", "these", "they", "they'd", "they'll", 
        "they're", "they've", "this", "those", "through", "to", "too", "under", 
        "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll", "we're", 
        "we've", "were", "weren't", "what", "what's", "when", "when's", 
        "where", "where's", "which", "while", "who", "who's", "whom", "why", 
        "why's", "with", "won't", "would", "wouldn't", "you", "you'd", 
        "you'll", "you're", "you've", "your", "yours", "yourself", 
        "yourselves" };
    static HashMap<String,String> languages;
    static HashMap<String,HashSet<String>>  stopwords;
    static
    {
        languages = new HashMap<String,String>();
        stopwords = new HashMap<String,HashSet<String>>();
        languages.put("english","en");
        languages.put("italian","it");
        HashSet enStopWords = new HashSet<String>();
        for ( int i=0;i<englishStopWords.length;i++ )
            enStopWords.add( englishStopWords[i]);
        HashSet itStopWords = new HashSet<String>();
        for ( int i=0;i<italianStopWords.length;i++ )
            enStopWords.add( italianStopWords[i]);
        stopwords.put("en",enStopWords);
        stopwords.put("it",itStopWords);
    }
    public static String strip( String str )
    {
        int start = 0;
        int end = str.length()-1;
        for ( int i=0;i<str.length();i++ )
        {
            if ( !Character.isWhitespace(str.charAt(i)) )
            {
                start = i;
                break;
            }
        }
        for ( int i=str.length()-1;i>=0;i-- )
        {
            if ( !Character.isWhitespace(str.charAt(i)) )
            {
                end = i;
                break;
            }
        }
        return str.substring(start,end+1);
    }
    /**
     * Extract the ISO language code from the language in the docid
     * @param projid the project (doc) id
     * @return a 2-char language code
     */
    public static String languageFromProjid( String projid )
    {
        String[] parts = projid.split("/");
        if ( languages.containsKey(parts[0]) )
            return languages.get(parts[0]);
        else
            return "en";
    }
    public static boolean isQuoteChar( char token )
    {
        return plainQuotes.indexOf(token)!=-1
            ||leadingQuotes.indexOf(token)!=-1
            ||trailingQuotes.indexOf(token)!=-1;
    }
    public static String stripQuotes( String str )
    {
        int start = 0;
        int end = str.length()-1;
        while ( start < end )
        {
            if ( !isQuoteChar(str.charAt(start)) )
                break;
            else
                start++;
        }
        while ( end >=0 )
        {
            if ( !isQuoteChar(str.charAt(end)) )
                break;
            else
                end--;
        }
        return str.substring(start,end+1);
    }
    /**
     * Get the stopwords for a given language
     * @param lang the ISO 2-letter lang code
     * @return a hashset of stopwords
     */
    public static HashSet<String> getStopwords( String lang ) throws SearchException
    {
        if ( !stopwords.containsKey(lang) )
            throw new SearchException("Stopwords for language "+lang+" not found");
        return stopwords.get(lang);
    }
    public static boolean isLeadingQuote( char token )
    {
        return plainQuotes.indexOf(token)!=-1||leadingQuotes.indexOf(token)!=-1;
    }
    public static boolean isTrailingQuote( char token )
    {
        return plainQuotes.indexOf(token)!=-1||trailingQuotes.indexOf(token)!=-1;
    }
    public static boolean quotesMatch( char first, char last )
    {
        if ( first == '"' && last == '"' )
            return true;
        if ( first == '\'' && last == '\'' )
            return true;
        if ( first == '“' && last == '”' )
            return true;
        if ( first == '‘' && last == '’' )
            return true;
        return false;
    }
    public static boolean isQuoted( String str )
    {
        String stripped = strip(str);
        char first = stripped.charAt(0);
        char last = stripped.charAt(str.length()-1);
        return isLeadingQuote(first) && isTrailingQuote(last) 
            && quotesMatch(first,last);
    }
    public static boolean isStopWord( String word, String lang )
    {
        if ( stopwords.containsKey(lang) )
        {
            HashSet set = stopwords.get(lang);
            return set.contains(word);
        }
        else
            return false;
    }
}
