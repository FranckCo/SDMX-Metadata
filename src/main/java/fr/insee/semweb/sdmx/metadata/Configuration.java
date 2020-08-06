package fr.insee.semweb.sdmx.metadata;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.DCTypes;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;

import fr.insee.semweb.utils.Utils;

/**
 * Configuration parameters, useful resources and methods, etc.
 * 
 * @author Franck
 */
public class Configuration {

	// Input files

	/** Excel file containing the SIMS/SIMSFr models */
	public static String SIMS_XLSX_FILE_NAME = "src/main/resources/data/SIMSFR_V20200225.xlsx";
	/** Excel file containing the code lists */
	public static String CL_XLSX_FILE_NAME = "src/main/resources/data/CODE_LISTS_20200615.xlsx";
	/** Turtle file containing the SDMX metadata model vocabulary */
	public static String SDMX_MM_TURTLE_FILE_NAME = "src/main/resources/data/sdmx-metadata.ttl";
	/** Excel file containing the information on organizations */
	public static String ORGANIZATIONS_XLSX_FILE_NAME = "src/main/resources/data/OrganisationScheme_20170719.xlsx";
	/** TriG file containing the "M0" (temporary model) RDF dataset */
	public static String M0_FILE_NAME = "src/main/resources/data/sauvegardeGSM_20200702.trig";
	/** Excel file containing the links between families and themes */
	public static String FAMILY_THEMES_XLSX_FILE_NAME = "src/main/resources/data/themes-familles.xlsx";
	/** Correspondence between DDS identifiers and Web4G identifiers for series */
	public static String DDS_ID_TO_WEB4G_ID_FILE_NAME = "src/main/resources/data/idSources.csv";
	/** Correspondence between M0 identifiers and Web4G identifiers for operations */
	public static String M0_ID_TO_WEB4G_ID_FILE_NAME = "src/main/resources/data/idOperations.csv";

	// Output files

	/** Concepts and concept schemes associated to the SIMS attributes */
	public static String SIMS_CS_TURTLE_FILE_NAME = "src/main/resources/data/sims-cs.ttl";
	/** Concepts and concept schemes associated to the SIMSFr attributes */
	public static String SIMS_FR_CS_TURTLE_FILE_NAME = "src/main/resources/data/sims-fr-cs.ttl";
	/** SIMS metadata structure definition and related resources */
	public static String SIMS_MSD_TURTLE_FILE_NAME = "src/main/resources/data/sims-msd.ttl";
	/** SIMS-FR metadata structure definition and related resources */
	public static String SIMS_FR_MSD_TURTLE_FILE_NAME = "src/main/resources/data/sims-fr-msd.ttl";
	/** SIMSFr code lists */
	public static String SIMS_CL_TURTLE_FILE_NAME = "src/main/resources/data/sims-cl.ttl";
	/** Concepts and concept schemes for the categorization of the operations and products */
	public static String THEMES_TURTLE_FILE_NAME = "src/main/resources/data/themes.ttl";

	// Constants for naming

	/** Base URI for Insee's base ontology */
	public static String BASE_INSEE_ONTO_URI = "http://rdf.insee.fr/def/base#";
	/** Base URI for SIMSv2 resources */ // TODO Officialize SIMS namespace
	public static String BASE_SIMS_URI = "http://ec.europa.eu/eurostat/simsv2/";
	/** Base URI for SIMSv2FR resources */
	public static String BASE_SIMS_FR_URI = "http://id.insee.fr/qualite/simsv2fr/";
	/** Base URI for SIMS metadata reports */
	protected static String QUALITY_BASE_URI = "http://id.insee.fr/qualite/";
	/** Base URI for SIMS quality metrics */
	protected static String METRIC_BASE_URI = BASE_SIMS_URI + "metric/";
	/** Prefix for the SDMX metadata model vocabulary */
	public static String SDMX_MM_PREFIX = "sdmx-mm";
	/** Base URI for the SDMX metadata model vocabulary */
	public static String SDMX_MM_BASE_URI = "http://www.w3.org/ns/sdmx-mm#";
	/** Base URI for the SDMX code lists */
	public static String SDMX_CODE_BASE_URI = "http://purl.org/linked-data/sdmx/2009/code#";
	/** Base URI for the names of the graphs in M0 dataset (add 'familles', 'series', 'operations', 'organismes', 'indicateurs', 'documents','documentations', 'codelists', 'codes', 'liens', 'associations') */
	public static String M0_BASE_GRAPH_URI = "http://rdf.insee.fr/graphe/";
	/** Base URI for the names of the graphs in target dataset */
	public static String INSEE_BASE_GRAPH_URI = "http://rdf.insee.fr/graphes/";
	/** Base URI for code list resources in M0 */
	static String M0_CODE_LISTS_BASE_URI = "http://baseUri/codelists/codelist/";
	/** Base URI for code item resources in M0 */
	static String M0_CODES_BASE_URI = "http://baseUri/codes/code/";
	/** Base URI for SIMS-related resources in M0 */
	static String M0_SIMS_BASE_URI = "http://baseUri/documentations/documentation/";
	/** Base URI for Insee operations (and families, series) */
	public static String INSEE_OPS_BASE_URI = "http://id.insee.fr/operations/";
	/** Base URI for Insee codes */
	public static String INSEE_CODES_BASE_URI = "http://id.insee.fr/codes/";
	/** Base URI for Insee code concepts */
	public static String INSEE_CODE_CONCEPTS_BASE_URI = INSEE_CODES_BASE_URI + "concept/";
	/** Base URI for organizations */
	public static String INSEE_ORG_BASE_URI = "http://id.insee.fr/organisations/";

	// Resources in the M0 model

	/** The ubiquitous 'values' property in M0 */
	protected static Property M0_VALUES = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#values");
	/** The ubiquitous 'values' property in M0, English version */
	protected static Property M0_VALUES_EN = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#valuesGb");
	/** The ubiquitous 'relatedTo' property in M0 */
	static Property M0_RELATED_TO = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedTo");
	/** The ubiquitous 'relatedTo' property in M0, English version */
	static Property M0_RELATED_TO_EN = ResourceFactory.createProperty("http://www.SDMX.org/resources/SDMXML/schemas/v2_0/message#relatedToGb");
	/** The ubiquitous 'varSims' property in M0 */
	static Property M0_VAR_SIMS = ResourceFactory.createProperty("http://rem.org/schema#varSims");

	// Resources in the target model

	/** Classes for operations and the like */ // TODO Use COOS resources
	public static final Resource STATISTICAL_OPERATION_FAMILY = ResourceFactory.createResource(BASE_INSEE_ONTO_URI + "StatisticalOperationFamily");
	public static final Resource STATISTICAL_OPERATION_SERIES = ResourceFactory.createResource(BASE_INSEE_ONTO_URI + "StatisticalOperationSeries");
	public static final Resource STATISTICAL_OPERATION = ResourceFactory.createResource(BASE_INSEE_ONTO_URI + "StatisticalOperation");
	public static final Resource STATISTICAL_INDICATOR = ResourceFactory.createResource(BASE_INSEE_ONTO_URI + "StatisticalIndicator");

	/** The reported attribute */
	protected static Resource SIMS_REPORTED_ATTRIBUTE = ResourceFactory.createResource("http://www.w3.org/ns/sdmx-mm#ReportedAttribute");
	/** The metadata report */
	protected static Resource SIMS_METADATA_REPORT = ResourceFactory.createResource("http://www.w3.org/ns/sdmx-mm#MetadataReport");
	/** The property linking a report to its target */
	protected static Property SIMS_TARGET = ResourceFactory.createProperty("http://www.w3.org/ns/sdmx-mm#target");
	/** The range of metadata attribute properties corresponding to 'rich text' attributes */
	public static Resource RICH_TEXT_MAP_RANGE = DCTypes.Text;
	/** The range of the 'data collector' attribute */
	public static Property DATA_COLLECTOR_PROPERTY = ResourceFactory.createProperty("http://rdf.insee.fr/def/base/dataCollector");
	/** The range of metadata attribute properties corresponding to the 'territory' attributes */
	public static Resource TERRITORY_MAP_RANGE = ResourceFactory.createResource("http://www.opengis.net/ont/geosparql#Feature");
	/** RDF predicate connecting rich text resources to documents/links resources */
	public static Property ADDITIONAL_MATERIAL = ResourceFactory.createProperty("http://rdf.insee.fr/def/base#additionalMaterial");

	/** Language codes (for now using http://psi.oasis-open.org/iso/639/) */
	public static final Resource LANGUAGE_EN = ResourceFactory.createResource("http://psi.oasis-open.org/iso/639/#eng");
	public static final Resource LANGUAGE_FR = ResourceFactory.createResource("http://psi.oasis-open.org/iso/639/#fra");

	// Parameters, mapping and other useful objects

	/** Specifies if reported attributes are created or if attribute properties are directly attached to the report */
	public static boolean CREATE_REPORTED_ATTRIBUTES = true;

	// Numbers of the columns where the SIMS information is stored in SIMS and SIMSFr Excel formats
	// Notation and name, Concept name, Concept code, Description, Representation, ESS guidelines, Quality indicators
	public static int[] SIMS_COLUMNS_SIMS = {0, 1, 2, 3, 4, 5, 6};
	public static int[] SIMS_COLUMNS_SIMS_FR = {1, 5, 4, 6, 9, -1, -1};

	/** Static mapping between direct attributes of operations and RDF Properties */
	public static Map<String, Property> propertyMappings;
	static {
		propertyMappings = new HashMap<String, Property>();
		propertyMappings.put("TITLE", SKOS.prefLabel);
		propertyMappings.put("ALT_LABEL", SKOS.altLabel);
		propertyMappings.put("SOURCE_CATEGORY", DCTerms.type);
		propertyMappings.put("SUMMARY", DCTerms.abstract_);
		propertyMappings.put("HISTORY", SKOS.historyNote);
		//propertyMappings.put("FREQ_COLL", DCTerms.accrualPeriodicity); FREQ_COLL is no longer a direct property
		propertyMappings.put("ORGANISATION", DCTerms.publisher);
		propertyMappings.put("STAKEHOLDERS", DCTerms.contributor);
		propertyMappings.put("DATA_COLLECTOR", DATA_COLLECTOR_PROPERTY); // TODO Publish in base ontology
		propertyMappings.put("REPLACES", DCTerms.replaces);
		propertyMappings.put("RELATED_TO", RDFS.seeAlso);
	}
	/** Those of the direct attributes of operations that have string values (and can have an English version) */
	public static List<String> stringProperties = Arrays.asList("TITLE", "ALT_LABEL", "SUMMARY", "HISTORY"); // TODO Verify that ALT_LABEL is in this list

	/** Correspondence between DDS identifiers and Web4G identifiers (for series) */
	public static Map<String, String> ddsToWeb4GIdMappings = null;
	static {
		try (Stream<String> stream = Files.lines(Paths.get(DDS_ID_TO_WEB4G_ID_FILE_NAME))) {
			ddsToWeb4GIdMappings = stream.filter(line -> line.startsWith("FR-")).map(line -> line.substring(3)).collect(Collectors.toMap(line -> line.split(",")[0], line -> line.split(",")[1]));
			// HACK Delete three lines to correct errors
			ddsToWeb4GIdMappings.remove("ENQUETE-PATRIMOINE"); // Series 125, web4G id 1282 is instead attributed to the 2014 survey (operation 158)
			ddsToWeb4GIdMappings.remove("ENQ-SDF"); // Series 85, web4G id 1267 is instead attributed to the 2001 survey (operation 189)
			ddsToWeb4GIdMappings.remove("ENQ-TRAJECTOIRES-2008-TEO"); // Series 118, web4G id 1276 is instead attributed to the survey (operation 199)
		} catch (IOException ignored) {
			// Do nothing, we will have an exception when trying to use the mapping
		}
	}

	/** Correspondence between M0 identifiers and Web4G (target) identifiers (for operations) */
	public static Map<Integer, String> m0ToWeb4GIdMappings = null;
	static {
		try (Stream<String> stream = Files.lines(Paths.get(M0_ID_TO_WEB4G_ID_FILE_NAME))) {
			m0ToWeb4GIdMappings = stream.collect(Collectors.toMap(line -> Integer.parseInt(line.split(",")[0]), line -> line.split(",")[1]));
		} catch (IOException ignored) {
			// Do nothing, we will have an exception when trying to use the mapping
		}
	}

	/**
	 * Returns the URI of the SIMSv2/SIMSFr metadata structure definition.
	 * 
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension URI is returned.
	 * @return The URI of the SIMSv2/SIMSFr metadata structure definition.
	 */
	public static String simsMSDURI(boolean simsStrict) {
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "msd";
	}

	/**
	 * Returns the label of the SIMSv2/SIMSFr metadata structure definition.
	 * 
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension label is returned.
	 * @param inFrench Boolean indicating whether the French (<code>true</true>) or English label is returned.
	 * @return The label of the SIMSv2/SIMSFr metadata structure definition.
	 */
	public static String simsMSDLabel(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Définition de structre de métadonnées SIMSv2" + (simsStrict ? "" : " - extension française");
		else return "SIMSv2 Metadata Structure Definition"  + (simsStrict ? "" : " - French extension");
	}

	/**
	 * Returns the URI of the SIMSv2/SIMSFr structure report.
	 * 
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension URI is returned.
	 * @return The URI of the SIMSv2/SIMSFr structure report.
	 */
	public static String simsStructureReportURI(boolean simsStrict) { // TODO Add a path segment?
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "reportStructure";
	}

	/**
	 * Returns the label of the SIMSv2/SIMSFr structure report.
	 * 
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension label is returned.
	 * @param inFrench Boolean indicating whether the French (<code>true</true>) or English label is returned.
	 * @return The label of the SIMSv2/SIMSFr structure report.
	 */
	public static String simsReportStructureLabel(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Structure de rapport de métadonnées SIMSv2" + (simsStrict ? "" : " - extension française");
		else return "SIMSv2 Metadata Structure Report"  + (simsStrict ? "" : " - French extension");
	}

	/**
	 * Returns the URI of a SIMSv2/SIMSFr metadata attribute specification.
	 * 
	 * @param entry A <code>SIMSFrEntry</code> corresponding to the SIMSv2/SIMSFr attribute.
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension URI is returned.
	 * @return The URI of the SIMSv2/SIMSFr attribute specification.
	 */
	public static String simsAttributeSpecificationURI(SIMSFrEntry entry, boolean simsStrict) {
		if (entry.isAddedOrModified() && (simsStrict == false)) return BASE_SIMS_FR_URI + "specificationAttribut/" + entry.getNotation();
		else return BASE_SIMS_URI + "attributeSpecification/" + entry.getNotation();
	}

	/**
	 * Returns the URI of a SIMSv2/SIMSFr metadata attribute property.
	 * 
	 * @param entry A <code>SIMSFrEntry</code> corresponding to the SIMSv2/SIMSFr attribute.
	 * @param simsStrict Boolean indicating whether the base SIMS (<code>true</true>) or French extension URI is returned.
	 * @return The URI of the SIMSv2/SIMSFr attribute specification.
	 */
	public static String simsAttributePropertyURI(SIMSFrEntry entry, boolean simsStrict) {
		if (entry.isAddedOrModified() && (simsStrict == false)) return BASE_SIMS_FR_URI + "attribut/" + entry.getNotation();
		return BASE_SIMS_URI + "attribute/" + entry.getNotation();
	}

	/**
	 * Returns the URI of a DCType:Text resource corresponding to the value of a SIMSFr rich text attribute.
	 * 
	 * @param m0Id The identifier of the SIMSFr attribute to which the text is attached.
	 * @param entry A <code>SIMSFrEntry</code> corresponding to the SIMSv2/SIMSFr attribute.
	 * @param language The language tag in which the rich text is expressed (should be either 'fr' or 'en').
	 * @return The URI of the DCType:Text resource.
	 */
	public static String simsFrRichTextURI(String m0Id, SIMSFrEntry entry, String language) {
		return QUALITY_BASE_URI + "attribut/" + m0Id + "/" + entry.getNotation() + "/text";
		//+ ("fr".equals(language) ? "e" : "");
	}

	/** Returns the URI of a geo:Feature resource corresponding to territorial attribute */
	// TODO This method should disappear and be replaced by direct mappings
	public static String geoFeatureURI(String m0Id, String code) {
		return simsReportURI(m0Id) + "/" + Utils.camelCase(code.replace("_", ""), true, false);
	}

	/** URI of the SIMSv2/SIMSFr concept scheme */
	public static String simsConceptSchemeURI(boolean simsStrict) {
		return (simsStrict ? BASE_SIMS_URI : BASE_SIMS_FR_URI) + "sims"; // TODO Add a path segment?
	}

	/** Name of the SIMSv2/SIMSFr concept scheme */
	public static String simsConceptSchemeName(boolean simsStrict, boolean inFrench) {
		if (inFrench) return "Structure Unique Intégrée de Métadonnées v2" + (simsStrict ? "" : " - extension française");
		return "Single Metadata Integrated Structure v2" + (simsStrict ? "" : " - French extension");
	}

	/** URI of the statistical themes concept scheme */
	public static String themeSchemeURI() {
		return "http://id.insee.fr/concepts/themes";
	}

	/** URI of the statistical theme class */
	public static String themeClassURI() {
		return "http://id.insee.fr/concepts/themes/Theme";
	}

	/** URI of a statistical theme as a function of its code */
	public static String themeURI(String themeNotation) {
		return "http://id.insee.fr/concepts/theme/" + themeNotation.toLowerCase();
	}

	/** URI of a code list */
	public static String codelistURI(String conceptName) {
		return INSEE_CODES_BASE_URI + Utils.camelCase(conceptName, true, true); // Lower camel case and plural
	}

	/** URI of the concept associated to a code list */
	public static String codeConceptURI(String conceptName) {
		return INSEE_CODE_CONCEPTS_BASE_URI + Utils.camelCase(conceptName, false, false); // Upper camel case and singular
	}

	/** URI of the concept associated to a SDMX code list */
	public static String sdmxCodeConceptURI(String codeListName) {
		// See https://github.com/UKGovLD/publishing-statistical-data/blob/master/specs/src/main/vocab/sdmx-code.ttl
		// Hack: for CL_REF_AREA, the vocabulary referenced above uses CL_AREA
		return SDMX_CODE_BASE_URI + (codeListName.equalsIgnoreCase("CL_REF_AREA") ? "Area" : codeListNameToConceptName(codeListName));
	}

	/** URI of a code list element */
	public static String inseeCodeURI(String notation, String conceptName) {
		return INSEE_CODES_BASE_URI + Utils.camelCase(conceptName, true, false) + "/" + notation;
	}

	/** URI of a SIMSv2 concept as a function of its notation */
	public static String simsConceptURI(String notation) {
		return BASE_SIMS_URI + "concept/" + notation;
	}

	/** URI of the graph containing a SIMSv2Fr report */
	public static String simsReportGraphURI(String reportId) {
		return "http://rdf.insee.fr/graphes/qualite/rapport/" + reportId;
	}

	/** URI of a SIMSv2/SIMSv2Fr concept represented by a SIMSFrEntry */
	public static String simsConceptURI(SIMSFrEntry entry) {
		if (entry.isOriginal()) return simsConceptURI(entry.getNotation());
		return "http://id.insee.fr/concepts/simsv2fr/" + entry.getNotation();
	}

	/** Determines from its identifier if an organization is external or internal */
	public static boolean isInseeOrganization(String organizationId) {
		// An identifier is considered internal if it ends with 3 digits (e.g. DG75-A001)
		// TODO Will not work for regional units
		return ((organizationId != null) && (organizationId.length() >= 4) && (StringUtils.isNumeric(StringUtils.right(organizationId, 3))));
	}

	/** URI of an organization */
	public static String organizationURI(String organizationId) {
		return INSEE_ORG_BASE_URI + Utils.slug(organizationId);
	}

	/** URI of an Insee organizational unit */
	public static String inseeUnitURI(String timbre) {
		return INSEE_ORG_BASE_URI + "insee/" + timbre.toLowerCase();
	}

	/** Returns a concept name associated to a code list name of the type CL_XXX_YYY */
	public static String codeListNameToConceptName(String codeListName) {
		// Assuming code list names have the form CL_TERM_OTHERTERM_ETC, will return TermOthertermEtc
		StringBuilder conceptNameBuilder = new StringBuilder();
		String[] terms = codeListName.substring(3).toUpperCase().split("_"); // Skipping initial CL_
		for (String term : terms) {
			conceptNameBuilder.append(term.charAt(0)).append(term.substring(1).toLowerCase());
		}
		return conceptNameBuilder.toString();
	}

	/**
	 * Target URI of a statistical operation, family, series or indicator.
	 * 
	 * @param web4GNumber The Web4G source number (4-digit integer)
	 * @param type Type of resource under consideration: should be 'famille', 'serie', 'operation' or 'indicateur'.
	 * @return The target URI of the resource (for example http://id.insee.fr/operations/serie/s1234)
	 */
	public static String operationResourceURI(String web4GNumber, String type) {
		if ("indicateur".equals(type)) return indicatorURI("p" + web4GNumber);
		return INSEE_OPS_BASE_URI + type + "/s" + web4GNumber;
	}

	/** URI of a statistical operation */
	public static String statisticalOperationURI(String name) {
		return INSEE_OPS_BASE_URI + "operation/" + Utils.slug(name);
	}

	/** URI of a statistical operation series */
	public static String statisticalOperationSeriesURI(String name) {
		return INSEE_OPS_BASE_URI + "serie/" + Utils.slug(name);
	}

	/** URI of a statistical operation family */
	public static String statisticalOperationFamilyURI(String name) {
		return INSEE_OPS_BASE_URI + "famille/" + Utils.slug(name);
	}

	/** URI of a statistical indicator */
	public static String indicatorURI(String indicatorId) {
		return "http://id.insee.fr/produits/indicateur/" + indicatorId;
	}

	/** URI of a SIMS quality report */
	public static String simsReportURI(String documentationId) {
		return QUALITY_BASE_URI + "rapport/" + documentationId;
	}

	/** URI of a reported attribute */
	public static String simsReportedAttributeURI(String documentationId, String attributeId) {
		return QUALITY_BASE_URI + "attribut/" + documentationId + "/" + attributeId;
	}

	/** URI of a SIMS quality metric (called actually indicator) */
	public static String simsQualityMetricURI(String metricId) {
		return METRIC_BASE_URI + metricId;
	}

	/** URI of the FOAF document representing a 'link' object */
	public static String linkURI(int linkNumber) {
		return "http://id.insee.fr/documents/page/" + linkNumber;
	}

	/** URI of the FOAF document representing a 'document' object */
	public static String documentURI(int documentNumber) {
		return "http://id.insee.fr/documents/document/" + documentNumber;
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
