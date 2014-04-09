package com.ethlo.persistence.tools.eclipselink;

/**
 * 
 * @author Morten Haraldsen
 */
public class ReflectionHelper
{
    public static boolean classExists(String fqName, ClassLoader classLoader)
    {
        try
        {
            Class.forName(fqName, false, classLoader);
            return true;
        }
        catch (ClassNotFoundException e)
        {
            return false;
        }
    }
}
