package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;

public class ConfigurationTest {

	@Test
	public void testCodeListNameToConceptName() {
		assertEquals(Configuration.codeListNameToConceptName("CL_FREQ"), "Freq");
		assertEquals(Configuration.codeListNameToConceptName("CL_UNIT_MEASURE"), "UnitMeasure");
		assertEquals(Configuration.codeListNameToConceptName("CL_COLLECTION_MODE"), "CollectionMode");
		assertEquals(Configuration.codeListNameToConceptName("cl_collection_mode"), "CollectionMode");
		assertEquals(Configuration.codeListNameToConceptName("CL_SURVEY_UNIT"), "SurveyUnit");
	}

	@Test
	public void testSDMXCodeConceptURI() {
		assertEquals(Configuration.sdmxCodeConceptURI("CL_FREQ"), "http://purl.org/linked-data/sdmx/2009/code#Freq");
		assertEquals(Configuration.sdmxCodeConceptURI("CL_REF_AREA"), "http://purl.org/linked-data/sdmx/2009/code#Area");
		assertEquals(Configuration.sdmxCodeConceptURI("cl_ref_area"), "http://purl.org/linked-data/sdmx/2009/code#Area");
		assertEquals(Configuration.sdmxCodeConceptURI("CL_UNIT_MEASURE"), "http://purl.org/linked-data/sdmx/2009/code#UnitMeasure");
	}

	@Test
	public void testCamelCase1() {
		assertEquals(Configuration.camelCase("How about that", true, false), "howAboutThat");
		assertEquals(Configuration.camelCase("How about that", true, true), "howsAboutThat");
		assertEquals(Configuration.camelCase("A  B C dF edd", true, false), "aBCDfEdd");
		assertNull(Configuration.camelCase(null, true, true));
	}

	@Test
	public void testCamelCase2() {
		assertEquals(Configuration.camelCase("Type de source", true, true), "typesSource");
		assertEquals(Configuration.camelCase("Type de source", true, false), "typeSource");
		assertEquals(Configuration.camelCase("Type de source", false, true), "TypesSource");
		assertEquals(Configuration.camelCase("Type de source", false, false), "TypeSource");
		assertEquals(Configuration.camelCase("Unité enquêtée", true, true), "unitesEnquetees");		
		assertEquals(Configuration.camelCase("Unité enquêtée", true, false), "uniteEnquetee");		
	}

	@Test
	public void testCamelCase3() {
		assertEquals(Configuration.camelCase("Frequence", true, true), "frequences");
		assertEquals(Configuration.camelCase("Statut de l'enquête", true, false), "statutEnquete");
		assertEquals(Configuration.camelCase("Statut de l'enquête", false, true), "StatutsEnquete");
		assertEquals(Configuration.camelCase("Catégorie de source", false, true), "CategoriesSource");
	}

	@Test
	public void testIdMapping() {
		System.out.println(Configuration.dds2Web4GIdMappings);
	}
}