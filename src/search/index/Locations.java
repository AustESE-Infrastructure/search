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
import edu.luc.nmerge.mvd.Base64;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import search.exception.IndexException;
import me.lemire.integercompression.differential.*;
import java.util.Random;
import search.exception.SearchException;
/**
 * A compressed index of documents and word-offsets
 * @author desmond
 */
public class Locations implements Serializable 
{
    static final long serialVersionUID = 5983741889767318458L;
    /** the FastPFor compressed docid list for this word */
    int[] compressedDocids; 
    /** the UnsortedIntCompressor list of word-locations in docis */
    int[] compressedOffsets;
    /** locs used to look up words but constructed lazily when needed */
    transient ArrayList<Location> locs;
    Locations()
    {
    }
    /**
     * Get the current size of this locations object
     * @return an int
     */
    int size()
    {
        return (locs==null)?0:locs.size();
    }
    /**
     * Build a location from the two indices
     * @param index the position in the index
     * @return a Location object
     */
    Location location( int index )
    {
        return locs.get(index);
    }
    /**
     * Do we contain the given location? Only works once loaded.
     * @param loc the loc object
     * @return true if it is there
     */
    boolean contains( Location loc )
    {
        if ( locs != null )
        {
            // ordinary binary search
            int top = 0;
            int bottom = locs.size()-1;
            while ( top <= bottom )
            {
                int mid = (top+bottom)/2;
                Location midLoc = locs.get(mid);
                int res =  loc.compareTo(midLoc);
                if ( res<0 )
                    bottom = mid-1;
                else if ( res > 0 )
                    top = mid+1;
                else
                    return true;
            }
        }
        return false;
    }
    /**
     * Add a location to the index (only useful when indexing)
     * @param loc the new location
     * @throws IndexException 
     */
    void add( Location loc ) throws IndexException
    {
        if ( locs == null )
            locs = new ArrayList<Location>();
        locs.add(loc);
    }
    /**
     * Read in as a Locations object
     */
    private void readObject( ObjectInputStream ois) 
        throws ClassNotFoundException, IOException 
    {
        ois.defaultReadObject();
        IntegratedIntCompressor iic = new IntegratedIntCompressor();
        locs = new ArrayList<Location>();
        if ( compressedDocids.length>1 )
        {
            int[] docids = iic.uncompress(compressedDocids);
            int[] offsets = UnsortedIntCompressor.decompress(compressedOffsets);
            // locations should alreayd be sorted
            for ( int i=0;i<docids.length;i++ )
            {
                Location loc = new Location(docids[i],offsets[i]);
                locs.add(loc);
            }
        }
        else
        {
            // common case - don't use compression
            locs.add(new Location(compressedDocids[0],compressedOffsets[0]));
        }
    }
    /**
    * Write out the compressed Locations index
    */
    private void writeObject( ObjectOutputStream ous ) 
        throws IOException, NumberFormatException
    {
        Location[] array = new Location[locs.size()];
        if ( locs.size()==1 )
        {
            // common case
            compressedDocids = new int[1];
            compressedOffsets = new int[1];
            compressedDocids[0] = locs.get(0).docId;
            compressedOffsets[0] = locs.get(0).pos;
        }
        else
        {
            locs.toArray(array);
            Arrays.sort(array); 
            int[] docids = new int[array.length];
            int[] offsets = new int[array.length];
            for ( int i=0;i<array.length;i++ )
            {
                Location loc = array[i];
                docids[i] = loc.docId;
                offsets[i] = loc.pos;
            }
            IntegratedIntCompressor iic = new IntegratedIntCompressor();
            compressedDocids = iic.compress(docids);
            compressedOffsets = UnsortedIntCompressor.compress(offsets);
        }
        ous.defaultWriteObject();
    }
    /**
     * Write the object to a string
     * @throws SearchException 
     */
    public String save() throws SearchException
    {
        try
        {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream( bos );
            out.writeObject(this);
            out.close();
            byte[] data = bos.toByteArray();
            String b64Data = Base64.encodeBytes( data );
            return b64Data;
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Load the object from a serialised string
     */
    public static Locations load( String str ) throws SearchException
    {
        try
        {
            byte[] data = Base64.decode( str );
            ByteArrayInputStream bis = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream( bis );
            Locations locs = (Locations)ois.readObject();
            ois.close();
            return locs;
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    public static void main(String[] args )
    {
        // 1. generate a list of 100 random numbers from 1-100
        // 2.generate a list of 100 random numbers from 1-1000
        Random r = new Random();
        int[] arr1 = new int[100];
        int[] arr2 = new int[100];
        for ( int i=0;i<100;i++ )
        {
            arr1[i] = r.nextInt(100);
            arr2[i] = r.nextInt(1000);
        }
        Locations locs = new Locations();
        try
        {
            for ( int i=0;i<100;i++ )
            {
                Location loc = new Location(arr1[i],arr2[i]);
                locs.add(loc);
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace(System.out);
        }
        // test for serialisation
        try
        {
            String str = locs.save();
            Locations locs2 = Locations.load( str );
            boolean error = false;
            for ( int i=0;i<100;i++ )
            {
                Location loc = new Location(arr1[i],arr2[i]);
                if ( !locs2.contains(loc) )
                {
                    error = true;
                    System.out.println("Location "+loc+" not found!");
                }
            }
            if ( !error )
                System.out.println("No errors detected!");
        }
        catch ( Exception e )
        {
            e.printStackTrace(System.out);
        }
    }
}