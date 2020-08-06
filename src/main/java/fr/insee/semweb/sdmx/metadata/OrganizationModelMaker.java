package fr.insee.semweb.sdmx.metadata;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.TreeMap;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.DCTerms;
import org.apache.jena.vocabulary.ORG;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Row.MissingCellPolicy;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import fr.insee.semweb.utils.Utils;

/**
 * Creates RDF datasets containing information on the organizations referred to in the SIMSFr.
 * 
 * @author Franck Cotton
 */
public class OrganizationModelMaker {

	public static Logger logger = LogManager.getLogger(OrganizationModelMaker.class);
	/** Use directly the DILA URIs for the organizations or make sameAs links */
	public static boolean USE_DILA_URI = true;
	/** For these line numbers, org:linkedTo relations will be used between the organization and its mother */
	public static List<Integer> reportLinkOrg = Arrays.asList(3, 21, 22, 31, 32); // 3 DREES (to MFEDF), 21 CNAMTS, 22 Céreq, 31 Bpifrance, 32 Acoss
	/** For these line numbers, org:linkedTo relations will be used between the organization's mother and its grand-mother */
	public static List<Integer> reportLinkMother = Arrays.asList(20, 23, 24); // 20 CASD, 23 ONDRP, 24 CépiDc
	/** Additional links as mappings between line number and list of mothers */
	public static Map<Integer, List<String>> additionalReportingLinks = new HashMap<Integer, List<String>>();
	public static Map<Integer, List<String>> additionalInclusionLinks = new HashMap<Integer, List<String>>();
	public static Map<Integer, List<String>> additionalMotherReportingLinks = new HashMap<Integer, List<String>>();
	public static Map<Integer, List<String>> additionalMotherInclusionLinks = new HashMap<Integer, List<String>>();
	static {
		// DREES included in MASS (http://lannuaire.service-public.fr/192696) - Commented out 19/07/2017
		// additionalInclusionLinks.put(3, Arrays.asList("http://lannuaire.service-public.fr/192696"));
		// DREES reports to Travail (http://lannuaire.service-public.fr/172240) and Économie (http://lannuaire.service-public.fr/172224) - Commented out 19/07/2017
		// additionalReportingLinks.put(3, Arrays.asList("http://lannuaire.service-public.fr/172240", "http://lannuaire.service-public.fr/172224"));
		// DESL included in Intérieur (http://lannuaire.service-public.fr/172232) - Commented out 19/07/2017
		// additionalInclusionLinks.put(17, Arrays.asList("http://lannuaire.service-public.fr/172232"));
		// DGCL included in Cohésion (http://lannuaire.service-public.fr/172090)
        additionalMotherInclusionLinks.put(17, Arrays.asList("http://lannuaire.service-public.fr/172090")); // New - 19/07/2017
		// CNAMTS reports to Économie (http://lannuaire.service-public.fr/172224) - Commented out 19/07/2017
		// additionalReportingLinks.put(21, Arrays.asList("http://lannuaire.service-public.fr/172224"));
		// CEREQ reports to Travail (http://lannuaire.service-public.fr/172240) - Commented out 19/07/2017
		// additionalReportingLinks.put(22, Arrays.asList("http://lannuaire.service-public.fr/172240"));
		// INSERM reports to Recherche (http://lannuaire.service-public.fr/172226) - Commented out 19/07/2017
		// additionalMotherReportingLinks.put(24, Arrays.asList("http://lannuaire.service-public.fr/172226"));
	}

	/**
	 * Reads the information on Insee structures in the dedicated Excel sheet and transforms it into a Jena model.
	 * 
	 * @param orgWorkbook An Excel workbook containing the source information (<code>Workbook</code> object).
	 * @return A Jena <code>Model</code> containing the organization scheme conforming to the ORG ontology.
	 */
	public static Model createInseeModel(Workbook orgWorkbook) {

		Model inseeModel = ModelFactory.createDefaultModel();

		inseeModel.setNsPrefix("rdfs", RDFS.getURI());
		inseeModel.setNsPrefix("dcterms", DCTerms.getURI());
		inseeModel.setNsPrefix("org", ORG.getURI());
		inseeModel.setNsPrefix("skos", SKOS.getURI());

		// Create Insee as an organization
		String inseeURI = Configuration.organizationURI("Insee");
		Resource insee = inseeModel.createResource(inseeURI, ORG.Organization);
		insee.addProperty(DCTerms.identifier, "Insee");
		insee.addProperty(SKOS.prefLabel, inseeModel.createLiteral("Institut national de la statistique et des études économiques", "fr"));
		insee.addProperty(SKOS.prefLabel, inseeModel.createLiteral("National Institute of Statistics and Economic Studies", "en"));
		// TODO Check that this is coherent with resource created in the SSM model

		Sheet inseeSheet = orgWorkbook.getSheetAt(2);
		Iterator<Row> unitRows = inseeSheet.rowIterator();

		String cellValue = "";
		String directionName = "", departmentName = "", divisionName = "";
		Resource direction = null, department = null, division = null;
		String idPrefix = "DG75-" ;
		unitRows.next();
		while (unitRows.hasNext()) {
			Row unitRow = unitRows.next();
			// Read direction column
			cellValue = unitRow.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if ((cellValue.length() > 0) && (!cellValue.equals(directionName))) {
				String directionId = idPrefix + cellValue.substring(1, 5);
				logger.debug("Creating resource for direction " + directionId);
				direction = inseeModel.createResource(Configuration.inseeUnitURI(directionId), ORG.OrganizationalUnit);
				direction.addProperty(RDF.type, ORG.Organization); // Materialize the subsumption in order to simplify requests
				direction.addProperty(DCTerms.identifier, directionId);
				direction.addProperty(SKOS.prefLabel, inseeModel.createLiteral(cellValue.substring(7).trim(), "fr"));
				insee.addProperty(ORG.hasUnit, direction);
				direction.addProperty(ORG.unitOf, insee);
				directionName = cellValue;
			}
			// Read department column
			cellValue = unitRow.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if ((cellValue.length() > 0) && (!cellValue.equals(departmentName))) {
				String departmentId = idPrefix + cellValue.substring(1, 5);
				department = inseeModel.createResource(Configuration.inseeUnitURI(departmentId), ORG.OrganizationalUnit);
				department.addProperty(RDF.type, ORG.Organization);
				department.addProperty(DCTerms.identifier, departmentId);
				department.addProperty(SKOS.prefLabel, inseeModel.createLiteral(cellValue.substring(7).trim(), "fr"));
				direction.addProperty(ORG.hasUnit, department);
				department.addProperty(ORG.unitOf, direction);
				departmentName = cellValue;
			}
			// Read division column
			cellValue = unitRow.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if ((cellValue.length() > 0) && (!cellValue.equals(divisionName))) {
				String divisionId = idPrefix + cellValue.substring(1, 5);
				division = inseeModel.createResource(Configuration.inseeUnitURI(divisionId), ORG.OrganizationalUnit);
				division.addProperty(RDF.type, ORG.Organization);
				division.addProperty(DCTerms.identifier, divisionId);
				division.addProperty(SKOS.prefLabel, inseeModel.createLiteral(cellValue.substring(7).trim(), "fr"));
				department.addProperty(ORG.hasUnit, division);
				division.addProperty(ORG.unitOf, department);
				divisionName = cellValue;
			}
		}

		return inseeModel;
	}

	/**
	 * Reads the information on Insee structures in the internal LDAP directory and transforms it into a Jena model.
	 * Note: execution requires connectivity to the LDAP directory.
	 * 
	 * @return A Jena <code>Model</code> containing the organization scheme conforming to the ORG ontology.
	 */
	public static Model createInseeModelFromLDAP() {

		final String LDAP_PROPERTIES_PATH = "src/main/resources/ldap.properties";

		logger.info("Building Insee organization model from LDAP directory");

		// Read properties (properties file should be UTF-8)
		Properties ldapProperties = new Properties();
		try (InputStream ldapPropertiesStream = new FileInputStream(LDAP_PROPERTIES_PATH))  {
			ldapProperties.load(new InputStreamReader(ldapPropertiesStream, StandardCharsets.UTF_8));
		} catch (Exception e) {
			logger.error("Error while reading LDAP properties file " + LDAP_PROPERTIES_PATH + e.getMessage());
			return null;
		}

		// Read and check properties
		String ldapHostname = ldapProperties.getProperty("ldap.hostname");
		String ldapBase = ldapProperties.getProperty("ldap.base");
		String ldapFilter = ldapProperties.getProperty("ldap.filter");
		String ldapAttributesString = ldapProperties.getProperty("ldap.attributes");
		if ((ldapHostname == null) || (ldapBase == null) || (ldapFilter == null) || (ldapAttributesString == null)) {
			logger.error("Invalid LDAP properties " + ldapProperties);
			return null;
		}
		String[] ldapAttributes = ldapAttributesString.split(",");
		logger.info("LDAP parameters - host: '" + ldapHostname + "', base: '" + ldapBase + "', filter: '" + ldapFilter + "', attributes: " + Arrays.toString(ldapAttributes));

		// Construct environment and connect to the directory root
		Hashtable<String, String> environment = new Hashtable<String, String>();
		environment.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		environment.put(Context.PROVIDER_URL, ldapHostname);
		environment.put(Context.SECURITY_AUTHENTICATION, "none");
		SortedMap<String, String> units = new TreeMap<>();
		SortedMap<String, String> hierarchy = new TreeMap<>();
		try {
			DirContext context = new InitialDirContext(environment);

			// Specify search criteria for units
			SearchControls controls = new SearchControls();
			controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			controls.setReturningAttributes(ldapAttributes);
			// Execute search and browse through results to fill unit lists
			NamingEnumeration<SearchResult> results = context.search(ldapBase, ldapFilter, controls);
			while (results.hasMore()) {
				SearchResult entree = results.next();
				String unit = entree.getAttributes().get(ldapAttributes[0]).toString();
				units.put(unit, entree.getAttributes().get(ldapAttributes[1]).toString());
				if (entree.getAttributes().get(ldapAttributes[2]) != null) {
					String parent = entree.getAttributes().get(ldapAttributes[2]).toString().split(",")[0].substring(3);
					hierarchy.put(unit, parent);
				}
			}
			context.close();
		} catch (NamingException e) {
			logger.error("Error while querying the list of units - " + e.getMessage());
		}

		// Create the Jena model
		Model inseeModel = ModelFactory.createDefaultModel();

		inseeModel.setNsPrefix("rdfs", RDFS.getURI());
		inseeModel.setNsPrefix("dcterms", DCTerms.getURI());
		inseeModel.setNsPrefix("org", ORG.getURI());
		inseeModel.setNsPrefix("skos", SKOS.getURI());

		// Create Insee as an organization
		String inseeURI = Configuration.organizationURI("Insee");
		Resource insee = inseeModel.createResource(inseeURI, ORG.Organization);
		insee.addProperty(DCTerms.identifier, "Insee");
		insee.addProperty(SKOS.prefLabel, inseeModel.createLiteral("Institut national de la statistique et des études économiques", "fr"));
		insee.addProperty(SKOS.prefLabel, inseeModel.createLiteral("National Institute of Statistics and Economic Studies", "en"));

		for (String unitIdentifier : units.keySet()) {
			logger.debug("Creating resource for unit " + unitIdentifier);
			Resource unitResource = inseeModel.createResource(Configuration.inseeUnitURI(unitIdentifier.replaceFirst("ou: ", "")));
			unitResource.addProperty(RDF.type, ORG.OrganizationalUnit);
			unitResource.addProperty(RDF.type, ORG.Organization); // Materialize the subsumption in order to simplify requests
			unitResource.addProperty(DCTerms.identifier, unitIdentifier.replaceFirst("ou: ", ""));
			unitResource.addProperty(SKOS.prefLabel, inseeModel.createLiteral(units.get(unitIdentifier).replaceFirst("description: ", "").trim(), "fr"));
			if (hierarchy.containsKey(unitIdentifier)) {
				Resource parentResource = inseeModel.createResource(Configuration.inseeUnitURI(hierarchy.get(unitIdentifier)));
				parentResource.addProperty(ORG.hasUnit, unitResource);
				unitResource.addProperty(ORG.unitOf, parentResource);
			} else {
				insee.addProperty(ORG.hasUnit, unitResource);
				unitResource.addProperty(ORG.unitOf, insee);
			}
		}

		return inseeModel;
	}

	/**
	 * Reads the information on SSM structures in the dedicated Excel sheet and transforms it into a Jena model.
	 * 
	 * @param orgWorkbook An Excel workbook containing the source information (<code>Workbook</code> object).
	 * @return A Jena <code>Model</code> containing the organization scheme conforming to the ORG ontology.
	 */
	public static Model createSSMModel(Workbook orgWorkbook) {

		Model orgModel = ModelFactory.createDefaultModel();
		orgModel.setNsPrefix("rdfs", RDFS.getURI());
		orgModel.setNsPrefix("dcterms", DCTerms.getURI());
		orgModel.setNsPrefix("org", ORG.getURI());
		orgModel.setNsPrefix("skos", SKOS.getURI());
		orgModel.setNsPrefix("insee", Configuration.BASE_INSEE_ONTO_URI);

		Sheet orgSheet = orgWorkbook.getSheetAt(0);;
		Sheet detailSheet = orgWorkbook.getSheetAt(1);

		Iterator<Row> orgRows = orgSheet.rowIterator();
		Iterator<Row> detailRows = detailSheet.rowIterator();
		orgRows.next(); detailRows.next(); // Skip the title lines

		while (orgRows.hasNext()) {
			Row orgRow = orgRows.next();
			Row detailRow = detailRows.next();
			int rowNumber = orgRow.getRowNum() + 1; // Because row numbers used in static specifications are 1-based

			String orgId = orgRow.getCell(0).toString().trim();
			String orgIdCheck = detailRow.getCell(4).toString().trim();
			if (!orgId.equals(orgIdCheck)) {
				logger.error("Inconsistency on organization identifier line " + orgRow.getRowNum() + ": " + orgId + " different from " + orgIdCheck);
				continue;
			}

			String dilaURI = orgRow.getCell(6, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			String orgURI = (USE_DILA_URI && (dilaURI.length() > 0) ? dilaURI : Configuration.organizationURI(orgId));
			logger.debug("Creating organization resource for " + orgId + " with URI " + orgURI);
			Resource organization = orgModel.createResource(orgURI, ORG.Organization);

			organization.addProperty(DCTerms.identifier, orgId);
			// French label is in column B and English label in column C (both should be always present)
			String label = stripTrailingParenthesis(orgRow.getCell(1).toString().trim());
			organization.addProperty(SKOS.prefLabel, orgModel.createLiteral(label, "fr"));
			label = stripTrailingParenthesis(orgRow.getCell(2).toString().trim());
			organization.addProperty(SKOS.prefLabel, orgModel.createLiteral(label, "en"));
			// TODO I don't really know what to do with 'Dénomination SSM', declare it as altLabel for now
			String ssmName = orgRow.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (ssmName.length() > 0) organization.addProperty(SKOS.altLabel, orgModel.createLiteral(ssmName, "fr"));
			boolean isOna = (orgRow.getCell(4, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().length() > 0);
			if (isOna) organization.addProperty(RDF.type, orgModel.createResource(Configuration.BASE_INSEE_ONTO_URI + "OtherNationalAuthority"));
			boolean isNSI = (orgRow.getCell(5, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim().length() > 0);
			if (isNSI) organization.addProperty(RDF.type, orgModel.createResource(Configuration.BASE_INSEE_ONTO_URI + "NationalStatisticalInstitute"));
			String url = orgRow.getCell(7, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			if (url.length() == 0) url = orgRow.getCell(8, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim(); // URL should never be empty
			organization.addProperty(RDFS.seeAlso, orgModel.createResource(url));

			// Create sameAs links if dilaURI is not used for identifying the resource
			if (!USE_DILA_URI) organization.addProperty(OWL.sameAs, orgModel.createResource(dilaURI));

			// The direct mother will be in column C, or A if C is empty (or no mother)
			// Grand-mother will be in A if mother in C (or no grand-mother)
			String motherLabel = detailRow.getCell(2, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
			String motherURI = null;
			String grannyLabel = null;
			String grannyURI = null;
			if (motherLabel.length() > 0) {
				// If there is a DILA URI for the mother use it, otherwise create a URI in Insee namespace
				motherURI = detailRow.getCell(3, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				if (motherURI.length() == 0) motherURI = getOrganizationURIFromLabel(motherLabel);
				// If there was a mother in C, there is a grand-mother in A (never empty)
				grannyLabel = detailRow.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				grannyURI = detailRow.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				if (grannyURI.length() == 0) grannyURI = getOrganizationURIFromLabel(grannyLabel);
			}
			else {
				// No mother in column C, then the mother is in column A (never empty)
				motherLabel = detailRow.getCell(0, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				motherURI = detailRow.getCell(1, MissingCellPolicy.CREATE_NULL_AS_BLANK).toString().trim();
				if (motherURI.length() == 0) motherURI = getOrganizationURIFromLabel(motherLabel);
			} 

			// Create mother organization and links to the daughter with appropriate property
			// NB: there can be duplicates in mothers and grand-mothers, so we avoid to create duplicate SKOS labels
			boolean existingOrg = orgModel.contains(ResourceFactory.createResource(motherURI), null, (RDFNode) null);
			Resource mother = orgModel.createResource(motherURI, ORG.Organization);
			if (!existingOrg) mother.addProperty(SKOS.prefLabel, orgModel.createLiteral(motherLabel, "fr"));
			// Add organization as a daughter with appropriate property
			if (reportLinkOrg.contains(rowNumber)) {
				organization.addProperty(ORG.reportsTo, mother);
				mother.addProperty(ORG.linkedTo, organization);
				logger.debug("Adding functional links with mother " + mother.getURI());
			} else {
				// Second level organizations are usually organizational units, except when link to grand-mother is reporting link
				organization.addProperty(RDF.type, ORG.OrganizationalUnit); // If organization not in reportLinkOrg, they are organizational units of their mother
				organization.addProperty(ORG.unitOf, mother);
				mother.addProperty(ORG.hasUnit, organization);
				logger.debug("Adding hierarchical links with mother " + mother.getURI());
			}

			// Create grand-mother organization if there is one
			if (grannyURI != null) {
				existingOrg = orgModel.contains(ResourceFactory.createResource(grannyURI), null, (RDFNode) null);
				Resource granny = orgModel.createResource(grannyURI, ORG.Organization);
				if (!existingOrg) granny.addProperty(SKOS.prefLabel, orgModel.createLiteral(grannyLabel, "fr"));
				if (reportLinkMother.contains(rowNumber)) {
					granny.addProperty(ORG.linkedTo, mother);
					mother.addProperty(ORG.reportsTo, granny);					
					logger.debug("Adding functional links between mother " + mother.getURI() + " and grand-mother " + granny.getURI());
				} else {
					mother.addProperty(RDF.type, ORG.OrganizationalUnit);
					granny.addProperty(ORG.hasUnit, mother);
					mother.addProperty(ORG.unitOf, granny);					
					logger.debug("Adding hierarchical links between mother " + mother.getURI() + " and grand-mother " + granny.getURI());
				}				
			}
			// Add additional links if they concern the organization
			if (additionalReportingLinks.containsKey(rowNumber)) {
				for (String uri : additionalReportingLinks.get(rowNumber)) {
					Resource otherMother = orgModel.createResource(uri);
					organization.addProperty(ORG.reportsTo, otherMother);
					otherMother.addProperty(ORG.linkedTo, organization);
					logger.debug("Adding additional functional links with other mother " + otherMother.getURI());
				}
			}
			if (additionalInclusionLinks.containsKey(rowNumber)) {
				for (String uri : additionalInclusionLinks.get(rowNumber)) {
					Resource otherMother = orgModel.createResource(uri);
					organization.addProperty(ORG.unitOf, otherMother);
					otherMother.addProperty(ORG.hasUnit, organization);
					logger.debug("Adding additional hierarchical links with other mother " + otherMother.getURI());
				}
			}
			if (additionalMotherReportingLinks.containsKey(rowNumber)) {
				for (String uri : additionalMotherReportingLinks.get(rowNumber)) {
					Resource otherGranny = orgModel.createResource(uri);
					mother.addProperty(ORG.reportsTo, otherGranny);
					otherGranny.addProperty(ORG.linkedTo, mother);
					logger.debug("Adding additional functional links between mother " + mother.getURI() + " and other grand-mother " + otherGranny.getURI());
				}
			}
			if (additionalMotherInclusionLinks.containsKey(rowNumber)) {
				for (String uri : additionalMotherInclusionLinks.get(rowNumber)) {
					Resource otherGranny = orgModel.createResource(uri);
					mother.addProperty(ORG.unitOf, otherGranny);
					otherGranny.addProperty(ORG.hasUnit, mother);
					logger.debug("Adding additional hierarchical links between mother " + mother.getURI() + " and other grand-mother " + otherGranny.getURI());
				}
			}
		}
		try { orgWorkbook.close(); } catch (IOException ignored) { }

		return orgModel;
	}

	/**
	 * Strips from a string any right-most parenthesis with its content.
	 * 
	 * @param label The string to process.
	 * @return The string without the right-most parenthesis (if any).
	 */
	public static String stripTrailingParenthesis(String label) {

		if (label.endsWith(")")) {
			return label.substring(0, label.lastIndexOf("(")).trim();
		}
		return label;
	}

	/**
	 * Creates an acronym from a string by concatenating the first letter of each token.
	 * 
	 * @param label The string to process.
	 * @return The acronym created.
	 */
	public static String createAccronym(String label) {

		System.out.println(label);
		String[] terms = label.split("\\s");
		StringBuilder builder = new StringBuilder();
		for (String term : terms) builder.append(term.charAt(0));

		return builder.toString();
	}

	/**
	 * Creates a URI from the name of an organization.
	 * Essentially deals with the BdF special case, otherwise relaying on Configuration.organizationURI().
	 * 
	 * @param label The name of the organization.
	 * @return The URI for the organization.
	 */
	public static String getOrganizationURIFromLabel(String label) {

		// Hack: For Banque de France we dont want to collision the URI of the daughter (also BDF)
		String accronym = createAccronym(Utils.slug(label));
		if (accronym.equals("b")) return Configuration.organizationURI("banque de france");
		return Configuration.organizationURI(accronym);
	}
}
