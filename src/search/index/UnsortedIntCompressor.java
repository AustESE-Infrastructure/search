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
import java.nio.ByteBuffer;
import java.util.Random;

/**
 * Compress/decompress an array of one to four-byte ints
 * @author desmond
 */
public class UnsortedIntCompressor 
{
    /**
     * Find out how many bits we'll need to represent this array of ints
     * @param maxValue the maximum positive value
     * @param minValue the minimum negative value or 0
     * @return the number of bits needed, rounded to nearest 8, max 32
     */
    private static int bitsNeeded( int maxValue, int minValue )
    {
        int minBits = 0;
        int maxBits = 32;
        if ( minValue == 0 )
            minBits = 0;
        else if ( minValue >= -128 )
            minBits = 8;
        else if ( minValue >= -32768 )
            minBits = 16;
        else if ( minValue >= -8388608 )
            minBits = 24;
        else
            minBits = 32;
        if ( maxValue <= 127 )
            maxBits = 8;
        else if ( maxValue < 32767 )
            maxBits = 16;
        else if ( maxValue < 8388607 )
            maxBits = 24;
        else
            maxBits = 32;
        return (maxBits>=minBits)?maxBits:minBits;
    }
    /**
     * Compress an array of ints
     * @param array an array of unsorted ints to compress
     * @return the compressed array
     */
    public static int[] compress( int[] array ) 
    {
        int maxValue = 0;
        int minValue = 0;
        for ( int i=0;i<array.length;i++ )
        {
            int value = array[i];
            if ( value > maxValue )
                maxValue = array[i];
            else if ( value < minValue )
                minValue = value;
        }
        int numBits = bitsNeeded( maxValue, minValue );
        // the first entry will store the number of bits/8
        int numBytes = numBits/8;
        // plus first entry
        int len = array.length*numBytes + 4;
        // 3 is the maximum overrun
        ByteBuffer buf = ByteBuffer.allocate(len+3);
        buf.putInt(numBytes);
        for ( int i=0,j=0;i<array.length;i++ )
        {
            int value = array[i];
            if ( numBits>24 )
                buf.put((byte)(value>>>24));
            if ( numBits > 16 )
                buf.put((byte)(value>>>16));
            if ( numBits > 8 )
                buf.put((byte)(value>>>8));
            buf.put((byte)value);
        }
        int resLen = (array.length*numBytes)/4 + 1;
        if ( (array.length*numBytes)%4 != 0 )
            resLen++;
        int[] res = new int[resLen];
        res[0] = numBytes;
        for ( int i=1;i<resLen;i++ )
        {
            res[i]= buf.getInt(i*4);
        }
        return res;
    }
    /**
     * Turn a reduced-byte integer array into a 4-byte integer array
     * @param array the 3-byte int array previously compressed
     * @return an array of ints the same size as before it was compressed
     */
    public static int[] decompress( int[] array ) throws NumberFormatException
    {
        int numBytes = array[0];
        if ( numBytes < 1 || numBytes > 4 )
            throw new NumberFormatException("numBytes must be between 1 and 4");
        int len = ((array.length-1)*4)/numBytes;
        int rem = ((array.length-1)*4)%numBytes;
        if ( rem == 0 )
        {
            int mask = 0xFF<<((numBytes-1)*8);
            if ( (array[array.length-1]&mask) == 0 )
                len--;
        }
        ByteBuffer buf = ByteBuffer.allocate((array.length+1)*4);
        for ( int i=1;i<array.length;i++ )
            buf.putInt(array[i]);
        int[] res = new int[len];
        for ( int i=0;i<len;i++ )
        {
            res[i]= buf.getInt(i*numBytes);
            res[i] = res[i] >> 8*(4-numBytes);
        }
        return res;
    }
    public static void main( String[] args )
    {
        Random r = new Random();
        int[] uncompressed = new int[100];
        int limit = (int)Math.pow(2,23);
        for ( int i=0;i<100;i++ )
        {
            uncompressed[i] = r.nextInt(limit);
        }
        int[] compressed = null;
        try
        {
            compressed = UnsortedIntCompressor.compress(uncompressed);
            System.out.println("Length of compressed="+compressed.length);
            int[] decompressed = UnsortedIntCompressor.decompress(compressed);
            if ( decompressed.length != 100 )
                System.out.println("decompression failed. length="+decompressed.length);
            for ( int i=0;i<100;i++ )
            {
                if ( decompressed[i] != uncompressed[i] )
                    System.out.println("uncompressed ("+uncompressed[i]
                        +") not equal to decompressed ("+decompressed[i]+")");
            }
        }
        catch ( NumberFormatException nfe )
        {
            nfe.printStackTrace(System.out);
        }
        
    }
}
