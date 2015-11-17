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
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
/**
 * Location in a document where a word occurs
 * @author desmond
 */
public class Location implements Serializable {
    static final long serialVersionUID = 5983741889767318458L;
    public int pos;
    /** the document id number in the index */
    public int docId;
    public Location( int pos, int docId )
    {
        this.docId = docId;
        this.pos = pos;
    }
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
     * Always treat de-serialization as a full-blown constructor, by
     * validating the final state of the de-serialized object.
     */
    private void readObject( ObjectInputStream aInputStream) 
        throws ClassNotFoundException, IOException 
    {
        aInputStream.defaultReadObject();
    }
    /**
    * This is the default implementation of writeObject.
    * Customise if necessary.
    */
    private void writeObject( ObjectOutputStream aOutputStream) 
        throws IOException 
    {
        aOutputStream.defaultWriteObject();
    }
}
