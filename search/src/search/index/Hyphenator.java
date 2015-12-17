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
 *  along with Search. If not, see <http://www.gnu.org/licenses/>.
 *  (c) copyright Desmond Schmidt 2015
 */
package search.index;
import calliope.AeseSpeller;
import calliope.core.constants.Database;
import calliope.core.constants.JSONKeys;
import search.exception.*;
import java.util.HashSet;
import calliope.core.database.*;
import calliope.core.exception.DbException;
import org.json.simple.*;
/**
 * Perform hyphenation tasks
 * @author desmond
 */
public class Hyphenator 
{
    AeseSpeller speller;
    String lang;
    HashSet<String> hhExceptions;
    public Hyphenator( String projid, String lang ) throws SpellException
    {
        try
        {
            this.lang = lang;
            this.speller = new AeseSpeller( lang );
            this.hhExceptions = getHHExceptions(projid);
        }
        catch ( Exception e )
        {
            throw new SpellException(e);
        }
    }
    /**
     * Get a set of hyphen exceptions
     * @param projid the project identifier
     * @return a set of hyphen exceptions for this project or the empty set
     * @throws DbException 
     */
    HashSet<String> getHHExceptions( String projid ) throws DbException
    {
        HashSet<String> set = new HashSet<String>();
        Connection conn = Connector.getConnection();
        String projectStr = conn.getFromDb(Database.PROJECTS,projid);
        if ( projectStr != null )
        {
            JSONObject jObj = (JSONObject)JSONValue.parse(projectStr);
            if ( jObj.containsKey(JSONKeys.HH_EXCEPTIONS) )
            {
                String hes = (String)jObj.get(JSONKeys.HH_EXCEPTIONS);
                String[] parts = hes.split(" ");
                for ( int i=0;i<parts.length;i++ )
                    set.add( parts[i] );
            }
        }
        return set;
    }
    /**
     * Does a hyphenated word need its internal hyphen(s)?
     * @param hyphenated the word with embedded hyphens
     * @return true if they should stay else false 
     */
    public boolean wantsHyphen( String hyphenated )
    {
        String[] parts = hyphenated.split("-");
        boolean wants = true;
        for ( int i=0;i<parts.length;i++ )
        {
            if ( !this.speller.hasWord(parts[i],lang) )
            {
                wants = false;
                break;
            }
        }
        if ( wants )    // check hhExceptions
        {
            String merged = hyphenated.replaceAll("-","");
            if ( this.speller.hasWord(merged,lang) )
                wants = false;
            // if it is listed we *remove* the hyphen
            else if ( this.hhExceptions.contains(hyphenated) )
                wants = false;
        }
        return wants;
    }
}
