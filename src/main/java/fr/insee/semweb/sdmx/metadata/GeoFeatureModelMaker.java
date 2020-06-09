package fr.insee.semweb.sdmx.metadata;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.jena.geosparql.implementation.vocabulary.Geo;
import org.apache.jena.geosparql.implementation.vocabulary.GeoSPARQL_URI;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class GeoFeatureModelMaker {

    public static final Logger logger = LogManager.getLogger(GeoFeatureModelMaker.class);

    private static String metadataApi = "http://qfrmeswnczlht01.ad.insee.intra/";
   // private static String metadataApi = "http://pdrmeswncdlht01.ad.insee.intra/";

    private static String baseUri = "http://uri_a_modifier/";

    public static Map<String, String> labelFeatureMap = new HashMap<>();

    public static void exportGeoFeatures() {
        Model geoModel = null;
        try {
            geoModel = createRegionModel();
            geoModel.add(createDepartementModel());
            geoModel.add(createFranceModel());

        }
        catch (IOException e1) {
            logger.error("Error creating geo model");
            return;
        }
        try {
            geoModel.write(new FileWriter(Configuration.GEOFEATURES_TURTLE_FILE_NAME), "TTL");
        }
        catch (IOException e) {
            logger.error("Error writing model to file");
        }
    }

    public static Model createRegionModel() throws IOException {
        JSONArray regions = getJSON("geo/regions");
        return createRdfGeoFeature(regions);
    }

    public static Model createDepartementModel() throws IOException {
        JSONArray departements = getJSON("geo/departements");
        return createRdfGeoFeature(departements);
    }
    
    public static Model createFranceModel() throws IOException {
        Model geoModel = ModelFactory.createDefaultModel();
        String featureUri = baseUri + "FR";
        Resource frResource = geoModel.createResource(featureUri, Geo.FEATURE_RES);
        frResource.addProperty(RDFS.label, "France");
        labelFeatureMap.put("FRANCE", featureUri);
        return geoModel;
    }

    private static Model createRdfGeoFeature(JSONArray jsonArray) {
        Model geoModel = ModelFactory.createDefaultModel();
        geoModel.setNsPrefix("geo", GeoSPARQL_URI.GEO_URI);

        // Create geoFeature for each item
        for (Object o : jsonArray) {
            if (o instanceof JSONObject) {
                JSONObject geoItem = (JSONObject) o;
                String code = geoItem.getString("code");
                String featureUri = baseUri + code;
                Resource regionResource = geoModel.createResource(featureUri, Geo.FEATURE_RES);
                regionResource.addProperty(RDFS.label, geoItem.getString("intitule"));
                regionResource.addProperty(OWL.sameAs, geoModel.createResource(geoItem.getString("uri")));
                labelFeatureMap.put(geoItem.getString("intitule"), featureUri);
            }
        }

        return geoModel;
    }

    public static Map<String, String> getM0CogCorrespondences() throws IOException {

        Map<String, String> finalMap = new HashMap<>();

        // GET M0 Map with M0-code and label
        Map<String, String> m0Map = new HashMap<>();
        Model m0CodeListsModel = M0Converter.convertCodeLists();
        // Main loop on concept schemes
        m0CodeListsModel.listStatements(null, RDF.type, SKOS.ConceptScheme).forEachRemaining(schemeStatement -> {
            Resource codeList = schemeStatement.getSubject();
            if (codeList.getURI().equals("http://baseUri/codelists/codelist/7")) {
                m0CodeListsModel.listStatements(null, SKOS.inScheme, codeList).forEachRemaining(codeStatement -> {
                    Resource codeResource = codeStatement.getSubject();
                    String code =
                        codeResource.getRequiredProperty(SKOS.notation).getObject().asLiteral().getLexicalForm();
                    String label =
                        codeResource.getRequiredProperty(SKOS.prefLabel, "fr").getObject().asLiteral().getLexicalForm();
                    m0Map.put(label, code);
                });
            }
        });
        m0CodeListsModel.close();

        // GET Target Map
        exportGeoFeatures();

        // Mix the two map by label to another map (code, feature)
        // M0Map = label, code
        // TargetMap = label, featureUri
        FileWriter fileWriter = new FileWriter("src/main/resources/data/geoM0ToCreate.txt");

        m0Map.forEach((label,code)->{
            if (labelFeatureMap.containsKey(label.trim())) {
                finalMap.put(code, labelFeatureMap.get(label.trim()));
            }else {
                try {
                    fileWriter.write(code+";"+label+"\n");
                }
                catch (IOException e) {
                    logger.error("Unexpected error : can't write in file");
                }
            }
        });
        fileWriter.close();
        return finalMap;

    }

    private static JSONArray getJSON(String path) throws IOException {
        URL url = new URL(metadataApi + path+"?date=%2A");
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setRequestMethod("GET");
        con.setRequestProperty("accept", "application/json");

        BufferedReader in = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String inputLine;
        StringBuffer content = new StringBuffer();
        while ((inputLine = in.readLine()) != null) {
            content.append(inputLine);
        }
        in.close();

        JSONArray json = null;
        try {
            json = new JSONArray(content.toString());
        }
        catch (JSONException e) {
            logger.error("Error parsing response");
        }

        con.disconnect();
        return json;
    }
}
