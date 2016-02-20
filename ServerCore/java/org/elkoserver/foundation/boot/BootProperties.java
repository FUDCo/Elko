package org.elkoserver.foundation.boot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Enhanced version of the Java {@link Properties} class that knows how to pull
 * properties settings out of the command line and also provides a friendlier
 * interface to the values of the properties settings themselves.
 */
public class BootProperties extends Properties
{
    /**
     * Creates an empty property collection with no default values.
     */
    public BootProperties() {
        super();
    }

    /**
     * Creates an empty property collection with the specified defaults.
     *
     * @param defaults  A set of properties to use as a default initializer.
     */
    public BootProperties(Properties defaults) {
        super(defaults);
    }

    /**
     * Get the value of a property as a boolean.
     *
     * @param property  The name of the property to test.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as a boolean.
     *
     * @throws IllegalArgumentException if the property has a value that is
     *    neither of the strings "true" nor "false".
     */
    public boolean boolProperty(String property, boolean defaultValue) {
        String val = getProperty(property);
        if ("true".equals(val)) {
            return true;
        } else if ("false".equals(val)) {
            return false;
        } else {
            return defaultValue;
        }
    }

    /**
     * Get the value of a property as a double.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as a double.
     */
    public double doubleProperty(String property, double defaultValue) {
        String val = getProperty(property);
        if (val != null) {
            try {
                return Double.parseDouble(val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    /**
     * Get the value of a property as an integer.
     *
     * @param property  The name of the property of interest.
     * @param defaultValue  The default value in the event of absence or error.
     *
     * @return the value of the property interpreted as an int.
     */
    public int intProperty(String property, int defaultValue) {
        String val = getProperty(property);
        if (val != null) {
            try {
                return Integer.parseInt(val);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        } else {
            return defaultValue;
        }
    }

    /**
     * Read properties files and parse property settings from the command line.
     * Unless otherwise directed, a default properties file is read for
     * property definitions.  The command line is parsed for additional
     * property assignments according to the following rules:<p>
     *
     * <tt><i>key</i>=<i>val</i></tt><blockquote>
     *      The property named <tt><i>key</i></tt> is given the value
     *      <tt><i>val</i></tt>.</blockquote>
     *
     * <tt>-nodefaults</tt><blockquote>
     *      Suppress reading <tt>~/.propsrc</tt> for further property
     *      definitions.</blockquote>
     *
     * <tt>-properties <i>filename</i></tt><blockquote>
     *      File <tt><i>filename</i></tt> is read for further property
     *      definitions.</blockquote>
     *
     * <tt><i>anythingelse</i></tt><blockquote>
     *      The argument is added to the returned arguments array.</blockquote>
     *
     * @param inArgs  The raw, unprocessed command line arguments array.
     *
     * @return an array of the arguments remaining after all the
     *    property-specifying ones are stripped out.
     *
     * @throws IOException when a file from which it is supposed to read
     *    further properties does not exist or cannot be read.  However, if any
     *    of the <i>default</i> properties files do not exist, their
     *    non-existence is silently ignored rather than causing an exception.
     *    On the other hand, if they <i>do</i> exist but simply aren't
     *    readable, an exception is thrown as for any other properties file.
     */
    public String[] scanArgs(String inArgs[]) throws IOException {
        boolean shouldLoadDefaults = true;

        List<String> args = new ArrayList<String>(inArgs.length);
        List<String> propSets = new ArrayList<String>(inArgs.length);
        List<String> propFiles = new ArrayList<String>(inArgs.length);

        /* First pass parse of inArgs array */
        for (int i = 0; i < inArgs.length; ++i) {
            String arg = inArgs[i];
            if (arg.equals("-properties")) {
                if (++i < inArgs.length) {
                    propFiles.add(inArgs[i]);
                } else {
                    throw new IllegalArgumentException(
                        "-properties must be followed by a filename");
                }
            } else if (arg.equals("-nodefaults")) {
                shouldLoadDefaults = false;
            } else if (arg.indexOf('=') > 0) {
                propSets.add(arg);
            } else {
                args.add(arg);
            }
        }

        /* Load default props files (if configuration says to) */
        if (shouldLoadDefaults) {
            try {
                loadPropsFile(System.getProperty("user.home", "") +
                              "/.propsrc");
            } catch (FileNotFoundException ex) {
                /*
                 * The contract of this method requires that if it's loading
                 * these by default (as opposed to by being told to), and the
                 * files don't exist, to just silently ignore the error.
                 */
            }
        }

        /* Load props files given on command line */
        int len = propFiles.size();
        for (int i = 0; i < len; ++i) {
            loadPropsFile(propFiles.get(i));
        }

        /* Assign props set directly on command line */
        len = propSets.size();
        for (int i = 0; i < len; ++i) {
            String assoc = propSets.get(i);
            int j = assoc.indexOf('=') ;
            String key = assoc.substring(0, j);
            String value = assoc.substring(j+1);
            put(key, value);
        }

        /* Return the actual args that remain */
        return (String[]) args.toArray(new String[args.size()]);
    }

    /**
     * Test the setting of a boolean property.  A boolean property is
     * <tt>true</tt> if its string value is "true", and <tt>false</tt> if its
     * string value is "false" or if the property is not set at all.
     *
     * @param property  The name of the property to test.
     *
     * @return the value of the property interpreted as a boolean.
     *
     * @throws IllegalArgumentException if the property has a value that is
     *    neither of the strings "true" nor "false".
     */
    public boolean testProperty(String property) {
        return boolProperty(property, false);
    }

    /**
     * Test if the value of a property is a particular string.
     *
     * @param property  The name of the property to test.
     * @param possibleValue  String value to test if it is equal to.
     *
     * @return <tt>true</tt> if the property has a value equal to
     *    <tt>possibleValue</tt>.
     */
    public boolean testProperty(String property, String possibleValue) {
        if (possibleValue != null) {
            return possibleValue.equals(getProperty(property));
        } else {
            return false;
        }
    }

    /**
     * If the file named by <tt>filename</tt> exists and is readable, it is
     * read as a properties-defining file and the contained definitions are
     * added to our property settings.
     *
     * @param filename  Name of a properties file.
     *
     * @throws FileNotFoundException if the file doesn't exist.
     * @throws IOException if there was a problem reading from it.
     */
    private void loadPropsFile(String filename)
        throws FileNotFoundException, IOException
    {
        filename = new File(filename).getAbsolutePath();
        FileInputStream instream;
        try {
            instream = new FileInputStream(filename);
        } catch (FileNotFoundException ex) {
            throw new FileNotFoundException("Error opening " + filename +
                                            ": " + ex);
        }
        try {
            load(instream);
        } catch (IOException ex) {
            throw new IOException("Error reading " + filename + ": " + ex);
        } finally {
            instream.close();
        }
    }
}
