package eu.casd.semweb.psp.test;

import eu.casd.semweb.psp.SourceConverter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class SourceConverterTest {

	@Test
	public void testSplitModel() throws IOException {

		List<String> testOperations = Arrays.asList("IND-COUT-TRAVAIL-ICHT-TS", "IND-COMMANDES-INDUSTRIE");
		SourceConverter.splitModel(testOperations);
	}

	@Test
	public void testGetOperationName() {

		assertEquals(SourceConverter.getOperationId("http://baseUri/FR-IND-COMMANDES-INDUSTRIE/REF_AREA"), "IND-COMMANDES-INDUSTRIE");
	}

	@Test
	public void testGetPropertyName() {

		assertEquals(SourceConverter.getPropertyCode("http://baseUri/FR-IND-COMMANDES-INDUSTRIE/REF_AREA"), "REF_AREA");
		assertNull(SourceConverter.getPropertyCode("http://baseUri/FR-IND-COMMANDES-INDUSTRIE"));
	}
}
