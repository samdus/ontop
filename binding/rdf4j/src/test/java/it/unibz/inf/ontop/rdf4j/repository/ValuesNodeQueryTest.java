package it.unibz.inf.ontop.rdf4j.repository;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.sql.SQLException;

import static org.junit.Assert.assertEquals;


public class ValuesNodeQueryTest extends AbstractRDF4JTest {

    private static final String MAPPING_FILE = "/values-node/mapping.obda";
    private static final String ONTOLOGY_FILE = "/values-node/ontology.owl";
    private static final String SQL_SCRIPT = "/values-node/example.sql";

    @BeforeClass
    public static void before() throws IOException, SQLException {
        initOBDA(SQL_SCRIPT, MAPPING_FILE, ONTOLOGY_FILE);
    }

    @AfterClass
    public static void after() throws SQLException {
        release();
    }
    @Test
    public void testOntologySpo() {
        int count = runQueryAndCount("SELECT * WHERE {\n" +
                "\t?s ?p ?o\n" +
                "}");
        assertEquals(count, 9);
    }
}
