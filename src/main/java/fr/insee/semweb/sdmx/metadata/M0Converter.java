package fr.insee.semweb.sdmx.metadata;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Selector;
import org.apache.jena.rdf.model.SimpleSelector;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import fr.insee.stamina.utils.PROV;

/**
 * Converts RDF information expressed in the interim format ("M0 model") to the target model.
 * 
 * @author Franck
 */
public class M0Converter {

	public static Logger logger = LogManager.getLogger(M0Converter.class);

	/** Base URI for the names of the graphs in M0 dataset (add 'familles', 'series', 'operations', 'organismes', 'indicateurs', 'documents','documentations', 'codelists', 'codes', 'liens', 'associations') */
	public static String M0_BASE_GRAPH_URI = "http://rdf.insee.fr/graphe/";
	/** Base URI for SIMS-related resources in M0 */
	static String M0_SIMS_BASE_URI = "http://baseUri/documentations/documentation/";

	/** Base URI for SIMS metadata reports */
	static String REPORT_BASE_URI = "http://id.insee.fr/qualite/rapport/"; // TODO Move to configuration eventually

	/** The ubiquitous 'values' property in M0 */
	static Property M0_VALUES = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#values");
	/** The ubiquitous 'values' property in M0, English version */
	static Property M0_VALUES_EN = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#valuesGb");
	/** The ubiquitous 'relatedTo' property in M0 */
	static Property M0_RELATED_TO = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo");
	/** The ubiquitous 'relatedTo' property in M0, English version */
	static Property M0_RELATED_TO_EN = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedToGb");

	/** The reported attribute */
	static Resource SIMS_REPORTED_ATTRIBUTE = ResourceFactory.createResource("http://www.w3.org/ns/sdmx-mm#ReortedAttribute");
	/** The DQV quality measurement */
	static Resource DQV_QUALITY_MEASUREMENT = ResourceFactory.createResource("http://www.w3.org/ns/dqv#QualityMeasurement");

	/** The SIMS-FR metadata structure definition */
	static Model simsFrMSD = null;
	/** The SIMS-FR scheme */
	static SIMSFRScheme simsFRScheme = null;

	/** The M0 dataset containing all the models */
	static Dataset dataset = null;

	/** The explicit fixed mappings between M0 and target URIs for operations and the like */
	static Map<String, String> fixedURIMappings = null;

	/** The mappings between M0 and target URIs for organizations */
	static Map<String, String> organizationURIMappings = null;

	/** All the mappings between M0 and target URIs for families, series, operations and indicators */
	static Map<String, String> allURIMappings = null;

	public static void main(String[] args) throws IOException {

		// Read the source M0 dataset and extract SIMS information
		readDataset();
		Model m0SIMSModel = dataset.getNamedModel(M0_BASE_GRAPH_URI + "documentations");

		// Read the SIMSFr structure from the Excel specification
		simsFRScheme = SIMSFRScheme.readSIMSFRFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));

		// Read the SIMS MSD
		simsFrMSD = ModelFactory.createOntologyModel();
		simsFrMSD.read(Configuration.SIMS_FR_MSD_TURTLE_FILE_NAME);

		Model m01508Model = extractM0ResourceModel(m0SIMSModel, "http://baseUri/operations/operation/1508");
		Model sims1508Model = m0ConvertToSIMS(m01508Model);
		sims1508Model.write(new FileOutputStream("src/main/resources/models/sims-1508.ttl"), "TTL");

		m0SIMSModel.close();
	}

	/**
	 * Splits the base M0 SIMS model into smaller models corresponding to metadata set identifiers passed as a list, and saves the smaller models to disk.
	 * 
	 * @param m0SIMSModel A Jena <code>Model</code> containing the SIMS metadata in M0 format.
	 * @param m0Ids A <code>List</code> of M0 metadata set identifiers.
	 * @throws IOException In case of problem while writing the model to disk.
	 */
	public static void m0SplitAndSave(Model m0SIMSModel, List<String> m0Ids) throws IOException {

		logger.debug("Splitting M0 model into " + m0Ids.size() + " models");
		for (String m0Id : m0Ids) {
			// Create model for the current source
			Model sourceModel = extractM0ResourceModel(m0SIMSModel, "http://baseUri/documentations/documentation/" + m0Id);
			sourceModel.write(new FileOutputStream("src/main/resources/models/m0-"+ m0Id + ".ttl"), "TTL");
			sourceModel.close();
		}
	}

	/**
	 * Converts a metadata set from M0 to SIMSFr RDF format.
	 * 
	 * @param m0Model A Jena <code>Model</code> containing the metadata in M0 format.
	 * @return A Jena <code>Model</code> containing the metadata in SIMSFr format.
	 */
	public static Model m0ConvertToSIMS(Model m0Model) {

		// Retrieve base URI (the base resource is a skos:Concept) and the corresponding M0 identifier
		Resource baseResource = m0Model.listStatements(null, RDF.type, SKOS.Concept).toList().get(0).getSubject(); // Should raise an exception in case of problem
		String m0Id = baseResource.getURI().substring(baseResource.getURI().lastIndexOf('/') + 1);

		// Will be handy for parsing dates
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

		logger.debug("Creating metadata report model for m0 documentation " + m0Id);

		Model simsModel = ModelFactory.createDefaultModel();
		simsModel.setNsPrefix("rdfs", RDFS.getURI());
		simsModel.setNsPrefix("dcterms", DCTerms.getURI());
		simsModel.setNsPrefix("skos", SKOS.getURI());
		simsModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);

		// Create metadata report
		Resource report = simsModel.createResource(REPORT_BASE_URI + m0Id, simsModel.createResource(Configuration.SDMX_MM_BASE_URI + "MetadataReport"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Metadata report " + m0Id, "en"));
		report.addProperty(RDFS.label, simsModel.createLiteral("Rapport de métadonnées " + m0Id, "fr"));
		// TODO Do we create a root Metadata Attribute?

		for (SIMSFREntry entry : simsFRScheme.getEntries()) {
			// Create a m0 resource corresponding to the SIMS entry
			Resource m0Resource = ResourceFactory.createResource(baseResource.getURI() + "/" + entry.getCode());
			// Check if the resource has values in M0 (French values are sine qua non)
			List<RDFNode> objectValues = m0Model.listObjectsOfProperty(m0Resource, M0_VALUES).toList();
			if (objectValues.size() == 0) continue; // Resource actually not present in the M0 model
			if (objectValues.size() > 1) {
				// Several values for the resource, we have a problem
				logger.error("Multiple values for resource " + m0Resource);
				continue;
			}
			// If we arrived here, we have one value, but it can be empty (including numerous cases where the value is just new line characters)
			String stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", ""); // TODO Check cases where value is "\n\n"
			if (stringValue.length() == 0) continue;
			logger.debug("Value found for M0 resource " + m0Resource);
			// Get the metadata attribute property from the MSD and get its range
			Property metadataAttributeProperty = simsFrMSD.getProperty(Configuration.simsAttributePropertyURI(entry, false));
			Statement rangeStatement = metadataAttributeProperty.getProperty(RDFS.range);
			Resource range = (rangeStatement == null) ? null : rangeStatement.getObject().asResource();
			logger.debug("Target property is " + metadataAttributeProperty + " with range " + range);
			if (range == null) {
				// We are in the case of a 'text + seeAlso' object
				Resource objectResource = simsModel.createResource(); // Anonymous for now
				objectResource.addProperty(RDF.value, simsModel.createLiteral(stringValue, "fr"));
				report.addProperty(metadataAttributeProperty, objectResource);
			}
			else if (range.equals(SIMS_REPORTED_ATTRIBUTE)) {
				// Just a placeholder for now, the case does not seem to exist in currently available data
				report.addProperty(metadataAttributeProperty, simsModel.createResource(SIMS_REPORTED_ATTRIBUTE));
			}
			else if (range.equals(XSD.xstring)) {
				// TODO For now we attach all properties to the report, but a hierarchy of reported attributes should be created
				report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "fr"));
				// See if there is an English version
				objectValues = m0Model.listObjectsOfProperty(m0Resource, M0_VALUES_EN).toList();
				if (objectValues.size() == 0) {
					stringValue = objectValues.get(0).asLiteral().getString().trim().replaceAll("^\n", "");
					if (stringValue.length() > 0) report.addProperty(metadataAttributeProperty, simsModel.createLiteral(stringValue, "en"));
				}
			}
			else if (range.equals(XSD.date)) {
				// Try to parse the string value as a date (yyyy-MM-dd seems to be used in the documentations graph)
				try {
					dateFormat.parse(stringValue); // Just to make sure we have a valid date
					report.addProperty(metadataAttributeProperty, simsModel.createTypedLiteral(stringValue, XSDDatatype.XSDdate));
				} catch (ParseException e) {
					logger.error("Unparseable date value " + stringValue + " for M0 resource " + m0Resource.getURI());
				}
			}
			else if (range.equals(DQV_QUALITY_MEASUREMENT)) {
				// This case should not exist
			}
			else {
				// Only remaining case is code list (check that)
			}
		}

		return simsModel;
	}

	/**
	 * Extracts from the base M0 model all the statements related to a given base resource (series, operation, etc.).
	 * The statements extracted are those whose subject URI begins with the base resource URI.
	 * 
	 * @param m0Model A Jena <code>Model</code> in M0 format from which the statements will be extracted.
	 * @param m0URI The URI of the M0 base resource for which the statements must to extracted.
	 * @return A Jena <code>Model</code> containing the statements of the extract in M0 format.
	 */
	public static Model extractM0ResourceModel(Model m0Model, String m0URI) {

		logger.debug("Extracting M0 model for resource: " + m0URI);

		Model extractModel = ModelFactory.createDefaultModel();
		Selector selector = new SimpleSelector(null, null, (RDFNode) null) {
									// Override 'selects' method to retain only statements whose subject URI begins with the wanted URI
							        public boolean selects(Statement statement) {
							        	return statement.getSubject().getURI().startsWith(m0URI);
							        }
							    };
		// Copy the relevant statements to the extract model
		extractModel.add(m0Model.listStatements(selector));

		return extractModel;
	}

	/**
	 * Extracts from an M0 model all the statements related to a given attribute.
	 * Warning: only statements with (non empty) literal object will be selected.
	 * 
	 * @param m0Model A Jena <code>Model</code> in M0 format from which the statements will be extracted.
	 * @param attributeName The name of the attribute (e.g. SUMMARY).
	 * @return A Jena <code>Model</code> containing the statements of the extract in M0 format.
	 */
	public static Model extractAttributeStatements(Model m0Model, String attributeName) {

		logger.debug("Extracting M0 model for property: " + attributeName);

		Model extractModel = ModelFactory.createDefaultModel();
		Selector selector = new SimpleSelector(null, M0_VALUES, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with the property name
	        public boolean selects(Statement statement) {
	        	return statement.getSubject().getURI().endsWith(attributeName);
	        }
	    };

		// Copy the relevant statements to the extract model
		extractModel.add(m0Model.listStatements(selector));

		// TODO Add the English values for string properties
		selector = new SimpleSelector(null, M0_VALUES_EN, (RDFNode) null) {
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith(attributeName)) && (statement.getObject().isLiteral()) && (statement.getLiteral().getString().trim().length() > 0));
	        }
	    };

		// Add the selected statements to the extract model
		extractModel.add(m0Model.listStatements(selector));

		return extractModel;
	}

	/**
	 * Extracts the code lists from the M0 model and restructures them as SKOS concept schemes.
	 * 
	 * @return A Jena <code>Model</code> containing the M0 code lists as SKOS concept schemes.
	 */
	public static Model extractCodeLists() {

		// Mappings between M0 'attribute URIs' and SKOS properties
		final Map<String, Property> clMappings = new HashMap<String, Property>();
		clMappings.put("ID", null); // ID is equal to code or code list number, no business meaning (and expressed with a weird property http://www.SDMX.org/.../message#values"
		clMappings.put("CODE_VALUE", SKOS.notation); // CODE_VALUE seems to be the notation, FIXME it is in French
		clMappings.put("ID_METIER", RDFS.comment); // ID_METIER is just TITLE - ID, store in a comment for now
		clMappings.put("TITLE", SKOS.prefLabel); // Can have French and English values
		final List<String> stringProperties = Arrays.asList("ID_METIER", "TITLE"); // Property whose values should have a language tag
		
		readDataset();
		logger.debug("Extracting code lists from dataset " + Configuration.M0_FILE_NAME);
		Model skosModel = ModelFactory.createDefaultModel();
		skosModel.setNsPrefix("rdfs", RDFS.getURI());
		skosModel.setNsPrefix("skos", SKOS.getURI());

		// Open the 'codelists' model first to obtain the number of code lists and create them in SKOS model
		Model clModel = dataset.getNamedModel(M0_BASE_GRAPH_URI + "codelists");
		// Code lists M0 URIs take the form http://baseUri/codelists/codelist/n, where n is an increment strictly inferior to the value of http://baseUri/codelists/codelist/sequence
		int clNumber = getMaxSequence(clModel);
		logger.debug(clNumber + " code lists found in 'codelists' model");

		// Then we read in the 'associations' model the mappings between code lists and codes and store them as a map
		// Mappings are of the form {code list URI}/RELATED_TO M0_RELATED_TO {code URI}/RELATED_TO
		Map<Integer, List<Integer>> codeMappings = new HashMap<Integer, List<Integer>>();
		Model assoModel = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		for (int index = 1; index <= clNumber; index++) {
			List<Integer> listOfCodes = new ArrayList<Integer>();
			Resource clResource = clModel.createResource("http://baseUri/codelists/codelist/" + index + "/RELATED_TO");
			// Retrieve the numeric identifiers of the codes related to the current code list
			StmtIterator assoIterator = assoModel.listStatements(clResource, M0_RELATED_TO, (RDFNode)null);
			assoIterator.forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					Integer code = Integer.parseInt(statement.getObject().asResource().getURI().split("/")[5]);
					listOfCodes.add(code);
				}
			});
			codeMappings.put(index, listOfCodes);
		}
		assoModel.close();

		// Open the 'code' model and browse both 'codelists' and 'codes' models to produce the target SKOS model
		Model codeModel = dataset.getNamedModel(M0_BASE_GRAPH_URI + "codes");
		// Main loop is on code lists
		for (int clIndex = 1; clIndex <= clNumber; clIndex++) {
			Resource clResource = clModel.createResource("http://baseUri/codelists/codelist/" + clIndex);
			Resource skosCLResource = skosModel.createResource("http://baseUri/codelists/codelist/" + clIndex, SKOS.ConceptScheme);
			logger.info("Creating code list " + skosCLResource.getURI() + " containing codes " + codeMappings.get(clIndex));
			for (String property : clMappings.keySet()) {
				if (clMappings.get(property) == null) continue;
				Resource propertyResource = clModel.createResource(clResource.getURI() + "/" + property);
				StmtIterator valueIterator = clModel.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
				if (!valueIterator.hasNext()) {
					logger.error("No value for property " + property + " of code list " + clResource.getURI());
					continue;
				}
				// Create the relevant statement in the SKOS model, adding a language tag if the property is in stringProperties
				if (stringProperties.contains(property)) {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
				} else {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
				}
				if (valueIterator.hasNext()) logger.error("Several values for property " + property + " of code list " + clResource.getURI());
				valueIterator = clModel.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					skosCLResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
				}
			}
			// Read in the code mappings the list of codes associated to the current code list
			List<Integer> codesOfList = codeMappings.get(clIndex);
			for (int codeIndex : codesOfList) {
				Resource codeResource = codeModel.createResource("http://baseUri/codes/code/" + codeIndex);
				Resource skosCodeResource = skosModel.createResource("http://baseUri/codes/code/" + codeIndex, SKOS.Concept);
				// Create the statements associated to the code
				for (String property : clMappings.keySet()) {
					if (clMappings.get(property) == null) continue;
					Resource propertyResource = codeModel.createResource(codeResource.getURI() + "/" + property);
					StmtIterator valueIterator = codeModel.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
					if (!valueIterator.hasNext()) {
						logger.error("No value for property " + property + " of code " + codeResource.getURI());
						continue;
					}
					if (stringProperties.contains(property)) {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
					} else {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
					}
					if (valueIterator.hasNext()) logger.error("Several values for property " + property + " of code " + codeResource.getURI());
					valueIterator = codeModel.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
					if (valueIterator.hasNext()) {
						skosCodeResource.addProperty(clMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
					}
					// Finally, add the relevant SKOS properties between the code and the code list
					skosCodeResource.addProperty(SKOS.inScheme, skosCLResource);
					skosCodeResource.addProperty(SKOS.topConceptOf, skosCLResource);
					skosCLResource.addProperty(SKOS.hasTopConcept, skosCodeResource);
				}
			}
		}

		clModel.close();
		codeModel.close();

		return skosModel;
	}

	/**
	 * Extracts the information on organizations from the M0 model and restructures it according to ORG.
	 * For now we only extract the ID_CODE and (French) TITLE, and check consistency with target model (created from spreadsheet).
	 * 
	 * @return A Jena <code>Model</code> containing the M0 organizations in ORG format.
	 */
	public static Model extractOrganizations() {

		// Read dataset and create model to return and read target model for consistency check
		readDataset();
		logger.debug("Extracting information on organizations from dataset " + Configuration.M0_FILE_NAME);
		Model orgModel = ModelFactory.createDefaultModel();
		orgModel.setNsPrefix("rdfs", RDFS.getURI());
		orgModel.setNsPrefix("org", ORG.getURI());
		Model targetModel = ModelFactory.createDefaultModel();
		try {
			targetModel.read("src/main/resources/ssm.ttl");
			logger.debug("Target model read from 'src/main/resources/ssm.ttl'");
		} catch (Exception e) {
			// Model will be empty: all requests will return no results
			logger.warn("Error while reading the target organization model - " + e.getMessage());
		}

		// Open the 'organismes' model first to obtain the number of organizations and create them in an ORG model
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		// Code lists M0 URIs take the form http://baseUri/organismes/organisme/n, where n is an increment strictly inferior to the value of http://baseUri/organismes/organisme/sequence
		int orgNumber = getMaxSequence(m0Model);
		logger.debug(orgNumber + " organizations found in 'organismes' model");

		for (int orgIndex = 1; orgIndex <= orgNumber; orgIndex++) {
			String resourceURI = "http://baseUri/organismes/organisme/" + orgIndex;
			logger.info("Creating organization " + resourceURI);
			Resource propertyResource = m0Model.createResource(resourceURI + "/ID_CODE");
			StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // There should be exactly one value
			String orgId = "";
			if (valueIterator.hasNext()) orgId = valueIterator.next().getObject().toString().trim();
			if (orgId.length() == 0) {
				logger.warn("No organization for index  " + orgIndex);
				continue;
			}
			// Create resource with its identifier
			Resource orgResource = orgModel.createResource(resourceURI, ORG.organization);
			orgResource.addProperty(ORG.identifier, orgId);
			// Add the title of the organization
			propertyResource = m0Model.createResource(resourceURI + "/TITLE");
			valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // We assume there is exactly one value
			orgResource.addProperty(RDFS.label, valueIterator.next().getObject().toString().trim());

			// Check that organization is in the target scheme (for non Insee organizations)
			if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1)))) continue; // Insee organizations identifiers are like XNNN
			// Look in the target model for an organization with identifier equal to orgId
			if (!targetModel.contains(null, DCTerms.identifier, orgId)) logger.warn("Organization " + orgId + " not found in target model");
			
		}
		m0Model.close();
		targetModel.close();

		return orgModel;
	}

	/**
	 * Returns the correspondence between the M0 identifiers and the target URI for series and operations which have a fixed (existing) target Web4G identifier.
	 * 
	 * @param m0Dataset The MO dataset.
	 * @param type Type of resource under consideration: should be 'famille', 'serie', 'operation' or 'indicateur'.
	 * @return A map giving the correspondences between the M0 identifier (integer) and the target URI of the resource.
	 */
	public static Map<Integer, String> getIdURIFixedMappings(Dataset m0Dataset, String type) {

		Map<Integer, String> mappings = new HashMap<Integer, String>();

		// For families and indicators, there is no fixed mappings: return an empty map
		if (("famille".equals(type)) || ("indicateur".equals(type))) return mappings;

		// For operations, there are only a few cases where the Web4G identifier is fixed
		if ("operation".equals(type)) {
			for (Integer m0Id : Configuration.m02Web4GIdMappings.keySet()) mappings.put(m0Id, Configuration.operationResourceURI(Configuration.m02Web4GIdMappings.get(m0Id), type));
			return mappings;
		}

		// Here we should only have type 'serie'
		if (!("serie".equals(type))) return null;

		// For series, the Web4G identifier is obtained through the DDS identifier: extract the ID_DDS property from the series model
		String graphURI = M0_BASE_GRAPH_URI + type + "s";
		Model extract = extractAttributeStatements(m0Dataset.getNamedModel(graphURI), "ID_DDS");
		logger.debug("Extracted ID_DDS property statements from graph " + graphURI + ", size of resulting model is " + extract.size());

		extract.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Retrieve the M0 numeric identifier, assuming URI structure http://baseUri/{type}s/{type}/{nn}/ID_DDS
				String m0URI = statement.getSubject().getURI().toString();
				Integer m0Id = Integer.parseInt(m0URI.split("/")[5]);
				// Retrieve the "DDS" identifier from the object of the ID_DDS statement (eg OPE-ENQ-SECTORIELLE-ANNUELLE-ESA, skip the 'OPE-' start)
				String ddsId = statement.getObject().asLiteral().toString().substring(4);
				// Retrieve the "Web4G" identifier from the "DDS" identifier and the mappings contained in the Configuration class
				if (!Configuration.dds2Web4GIdMappings.containsKey(ddsId)) {
					logger.warn("No correspondence found for DDS identifier " + ddsId + " (M0 resource " + m0URI + ")");
				} else {
					String web4GId = Configuration.dds2Web4GIdMappings.get(ddsId);
					logger.debug("Correspondence found for " + type + " with DDS identifier " + ddsId + ": Web4G identifier is " + web4GId);
					String targetURI = Configuration.operationResourceURI(web4GId, type);
					mappings.put(m0Id, targetURI);
				}
			}
		});
		extract.close();
		// HACK Add three direct mappings for series 135, 136 and 137 because they have an ID_DDS but it is not in the M0 dataset
		mappings.put(135, Configuration.operationResourceURI("1241", "serie"));
		mappings.put(136, Configuration.operationResourceURI("1195", "serie"));
		mappings.put(137, Configuration.operationResourceURI("1284", "serie"));

		return mappings;
	}

	/**
	 * Returns all URI mappings for operations, series, families and indicators.
	 * 
	 * @return The mappings as a map where the keys are the M0 URIs and the values the target URIs, sorted on keys.
	 */
	public static SortedMap<String, String> createURIMappings() {

		// Fix the sizes of the ranges reserved for the new identifications of the different types of objects
		Map<String, Integer> idRanges = new HashMap<String, Integer>();
		idRanges.put("famille", 0); // Families are identified in their own range [1, 999], not in the common range
		idRanges.put("serie", 50); // There are only 7 series without a fixed mapping, that leaves 43 for future creations
		idRanges.put("operation", 430); // There are 17 out of 243 operations with a fixed mapping, that leaves 204
		idRanges.put("indicateur", 0); // Not used
		Map<String, Integer> idCounters = new HashMap<String, Integer>();

		readDataset();
		SortedMap<String, String> mappings = new TreeMap<String, String>();
		List<String> types = Arrays.asList("famille", "serie", "operation", "indicateur");
		logger.info("Starting the creation of all the URI mappings for families, series, operations and indicators");

		// 1: Get fixed mappings and remove correspondent identifiers from available identifiers
		// Target identifiers range from 1001 upwards (except for families)
		List<Integer> availableNumbers = IntStream.rangeClosed(1001, 1999).boxed().collect(Collectors.toList());
		// First we have to remove from available numbers all those associated with fixed mappings
		// We have to do a complete pass on all types of objects because there is no separation of the ranges for identifiers of different types
		for (String resourceType : types) {
			Map<Integer, String> typeMappings = getIdURIFixedMappings(dataset, resourceType);
			if (typeMappings.size() == 0) logger.info("No fixed mappings for type " + resourceType);
			else logger.info("Number of fixed mappings for type " + resourceType + ": " + typeMappings.size() + ", a corresponding amount of available identifiers will be removed");
			for (int index : typeMappings.keySet()) {
				// Add fixed mapping to the global list of all mappings
				mappings.put("http://baseUri/" + resourceType + "s/" + resourceType + "/" + index, typeMappings.get(index));
				int toRemove = Integer.parseInt(StringUtils.substringAfterLast(typeMappings.get(index), "/").substring(1));
				availableNumbers.removeIf(number -> number == toRemove); // Not super-efficient, but the list is not that big
			}
		}
		logger.info("Total number of fixed mappings: " + mappings.size());

		// 2: Attribute remaining identifiers to all resources that don't have a fixed mapping
		for (String resourceType : types) {
			idCounters.put(resourceType, 0); // Initialize identification counter for this type of resources
			// Get the model corresponding to this type of resource
			Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + resourceType + "s");
			int maxNumber = getMaxSequence(m0Model);
			for (int index = 1; index <= maxNumber; index++) {
				String m0URI = "http://baseUri/" + resourceType + "s/" + resourceType + "/" + index;
				if (mappings.containsKey(m0URI)) continue; // Fixed mappings already dealt with
				// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
				Resource m0Resource = m0Model.createResource(m0URI);
				if (!m0Model.contains(m0Resource, null)) continue; // Verify that M0 resource actually exist
				// At this point, the resource exists and has not a fixed mapping: attribute target URI based on first available number, except for families who use the M0 index
				if ("famille".equals(resourceType)) mappings.put(m0Resource.getURI(), Configuration.operationResourceURI(Integer.toString(index), resourceType));
				else {
					Integer targetId = availableNumbers.get(0);
					availableNumbers.remove(0);
					mappings.put(m0Resource.getURI(), Configuration.operationResourceURI(targetId.toString(), resourceType));
				}
				idCounters.put(resourceType, idCounters.get(resourceType) + 1);
				if (idRanges.get(resourceType) > 0) idRanges.put(resourceType, idRanges.get(resourceType) - 1);
			}
			m0Model.close();
			logger.info("Number of new mappings created for type " + resourceType + ": " + idCounters.get(resourceType));
			if (idRanges.get(resourceType) > 0) {
				//idRanges.put(resourceType, idRanges.get(resourceType) - idCounters.get(resourceType));
				// Reserve some available numbers for future new series or operations
				logger.debug("Reserving " + idRanges.get(resourceType) + " identifiers for future instances of type " + resourceType);
				availableNumbers.subList(0, idRanges.get(resourceType)).clear();
			}
			logger.info("Total number of remaining identifiers for new mappings: " + availableNumbers.size());
			logger.debug("Next available identifier is " + availableNumbers.get(0));
		}
		logger.info("Total number of mappings: " + mappings.size());
		return mappings;
	}

	/**
	 * Extracts the informations on the families from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for families.
	 */
	public static Model extractFamilies() {

		// Read the M0 model and the URI mappings for families
		readDataset();
		logger.debug("Extracting the information on families from dataset " + Configuration.M0_FILE_NAME);
		Map<Integer, String> identificationMappings = getIdURIFixedMappings(dataset, "famille"); // Just in case, but for now there is no mappings for families
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "familles");

		// Create the target model and set appropriate prefix mappings
		Model familyModel = ModelFactory.createDefaultModel();
		familyModel.setNsPrefix("rdfs", RDFS.getURI());
		familyModel.setNsPrefix("skos", SKOS.getURI());
		familyModel.setNsPrefix("dcterms", DCTerms.getURI());
		familyModel.setNsPrefix("org", ORG.getURI());
		familyModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Family M0 URIs take the form http://baseUri/familles/famille/n, where n is an increment strictly inferior to the sequence number
		int familyMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for families is " + familyMaxNumber);

		// Loop on the family index
		int familyRealNumber = 0;
		for (int familyIndex = 1; familyIndex <= familyMaxNumber; familyIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/familles/famille/" + familyIndex);
			if (!m0Model.contains(m0Resource, null)) continue;
			familyRealNumber++;
			String targetURI = identificationMappings.get(familyIndex); // Will be null until we have 'web4G' identifiers for families
			if (targetURI == null) {
				logger.error("No target identifier found for M0 family " + m0Resource.getURI());
				targetURI = Configuration.operationResourceURI(Integer.toString(familyIndex), "famille");
			}
			Resource targetResource = familyModel.createResource(targetURI, OperationModelMaker.statisticalOperationFamily);
			logger.info("Creating target family " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		logger.info(familyRealNumber + " families extracted");
		m0Model.close();

		return familyModel;
	}

	/**
	 * Extracts the informations on the series from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for series.
	 */
	public static Model extractSeries() {

		// Read the M0 model and the URI mappings for series
		readDataset();
		logger.debug("Extracting the information on series from dataset " + Configuration.M0_FILE_NAME);
		Map<Integer, String> identificationMappings = getIdURIFixedMappings(dataset, "serie");
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "series");

		// Create the target model and set appropriate prefix mappings
		Model seriesModel = ModelFactory.createDefaultModel();
		seriesModel.setNsPrefix("rdfs", RDFS.getURI());
		seriesModel.setNsPrefix("skos", SKOS.getURI());
		seriesModel.setNsPrefix("dcterms", DCTerms.getURI());
		seriesModel.setNsPrefix("org", ORG.getURI());
		seriesModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Series M0 URIs take the form http://baseUri/series/serie/n, where n is an increment strictly inferior to the sequence number
		int seriesMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for series is " + seriesMaxNumber);

		// Loop on series number, but actually not all values of index correspond to existing series so the existence of the resource has to be tested
		int seriesRealNumber = 0;
		for (int seriesIndex = 1; seriesIndex <= seriesMaxNumber; seriesIndex++) {
			// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
			Resource m0Resource = m0Model.createResource("http://baseUri/series/serie/" + seriesIndex);
			if (!m0Model.contains(m0Resource, null)) continue;
			seriesRealNumber++;
			String targetURI = identificationMappings.get(seriesIndex);
			if (targetURI == null) {
				logger.error("No target identifier found for M0 series " + m0Resource.getURI() + ", using M0 identifier for now");
				targetURI = Configuration.operationResourceURI(Integer.toString(seriesIndex), "serie");
			}
			Resource targetResource = seriesModel.createResource(targetURI, OperationModelMaker.statisticalOperationSeries);
			logger.info("Creating target series " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		logger.info(seriesRealNumber + " operations extracted");
		m0Model.close();

		return seriesModel;
	}

	/**
	 * Extracts the informations on the operations from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for operations.
	 */
	public static Model extractOperations() {

		// Read the M0 model and the URI mappings for operations
		readDataset();
		logger.debug("Extracting the information on operations from dataset " + Configuration.M0_FILE_NAME);
		Map<Integer, String> identificationMappings = getIdURIFixedMappings(dataset, "operation");
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "operations");

		// Create the target model and set appropriate prefix mappings
		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Operation M0 URIs take the form http://baseUri/operations/operation/n, where n is an increment strictly inferior to the sequence number
		int operationMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for operations is " + operationMaxNumber);

		// Loop on the operation index
		int operationRealNumber = 0;
		for (int operationIndex = 1; operationIndex <= operationMaxNumber; operationIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/operations/operation/" + operationIndex);
			if (!m0Model.contains(m0Resource, null)) continue; // Cases where the index is not attributed
			operationRealNumber++;
			String targetURI = identificationMappings.get(operationIndex);
			if (targetURI == null) {
				logger.info("No target identifier found for M0 operation " + m0Resource.getURI() + ", using M0 identifier for now");
				targetURI = Configuration.operationResourceURI(Integer.toString(operationIndex), "operation");
			} else logger.info("Target identifier found for M0 operation " + m0Resource.getURI() + ": target URI will be " + targetURI);
			Resource targetResource = operationModel.createResource(targetURI, OperationModelMaker.statisticalOperation);
			logger.info("Creating target operation " + targetURI + " from M0 resource " + m0Resource.getURI());
			// Extract TITLE, ALT_LABEL and MILLESIME (or MILESSIME)
			fillLiteralProperties(targetResource, m0Model, m0Resource);
			for (String propertyName : Arrays.asList("MILLESIME", "MILESSIME")) {
				Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + propertyName);
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) continue;
				String year = valueIterator.next().getObject().asLiteral().toString().trim();
				if (year.length() == 0) continue;
				if ((year.length() != 4) || (!StringUtils.isNumeric(year))) {
					logger.error("Invalid year value for resource " + m0Resource.getURI() + ": " + year);
				} else { // Assuming there is no M0 resource with both MILLESIME and MILESSIME attributes
					targetResource.addProperty(DCTerms.valid, year); // TODO dct:valid is probably not the best option
				}
			}
		}
		logger.info(operationRealNumber + " operations extracted");
		m0Model.close();

		return operationModel;
	}

	/**
	 * Extracts the informations on the indicators from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for indicators.
	 */
	public static Model extractIndicators() {

		// Read the M0 model and the URI mappings for indicators
		readDataset();
		logger.debug("Extracting the information on indicators from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "indicateurs");

		// Create the target model and set appropriate prefix mappings
		Model indicatorModel = ModelFactory.createDefaultModel();
		indicatorModel.setNsPrefix("skos", SKOS.getURI());
		indicatorModel.setNsPrefix("dcterms", DCTerms.getURI());
		indicatorModel.setNsPrefix("prov", PROV.getURI());
		indicatorModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Indicator M0 URIs take the form http://baseUri/indicateurs/indicateur/n, where n is an increment strictly inferior to the sequence number
		int indicatorMaxNumber = getMaxSequence(m0Model);
		logger.debug("Maximum index for indicators is " + indicatorMaxNumber);

		// Loop on the indicator index
		for (int indicatorIndex = 1; indicatorIndex <= indicatorMaxNumber; indicatorIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/indicateurs/indicateur/" + indicatorIndex);
			String  targetURI = Configuration.indicatorURI(Integer.toString(indicatorIndex));
			Resource targetResource = indicatorModel.createResource(targetURI, OperationModelMaker.statisticalIndicator);
			logger.info("Creating indicator " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		m0Model.close();
		// Add the PRODUCED_FROM relations
		Map<String, List<String>> productionRelations = extractProductionRelations();
		for (String indicatorM0URI : productionRelations.keySet()) {
			Resource indicatorResource = indicatorModel.createResource(Configuration.indicatorURI(StringUtils.substringAfterLast(indicatorM0URI, "/")));
			for (String seriesM0URI : productionRelations.get(indicatorM0URI)) {
				Resource seriesResource = indicatorModel.createResource(convertM0URI(seriesM0URI));
				indicatorResource.addProperty(PROV.wasGeneratedBy, seriesResource);
				logger.debug("PROV wasGeneratedBy property created from indicator " + indicatorResource.getURI() + " to series " + seriesResource.getURI());
			}
		}
		return indicatorModel;
	}

	/**
	 * Fills the basic literal properties for operation-related resources.
	 * 
	 * @param targetResource The resource in the target model.
	 * @param m0Model The M0 model where the information is taken from.
	 * @param m0Resource The origin M0 resource (a SKOS concept).
	 */
	private static void fillLiteralProperties(Resource targetResource, Model m0Model, Resource m0Resource) {
		for (String property : Configuration.propertyMappings.keySet()) {
			Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + property);
			if (Configuration.stringProperties.contains(property)) {
				// Start with the string properties that can have a French and an English value (except ALT_LABEL?)
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be at most one)
				if (valueIterator.hasNext()) {
					// Must go through lexical values to avoid double escaping
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					if (propertyValue.length() == 0) continue; // Ignore empty values for text properties
					// Remove this is ALT_LABEL should have a language tag
					if ("ALT_LABEL".equals(property)) {
						targetResource.addProperty(Configuration.propertyMappings.get(property), ResourceFactory.createStringLiteral(propertyValue));
						continue;
					}
					// Create the current property on the target resource, with string value tagged '@fr'
					Literal langValue = ResourceFactory.createLangLiteral(propertyValue, "fr");
					targetResource.addProperty(Configuration.propertyMappings.get(property), langValue);
				}
				valueIterator = m0Model.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					// Create the current property on the target resource, with string value tagged '@en'
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					targetResource.addProperty(Configuration.propertyMappings.get(property), ResourceFactory.createLangLiteral(propertyValue, "en"));
				}
			} else {
				// In the other properties, select the coded ones (SOURCE_CATEGORY and FREQ_COLL)
				// TODO FREQ_DISS (at least for indicators)? But there is no property mapping for this attribute
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) continue;
				// Then process the SOURCE_CATEGORY and FREQ_COLL attributes, values are taken from code lists
				if (("SOURCE_CATEGORY".equals(property)) || ("FREQ_COLL".equals(property))) {
					String frenchLabel = ("SOURCE_CATEGORY".equals(property)) ? "Catégorie de source" : "Fréquence"; // TODO Find better method
					String codeURI = Configuration.inseeCodeURI(valueIterator.next().getObject().toString(), frenchLabel);
					targetResource.addProperty(Configuration.propertyMappings.get(property), m0Model.createResource(codeURI));
				}
				// The remaining (object) properties (ORGANISATION, STAKEHOLDERS, REPLACES and RELATED_TO) are processed by dedicated methods.
			}
		}
	}

	/**
	 * Extracts from the current dataset all informations about families, series and operations, and relations between them
	 * 
	 * @return A Jena model containing all the statements.
	 */
	public static Model extractAllOperations() {

		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");

		// First add models on families, series and operations
		operationModel.add(extractFamilies()).add(extractSeries()).add(extractOperations());
		// We will need the URI mappings for the relations
		if (allURIMappings == null) allURIMappings = createURIMappings();
		// Now read the links of various kinds between families, series and operations, starting with hierarchies
		Model m0AssociationModel = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> simpleRelations = extractHierarchies(m0AssociationModel);
		for (String chilM0dURI : simpleRelations.keySet()) {
			Resource child = operationModel.createResource(convertM0URI(chilM0dURI));
			Resource parent = operationModel.createResource(convertM0URI(simpleRelations.get(chilM0dURI)));
			child.addProperty(DCTerms.isPartOf, parent);
			parent.addProperty(DCTerms.hasPart, child);
			logger.debug("Hierarchy properties created between child " + child.getURI() + " and parent " + parent.getURI());
		}
		// RELATED_TO relations (excluding indicators)
		Map<String, List<String>> multipleRelations = extractRelations(m0AssociationModel);
		for (String startM0URI : multipleRelations.keySet()) {
			if (startM0URI.startsWith("http://baseUri/indicateurs")) continue;
			Resource startResource = operationModel.createResource(convertM0URI(startM0URI));
			for (String endM0URI : multipleRelations.get(startM0URI)) {
				Resource endResource = operationModel.createResource(convertM0URI(endM0URI));
				startResource.addProperty(RDFS.seeAlso, endResource); // extractRelations returns each relation twice (in each direction)
				logger.debug("See also property created from resource " + startResource.getURI() + " to resource " + endResource.getURI());
			}
		}
		// REPLACES relations
		multipleRelations = extractReplacements(m0AssociationModel);
		for (String replacingM0URI : multipleRelations.keySet()) {
			Resource replacingResource = operationModel.createResource(convertM0URI(replacingM0URI));
			for (String replacedM0URI : multipleRelations.get(replacingM0URI)) {
				Resource replacedResource = operationModel.createResource(convertM0URI(replacedM0URI));
				replacingResource.addProperty(DCTerms.replaces, replacedResource);
				replacedResource.addProperty(DCTerms.isReplacedBy, replacingResource);
				logger.debug("Replacement property created between resource " + replacingResource.getURI() + " replacing resource " + replacedResource.getURI());
			}
		}
		// Finally, add relations to organizations
		for (OrganizationRole role : OrganizationRole.values()) {
			logger.debug("Creating organizational relations with role " + role.toString());
			multipleRelations = extractOrganizationalRelations(m0AssociationModel, role);
			System.out.println(multipleRelations);
			for (String operationM0URI : multipleRelations.keySet()) {
				Resource operationResource = operationModel.createResource(convertM0URI(operationM0URI));
				for (String organizationURI : multipleRelations.get(operationM0URI)) {
					Resource organizationResource = ResourceFactory.createResource(convertM0OrganizationURI(organizationURI));
					System.out.print(operationM0URI + "\t" + organizationURI);
					System.out.println(operationResource.getURI() + "\t" + organizationResource.getURI());
					operationResource.addProperty(role.getProperty(), organizationResource);
				}
			}
		}
		m0AssociationModel.close();
		return operationModel;
	}

	/**
	 * Reads all the replacement properties and stores them as a map where the keys are the resources replaced and the values are lists of the resources they replaced.
	 * 
	 * @return A map containing the replacement relations.
	 */
	public static Map<String, List<String>> extractReplacements() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on replacements from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> replacementMappings = extractReplacements(m0Model);
	    m0Model.close();

		return replacementMappings;
	}

	/**
	 * Reads all the replacement properties and stores them as a map where the keys are the resources replacing and the values are lists of the resources they replaced.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractReplacements(Model m0AssociationModel) {
		// The relations are in the 'associations' graph and have the following structure :
		// <http://baseUri/series/serie/12/REPLACES> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/13/REMPLACE_PAR> .

		logger.debug("Extracting the information on replacement relations between series");
		Map<String, List<String>> replacementMappings = new HashMap<String, List<String>>();
		
		if (m0AssociationModel == null) return extractReplacements();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'REPLACES' and object URI with 'REMPLACE_PAR'
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith("REPLACES")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("REMPLACE_PAR")));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String after = StringUtils.removeEnd(statement.getSubject().getURI(), "/REPLACES");
				String before = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/REMPLACE_PAR");
				if (!replacementMappings.containsKey(after)) replacementMappings.put(after, new ArrayList<String>());
				replacementMappings.get(after).add(before);
			}
		});

		return replacementMappings;
	}

	/**
	 * Reads all the relation properties between operation-like resources and stores them as a map.
	 * Each relation will be store twice: one for each direction.
	 * NB: the relations between code lists and codes is not returned.
	 * 
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractRelations() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> relationMappings = extractRelations(m0Model);
	    m0Model.close();

		return relationMappings;
	}
	
	/**
	 * Reads all the relation properties between operation-like resources and stores them as a map.
	 * Each relation will be store twice: one for each direction.
	 * NB: the relations between code lists and codes is not returned.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractRelations(Model m0AssociationModel) {

		// The relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/series/serie/99/RELATED_TO> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/98/RELATED_TO>

		logger.debug("Extracting the information on relations between series, indicators, etc.");
		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();

		if (m0AssociationModel == null) return extractRelations();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject URI ends with 'RELATED_TO' and object URI with 'RELATED_TO'
	        public boolean selects(Statement statement) {
	        	// There are also RELATED_TO relations between code lists and codes in the association model, that must be eliminated
	        	String subjectURI = statement.getSubject().getURI();
	        	if (subjectURI.startsWith("http://baseUri/code")) return false;
	        	return ((subjectURI.endsWith("RELATED_TO")) && (statement.getObject().isResource()) && (statement.getObject().asResource().getURI().endsWith("RELATED_TO")));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String oneEnd = StringUtils.removeEnd(statement.getSubject().getURI(), "/RELATED_TO");
				String otherEnd = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/RELATED_TO");
				if (!relationMappings.containsKey(oneEnd)) relationMappings.put(oneEnd, new ArrayList<String>());
				relationMappings.get(oneEnd).add(otherEnd);
			}
		});

		return relationMappings;	
	}
	
	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @return A map containing the hierarchies.
	 */
	public static Map<String, String> extractHierarchies() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on hierarchies from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> hierarchyMappings = extractHierarchies(m0Model);
	    m0Model.close();

	    return hierarchyMappings;
	}

	/**
	 * Reads all the hierarchies (family -> series or series -> operation) and stores them as a map.
	 * The map keys will be the children and the values the parents, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the hierarchies.
	 */
	public static Map<String, String> extractHierarchies(Model m0AssociationModel) {

		// The hierarchies are in the 'associations' graph and have the following structure:
		// <http://baseUri/familles/famille/58/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/117/ASSOCIE_A>

		logger.debug("Extracting the information on hierarchies between families, series and operations");
		Map<String, String> hierarchyMappings = new HashMap<String, String>();

		if (m0AssociationModel == null) return extractHierarchies();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'ASSOCIE_A' and begin with expected objects
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("ASSOCIE_A")) && (objectURI.endsWith("ASSOCIE_A")))) return false;
	        	if ((subjectURI.startsWith("http://baseUri/series")) && (objectURI.startsWith("http://baseUri/familles"))) return true;
	        	if ((subjectURI.startsWith("http://baseUri/operations")) && (objectURI.startsWith("http://baseUri/series"))) return true;
	        	return false;
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String child = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String parent = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// Each series or operation should have at most one parent
				if (hierarchyMappings.containsKey(child)) logger.error("Conflicting parents for " + child + " - " + parent + " and " + hierarchyMappings.get(child));
				else hierarchyMappings.put(child, parent);
			}
		});

		return hierarchyMappings;	
	}

	/**
	 * Reads all the relations between SIMS metadata sets and series and operations (and possibly indicators), and returns them as a map.
	 * The map keys will be the SIMS 'documentation' and the values the series, operation or indicator, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the attachment relations.
	 */
	public static Map<String, String> extractSIMSAttachments(boolean includeIndicators) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on SIMS metadata sets attachment from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> attachmentMappings = extractSIMSAttachments(m0Model, includeIndicators);
	    m0Model.close();

	    return attachmentMappings;
	}

	/**
	 * Reads all the relations between SIMS metadata sets and series and operations (and possibly indicators), and returns them as a map.
	 * The map keys will be the SIMS 'documentation' and the values the series, operation or indicator, both expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param includeIndicators If <code>true</code>, the attachments to indicators will also be returned, otherwise only series and operations are considered.
	 * @return A map containing the attachment relations.
	 */
	public static Map<String, String> extractSIMSAttachments(Model m0AssociationModel, boolean includeIndicators) {

		// The attachment relations are in the 'associations' graph and have the following structure:
		// <http://baseUri/documentations/documentation/1527/ASSOCIE_A> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/operations/operation/1/ASSOCIE_A>

		logger.debug("Extracting the information on attachment between SIMS metadata sets and series or operations");
		Map<String, String> attachmentMappings = new HashMap<String, String>();

		if (m0AssociationModel == null) return extractSIMSAttachments(includeIndicators);
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'ASSOCIE_A' and begin with expected objects
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("ASSOCIE_A")) && (objectURI.endsWith("ASSOCIE_A")))) return false;
	        	if (subjectURI.startsWith("http://baseUri/documentations")) {
	        		if (objectURI.startsWith("http://baseUri/series")) return true;
	        		if (objectURI.startsWith("http://baseUri/operations")) return true;
	        		if (includeIndicators && objectURI.startsWith("http://baseUri/indicateurs")) return true;
	        	}
	        	return false;
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String simsSet = StringUtils.removeEnd(statement.getSubject().getURI(), "/ASSOCIE_A");
				String operation = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/ASSOCIE_A");
				// We can check that each operation or series has not more than one SIMS metadata set attached
				if (attachmentMappings.containsValue(operation)) logger.warn("Several SIMS metadata sets are attached to " + operation);
				// Each SIMS metadata set should be attached to only one series/operation
				if (attachmentMappings.containsKey(simsSet)) logger.error("SIMS metadata set " + simsSet + " is attached to both " + operation + " and " + attachmentMappings.get(simsSet));
				else attachmentMappings.put(simsSet, operation);
			}
		});

		return attachmentMappings;	
	}

	/**
	 * Reads all the relations of a specified type (production, stakeholding) between operations and organizations and stores them as a map.
	 * The map keys will be the operations and the values the lists of stakeholders, all expressed as M0 URIs.
	 * 
	 * @param organizationRole Role of the organizations to extract: producers or stakeholders.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractOrganizationalRelations(OrganizationRole organizationRole) {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations to organizations from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> organizationMappings = extractOrganizationalRelations(m0Model, organizationRole);
	    m0Model.close();

	    return organizationMappings;
	}

	/**
	 * Reads all the relations of a specified type (production, stakeholding) between operations and organizations and stores them as a map.
	 * The map keys will be the operations and the values the lists of organizations, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @param organizationRole Role of the organizations to extract: producers or stakeholders.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractOrganizationalRelations(Model m0AssociationModel, OrganizationRole organizationRole) {

		// The relations between operations and organizations are in the 'associations' graph and have the following structure (same with '/ORGANISATION' for producer):
		// <http://baseUri/series/serie/42/STAKEHOLDERS> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/organismes/organisme/10/STAKEHOLDERS>

		logger.debug("Type of relationship extracted " + organizationRole);
		Map<String, List<String>> organizationMappings = new HashMap<String, List<String>>();
		String suffix = "/" + organizationRole.toString();

		if (m0AssociationModel == null) return extractOrganizationalRelations(organizationRole);
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with the appropriate suffix
	        public boolean selects(Statement statement) {
	        	return ((statement.getSubject().getURI().endsWith(suffix)) && (statement.getObject().isResource())
	        			&& (statement.getObject().asResource().getURI().startsWith("http://baseUri/organismes")) && (statement.getObject().asResource().getURI().endsWith(suffix)));
	        }
	    };
	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String operation = StringUtils.removeEnd(statement.getSubject().getURI(), suffix);
				String organization = StringUtils.removeEnd(statement.getObject().asResource().getURI(), suffix);
				if (!organizationMappings.containsKey(operation)) organizationMappings.put(operation, new ArrayList<String>());
				organizationMappings.get(operation).add(organization);
			}
		});

		return organizationMappings;	
	}

	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractProductionRelations() {

		// Read the M0 'associations' model
		readDataset();
		logger.debug("Extracting the information on relations between indicators and series from dataset " + Configuration.M0_FILE_NAME);
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, List<String>> relationMappings = extractProductionRelations(m0Model);
	    m0Model.close();

	    return relationMappings;
	}


	/**
	 * Reads all the relations stating that an indicator is produced from a series and stores them as a map.
	 * The map keys will be the indicators and the values the lists of series they are produced from, all expressed as M0 URIs.
	 * 
	 * @param m0AssociationModel The M0 'associations' model where the information should be read.
	 * @return A map containing the relations.
	 */
	public static Map<String, List<String>> extractProductionRelations(Model m0AssociationModel) {

		// The relations between operations and organizations are in the 'associations' graph and have the following structure (same with '/ORGANISATION' for producer):
		// <http://baseUri/indicateurs/indicateur/9/PRODUCED_FROM> <http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo> <http://baseUri/series/serie/71/PRODUCED_FROM>
		// TODO See cases where PRODUIT_INDICATEURS attribute is used.

		logger.debug("Extracting 'PRODUCED_FROM' relations between series and indicators");
		Map<String, List<String>> relationMappings = new HashMap<String, List<String>>();

		//if (m0AssociationModel == null) return extractProductionRelations();
		Selector selector = new SimpleSelector(null, M0_RELATED_TO, (RDFNode) null) {
			// Override 'selects' method to retain only statements whose subject and object URIs end with 'PRODUCED_FROM' and begin with expected objects
	        public boolean selects(Statement statement) {
	        	String subjectURI = statement.getSubject().getURI();
	        	String objectURI = statement.getObject().asResource().getURI();
	        	if (!((subjectURI.endsWith("PRODUCED_FROM")) && (objectURI.endsWith("PRODUCED_FROM")))) return false;
	        	if ((subjectURI.startsWith("http://baseUri/indicateurs")) && (objectURI.startsWith("http://baseUri/series"))) return true;
	        	return false;
	        }
	    };

	    m0AssociationModel.listStatements(selector).forEachRemaining(new Consumer<Statement>() {
			@Override
			// 
			public void accept(Statement statement) {
				String indicatorURI = StringUtils.removeEnd(statement.getSubject().getURI(), "/PRODUCED_FROM");
				String seriesURI = StringUtils.removeEnd(statement.getObject().asResource().getURI(), "/PRODUCED_FROM");
				if (!relationMappings.containsKey(indicatorURI)) relationMappings.put(indicatorURI, new ArrayList<String>());
				relationMappings.get(indicatorURI).add(seriesURI);
			}
		});

		return relationMappings;
	}

	/**
	 * Extracts the operation identifier from a resource URI.
	 * 
	 * @param uri The resource URI.
	 * @return The identifier of the operation.
	 */
	public static String getOperationId(String uri) {

		// TODO Review
		// Assuming URIs of the type 'http://baseUri/FR-ACCES-FINANCEMENT-PME-10-PERSONNES/SOURCE_CODE'
		return uri.substring(18).split("/")[0];
	}

	/**
	 * Returns the maximum of the sequence number used in a M0 model.
	 * 
	 * M0 URIs use a sequence number an increment strictly inferior to the value of property http://rem.org/schema#sequenceValue of resource http://baseUri/codelists/codelist/sequence
	 * @param m0Model The M0 model (extracted from the dataset).
	 * @return The maximum sequence number, or 0 if the information cannot be obtained in the model.
	 */
	public static int getMaxSequence(Model m0Model) {

		// M0 URIs use a sequence number an increment strictly inferior to the value of property http://rem.org/schema#sequenceValue of resource http://baseUri/{type}s/{type}/sequence
		// We assume that there is only one triple containing this property per graph.
		final Property sequenceValueProperty = ResourceFactory.createProperty("http://rem.org/schema#sequenceValue");

		StmtIterator statements = m0Model.listStatements(null, sequenceValueProperty, (RDFNode)null);
		if (!statements.hasNext()) return 0;
		Statement sequenceStatement = statements.next();

		if (!sequenceStatement.getObject().isLiteral()) return 0;

		return (Integer.parseInt(sequenceStatement.getObject().asLiteral().toString()) - 1); // TODO A try/catch would be more secure
	}

	/**
	 * Returns the list of all attributes used in a M0 model.
	 * M0 attributes are those which correspond to the last path element of subject resources in the M0 model.
	 * 
	 * @param m0Model The M0 model to study.
	 * @return The list of the M0 attributes used in the model.
	 */
	public static List<String> listModelAttributes(Model m0Model) {
	
		List<String> attributes = new ArrayList<String>();
		StmtIterator iterator = m0Model.listStatements();
		iterator.forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				String attributeName = StringUtils.substringAfterLast(statement.getSubject().getURI(), "/");
				 // Avoid base resources and special attribute 'sequence' (used to increment M0 identifier)
				if (!StringUtils.isNumeric(attributeName) && !("sequence".equals(attributeName)) && !attributes.contains(attributeName)) attributes.add(attributeName);
			}
		});
		return attributes;
	}

	/**
	 * Reads the complete M0 dataset if it has not been read already.
	 */
	private static void readDataset() {
		if (dataset == null) {
			dataset = RDFDataMgr.loadDataset(Configuration.M0_FILE_NAME);
			logger.debug("M0 dataset read from file " + Configuration.M0_FILE_NAME);
		}
	}

	/**
	 * Aggregates all the specific mappings between M0 and target URIs for families, series and operations, and stores them in the 'fixedURIMappings' class member.
	 */
	private static void readURIMappings() {

		// Aggregate all the URI mappings to avoid multiple queries on ID_DDS
		fixedURIMappings = new HashMap<String, String>();
		Map<Integer, String> idMappings = getIdURIFixedMappings(dataset, "famille");
		for (int m0Id : idMappings.keySet()) fixedURIMappings.put("http://baseUri/familles/famille/" + m0Id, idMappings.get(m0Id)); // Empty for families, so it's just in case
		idMappings = getIdURIFixedMappings(dataset, "serie");
		for (int m0Id : idMappings.keySet()) fixedURIMappings.put("http://baseUri/series/serie/" + m0Id, idMappings.get(m0Id));
		idMappings = getIdURIFixedMappings(dataset, "operation");
		for (int m0Id : idMappings.keySet()) fixedURIMappings.put("http://baseUri/operations/operation/" + m0Id, idMappings.get(m0Id));
	}

	/**
	 * Reads the mappings between M0 and target URIs for organizations.
	 */
	public static void readOrganizationURIMappings() {

		readDataset();
		organizationURIMappings = new HashMap<String, String>();
		// Read the 'organismes' model and loop through the statements with 'ID_CODE' subjects
		Model m0Model = dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		Model extractModel = extractAttributeStatements(m0Model, "ID_CODE");
		extractModel.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Get the M0 URI (just strip the /ID_CODE part)
				String m0URI = StringUtils.removeEnd(statement.getSubject().toString(), "/ID_CODE");
				// Read the value of the property
				String orgId = statement.getObject().asLiteral().toString();
				// HACK Organization 81 has a weird identifier
				if (m0URI.endsWith("/81")) orgId = "Drees";
				String orgURI = null;
				if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1)))) orgURI = Configuration.inseeUnitURI("DG75-" + orgId);
				else orgURI = Configuration.organizationURI(orgId);
				organizationURIMappings.put(m0URI, orgURI);
			}
		});
	}

	/**
	 * Converts an M0 operation resource URI into the corresponding target URI.
	 * 
	 * @param m0URI The M0 operation resource URI.
	 * @return The target URI for the resource.
	 */
	public static String convertM0URI(String m0URI) {

		if (fixedURIMappings == null) readURIMappings();
		if (fixedURIMappings.containsKey(m0URI)) return fixedURIMappings.get(m0URI);
		String type = m0URI.split("/")[4];
		if ("indicateur".equals(type)) return Configuration.indicatorURI(StringUtils.substringAfterLast(m0URI, "/"));
		return Configuration.operationResourceURI(StringUtils.substringAfterLast(m0URI, "/"), m0URI.split("/")[4]);
	}

	/**
	 * Converts an M0 organization resource URI into the corresponding target URI.
	 * 
	 * @param m0URI The M0 organization resource URI.
	 * @return The target URI for the resource.
	 */
	public static String convertM0OrganizationURI(String m0URI) {

		if (organizationURIMappings == null) readOrganizationURIMappings();
		if (organizationURIMappings.containsKey(m0URI)) return organizationURIMappings.get(m0URI);
		return null;
	}

	/**
	 * Enumeration of the different roles in which an organization can appear in the M0 model.
	 */
	public enum OrganizationRole {
		PRODUCER,
		STAKEHOLDER;

		@Override
		public String toString() {
			switch(this) {
				case PRODUCER: return "ORGANISATION";
				case STAKEHOLDER: return "STAKEHOLDERS";
				default: return "unknown";
			}
		}

		/** Returns the OWL property associated to the organization role */
		public Property getProperty() {
			switch(this) {
			case PRODUCER: return DCTerms.creator;
			case STAKEHOLDER: return DCTerms.contributor;
			default: return null;
			}
		}
	}
}
