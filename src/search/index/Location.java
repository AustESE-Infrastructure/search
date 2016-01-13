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
/**
 * Location in a document where a word occurs
 * @author desmond
 */
public class Location implements Comparable<Location> {
    public int pos;
    /** the document id number in the index */
    public int docId;
    /**
     * Create a location
     * @param docId the document identifier
     * @param pos the position
     */
    public Location( int docId, int pos )
    {
        this.docId = docId;
        this.pos = pos;
    }
    /**
     * Needed for sorting
     * @param other the other location to compare to
     * @return true if they are the same
     */
    public boolean equals( Object other )
    {
        if ( other instanceof Location )
        {
            Location l = (Location)other;
            return l.pos == this.pos && l.docId == this.docId;
        }
        else
            return false;
    }
    /**
     * To help debugging
     * @return a string representation of this location
     */
    public String toString()
    {
        return docId+","+pos;
    }
    /**
     * Allow sorting of locations by comparing one location to another
     * @param other the other location
     * @return 0 if they are the same, -1 if we are less than other, else 1
     */
    public int compareTo( Location other )
    {
        if ( this.docId < other.docId
                || (this.docId==other.docId&&this.pos<other.pos) )
            return -1;
        else if ( this.docId > other.docId
                || (this.docId==other.docId&&this.pos>other.pos) )
            return 1;
        else
            return 0;
    }
}
