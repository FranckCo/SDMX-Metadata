package fr.insee.semweb.sdmx.metadata.test;

import static org.junit.Assert.assertNotNull;

import java.io.File;

import org.junit.Test;

import fr.insee.semweb.sdmx.metadata.Configuration;
import fr.insee.semweb.sdmx.metadata.SIMSFRScheme;

public class SIMSPlusSchemeTest {

	@Test
	public void testReadSIMSPlusFromExcel() {

		SIMSFRScheme simsPlusScheme = SIMSFRScheme.readSIMSFRFromExcel(new File(Configuration.SIMS_XLSX_FILE_NAME));
		assertNotNull(simsPlusScheme);

		System.out.println(simsPlusScheme);

	}

}