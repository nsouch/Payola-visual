package cz.payola.domain.test

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cz.payola.common.rdf.IdentifiedVertex
import cz.payola.domain.entities.plugins.DataSource
import cz.payola.domain.entities.plugins.concrete.data.SparqlEndpointFetcher

class DataSourceSpec extends AnyFlatSpec with Matchers
{
    val instance = (new SparqlEndpointFetcher).createInstance().setParameter(SparqlEndpointFetcher.endpointURLParameter, "https://data.gov.cz/sparql")

    val dataSource = DataSource("DBPedia", None, instance)

    "Data source" should "execute sparql queries" in {

        /** GET  related to Education, culture and sport (en) */
        val query = """
            CONSTRUCT {
                ?s <http://www.w3.org/ns/dcat#theme> <http://publications.europa.eu/resource/authority/data-theme/EDUC> .
            } WHERE {
                ?s <http://www.w3.org/ns/dcat#theme> <http://publications.europa.eu/resource/authority/data-theme/EDUC> .
            }
            LIMIT 50
                    """

        val result = dataSource.executeQuery(query)
        assert(!result.isEmpty, "The result is empty.")
        assert(result.vertices.size == result.edges.size + 1, "The graph doesn't conform to the expected result.")
    }

    it should "retrieve neighbours" in {
        val uri = "https://data.gov.cz/zdroj/datové-sady/00023221/1042474980"
        val neighbourhood = dataSource.getNeighbourhood(uri)
        assert(!neighbourhood.isEmpty, "The neighbourhood is empty.")
    }
}
