/*
 *  NMerge is Copyright 2015 Desmond Schmidt
 * 
 *  This file is part of NMerge. NMerge is a Java library for merging 
 *  multiple versions into multi-version documents (MVDs), and for 
 *  reading, searching and comparing them.
 *
 *  NMerge is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  NMerge is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package search.index;

import edu.luc.nmerge.exception.MVDException;
import java.util.BitSet;
/**
 * Based on KMPSearchState we navigate an MVD reading words
 * @author desmond
 */
public class WordSearchState
{
    WordSearchState following;
    BitSet v;
    int index;
    char[] word;
    int state;
    int mvdPos;
    boolean hasHyphen;
    WordFinder parent;
    static final int MAX_WORD_LEN = 128;
	
    /**
	 * Initialisation is easy.
	 * @param v the current versions of the state
	 */
	public WordSearchState( BitSet v, WordFinder parent )
	{
		this.v = v;
        this.word = new char[MAX_WORD_LEN];
        this.parent = parent;
	}
    /**
	 *	Constructor for cloning this object - useful for split. 
	 *	Leave the versions empty
	 *	@param ss the SearchState object to clone
	 */
	private WordSearchState( WordSearchState ss, BitSet bs )
	{
		this.v = new BitSet();
		this.v.or( bs );	
		this.index = ss.index;
        this.word = new char[MAX_WORD_LEN];
        this.state = ss.state;
        this.mvdPos = ss.mvdPos;
        this.parent = ss.parent;
        this.hasHyphen = ss.hasHyphen;
        if ( this.index > 0 )   
        {
            for ( int i=0;i<ss.index;i++ )
                this.word[i] = ss.word[i];
        }
	}
    /**
	 *	Split off a clone of ourselves intersecting with bs as its set of 
	 * 	versions. Should only be called after this.v.intersects(bs) has  
	 *  returned true.
	 *	@param bs the set which must intersect with our versions.
	 *	@return a clone of everything we stand for.
	 */
	WordSearchState split( BitSet bs )
	{
		BitSet newBs = new BitSet();
		for (int i=bs.nextSetBit(0); i>=0; i=bs.nextSetBit(i+1))
		{
			if ( v.nextSetBit(i)==i )
			{
				// move each bit in v & bs to newBs
				v.clear( i );
				newBs.set( i );
			}
		}
		return new WordSearchState( this, newBs );
	}
	/**
	 *	Concatenate a list of SearchState objects to the end of our list.
	 *	@param list a list of SearchState objects
	 */
	void append( WordSearchState list )
	{
		WordSearchState temp = this;
		while ( temp.following != null )
			temp = temp.following;
		temp.following = list;
	}
	/**
	 *	Remove a SearchState object from the list of which we are a part.
	 *	The object must be in the list FROM the point at which we are at 
	 *	(because we are not doubly-linked).
	 *	@param item the list item to remove
	 *	@return the list with the item removed (may be null)
	 *	@throws MVDException
	 */
	WordSearchState remove( WordSearchState item ) throws MVDException
	{
		WordSearchState previous,list,temp;
		previous = temp = list = this;
		while ( temp != null && temp != item )
		{
			previous = temp;
			temp = temp.following;
		}
		if ( previous == temp )	// it matched immediately
		{
			list = temp.following;	// could be null!
			temp.following = null;
		}
		else if ( temp == null )	// it didn't find it!
			throw new MVDException("List item not found");
		else					// temp in the middle of the list
		{
			previous.following = temp.following;
			temp.following = null;
		}
		return list;
	}
    /**
     * Store a word in the map
     * @return true if the word had a new location
     */
    boolean storeWord()
    {
        boolean res = false;
        String w = new String( this.word, 0, this.index );
        // force lowercase
        w = w.toLowerCase();
        if ( this.hasHyphen )
        {
            // if the word is already in the dictionary in  
            // hyphented form then it must be right thus
            if ( !parent.map.containsKey(w) 
                && !parent.hyphenator.wantsHyphen(w) )
                w = w.replaceAll("-","");
        }
        if ( !parent.stopwords.contains(w) )
        {
            Location loc = new Location( this.mvdPos, parent.docId );
            Locations locs = parent.map.get(w);
            if ( locs == null )
            {
                locs = new Locations();
                parent.map.put( w, locs );
            }
            if ( !locs.contains(loc) )
            {
                try
                {
                    locs.add( loc );
                }
                catch ( Exception e )
                {
                    e.printStackTrace(System.out);
                }
                res = true;
            }
        }
        this.index = 0;
        this.state = 0;
        this.hasHyphen = false;
        return res;
    }
    /**
     * Are we empty of text and ready to die?
     * @return true if we can be removed else false
     */
    boolean isEmpty()
    {
        return this.index==0;
    }
    boolean equals( WordSearchState other )
    {
        if ( this.index == other.index )
        {
            for ( int i=0;i<this.index;i++ )
            {
                if ( this.word[i] != other.word[i] )
                    return false;
            }
            return this.mvdPos==other.mvdPos;
        }
        else
            return false;
    }
    /**
     * Receive the incoming character and change state
     * @param token the incoming token
     * @return true if a word-location was added to the index
     */
    boolean update( char token )
    {
        boolean res = false;
        switch ( state )
        {
            case 0: // looking for first char
                if ( Character.isLetter(token) )
                {
                    this.word[this.index++] = token;
                    this.state = 1;
                    this.mvdPos = this.parent.mvdPosition;
                }
                break;
            case 1: // seen at least one letter
                if ( token == '-' )
                    state = 2;
                else if ( token == '\'' )
                    state = 3;
                else if ( token == '’' )
                    state = 4;
                else if ( !Character.isLetter(token) )
                    res = storeWord();
                else 
                    this.word[this.index++] = token;
                break;
            case 2: // seen hyphen
                if ( Character.isWhitespace(token) )
                    state = 5;
                else if ( Character.isLetter(token) )
                {
                    this.word[this.index++] = '-';
                    this.word[this.index++] = token;
                    state = 1;
                }
                else
                    storeWord();
                break;
            case 3: // seen a single straight apostrophe
                if ( Character.isLetter(token) )
                {
                    this.word[this.index++] = '\'';
                    this.word[this.index++] = token;
                    state = 1;
                }
                else
                    storeWord();
                break;
            case 4: // seen a single curly apostrophe
                if ( Character.isLetter(token) )
                {
                    this.word[this.index++] = '’';
                    this.word[this.index++] = token;
                    state = 1;
                }
                else
                    storeWord();
                break;
            case 5: // seen letter, hyphen followed by whitespace
                if ( Character.isLetter(token) )
                {
                    this.word[this.index++] = '-';
                    this.word[this.index++] = token;
                    this.hasHyphen = true;
                    state = 1;
                }
                else if ( !Character.isWhitespace(token) )
                    storeWord();
                break;
        }
        return res;
    }    
    /**
	 *	Combine the versions of the given search state with ours.
	 *	@param s the search state object to merge with this one
	 */
	void merge( WordSearchState s )
	{
		v.or( s.v );
	}
}