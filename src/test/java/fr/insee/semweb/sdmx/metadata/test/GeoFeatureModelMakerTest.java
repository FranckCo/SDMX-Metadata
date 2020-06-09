package fr.insee.semweb.sdmx.metadata.test;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

import org.apache.jena.rdf.model.Model;
import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.GeoFeatureModelMaker;

public class GeoFeatureModelMakerTest {

    @Test
    public void testGetRegionModel() throws IOException {
        Model m = GeoFeatureModelMaker.createRegionModel();
        m.write(new FileWriter(Configuration.GEOFEATURES_TURTLE_FILE_NAME), "TTL");
        m.close();
    }

    @Test
    public void testExportGeoFeatures() throws IOException {
        GeoFeatureModelMaker.exportGeoFeatures();
    }
    
    @Test
    public void testGetCorrespondences() throws IOException {
        Map<String, String> map = GeoFeatureModelMaker.getM0CogCorrespondences();
        FileWriter fileWriter = new FileWriter("src/main/resources/data/geoM0Correspondences.txt");
        map.forEach((k,v) -> {
            try {
                fileWriter.write(k+";"+v+"\n");
            }
            catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
            }
        });
        fileWriter.close();
    }
    
}
