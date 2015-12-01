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
package search.format;

/**
 * Manage formatting of a span of hits in a match
 * @author desmond
 */
public class HitSpan 
{
    String[] terms;
    int[] positions;
    char[] data;
    static final int MAX_LEN = 100;
    /**
     * Create a hitspan
     * @param data the underlying data of the whole version
     * @param term the first term
     * @param start the start offset of the first term
     */
    HitSpan( char[] data, String term, int start )
    {
        this.data = data;
        this.terms = new String[1];
        this.terms[0] = term;
        this.positions = new int[1];
        this.positions[0] = start;
    }
    /**
     * Will another term fit into this span?
     * @param term the term to squeeze in
     * @param pos its position in data
     * @return true if it fits else false
     */
    boolean wants( String term, int pos )
    {
        int start = moveBack( this.positions[0], 1 );
        int end = moveForward( this.positions[this.positions.length-1], 1 );
        return pos >= this.positions[this.positions.length-1]
            +this.terms[this.terms.length-1].length() 
            && end-start < MAX_LEN
            && (pos+term.length())-start < MAX_LEN;
    }
    /**
     * Add a new span already accepted in principle
     * @param term the word of the term
     * @param pos its position in data
     */
    void add( String term, int pos )
    {
        int[] newPositions = new int[this.positions.length+1];
        System.arraycopy( this.positions, 0, newPositions, 0, 
            this.positions.length );
        newPositions[this.positions.length] = pos;
        this.positions = newPositions;
        String[] newTerms = new String[this.terms.length+1];
        System.arraycopy( this.terms, 0, newTerms, 0, this.terms.length );
        newTerms[this.terms.length] = term;
        this.terms = newTerms;
    }
    /**
     * Move forward a certain number of words
     * @param pos the start-position of the first match word
     * @param nWords the number of words to move forward
     * @return 
     */
    int moveForward( int pos, int nWords )
    {
        int state = 0;
        int count = 0;
        for ( int i=pos;i<data.length;i++ )
        {
            switch ( state )
            {
                case 0: // recognising text
                    if ( Character.isWhitespace(data[i]) )
                        state = 1;
                    break;
                case 1: // recognising whitespace
                    if ( !Character.isWhitespace(data[i]) )
                    {
                        if ( count < nWords )
                        {
                            count++;
                            state = 0;
                        }
                        else
                        {
                            state = 2;
                            pos = i;
                        }
                    }
                    break;
            }
            if ( state == 2 )
                break;
        }
        return pos;
    }
    /**
     * Move back a certain number of words
     * @param pos the start-position of the first match word
     * @param nWords the number of words to move back
     * @return 
     */
    int moveBack( int pos, int nWords )
    {
        int state = 0;
        int count = 0;
        int lastWordStart = pos;
        for ( int i=pos;i>=0;i-- )
        {
            switch ( state )
            {
                case 0: // recognising text
                    if ( Character.isWhitespace(data[i]) )
                        state = 1;
                    else
                        lastWordStart = i;
                    break;
                case 1: // recognising whitespace
                    if ( !Character.isWhitespace(data[i]) )
                    {
                        if ( count < nWords )
                        {
                            count++;
                            state = 0;
                        }
                        else
                        {
                            state = 2;
                            pos = lastWordStart;
                        }
                    }
                    break;
            }
            if ( state == 2 )
                break;
        }
        return pos;
    }
    /**
     * Get the length of the term by matching all characters of the term
     * @param term the term
     * @param the context to search in
     * @param actual the offset in context
     * @return the length which may be greater than term.length()
     */
    private int getTermLength( String term, String context, int actual )
    {
        int length = 0;
        int pos = 0;
        for ( int i=actual;i<context.length();i++ )
        {
            char token = context.charAt(i);
            if ( term.charAt(pos)==token )
                pos++;
            length++;
            if ( pos == term.length() )
                break;
        }
        return length;
    }
    /**
     * Convert this match-span to a string
     * @return a String
     */
    public String toString()
    {
        int start = this.positions[0];
        int end = this.positions[this.positions.length-1]
            +this.terms[this.terms.length-1].length();
        // this may overshoot a bit but not by much
        while ( end-start < MAX_LEN )
        {
            start = moveBack( start, 1 );
            end = moveForward( end, 1 );
        }
        String context = new String( data, start, end-start );
        int len = (end-start)+1;
        int prev = 0;
        StringBuilder sb = new StringBuilder();
        for ( int i=0;i<terms.length;i++ )
        {
            int actual = this.positions[i]-start;
            if ( actual < len )
            {
                sb.append( context.substring(prev,actual) );
                prev = actual+getTermLength(this.terms[i],context,actual);
                sb.append("<span class=\"match\">");
                sb.append(context.substring(actual,prev));
                sb.append("</span>");
            }
        }
        if ( prev < len )
            sb.append(context.substring(prev,len-1));
        sb.append(" ... ");
        StringBuilder cleaned = new StringBuilder();
        int state=0;
        for ( int i=0;i<sb.length();i++ )
        {
            char token = sb.charAt(i);
            switch (state)
            {
                case 0: 
                    if ( Character.isWhitespace(token) )
                    {
                        cleaned.append(" ");
                        state = 1;
                    }
                    else
                        cleaned.append(token);
                    break;
                case 1:
                    if ( !Character.isWhitespace(token) )
                    {
                        cleaned.append(token);
                        state = 0;
                    }
                    break;
            }
        }
        return cleaned.toString();
    }
}
