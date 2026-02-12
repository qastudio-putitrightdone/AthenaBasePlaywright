package org.athena;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

public class ReadConfigData {

    /**
     * Get properties in the format of key and value pair from provided file path.
     * @param filePath
     * @return
     */
    private Properties getConfigData(String filePath) {
        try {
            File file = new File(filePath);
            FileInputStream fileInputStream = new FileInputStream(file);
            Properties properties = new Properties();
            properties.load(fileInputStream);
            return properties;
        } catch (FileNotFoundException e) {
            System.out.println("Unable to find config file!!!");
            throw new RuntimeException(e);
        } catch (IOException e) {
            System.out.println("Error while loading properties!!!");
            throw new RuntimeException(e);
        }
    }

    public Properties fetchConfigData(String filePath) {
        return getConfigData(filePath);
    }
}
