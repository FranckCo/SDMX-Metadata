package fr.insee.semweb.sdmx.metadata;

import static fr.insee.semweb.sdmx.metadata.Configuration.FAMILY_THEMES_XLSX_FILE_NAME;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_BASE_GRAPH_URI;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_CODES_BASE_URI;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_CODE_LISTS_BASE_URI;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_FILE_NAME;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_RELATED_TO;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_VALUES;
import static fr.insee.semweb.sdmx.metadata.Configuration.M0_VALUES_EN;
import static fr.insee.semweb.sdmx.metadata.Configuration.ddsToWeb4GIdMappings;
import static fr.insee.semweb.sdmx.metadata.Configuration.inseeCodeURI;
import static fr.insee.semweb.sdmx.metadata.Configuration.inseeUnitURI;
import static fr.insee.semweb.sdmx.metadata.Configuration.m0ToWeb4GIdMappings;
import static fr.insee.semweb.sdmx.metadata.Configuration.operationResourceURI;
import static fr.insee.semweb.sdmx.metadata.Configuration.organizationURI;
import static fr.insee.semweb.sdmx.metadata.Configuration.propertyMappings;
import static fr.insee.semweb.sdmx.metadata.Configuration.stringProperties;
import static fr.insee.semweb.sdmx.metadata.Configuration.themeURI;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.rdf.model.Literal;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import fr.insee.semweb.utils.URIComparator;
import fr.insee.stamina.utils.PROV;

/**
 * Converts RDF information expressed in the interim format ("M0 model") to the target model.
 * This base class deals mainly with resources representing families, series, operations and indicators.
 * 
 * @author Franck
 */
public class M0Converter {

	/** Log4J2 logger */
	public static Logger logger = LogManager.getLogger(M0Converter.class);

	/** The M0 dataset containing all the models */
	protected static Dataset m0Dataset = null;

	/** The explicit fixed mappings between M0 and target URIs for operations and the like */
	static Map<String, String> fixedURIMappings = null;

	/** The mappings between M0 and target URIs for organizations */
	static SortedMap<String, String> organizationURIMappings = null;

	/** All the mappings between M0 and target URIs for families, series, operations and indicators */
	static Map<String, String> allURIMappings = null;

	/**
	 * Return a dataset containing two named graphs: one for families, series and operations, and one for indicators.
	 * 
	 * @param operationGraph The URI to use for the 'operations' graph.
	 * @param indicatorGraph The URI to use for the 'indicators' graph.
	 * @return A Jena <code>Dataset</code> containing the information organized in two graphs.
	 */
	public static Dataset convertAllOperationsAndIndicators(String operationGraph, String indicatorGraph) {

		logger.debug("Extracting M0 dataset with graph: " + operationGraph + " for operations and graph " + indicatorGraph + " for indicators");
		Dataset dataset = DatasetFactory.create();
		dataset.addNamedModel(operationGraph, convertAllOperations());
		dataset.addNamedModel(indicatorGraph, convertIndicators());

		return dataset;
	}

	/**
	 * Extracts the code lists from the M0 model and restructures them as SKOS concept schemes, keeping M0 URIs.
	 * 
	 * @return A Jena <code>Model</code> containing the M0 code lists as SKOS concept schemes.
	 */
	public static Model convertCodeLists() {

		// Mappings between M0 'attribute URIs' and SKOS properties
		final Map<String, Property> clPropertyMappings = new HashMap<String, Property>();
		clPropertyMappings.put("ID", null); // ID is equal to code or code list number, no business meaning (and expressed with a weird property http://www.SDMX.org/.../message#values"
		clPropertyMappings.put("CODE_VALUE", SKOS.notation); // CODE_VALUE seems to be the notation, FIXME it is in French
		clPropertyMappings.put("ID_METIER", RDFS.comment); // ID_METIER is just TITLE - ID, store in a comment for now
		clPropertyMappings.put("TITLE", SKOS.prefLabel); // Can have French and English values
		final List<String> stringProperties = Arrays.asList("ID_METIER", "TITLE"); // Property whose values should have a language tag
		
		readDataset();
		logger.debug("Extracting code lists from M0 dataset " + M0_FILE_NAME);
		Model skosModel = ModelFactory.createDefaultModel();
		skosModel.setNsPrefix("rdfs", RDFS.getURI());
		skosModel.setNsPrefix("skos", SKOS.getURI());

		// Open the 'codelists' M0 model first to obtain the number of code lists and create them in SKOS model
		Model clM0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "codelists");
		// Code lists M0 URIs take the form http://baseUri/codelists/codelist/n, where n is an increment strictly inferior to the value of http://baseUri/codelists/codelist/sequence
		int clNumber = M0Extractor.getMaxSequence(clM0Model);
		logger.debug("Maximum sequence number for code lists is " + clNumber);

		// Then we read in the 'associations' model the mappings between code lists and codes and store them as a map
		// Mappings are of the form {code list URI}/RELATED_TO M0_RELATED_TO {code URI}/RELATED_TO
		Map<Integer, List<Integer>> codeMappings = new HashMap<Integer, List<Integer>>();
		Model associationsM0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		for (int index = 1; index <= clNumber; index++) {
			List<Integer> listOfCodes = new ArrayList<Integer>();
			Resource clRelationResource = clM0Model.createResource(M0_CODE_LISTS_BASE_URI + index + "/RELATED_TO");
			// Retrieve the numeric identifiers of the codes related to the current code list
			StmtIterator associationIterator = associationsM0Model.listStatements(clRelationResource, M0_RELATED_TO, (RDFNode)null);
			associationIterator.forEachRemaining(new Consumer<Statement>() {
				@Override
				public void accept(Statement statement) {
					// Get code identifier, which is the last-but-one part of the URI, cast to integer
					String[] pathElements = statement.getObject().asResource().getURI().split("/");
					Integer code = Integer.parseInt(pathElements[pathElements.length - 2]);
					listOfCodes.add(code);
				}
			});
			if (listOfCodes.size() > 0) {
                codeMappings.put(index, listOfCodes);
            }
		}
		logger.debug(codeMappings.size() + " code lists found in the 'codelists' M0 model");
		associationsM0Model.close();

		// Open the 'code' model and browse both 'codelists' and 'codes' models to produce the target SKOS model
		Model codeM0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "codes");
		// Main loop is on code lists
		for (int clIndex = 1; clIndex <= clNumber; clIndex++) {
			if (codeMappings.get(clIndex) == null)
             {
                continue; // Case of discontinuity in the numbering sequence
            }
			Resource clResource = clM0Model.createResource(M0_CODE_LISTS_BASE_URI + clIndex);
			Resource skosCLResource = skosModel.createResource(M0_CODE_LISTS_BASE_URI + clIndex, SKOS.ConceptScheme);
			logger.info("Creating code list " + skosCLResource.getURI() + " containing codes " + codeMappings.get(clIndex));
			for (String property : clPropertyMappings.keySet()) { // Looping through M0 properties
				if (clPropertyMappings.get(property) == null) {
                    continue;
                }
				Resource propertyResource = clM0Model.createResource(clResource.getURI() + "/" + property);
				StmtIterator valueIterator = clM0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
				if (!valueIterator.hasNext()) {
					logger.error("No value for property " + property + " of code list " + clResource.getURI());
					continue;
				}
				// Create the relevant statement in the SKOS model, adding a language tag if the property is in stringProperties
				if (stringProperties.contains(property)) {
					skosCLResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
				} else {
					skosCLResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
				}
				if (valueIterator.hasNext()) {
                    logger.error("Several values for property " + property + " of code list " + clResource.getURI());
                }
				valueIterator = clM0Model.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					skosCLResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
				}
			}
			// Read in the code mappings the list of codes associated to the current code list
			List<Integer> codesOfList = codeMappings.get(clIndex);
			for (int codeIndex : codesOfList) {
				Resource codeResource = codeM0Model.createResource(M0_CODES_BASE_URI + codeIndex);
				Resource skosCodeResource = skosModel.createResource(M0_CODES_BASE_URI + codeIndex, SKOS.Concept);
				// Create the statements associated to the code
				for (String property : clPropertyMappings.keySet()) {
					if (clPropertyMappings.get(property) == null) {
                        continue;
                    }
					Resource propertyResource = codeM0Model.createResource(codeResource.getURI() + "/" + property);
					StmtIterator valueIterator = codeM0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be exactly one)
					if (!valueIterator.hasNext()) {
						logger.error("No value for property " + property + " of code " + codeResource.getURI());
						continue;
					}
					if (stringProperties.contains(property)) {
						skosCodeResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "fr"));
					} else {
						skosCodeResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString()));
					}
					if (valueIterator.hasNext()) {
                        logger.error("Several values for property " + property + " of code " + codeResource.getURI());
                    }
					valueIterator = codeM0Model.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
					if (valueIterator.hasNext()) {
						skosCodeResource.addProperty(clPropertyMappings.get(property), skosModel.createLiteral(valueIterator.next().getObject().toString(), "en"));
					}
					// Finally, add the relevant SKOS properties between the code and the code list
					skosCodeResource.addProperty(SKOS.inScheme, skosCLResource);
					skosCodeResource.addProperty(SKOS.topConceptOf, skosCLResource);
					skosCLResource.addProperty(SKOS.hasTopConcept, skosCodeResource);
				}
			}
		}

		clM0Model.close();
		codeM0Model.close();

		return skosModel;
	}

	/**
	 * Extracts the information on organizations from the M0 model and restructures it according to ORG.
	 * For now we only extract the ID_CODE and (French) TITLE, and check consistency with target model (created from spreadsheet).
	 * 
	 * @return A Jena <code>Model</code> containing the M0 organizations in ORG format.
	 */
	public static Model convertOrganizations() {

		// Read dataset and create model to return and read target model for consistency check
		readDataset();
		logger.debug("Extracting information on organizations from M0 dataset " + M0_FILE_NAME);
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
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		// M0 URIs for organizations take the form http://baseUri/organismes/organisme/n, where n is an increment strictly inferior to the value of http://baseUri/organismes/organisme/sequence
		int orgNumber = M0Extractor.getMaxSequence(m0Model);
		logger.debug(orgNumber + " organizations found in 'organismes' model");

		for (int orgIndex = 1; orgIndex <= orgNumber; orgIndex++) {
			String resourceURI = "http://baseUri/organismes/organisme/" + orgIndex;
			logger.info("Creating organization " + resourceURI);
			Resource propertyResource = m0Model.createResource(resourceURI + "/ID_CODE");
			StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // There should be exactly one value
			String orgId = "";
			if (valueIterator.hasNext()) {
                orgId = valueIterator.next().getObject().toString().trim();
            }
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
			if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1))))
             {
                continue; // Insee organizations identifiers are like XNNN
            }
			// Look in the target model for an organization with identifier equal to orgId
			if (!targetModel.contains(null, DCTerms.identifier, orgId)) {
                logger.warn("Organization " + orgId + " not found in target model");
            }
			
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

		// For families and indicators, there are no fixed mappings: return an empty map
		if (("famille".equals(type)) || ("indicateur".equals(type))) {
			logger.debug("There are no fixed mappings for type " + type);
			return mappings;
		}

		// For operations, there are only a few cases where the Web4G identifier is fixed
		if ("operation".equals(type)) {
			logger.debug("Fixed mappings for operations are read from " + Configuration.M0_ID_TO_WEB4G_ID_FILE_NAME);
			for (Integer m0Id : m0ToWeb4GIdMappings.keySet()) {
                mappings.put(m0Id, operationResourceURI(m0ToWeb4GIdMappings.get(m0Id), type));
            }
			logger.debug("A total of  " + mappings.size() + " mappings for operations will be returned");
			return mappings;
		}

		// Here we should only have type 'serie'
		if (!("serie".equals(type))) {
			logger.error("Invalid resource type: " + type);
			return null;
		}

		// For series, the Web4G identifier is obtained through the DDS identifier: extract the ID_DDS property from the series model
		logger.debug("Mappings for series are defined between DDS identifiers and target (Web4G) identifiers, read from " + Configuration.DDS_ID_TO_WEB4G_ID_FILE_NAME);
		logger.debug("So the correspondence between series identifier and DDS identifier is needed to calculate the fixed URI mappings");
		String m0SeriesGraphURI = M0_BASE_GRAPH_URI + "series";
		Model m0IdDDSModel = M0Extractor.extractAttributeStatements(m0Dataset.getNamedModel(m0SeriesGraphURI), "ID_DDS");
		logger.debug("Extracted ID_DDS property statements from graph " + m0SeriesGraphURI + ", size of resulting model is " + m0IdDDSModel.size());

		m0IdDDSModel.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Retrieve the M0 numeric identifier, assuming URI structure http://baseUri/{type}s/{type}/{nn}/ID_DDS
				String m0URI = statement.getSubject().getURI().toString();
				Integer m0Id = Integer.parseInt(m0URI.split("/")[5]);
				// Retrieve the "DDS" identifier from the object of the ID_DDS statement (eg OPE-ENQ-SECTORIELLE-ANNUELLE-ESA, skip the 'OPE-' start)
				String ddsId = statement.getObject().asLiteral().toString().substring(4);
				// Retrieve the "Web4G" identifier from the "DDS" identifier and the mappings contained in the Configuration class
				if (!ddsToWeb4GIdMappings.containsKey(ddsId)) {
					logger.warn("No correspondence found in mapping file for DDS identifier " + ddsId + " (M0 resource is " + m0URI + ")");
				} else {
					String web4GId = ddsToWeb4GIdMappings.get(ddsId);
					logger.trace("Correspondence found for series " + m0Id + " with DDS identifier " + ddsId + ": Web4G identifier is " + web4GId);
					String targetURI = operationResourceURI(web4GId, type);
					mappings.put(m0Id, targetURI);
				}
			}
		});
		m0IdDDSModel.close();
		logger.debug("A total of " + mappings.size() + " series URI mappings were calculated via the DDS identifier");

		// HACK Add special direct mappings
		Map<Integer, String[]> specialMappings = new HashMap<>();
		specialMappings.put(135, new String[]{"1241", "has an ID_DDS but it is not in the M0 dataset"});
		specialMappings.put(136, new String[]{"1371", "has an ID_DDS but it is not in the M0 dataset, changed from 1195, see mail 2/20"});
		specialMappings.put(137, new String[]{"1284", "has an ID_DDS but it is not in the M0 dataset"});
		specialMappings.put(21, new String[]{"1229", "new mapping added (mail RC 3/7)"});
		logger.debug("Adding " + specialMappings.size() + " special fixed mappings for series");
		for (Integer m0Id : specialMappings.keySet()) {
			mappings.put(m0Id, operationResourceURI(specialMappings.get(m0Id)[0], "serie"));
			logger.debug("Adding special mapping from M0 series " + m0Id + " to target series " + specialMappings.get(m0Id)[0] + " (" + specialMappings.get(m0Id)[1] + ")");
		}

		logger.debug("A total of " + mappings.size() + " URI mappings for series will be returned");

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
		SortedMap<String, String> uriMappings = new TreeMap<String, String>(Comparator.nullsFirst(new URIComparator()));
		List<String> types = Arrays.asList("famille", "serie", "operation", "indicateur");
		logger.info("Starting the creation of all the URI mappings for families, series, operations and indicators");

		// 1: Get fixed mappings and remove correspondent identifiers from available identifiers
		// Target identifiers range from 1001 upwards (except for families)
		List<Integer> availableNumbers = IntStream.rangeClosed(1001, 1999).boxed().collect(Collectors.toList());
		// First we have to remove from available numbers all those associated with fixed mappings
		// We have to do a complete pass on all types of objects because there is no separation of the ranges for identifiers of different types
		for (String resourceType : types) {
			Map<Integer, String> typeMappings = getIdURIFixedMappings(m0Dataset, resourceType);
			if (typeMappings.size() != 0) {
                logger.info("Number of fixed mappings for type " + resourceType + ": " + typeMappings.size() + ", a corresponding amount of available identifiers will be removed");
            }
			for (int index : typeMappings.keySet()) {
				// Add fixed mapping to the global list of all mappings
				uriMappings.put("http://baseUri/" + resourceType + "s/" + resourceType + "/" + index, typeMappings.get(index));
				int toRemove = Integer.parseInt(StringUtils.substringAfterLast(typeMappings.get(index), "/").substring(1));
				availableNumbers.removeIf(number -> number == toRemove); // Not super-efficient, but the list is not that big
			}
		}
		logger.info("Total number of fixed mappings: " + uriMappings.size());

		// 2: Attribute remaining identifiers to all resources that don't have a fixed mapping
		for (String resourceType : types) {
			idCounters.put(resourceType, 0); // Initialize identification counter for this type of resources
			// Get the model corresponding to this type of resource
			Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + resourceType + "s");
			int maxNumber = M0Extractor.getMaxSequence(m0Model);
			for (int index = 1; index <= maxNumber; index++) {
				String m0URI = "http://baseUri/" + resourceType + "s/" + resourceType + "/" + index;
				if (uriMappings.containsKey(m0URI))
                 {
                    continue; // Fixed mappings already dealt with
                }
				// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
				Resource m0Resource = m0Model.createResource(m0URI);
				if (!m0Model.contains(m0Resource, null))
                 {
                    continue; // Verify that M0 resource actually exist
                }
				// At this point, the resource exists and has not a fixed mapping: attribute target URI based on first available number, except for families who use the M0 index
				if ("famille".equals(resourceType)) {
                    uriMappings.put(m0Resource.getURI(), operationResourceURI(Integer.toString(index), resourceType));
                }
                else {
					Integer targetId = availableNumbers.get(0);
					availableNumbers.remove(0);
					uriMappings.put(m0Resource.getURI(), operationResourceURI(targetId.toString(), resourceType));
				}
				idCounters.put(resourceType, idCounters.get(resourceType) + 1);
				if (idRanges.get(resourceType) > 0) {
                    idRanges.put(resourceType, idRanges.get(resourceType) - 1);
                }
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

		// 3: Check that there is no duplicate on the mapped URIs
		logger.debug("Checking for duplicate values in the mapped target URIs"); 
		List<String> mappedURIs = new ArrayList<String>();
		for (String m0URI : uriMappings.keySet()) {
			String mappedURI = uriMappings.get(m0URI);
			if (mappedURIs.contains(mappedURI)) {
                logger.error("Duplicate value in mappings: " + mappedURI);
            }
            else {
                mappedURIs.add(mappedURI);
            }
		}

		logger.info("Total number of URI mappings for operations, series, families and indicators: " + uriMappings.size());
		return uriMappings;
	}

	/**
	 * Extracts the informations on the families from the M0 model and converts them according to the target model.
	 * Also adds the references to statistical themes.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for families.
	 */
	public static Model convertFamilies() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null)
         {
            allURIMappings = createURIMappings(); // Not indispensable for families
        }
		// Get the family-themes relations
		Map<String, List<String>> familyThemesRelations = getFamilyThemesRelations();

		logger.debug("Extracting the information on families from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "familles");

		// Create the target model and set appropriate prefix mappings
		Model familyModel = ModelFactory.createDefaultModel();
		familyModel.setNsPrefix("rdfs", RDFS.getURI());
		familyModel.setNsPrefix("skos", SKOS.getURI());
		familyModel.setNsPrefix("dcterms", DCTerms.getURI());
		familyModel.setNsPrefix("org", ORG.getURI());
		familyModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Family M0 URIs take the form http://baseUri/familles/famille/n, where n is an increment strictly inferior to the sequence number
		int familyMaxNumber = M0Extractor.getMaxSequence(m0Model);
		logger.debug("Maximum index for families is " + familyMaxNumber);

		// Loop on the family index
		int familyRealNumber = 0;
		for (int familyIndex = 1; familyIndex <= familyMaxNumber; familyIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/familles/famille/" + familyIndex);
			if (!m0Model.contains(m0Resource, null))
             {
                continue; // No actual family for the current index
            }
			familyRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // Should really not happen
				targetURI = operationResourceURI(Integer.toString(familyIndex), "famille");
				logger.error("No target URI found for M0 family " + m0Resource.getURI() + ", defaulting to " + targetURI);
			}
			Resource targetResource = familyModel.createResource(targetURI, Configuration.STATISTICAL_OPERATION_FAMILY);
			logger.info("Creating target family " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
			// Add relation from family to theme(s)
			if (familyThemesRelations.containsKey(targetURI)) {
				for (String themeURI : familyThemesRelations.get(targetURI)) {
					targetResource.addProperty(DCTerms.subject, familyModel.createResource(themeURI));
					logger.debug("Adding theme " + themeURI + " to family");
				}
			}
            else {
                logger.warn("No statistical theme found for family " + targetURI);
            }
			
		}
		logger.info(familyRealNumber + " families extracted");
		m0Model.close();

		return familyModel;
	}

	/**
	 * Extracts the informations on the series from the M0 model and converts them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for series.
	 */
	public static Model convertSeries() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) {
            allURIMappings = createURIMappings();
        }

		logger.debug("Extracting the information on series from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "series");

		// Create the target model and set appropriate prefix mappings
		Model seriesModel = ModelFactory.createDefaultModel();
		seriesModel.setNsPrefix("rdfs", RDFS.getURI());
		seriesModel.setNsPrefix("skos", SKOS.getURI());
		seriesModel.setNsPrefix("dcterms", DCTerms.getURI());
		seriesModel.setNsPrefix("org", ORG.getURI());
		seriesModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Series M0 URIs take the form http://baseUri/series/serie/n, where n is an increment strictly inferior to the sequence number
		int seriesMaxNumber = M0Extractor.getMaxSequence(m0Model);
		logger.debug("Maximum index for series is " + seriesMaxNumber);

		// Loop on series number, but actually not all values of index correspond to existing series so the existence of the resource has to be tested
		int seriesRealNumber = 0;
		for (int seriesIndex = 1; seriesIndex <= seriesMaxNumber; seriesIndex++) {
			// The following instruction does not actually add the resource to the model, so the test on the next line will work as expected
			Resource m0Resource = m0Model.createResource("http://baseUri/series/serie/" + seriesIndex);
			if (!m0Model.contains(m0Resource, null)) {
                continue;
            }
			seriesRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.error("No target URI found for M0 series " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = seriesModel.createResource(targetURI, Configuration.STATISTICAL_OPERATION_SERIES);
			logger.info("Creating target series " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0Model, m0Resource);
		}
		logger.info(seriesRealNumber + " series extracted");
		m0Model.close();

		return seriesModel;
	}

	/**
	 * Extracts the informations on the operations from the M0 model and restructures them according to the target model.
	 * 
	 * @return A Jena <code>Model</code> containing the target RDF model for operations.
	 */
	public static Model convertOperations() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) {
            allURIMappings = createURIMappings();
        }

		logger.debug("Extracting the information on operations from dataset " + M0_FILE_NAME);
		Model m0Model = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "operations");

		// Create the target model and set appropriate prefix mappings
		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Operation M0 URIs take the form http://baseUri/operations/operation/n, where n is an increment strictly inferior to the sequence number
		int operationMaxNumber = M0Extractor.getMaxSequence(m0Model);
		logger.debug("Maximum index for operations is " + operationMaxNumber);

		// Loop on the operation index
		int operationRealNumber = 0;
		for (int operationIndex = 1; operationIndex <= operationMaxNumber; operationIndex++) {
			Resource m0Resource = m0Model.createResource("http://baseUri/operations/operation/" + operationIndex);
			if (!m0Model.contains(m0Resource, null))
             {
                continue; // Cases where the index is not attributed
            }
			operationRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.info("No target URI found for M0 operation " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = operationModel.createResource(targetURI, Configuration.STATISTICAL_OPERATION);
			logger.info("Creating target operation " + targetURI + " from M0 resource " + m0Resource.getURI());
			// Extract TITLE, ALT_LABEL and MILLESIME (or MILESSIME)
			fillLiteralProperties(targetResource, m0Model, m0Resource);
			for (String propertyName : Arrays.asList("MILLESIME", "MILESSIME")) {
				Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + propertyName);
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) {
                    continue;
                }
				String year = valueIterator.next().getObject().asLiteral().toString().trim();
				if (year.length() == 0) {
                    continue;
                }
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
	public static Model convertIndicators() {

		// Read the M0 model and create the URI mappings if necessary
		readDataset();
		if (allURIMappings == null) {
            allURIMappings = createURIMappings();
        }

		logger.debug("Reading the M0 model on indicators from dataset " + M0_FILE_NAME);
		Model m0IndicatorssModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "indicateurs");

		// Create the target model and set appropriate prefix mappings
		Model indicatorModel = ModelFactory.createDefaultModel();
		indicatorModel.setNsPrefix("skos", SKOS.getURI());
		indicatorModel.setNsPrefix("dcterms", DCTerms.getURI());
		indicatorModel.setNsPrefix("prov", PROV.getURI());
		indicatorModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");
		// Indicator M0 URIs take the form http://baseUri/indicateurs/indicateur/n, where n is an increment strictly inferior to the sequence number
		int indicatorMaxNumber = M0Extractor.getMaxSequence(m0IndicatorssModel);
		logger.debug("Maximum index for indicators is " + indicatorMaxNumber);

		// Loop on the indicator index
		int indicatorRealNumber = 0;
		for (int indicatorIndex = 1; indicatorIndex <= indicatorMaxNumber; indicatorIndex++) {
			Resource m0Resource = m0IndicatorssModel.createResource("http://baseUri/indicateurs/indicateur/" + indicatorIndex);
			if (!m0IndicatorssModel.contains(m0Resource, null))
             {
                continue; // Cases where the index is not attributed
            }
			indicatorRealNumber++;
			String targetURI = allURIMappings.get(m0Resource.getURI());
			if (targetURI == null) { // There is definitely a problem if the M0 URI is not in the mappings
				logger.info("No target URI found for M0 indicator " + m0Resource.getURI());
				continue;
			}
			Resource targetResource = indicatorModel.createResource(targetURI, Configuration.STATISTICAL_INDICATOR);
			logger.info("Creating indicator " + targetURI + " from M0 resource " + m0Resource.getURI());
			fillLiteralProperties(targetResource, m0IndicatorssModel, m0Resource);
		}
		m0IndicatorssModel.close();

		logger.info(indicatorRealNumber + " indicators extracted, now adding the PRODUCED_FROM, RELATED_TO and REPLACES relations");
		logger.debug("Reading the M0 model on associations from dataset " + M0_FILE_NAME);
		Model m0AssociationModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "asssociations");
		Map<String, List<String>> multipleRelations = M0Extractor.extractProductionRelations(m0AssociationModel);
		for (String indicatorM0URI : multipleRelations.keySet()) {
			String indicatorTargetURI = allURIMappings.get(indicatorM0URI);
			if (indicatorTargetURI == null) {
				logger.info("No target URI found for M0 indicator " + indicatorM0URI);
				continue;				
			}
			Resource indicatorResource = indicatorModel.createResource(indicatorTargetURI);
			for (String seriesM0URI : multipleRelations.get(indicatorM0URI)) {
				String seriesTargetURI = allURIMappings.get(seriesM0URI);
				if (seriesTargetURI == null) {
					logger.info("No target URI found for M0 series " + seriesM0URI);
					continue;
				}
				indicatorResource.addProperty(PROV.wasGeneratedBy, indicatorModel.createResource(seriesTargetURI));
				logger.debug("PROV wasGeneratedBy property created from indicator " + indicatorTargetURI + " to series " + seriesTargetURI);
			}
		}
		// RELATED_TO relations (limited to indicators)
		multipleRelations = M0Extractor.extractRelations(m0AssociationModel);
		for (String startM0URI : multipleRelations.keySet()) {
			if (!startM0URI.startsWith("http://baseUri/indicateurs")) {
                continue;
            }
			Resource startResource = indicatorModel.createResource(allURIMappings.get(startM0URI));
			for (String endM0URI : multipleRelations.get(startM0URI)) {
				Resource endResource = indicatorModel.createResource(allURIMappings.get(endM0URI));
				startResource.addProperty(RDFS.seeAlso, endResource); // extractRelations returns each relation twice (in each direction)
				logger.debug("See also property created from resource " + startResource.getURI() + " to resource " + endResource.getURI());
			}
		}
		// REPLACES relations (limited to indicators)
		multipleRelations = M0Extractor.extractReplacements(m0AssociationModel);
		for (String replacingM0URI : multipleRelations.keySet()) {
			if (!replacingM0URI.startsWith("http://baseUri/indicateurs")) {
                continue;
            }
			Resource replacingResource = indicatorModel.createResource(allURIMappings.get(replacingM0URI));
			for (String replacedM0URI : multipleRelations.get(replacingM0URI)) {
				Resource replacedResource = indicatorModel.createResource(allURIMappings.get(replacedM0URI));
				replacingResource.addProperty(DCTerms.replaces, replacedResource);
				replacedResource.addProperty(DCTerms.isReplacedBy, replacingResource);
				logger.debug("Replacement property created between resource " + replacingResource.getURI() + " replacing resource " + replacedResource.getURI());
			}
		}
		m0AssociationModel.close();

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
		for (String property : propertyMappings.keySet()) {
			Resource propertyResource = m0Model.createResource(m0Resource.getURI() + "/" + property);
			if (stringProperties.contains(property)) {
				// Start with the string properties that can have a French and an English value (except ALT_LABEL?)
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null); // Find French values (there should be at most one)
				if (valueIterator.hasNext()) {
					// Must go through lexical values to avoid double escaping
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					if (propertyValue.length() == 0)
                     {
                        continue; // Ignore empty values for text properties
                    }
					// Remove this is ALT_LABEL should have a language tag
					if ("ALT_LABEL".equals(property)) {
						targetResource.addProperty(propertyMappings.get(property), ResourceFactory.createStringLiteral(propertyValue));
						continue;
					}
					// Create the current property on the target resource, with string value tagged '@fr'
					Literal langValue = ResourceFactory.createLangLiteral(propertyValue, "fr");
					targetResource.addProperty(propertyMappings.get(property), langValue);
				}
				valueIterator = m0Model.listStatements(propertyResource, M0_VALUES_EN, (RDFNode)null); // Find English values (can be zero or one)
				if (valueIterator.hasNext()) {
					// Create the current property on the target resource, with string value tagged '@en'
					String propertyValue = valueIterator.next().getObject().asLiteral().getLexicalForm().trim();
					targetResource.addProperty(propertyMappings.get(property), ResourceFactory.createLangLiteral(propertyValue, "en"));
				}
			} else {
				// In the other properties, select the coded ones (SOURCE_CATEGORY and FREQ_COLL)
				// TODO FREQ_DISS (at least for indicators)? But there is no property mapping for this attribute
				StmtIterator valueIterator = m0Model.listStatements(propertyResource, M0_VALUES, (RDFNode)null);
				if (!valueIterator.hasNext()) {
                    continue;
                }
				// Then process the SOURCE_CATEGORY and FREQ_COLL attributes, values are taken from code lists
				if (("SOURCE_CATEGORY".equals(property)) || ("FREQ_COLL".equals(property))) {
					String frenchLabel = ("SOURCE_CATEGORY".equals(property)) ? "Catégorie de source" : "Fréquence"; // TODO Find better method
					String codeURI = inseeCodeURI(valueIterator.next().getObject().toString(), frenchLabel);
					targetResource.addProperty(propertyMappings.get(property), m0Model.createResource(codeURI));
				}
				// The remaining (object) properties (ORGANISATION, STAKEHOLDERS, REPLACES and RELATED_TO) are processed by dedicated methods.
			}
		}
	}

	/**
	 * Extracts from the current M0 dataset, and converts to the target model, all informations about families, series and operations, and relations between them
	 * 
	 * @return A Jena model containing all the statements.
	 */
	public static Model convertAllOperations() {

		Model operationModel = ModelFactory.createDefaultModel();
		operationModel.setNsPrefix("rdfs", RDFS.getURI());
		operationModel.setNsPrefix("skos", SKOS.getURI());
		operationModel.setNsPrefix("dcterms", DCTerms.getURI());
		operationModel.setNsPrefix("insee", "http://rdf.insee.fr/def/base#");

		// First add models on families, series and operations (this will read the dataset and create the URI mappings)
		operationModel.add(convertFamilies()).add(convertSeries()).add(convertOperations());

		// Now read the links of various kinds between families, series and operations, starting with hierarchies
		// For readability, we do not verify in this method that the M0 URIs are in the mappings
		Model m0AssociationModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "associations");
		Map<String, String> simpleRelations = M0Extractor.extractHierarchies(m0AssociationModel);
		for (String chilM0dURI : simpleRelations.keySet()) {
			Resource child = operationModel.createResource(allURIMappings.get(chilM0dURI));
			Resource parent = operationModel.createResource(allURIMappings.get(simpleRelations.get(chilM0dURI)));
			child.addProperty(DCTerms.isPartOf, parent);
			parent.addProperty(DCTerms.hasPart, child);
			logger.debug("Hierarchy properties created between child " + child.getURI() + " and parent " + parent.getURI());
		}
		// RELATED_TO relations (excluding indicators)
		Map<String, List<String>> multipleRelations = M0Extractor.extractRelations(m0AssociationModel);
		for (String startM0URI : multipleRelations.keySet()) {
			if (startM0URI.startsWith("http://baseUri/indicateurs")) {
                continue;
            }
			Resource startResource = operationModel.createResource(allURIMappings.get(startM0URI));
			for (String endM0URI : multipleRelations.get(startM0URI)) {
				Resource endResource = operationModel.createResource(allURIMappings.get(endM0URI));
				startResource.addProperty(RDFS.seeAlso, endResource); // extractRelations returns each relation twice (in each direction)
				logger.debug("See also property created from resource " + startResource.getURI() + " to resource " + endResource.getURI());
			}
		}
		// REPLACES relations (excluding indicators)
		multipleRelations = M0Extractor.extractReplacements(m0AssociationModel);
		for (String replacingM0URI : multipleRelations.keySet()) {
			if (replacingM0URI.startsWith("http://baseUri/indicateurs"))
             {
                continue; // There is no cross-relation of replacement between operations and indicators
            }
			Resource replacingResource = operationModel.createResource(allURIMappings.get(replacingM0URI));
			for (String replacedM0URI : multipleRelations.get(replacingM0URI)) {
				Resource replacedResource = operationModel.createResource(allURIMappings.get(replacedM0URI));
				replacingResource.addProperty(DCTerms.replaces, replacedResource);
				replacedResource.addProperty(DCTerms.isReplacedBy, replacingResource);
				logger.debug("Replacement property created between resource " + replacingResource.getURI() + " replacing resource " + replacedResource.getURI());
			}
		}
		// Finally, add relations to organizations
		for (Configuration.OrganizationRole role : Configuration.OrganizationRole.values()) {
			logger.debug("Creating organizational relations with role " + role.toString());
			multipleRelations = M0Extractor.extractOrganizationalRelations(m0AssociationModel, role);
			for (String operationM0URI : multipleRelations.keySet()) {
				Resource operationResource = operationModel.createResource(allURIMappings.get(operationM0URI));
				for (String organizationURI : multipleRelations.get(operationM0URI)) {
					Resource organizationResource = ResourceFactory.createResource(convertM0OrganizationURI(organizationURI));
					operationResource.addProperty(role.getProperty(), organizationResource);
					logger.debug("Organizational relation created between resource " + operationResource.getURI() + " and organization " + organizationResource.getURI());
				}
			}
		}
		m0AssociationModel.close();
		return operationModel;
	}

	/**
	 * Reads from an external spreadsheet the relations between the families and the statistical themes and stores them as a sorted map.
	 * The map keys will be the family URIs and the values the lists of theme URIs.
	 * 
	 * @return A map containing the relations or <code>null</code> in case of error.
	 */
	public static SortedMap<String, List<String>> getFamilyThemesRelations() {

		SortedMap<String, List<String>> relationMappings = new TreeMap<String, List<String>>();

		Workbook familyThemesWorkbook = null;
		File xlsxFile = new File(FAMILY_THEMES_XLSX_FILE_NAME);
		try {
			logger.info("Reading family-themes relations from Excel file " + xlsxFile.getAbsolutePath());
			familyThemesWorkbook = WorkbookFactory.create(xlsxFile);
		} catch (Exception e) {
			logger.fatal("Error while opening Excel file - " + e.getMessage());
			return relationMappings;
		}

		Iterator<Row> rows = familyThemesWorkbook.getSheetAt(0).rowIterator();
		while (rows.hasNext()) {
			Row row = rows.next();
			// Family URI is in column B
			String familyURI = row.getCell(1).toString();
			relationMappings.put(familyURI, new ArrayList<String>());
			// First theme identifier is in column D, never empty
			String themeId = row.getCell(3).toString();
			relationMappings.get(familyURI).add(themeURI(themeId));
			// Second theme identifier is in column E, can be empty
			Cell themeCell = row.getCell(4);
			if (themeCell != null) {
				themeId = themeCell.toString();
				relationMappings.get(familyURI).add(themeURI(themeId));
			}
			logger.debug("Themes registered for family " + familyURI + ": " + relationMappings.get(familyURI));
		}
		try { familyThemesWorkbook.close(); } catch (IOException ignored) { }

		return relationMappings;
	}

	/**
	 * Reads the complete M0 dataset if it has not been read already.
	 */
	protected static void readDataset() {
		if (m0Dataset == null) {
			m0Dataset = RDFDataMgr.loadDataset(M0_FILE_NAME);
			logger.debug("M0 dataset read from file " + M0_FILE_NAME);
		}
	}

	/**
	 * Reads the mappings between M0 and target URIs for organizations.
	 * 
	 * @return A sorted map in which the keys are the M0 URIs of the organizations and the values their target URIs.
	 */
	public static SortedMap<String, String> readOrganizationURIMappings() {

		readDataset();
		SortedMap<String, String> organizationURIMappings = new TreeMap<String, String>();
		// Read the 'organismes' model and loop through the statements with 'ID_CODE' subjects
		Model m0OrganizationsModel = m0Dataset.getNamedModel(M0_BASE_GRAPH_URI + "organismes");
		Model extractModel = M0Extractor.extractAttributeStatements(m0OrganizationsModel, "ID_CODE");
		extractModel.listStatements().forEachRemaining(new Consumer<Statement>() {
			@Override
			public void accept(Statement statement) {
				// Get the M0 URI (just strip the /ID_CODE part)
				String m0URI = StringUtils.removeEnd(statement.getSubject().toString(), "/ID_CODE");
				// Read the value of the property
				String orgId = statement.getObject().asLiteral().toString();
				// HACK Organization 81 has a weird identifier
				if (m0URI.endsWith("/81")) {
                    orgId = "Drees";
                }
				String orgURI = null;
				if ((orgId.length() == 4) && (StringUtils.isNumeric(orgId.substring(1)))) {
                    orgURI = inseeUnitURI("DG75-" + orgId);
                }
                else {
                    orgURI = organizationURI(orgId);
                }
				organizationURIMappings.put(m0URI, orgURI);
			}
		});
		return organizationURIMappings;
	}

	/**
	 * Converts an M0 organization resource URI into the corresponding target URI.
	 * 
	 * @param m0URI The M0 organization resource URI.
	 * @return The target URI for the resource.
	 */
	public static String convertM0OrganizationURI(String m0URI) {

		if (organizationURIMappings == null) {
            organizationURIMappings = readOrganizationURIMappings();
        }
		if (organizationURIMappings.containsKey(m0URI)) {
            return organizationURIMappings.get(m0URI);
        }
		return null;
	}
}
