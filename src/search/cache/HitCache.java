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
package search.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.security.MessageDigest;
import search.exception.SearchException;

/**
 * A cache to stored already searched for hits
 * @author desmond
 */
public class HitCache 
{
    public static String getKey( String str )
    {
        try
        {
            MessageDigest md = MessageDigest.getInstance("MD5");
            return new String(md.digest(str.getBytes()));
        }
        catch ( Exception e )
        {
            // can't happen
            System.out.println(e.getMessage());
            return "";
        }
    }
    /**
     * Store a json representation of hits
     * @param json the json as an array
     * @param key the key to store it under
     * @throws SearchException 
     */
    public static void store( String json, String key ) throws SearchException
    {
        String path = getPath(key);
        File file = new File(path);
        try
        {
            if ( file.exists() )
                file.delete();
            file.createNewFile();
            FileOutputStream fos = new FileOutputStream(file);
            fos.write( json.getBytes("UTF-8") );
            fos.close();
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
    /**
     * Get the temporary path to the cached file
     * @param key the cache file's key
     * @return the path to the key
     */
    private static String getPath( String key )
    {
        return System.getProperty("java.io.tmpdir")+File.pathSeparator
            +"search"+File.pathSeparator+key;
    }
    /**
     * Test if a file is already in the cache
     * @param key the key to test for
     * @return true if it is there else false
     */
    public static boolean exists( String key ) 
    {
        String path = getPath( key );
        File file = new File(path);
        return file.exists();
    }
    /**
     * Get a cached file form store
     * @param key the key to use
     * @return the stored json hist
     * @throws SearchException 
     */
    public static String retrieve( String key ) throws SearchException
    {
        String path = getPath( key );
        File file = new File(path);
        try
        {
            FileInputStream fis = new FileInputStream(file);
            int len = (int)file.length();
            byte[] data = new byte[len];
            fis.read( data );
            fis.close();
            return new String( data, "UTF-8");
        }
        catch ( Exception e )
        {
            throw new SearchException(e);
        }
    }
}
