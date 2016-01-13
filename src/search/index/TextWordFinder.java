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
 *  (c) copyright Desmond Schmidt 2016
 */
package search.index;
import java.util.HashMap;
import java.util.StringTokenizer;
import java.util.HashSet;
import search.exception.IndexException;
import calliope.AeseSpeller;
import java.io.FileInputStream;
import java.io.File;


/**
 * Find words in a straight text file
 * @author desmond
 */
public class TextWordFinder 
{
    String text;
    HashSet<String> sw;
    HashMap<String,Locations> map;
    String lang;
    int docId;
    int nWords;
    AeseSpeller speller;
    static HashMap<String,AeseSpeller> spellers;
    static HashSet<Character> roman;
    static {
        roman = new HashSet<Character>();
        roman.add('i');
        roman.add('v');
        roman.add('l');
        roman.add('d');
        roman.add('c');
        roman.add('m');
        spellers = new HashMap<String,AeseSpeller>();
    }
    /**
     * Is this token a page number?
     * @param text the text
     * @return true if it is an arabic or roman page number
     */
    boolean isPageNumber( String text )
    {
        int state = 0;
        int i;
        for ( i=0;i<text.length();i++ )
        {
            char token = text.charAt(i);
            switch ( state )
            {
                case 0: // looking for roman or arabic
                    if ( Character.isDigit(token) )
                        state = 1;
                    else if ( roman.contains(Character.toLowerCase(token)) )
                        state = 2;
                    else
                        state = -1;
                    break;
                case 1: // looking for arabic number
                    if ( Character.isDigit(token) )
                        continue;
                    else if ( Character.isLowerCase(token) )
                        state = 3;
                    else
                        state = -1;
                    break;
                case 2: // looking for a Roman number
                    if ( roman.contains(Character.toLowerCase(token)) )
                        continue;
                    else
                        state = -1;
                    break;
                case 3: // reading lowercase after digits
                    if ( Character.isLowerCase(token) )
                        continue;
                    else
                        state = -1;
                    break;
            }
            if ( state < 0 )
                break;
        }
        return i==text.length();
    }
    /**
     * Constructor for debugging
     */
    TextWordFinder()
    {
    }
    /**
     * Regular constructor
     * @param text the text to parse
     * @param map the word-map
     * @param sw stopwords
     * @param lang the iso-2-letter language it is in
     * @param docId the document id
     */
    TextWordFinder( String text, HashMap<String,Locations> map, 
        HashSet<String> sw, String lang, int docId ) throws IndexException
    {
        this.text = text;
        this.map = map;
        this.sw = sw;
        this.lang = lang;
        this.docId = docId;
        if ( !spellers.containsKey(lang) )
        {
            try
            {
                this.speller = new AeseSpeller(lang);
            }
            catch ( Exception e )
            {
                throw new IndexException("No speller found for "+lang);
            }
        }
    }
    /**
     * Strip punctuation off the front and end of a word
     * @param token the word to strip
     * @return a word or the empty string
     */
    String stripPunctuation( String token )
    {
        while ( token.length()>0 
            && !Character.isLetter(token.charAt(0)) )
            token = token.substring(1);
        while ( token.length()>0 
            && !Character.isLetter(token.charAt(token.length()-1)) )
            token = token.substring(0,token.length()-1);
        return token;
    }
    /**
     * Is this token a word? Don't look it up in dictionary.
    */
    boolean isWord( String word )
    {
        int state = 0;
        int i;
        for ( i=0;i<word.length();i++ )
        {
            char c = word.charAt(i);
            switch ( state )
            {
                case 0:
                    if ( Character.isUpperCase(c) )
                        state = 1;
                    else if ( Character.isLowerCase(c) )
                        state = 2;
                    else
                        state = -1;
                    break;
                case 1: // first letter uppercase
                    if ( Character.isUpperCase(c) )
                        state = 3;
                    else if ( Character.isLowerCase(c) )
                        state = 2;
                    else
                        state = -1;
                    break;
                case 2: // rest must be lowercase
                    if ( Character.isLowerCase(c) )
                        continue;
                    else if ( c=='\''||c=='’' )
                        continue;
                    else
                        state = -1;
                    break;
                case 3: // rest must be uppercase
                    if ( Character.isUpperCase(c) )
                        continue;
                    else
                        state = -1;
                    break;
            }
            if ( state == -1 )
                break;
        }
        return i==word.length();
    }
    /**
     * Store the word after making some final checks
     * @param word the word to store
     * @param pos the position in the input where it occurred
     * @throws IndexException 
     */
    void storeWord( String word, int pos ) throws IndexException
    {
        String lower = word.toLowerCase();
        if ( !sw.contains(lower) && lower.length()>0 )
        {
            Locations locs = map.get(lower);
            if ( locs != null )
                locs.add(new Location(docId,pos));
            else
            {
                locs = new Locations();
                locs.add( new Location(docId,pos) );
                map.put( lower, locs );
            }
            nWords++;
            // debug
            //System.out.print(lower+" ");
        }
    }
    /**
     * Is the token just whitespace?
     * @param token a token to test
     * @return 
     */
    boolean isWhitespace( String token )
    {
        return token.length()==1&&Character.isWhitespace(token.charAt(0));
    }
    /**
     * Resolve the ambiguity of a word followed by a hyphen and another word
     * @param first the first word in the dictionary
     * @param second the second word maybe in the dictionary
     * @param pos the position of the first word
     * @throws IndexException 
     */
    void resolveHyphenated( String first, String second, int pos ) 
        throws IndexException
    {
        String hyphenated = first+"-"+second;
        String composite = first+second;
        if ( speller.hasWord(second,lang) )
        {
            if ( map.containsKey(hyphenated) )
                storeWord(hyphenated,pos);
            else if ( speller.hasWord(composite,lang) )
                storeWord(composite,pos);
            else
                storeWord(hyphenated,pos);
        }
        else
            storeWord(composite,pos);
    }                            
    /**
     * Parse the text file looking for indexable words
     * @return number of word-locations found
     * @throws IndexException 
     */
    int find() throws IndexException
    {
        try
        {
            StringTokenizer st = new StringTokenizer( text," \t\r\n-", true );
            int state = 0;
            int pos = 0;
            int lastPos = 0;
            String lastWord = "";
            String pageNo = "";
            while ( st.hasMoreTokens() )
            {
                String token = st.nextToken();
                switch ( state )
                {
                    case 0: // looking for word
                        if ( isWhitespace(token) )
                            continue;
                        else if ( isPageNumber(token) )
                        {
                            if ( isWord(token) )
                            {
                                state = 1;
                                lastWord = token;
                                lastPos = pos;
                            }
                            // ignore arabic numbers anywhere
                        }
                        else // not a page-number or whitespace
                        {
                            lastWord = stripPunctuation(token);
                            lastPos = pos;
                            if ( isWord(lastWord) )
                                state = 2;
                            else
                                lastWord = "";
                        }
                        break;
                    case 1: // initial roman page-number
                        if ( token.equals("\n") || token.equals("\r") )
                        {
                            state = 0;
                            break;
                        }
                        // fall through 
                    case 2: // word
                        if ( isWhitespace(token) )
                        {
                            storeWord(lastWord,lastPos);
                            lastWord = "";
                            state = 0;
                        }
                        else if ( token.equals("-") )
                        {
                            // look up last word in dictionary
                            if ( speller.hasWord(lastWord,lang) )
                                state = 3;
                            else
                                state = 4;
                        }
                        break;
                    case 3: // dict-word, -
                        if ( token.equals("\r") )
                            state = 5;
                        else if ( token.equals("\n") )
                            state = 6;
                        else 
                        {
                            String newWord = stripPunctuation(token);
                            if ( isWord(newWord) )
                            {
                                lastWord = lastWord+"-"+newWord;
                                state = 2;
                            }
                            else
                            {
                                storeWord(lastWord,lastPos);
                                state = 0;
                            }
                        }
                        break;
                    case 4: // not-dict-word, hyphen
                        if ( token.equals("\r") )
                            state = 7;
                        else if ( token.equals("\n") )
                            state = 8;
                        else 
                        {
                            String newWord = stripPunctuation(token);
                            if ( isWord(newWord) )
                            {
                                // accept leading non-dict word,hyphen,dict-word
                                lastWord = lastWord+"-"+newWord;
                                state = 2;
                            }
                            else
                            {
                                storeWord(lastWord,lastPos);
                                state = 0;
                            }
                        }
                        break;
                    case 5: // dict-word, hyphen, \r
                        if ( token.equals("\n") )
                        {
                            state = 6;
                            break;
                        }
                        // fall through
                    case 6: // dict-word, -, \n|\r\n|\r
                        if ( isWhitespace(token) )
                        {
                            if ( lastWord.length()>0 )
                            {
                                storeWord(lastWord,pos);
                                lastWord = "";
                            }
                            state = 0;
                        }
                        else
                        {
                            String newWord = stripPunctuation(token);
                            if ( isPageNumber(token) )
                            {
                                if ( isWord(token) )
                                {
                                    pageNo = token;
                                    state = 11;
                                }
                                else
                                    state = 3; 
                            }
                            else 
                            {
                                resolveHyphenated(lastWord,newWord,lastPos);
                                state = 0;
                                lastWord = "";
                            }
                        }
                        break;
                    case 7: //non-dict-word,-,\r
                        if ( token.equals("\n") )
                        {
                            state = 8;
                            break;
                        }
                        // fall-through
                    case 8:    // non-dict-word,-,\n|\r\n|\r
                        if ( isWhitespace(token) )
                        {
                            if ( lastWord.length()>0 )
                                storeWord(lastWord,lastPos);
                            lastWord = "";
                            state = 0;
                        }
                        else
                        {
                            if ( isPageNumber(token) )
                            {
                                if ( isWord(token) )
                                {
                                    pageNo = token;
                                    state = 9;
                                }
                                else
                                    state = 4;  
                            }
                            else 
                            {
                                String newWord = stripPunctuation(token);
                                String composite = lastWord+newWord;
                                storeWord(composite,lastPos);
                                state = 0;
                                lastWord = "";
                            }
                        }
                        break;
                    case 9: // non-dict-word,-,\n|\r\n|\r,roman-page-no
                        if ( token.equals("\r") )
                        {
                            state = 10;
                            break;
                        }
                        // fall through
                    case 10:// non-dict-word,-,\n|\r\n|\r,roman-page-no
                        if ( token.equals("\n") )
                        {
                            pageNo = "";
                            state = 7;
                        }
                        else if ( isWhitespace(token) )
                        {
                            storeWord(lastWord+pageNo,lastPos);
                            state = 0;
                            lastWord = pageNo = "";
                        }
                        else if ( token.equals("-") )
                        {
                            lastWord = lastWord+"-"+pageNo;
                            state = 4;
                            pageNo = "";
                        }
                        break;
                    case 11: // dict-word,-,\n|\r\n|\r,roman-page-no
                        if ( token.equals("\r") )
                        {
                            state = 12;
                            break;
                        }
                        // fall through
                    case 12:// dict-word,-,\n|\r\n|\r,roman-page-no
                        if ( token.equals("\n") )
                        {
                            pageNo = "";
                            state = 6;
                        }
                        else if ( isWhitespace(token) )
                        {
                            resolveHyphenated(lastWord,pageNo,lastPos);
                            lastWord = pageNo = "";
                            state = 0;
                        }
                        else if ( token.equals("-") )
                        {
                            lastWord = lastWord+"-"+pageNo;
                            state = 3;
                            pageNo = "";
                        }
                        break;
                }
                pos += token.length();
            }
            return nWords;
        }
        catch ( Exception e )
        {
            throw new IndexException( e );
        }
    }
    public static void main(String[] args )
    {
        File f = new File(args[0]);
        try
        {
            FileInputStream bis = new FileInputStream(f);
            byte[] data = new byte[(int)f.length()];
            bis.read(data);
            String text = new String(data,"UTF-8");
            HashSet<String> stopwords = new HashSet<String>();
            stopwords.add("the");
            stopwords.add("a");
            stopwords.add("is");
            HashMap<String,Locations> wordMap = new HashMap<String,Locations>();
            TextWordFinder twf = new TextWordFinder( text, wordMap, stopwords, "en", 0);
            twf.find();
        }
        catch ( Exception e )
        {
            e.printStackTrace(System.out);
        }
    }
}
