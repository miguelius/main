package org.lareferencia.backend.harvester;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.lareferencia.backend.domain.OAIRecord;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import ORG.oclc.oai.harvester2.verb.ListRecords;

@Component
@Scope(value = "prototype")
public class OCLCBasedHarvesterImpl extends BaseHarvestingEventSource implements
		IHarvester {

	private static final int STANDARD_RECORD_SIZE = 100;
	int MAX_RETRIES = 3;
	private static TransformerFactory xformFactory = TransformerFactory.newInstance();
	private static DateTimeFormatter dateTimeParser = DateTimeFormat.forPattern("yyyy-MMM-dd'THH:mm:ssZ");

	public OCLCBasedHarvesterImpl() {
		super();
		System.out.println("Creando Harvester" + this.toString());
	}

	public void harvest(String uri, String from, String until, String setname,
			String metadataPrefix) {

		ListRecords actualListRecords = null;
		String resumptionToken = null;

		int batchIndex = 0;
		int actualRetry = 0;
		int secondsToNextRetry = 5;

		// La condición es que sea la primera corrida o que no sea null el
		// resumption (caso de fin)
		// TODO: Hay casos donde dio null y no era el fin, estudiar alternativas
		while (batchIndex == 0 || resumptionToken != null) {

			do {
				try {
					actualListRecords = listRecords(uri, setname,
							metadataPrefix, batchIndex, resumptionToken);
					resumptionToken = actualListRecords.getResumptionToken();

					

					fireHarvestingEvent(new HarvestingEvent(
							parseRecords(actualListRecords),
							HarvestingEventStatus.OK));

					batchIndex++;
					actualRetry = 0;
					secondsToNextRetry = 5;
					break;

				} catch (HarvestingException | NoSuchFieldException
						| TransformerException e) {
					System.out.println("Problemas en el lote: " + batchIndex
							+ " reintento: " + actualRetry);
					System.out.println(e.getMessage());

					System.out.print("Esperando " + secondsToNextRetry
							+ " segundos para el proximo reintento ..");
					try {
						Thread.sleep(secondsToNextRetry * 1000);
					} catch (InterruptedException t) {
					}
					System.out.println("OK");

					// Se incrementa el retry y se duplica el tiempo de espera
					actualRetry++;
					secondsToNextRetry = secondsToNextRetry * 2;
				}
			} while (actualRetry < MAX_RETRIES);

		}
	}

	private ListRecords listRecords(String baseURL, String setSpec,
			String metadataPrefix, int batchIndex, String resumptionToken)
			throws HarvestingException {

		ListRecords listRecords = null;
		/*
		 * Se encapsulan las dos llamadas distintas en una sola, que depende de
		 * la existencia del RT
		 */
		try {

			if (batchIndex == 0)
				listRecords = new ListRecords(baseURL, null, null, setSpec,
						metadataPrefix);
			else
				listRecords = new ListRecords(baseURL, resumptionToken);

			NodeList errors = listRecords.getErrors();

			if (errors != null && errors.getLength() > 0) {
				throw new HarvestingException(listRecords.toString());
			} else {
				resumptionToken = listRecords.getResumptionToken();
				if (resumptionToken != null && resumptionToken.length() == 0)
					resumptionToken = null;
			}

		} catch (IOException e) {
			throw new HarvestingException(e.getMessage());
		} catch (ParserConfigurationException e) {
			throw new HarvestingException(e.getMessage());
		} catch (SAXException e) {
			throw new HarvestingException(e.getMessage());
		} catch (TransformerException e) {
			throw new HarvestingException(e.getMessage());
		} catch (NoSuchFieldException e) {
			throw new HarvestingException(e.getMessage());
		} catch (Exception e) {
			throw new HarvestingException(e.getMessage());
		}

		return listRecords;
	}

	private List<OAIRecord> parseRecords(ListRecords listRecords) throws TransformerException, NoSuchFieldException {
		
		List<OAIRecord> result = new ArrayList<OAIRecord>(STANDARD_RECORD_SIZE);
		
		
		// La obtención de registros por xpath se realiza de acuerdo al schema correspondiente
		NodeList nodes = null;
		String namespace = null;
		
		if (listRecords.getSchemaLocation().indexOf(ListRecords.SCHEMA_LOCATION_V2_0) != -1) {
			nodes = listRecords.getNodeList("/oai20:OAI-PMH/oai20:ListRecords/oai20:record");
			namespace = "oai20";
		} else if (listRecords.getSchemaLocation().indexOf(ListRecords.SCHEMA_LOCATION_V1_1_LIST_RECORDS) != -1) {
			namespace = "oai11_ListRecords";
			nodes = listRecords.getNodeList("/oai11_ListRecords:ListRecords/oai11_ListRecords:record");
		} else {
			throw new NoSuchFieldException(listRecords.getSchemaLocation());
		}
		
		
		for (int i=0; i<nodes.getLength(); i++) {
			Node node = nodes.item(i);
			
			String identifier = listRecords.getSingleString(node, "//"+namespace+":header/" + namespace + ":identifier");
		    String datestampString = listRecords.getSingleString(node, "//"+namespace+":header/" + namespace + ":datestamp");
				
			//DateTime datestamp = dateTimeParser.parseDateTime(datestampString);
			
			result.add( new OAIRecord(identifier, null, getMetadataXMLFromRecordNode(node)) );
		}
		
		System.out.println();
		
		
		return result;
	}
	
	/**
	 * Retorna un String conteniendo el XML correspondiente al contenido de metadada, asumiendo que node "record"
	 * y que metadata es el ultimo (segundo) nodo bajo "record"
	 * 
	 * @param node
	 * @return
	 * @throws TransformerException
	 */
	private String getMetadataXMLFromRecordNode(Node node) throws TransformerException {
		
		StringWriter sw = new StringWriter();
	    Result output = new StreamResult(sw);
		Transformer idTransformer = xformFactory.newTransformer();
        idTransformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        idTransformer.transform( new DOMSource(node.getLastChild().getFirstChild()), output);
		return sw.toString();
		
	}
}
