/*******************************************************************************
 * Copyright (c) 2013 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v2.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.html
 * 
 * Contributors:
 *     Lautaro Matas (lmatas@gmail.com) - Desarrollo e implementación
 *     Emiliano Marmonti(emarmonti@gmail.com) - Coordinación del componente III
 * 
 * Este software fue desarrollado en el marco de la consultoría "Desarrollo e implementación de las soluciones - Prueba piloto del Componente III -Desarrollador para las herramientas de back-end" del proyecto “Estrategia Regional y Marco de Interoperabilidad y Gestión para una Red Federada Latinoamericana de Repositorios Institucionales de Documentación Científica” financiado por Banco Interamericano de Desarrollo (BID) y ejecutado por la Cooperación Latino Americana de Redes Avanzadas, CLARA.
 ******************************************************************************/
package org.lareferencia.backend.rest;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import lombok.Getter;
import lombok.Setter;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.HttpMethod;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.http.conn.DnsResolver;
import org.lareferencia.backend.domain.NationalNetwork;
import org.lareferencia.backend.domain.NetworkSnapshot;
import org.lareferencia.backend.domain.NetworkSnapshotStat;
import org.lareferencia.backend.domain.OAIOrigin;
import org.lareferencia.backend.domain.OAIProviderStat;
import org.lareferencia.backend.domain.OAIRecord;
import org.lareferencia.backend.domain.RecordStatus;
import org.lareferencia.backend.domain.SnapshotStatus;
import org.lareferencia.backend.harvester.OAIRecordMetadata;
import org.lareferencia.backend.indexer.IIndexer;
import org.lareferencia.backend.indexer.IndexerWorker;
import org.lareferencia.backend.repositories.NationalNetworkRepository;
import org.lareferencia.backend.repositories.NetworkSnapshotLogRepository;
import org.lareferencia.backend.repositories.NetworkSnapshotRepository;
import org.lareferencia.backend.repositories.NetworkSnapshotStatRepository;
import org.lareferencia.backend.repositories.OAIProviderStatRepository;
import org.lareferencia.backend.repositories.OAIRecordRepository;
import org.lareferencia.backend.repositories.OAIRecordValidationRepository;
import org.lareferencia.backend.stats.MetadataOccurrenceCountSnapshotStatProcessor;
import org.lareferencia.backend.stats.RejectedByFieldSnapshotStatProcessor;
import org.lareferencia.backend.tasks.SnapshotManager;
import org.lareferencia.backend.transformer.ITransformer;
import org.lareferencia.backend.util.JsonDateSerializer;
import org.lareferencia.backend.validator.IValidator;
import org.lareferencia.backend.validator.ValidationResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * Handles requests for the application home page.
 */
@Controller
public class BackEndController {
	
	@Autowired 
	private ApplicationContext applicationContext;
	
	@Autowired
	private NationalNetworkRepository nationalNetworkRepository;
	
	@Autowired
	private NetworkSnapshotRepository networkSnapshotRepository;
	
	@Autowired
	private NetworkSnapshotLogRepository networkSnapshotLogRepository;
	
	@Autowired
	private NetworkSnapshotStatRepository statsRepository;
	
	@Autowired
	private OAIRecordRepository recordRepository;
	
	@Autowired
	private OAIRecordValidationRepository recordValidationRepository;
	
	@Autowired 
	private OAIProviderStatRepository oaiProviderStatRepository;
	
	@Autowired
	IIndexer indexer;
	
	@Autowired
	TaskScheduler scheduler;
	
	@Autowired
	SnapshotManager snapshotManager;
	
	
	//private static final Logger logger = LoggerFactory.getLogger(BackEndController.class);
	
	//private static SimpleDateFormat dateFormater = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

	
	/**
	 * Login Services
	 */
	
	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String home(Locale locale, Model model) {	
		return "home";
	}
	
	@RequestMapping(value = "/diagnose/{networkISO}/{snapID}", method = RequestMethod.GET)
	public String diagnose(@PathVariable Long snapID, @PathVariable String networkISO, Locale locale, Model model) {	
		
		model.addAttribute("snapID", snapID);
		model.addAttribute("networkISO", networkISO);
		
		return "diagnose";
	}
	
	
	@RequestMapping(value = "/login", method = RequestMethod.GET)
	public String login(Locale locale, Model model) {	
		return "login";
	}
	
	@RequestMapping(value="/login", params="errorLogin", method = RequestMethod.GET)
	public String loginFailed(Locale locale, Model model) {
		model.addAttribute("loginFailed", true);
		return "login";
	}
	
	
	/************************** Backend 
	 * @throws Exception ************************************/
	
	@ResponseBody
	@RequestMapping(value="/private/startHarvestingByNetworkID/{networkID}", method=RequestMethod.GET)
	public ResponseEntity<String> startHarvesting(@PathVariable Long networkID) throws Exception {
		
		NationalNetwork network = nationalNetworkRepository.findOne(networkID);
		if ( network == null )
			throw new Exception("No se encontró RED");
		
		snapshotManager.lauchHarvesting(networkID);
		
		return new ResponseEntity<String>("Havesting iniciado red:" + networkID, HttpStatus.OK);
	}
	
	@ResponseBody
	@RequestMapping(value="/private/stopHarvestingBySnapshotID/{id}", method=RequestMethod.GET)
	public ResponseEntity<String> stopHarvesting(@PathVariable Long id) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + id);
		
		snapshotManager.stopHarvesting(id);
		
		return new ResponseEntity<String>("Havesting detenido Snapshot:" + id, HttpStatus.OK);
	}
	
	
	@ResponseBody
	@RequestMapping(value="/private/resumeHarvestingBySnapshotID/{snapshotID}", method=RequestMethod.GET)
	public ResponseEntity<String> resumeHarvestingBySnapshotID(@PathVariable Long snapshotID) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(snapshotID);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + snapshotID);
		
		snapshotManager.relauchHarvesting(snapshotID);
		
		return new ResponseEntity<String>("Relauch havesting:" + snapshotID, HttpStatus.OK);
	}
	
	/**
	 * Este servicio para cada origen explora los sets (no los almacenados sino los provistos por ListSets)
	 * y para cada uno de ellos realiza una cosecha. Si los sets son disjuntos la coschecha final es completa y
	 * sin repeticiones
	 * @param networkID
	 * @return
	 * @throws Exception 
	 */
	@ResponseBody
	@RequestMapping(value="/private/harvestSetBySet/{networkID}", method=RequestMethod.GET)
	public ResponseEntity<String> harvestSetBySet(@PathVariable Long networkID) throws Exception {
		
		NationalNetwork network = nationalNetworkRepository.findOne(networkID);
		if ( network == null )
			throw new Exception("No se encontró RED");
		
		snapshotManager.lauchSetBySetHarvesting(networkID);
		
		return new ResponseEntity<String>("Havesting:" + networkID, HttpStatus.OK);
	}
	
	
	
	
	@Transactional
	@ResponseBody
	@RequestMapping(value="/private/deleteAllButLGKSnapshot/{id}", method=RequestMethod.GET)
	public ResponseEntity<String> deleteAllButLGKSnapshot(@PathVariable Long id) throws Exception {
		
		NationalNetwork network = nationalNetworkRepository.findOne(id);
		if ( network == null )
			throw new Exception("No se encontró RED");
		
		
		NetworkSnapshot lgkSnapshot = networkSnapshotRepository.findLastGoodKnowByNetworkID(id);
		
		
		for ( NetworkSnapshot snapshot:network.getSnapshots() ) {
			
			System.out.println("Evaluando para borrado: " + snapshot.getId());
			
			if ( (lgkSnapshot == null || !snapshot.getId().equals(lgkSnapshot.getId())) && !snapshot.isDeleted() 
					&& snapshot.getStatus() != SnapshotStatus.HARVESTING && snapshot.getStatus() != SnapshotStatus.RETRYING 
					&& snapshot.getStatus() != SnapshotStatus.INDEXING) { // previene el borrado de harvestings en proceso
				
				System.out.println("Borrando ... " + snapshot.getId());
				
				// borra los resultados de validación
				recordValidationRepository.deleteBySnapshotID(snapshot.getId());
				// borra los registros
				recordRepository.deleteBySnapshotID(snapshot.getId());
				// borra el log de cosechas
				networkSnapshotLogRepository.deleteBySnapshotID(snapshot.getId());
				// lo marca borrado
				snapshot.setDeleted(true);
				// almacena el estado del snap
				networkSnapshotRepository.save(snapshot);
			}
		}
		
		
		return new ResponseEntity<String>("Borrados snapshots excedentes de:" + network.getName(), HttpStatus.OK);
	}
	
	@Transactional
	@ResponseBody
	@RequestMapping(value="/private/deleteRecordsBySnapshotID/{id}", method=RequestMethod.GET)
	public ResponseEntity<String> deleteRecordsBySnapshotID(@PathVariable Long id) {
		
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		snapshot.setDeleted(true);
		
		// borra los registros
		recordRepository.deleteBySnapshotID(id);
			
		// borra el log de cosechas
		networkSnapshotLogRepository.deleteBySnapshotID(snapshot.getId());
		
		networkSnapshotRepository.save(snapshot);
		
		return new ResponseEntity<String>("Registros borrados snapshot: " + id.toString(), HttpStatus.OK);
		
	}
	
	@ResponseBody
	@RequestMapping(value="/private/indexValidRecordsBySnapshotID/{id}", method=RequestMethod.GET)
	public ResponseEntity<String> indexRecordsBySnapshotID(@PathVariable Long id) {
		
		// Se crea un proceso separado para la indexación
		IndexerWorker worker = applicationContext.getBean("indexerWorker", IndexerWorker.class);
		worker.setSnapshotID(id);
		scheduler.schedule(worker, new Date());
	
		return new ResponseEntity<String>("Indexando Snapshot: " + id, HttpStatus.OK);
	}
	
	
	@ResponseBody
	@RequestMapping(value="/private/indexLGKSnapshotByNetworkID/{id}", method=RequestMethod.GET)
	public ResponseEntity<String> indexLGKByNetworkID(@PathVariable Long id) {
		
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findLastGoodKnowByNetworkID(id);

		if ( snapshot != null ) {
		
			// Se crea un proceso separado para la indexación
			IndexerWorker worker = applicationContext.getBean("indexerWorker", IndexerWorker.class);
			worker.setSnapshotID(snapshot.getId());
			scheduler.schedule(worker, new Date());
		
			return new ResponseEntity<String>("Indexando LGK Snapshot RED: " + id, HttpStatus.OK);
		} else 
			return new ResponseEntity<String>("No existe LGK Snapshot RED: " + id, HttpStatus.OK);

		
	}

	/**************************** FrontEnd  ************************************/

	@ResponseBody
	@RequestMapping(value="/public/validateOriginalRecordByID/{id}", method=RequestMethod.GET)
	public ResponseEntity<ValidationResult> validateOriginalRecordByID(@PathVariable Long id) throws Exception {
		
		OAIRecord record = recordRepository.findOne(id);
		
		
		if ( record != null ) {
			
			NationalNetwork network = record.getSnapshot().getNetwork();
			IValidator validator = applicationContext.getBean(network.getValidatorName(), IValidator.class);
			
			OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), record.getOriginalXML());
			ValidationResult result = validator.validate(metadata);
			ResponseEntity<ValidationResult> response = new ResponseEntity<ValidationResult>(result, HttpStatus.OK);
			return response; 
		}	
		else
			throw new Exception("Registro inexistente");
		
	}
	
	@ResponseBody
	@RequestMapping(value="/public/validateTransformedRecordByID/{id}", method=RequestMethod.GET)
	public ResponseEntity<ValidationResult> validateTransformedRecordByID(@PathVariable Long id) throws Exception {
		
		OAIRecord record = recordRepository.findOne(id);	
		
		if ( record != null ) {
			
			NationalNetwork network = record.getSnapshot().getNetwork();
			IValidator validator = applicationContext.getBean(network.getValidatorName(), IValidator.class);
			ITransformer transformer = applicationContext.getBean(network.getTransformerName(), ITransformer.class);

			OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), record.getOriginalXML());
			
			ValidationResult preValidationResult = validator.validate(metadata);
			transformer.transform(metadata, preValidationResult);
			ValidationResult posValidationResult = validator.validate(metadata);
	
			ResponseEntity<ValidationResult> response = new ResponseEntity<ValidationResult>(posValidationResult, HttpStatus.OK);
		
			return response;
		}
		else
			throw new Exception("Registro inexistente");
	}
	
	
	
	@ResponseBody
	@RequestMapping(value="/public/harvestMetadataByRecordID/{id}", method=RequestMethod.GET)
	public String harvestyMetadataByRecordID(@PathVariable Long id) throws Exception {
		
		
		OAIRecord record = recordRepository.findOne( id );	
		String result = "";
		
		if ( record != null ) {
			
			NationalNetwork network = record.getSnapshot().getNetwork();
		
			ArrayList<OAIOrigin> origins =  new ArrayList<>( network.getOrigins() );
			String oaiURLBase = origins.get(0).getUri();
			String recordURL = oaiURLBase +  "?verb=GetRecord&metadataPrefix=oai_dc&identifier=" + record.getIdentifier();
			
		
			HttpClient client = new HttpClient();
			client.getParams().setParameter("http.protocol.content-charset", "UTF-8");

			HttpMethod method = new GetMethod(recordURL);
			int responseCode = client.executeMethod(method);
			if (responseCode != 200) {
			    throw new HttpException("HttpMethod Returned Status Code: " + responseCode + " when attempting: " + recordURL);
			}
			
			result = new String( method.getResponseBody(), "UTF-8"); 
			
		}
		
		
		return result;
		
	}
	
	
	@ResponseBody
	@RequestMapping(value="/public/transformInfoByRecordID/{id}", method=RequestMethod.GET)
	public ResponseEntity<OAIRecordTransformationInfo> transformInfoByRecordID(@PathVariable Long id) throws Exception {
		
		OAIRecordTransformationInfo result = new OAIRecordTransformationInfo();
		
		OAIRecord record = recordRepository.findOne( id );	
		
		if ( record != null ) {
			
			NationalNetwork network = record.getSnapshot().getNetwork();
			IValidator validator = applicationContext.getBean(network.getValidatorName(), IValidator.class);
			ITransformer transformer = applicationContext.getBean(network.getTransformerName(), ITransformer.class);
			
			OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), record.getOriginalXML());
			
			ValidationResult preValidationResult = validator.validate(metadata);
			transformer.transform(metadata, preValidationResult);
			ValidationResult posValidationResult = validator.validate(metadata);
	
			result.id = id;
			result.originalHeaderId = record.getIdentifier();
			result.originalMetadata = record.getOriginalXML();
			result.transformedMetadata = metadata.toString();
			result.isOriginalValid = preValidationResult.isValid();
			result.isTransformedValid = posValidationResult.isValid();
			result.preValidationResult = preValidationResult;
			result.posValidationResult = posValidationResult;
			
			ResponseEntity<OAIRecordTransformationInfo> response = new ResponseEntity<OAIRecordTransformationInfo>(result, HttpStatus.OK);
			
			return response;
		}
			else
				throw new Exception("Registro inexistente");
			
	}
	
	@ResponseBody
	@RequestMapping(value="/public/transformRecordByID/{id}", method=RequestMethod.GET)
	public String transformRecordByID(@PathVariable Long id) throws Exception {
		
		
		OAIRecord record = recordRepository.findOne( id );	
		
		if ( record != null ) {
			
			NationalNetwork network = record.getSnapshot().getNetwork();
			IValidator validator = applicationContext.getBean(network.getValidatorName(), IValidator.class);
			ITransformer transformer = applicationContext.getBean(network.getTransformerName(), ITransformer.class);
			
			OAIRecordMetadata metadata = new OAIRecordMetadata(record.getIdentifier(), record.getOriginalXML());
			
			ValidationResult preValidationResult = validator.validate(metadata);
			transformer.transform(metadata, preValidationResult);
	
			
			return metadata.toString();
		
		}
			else
				throw new Exception("Registro inexistente");
			
	}
	
	
	@ResponseBody
	@RequestMapping(value="/public/lastGoodKnowSnapshotByNetworkID/{id}", method=RequestMethod.GET)
	public ResponseEntity<NetworkSnapshot> getLGKSnapshot(@PathVariable Long id) {
			
		NetworkSnapshot snapshot = networkSnapshotRepository.findLastGoodKnowByNetworkID(id);
		ResponseEntity<NetworkSnapshot> response = new ResponseEntity<NetworkSnapshot>(
			snapshot,
			snapshot == null ? HttpStatus.NOT_FOUND : HttpStatus.OK
		);
		return response;
	}

	@ResponseBody
	@RequestMapping(value="/public/getSnapshotByID/{id}", method=RequestMethod.GET)
	public ResponseEntity<NetworkSnapshot> getSnapshotByID(@PathVariable Long id) {
			
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		ResponseEntity<NetworkSnapshot> response = new ResponseEntity<NetworkSnapshot>(
			snapshot,
			snapshot == null ? HttpStatus.NOT_FOUND : HttpStatus.OK
		);
		return response;
	}
	
	
	@ResponseBody
	@RequestMapping(value="/public/lastGoodKnowSnapshotByCountryISO/{iso}", method=RequestMethod.GET)
	public ResponseEntity<NetworkSnapshot> getLGKSnapshot(@PathVariable String iso) throws Exception {
		
		NationalNetwork network = nationalNetworkRepository.findByCountryISO(iso);
		if ( network == null ) // TODO: Implementar Exc
			throw new Exception("No se encontró RED perteneciente a: " + iso);
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findLastGoodKnowByNetworkID(network.getId());
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot válido de la RED: " + iso);
		
		ResponseEntity<NetworkSnapshot> response = new ResponseEntity<NetworkSnapshot>(
			snapshot,
			snapshot == null ? HttpStatus.NOT_FOUND : HttpStatus.OK
		);
		return response;
	}
	
	@ResponseBody
	@RequestMapping(value="/public/listSnapshotsByCountryISO/{iso}", method=RequestMethod.GET)
	public ResponseEntity<List<NetworkSnapshot>> listSnapshotsByCountryISO(@PathVariable String iso) throws Exception {
		
		NationalNetwork network = nationalNetworkRepository.findByCountryISO(iso);
		if ( network == null )
			throw new Exception("No se encontró RED perteneciente a: " + iso);
		
		ResponseEntity<List<NetworkSnapshot>> response = new ResponseEntity<List<NetworkSnapshot>>(networkSnapshotRepository.findByNetworkOrderByEndTimeAsc(network), HttpStatus.OK);
		
		return response;
	}
	
	@ResponseBody
	@RequestMapping(value="/public/listNetworks", method=RequestMethod.GET)
	public ResponseEntity<List<NetworkInfo>> listNetworks() {
		
				
		List<NationalNetwork> allNetworks = nationalNetworkRepository.findByPublishedOrderByNameAsc(true);//OrderByName();
		List<NetworkInfo> NInfoList = new ArrayList<NetworkInfo>();

		for (NationalNetwork network:allNetworks) {
			
			NetworkInfo ninfo = new NetworkInfo();
			ninfo.networkID = network.getId();
			ninfo.country = network.getCountryISO();
			ninfo.name = network.getName();
			
			NetworkSnapshot snapshot = networkSnapshotRepository.findLastGoodKnowByNetworkID(network.getId());
			
			if ( snapshot != null) {
				
				ninfo.snapshotID = snapshot.getId();
				ninfo.datestamp = snapshot.getEndTime();
				ninfo.size = snapshot.getSize();
				ninfo.validSize = snapshot.getValidSize();
				
			}		
			NInfoList.add( ninfo );		
		}
	
		ResponseEntity<List<NetworkInfo>> response = new ResponseEntity<List<NetworkInfo>>(NInfoList, HttpStatus.OK);
		
		return response;
	}
	
	@ResponseBody
	@RequestMapping(value="/public/listNetworksHistory", method=RequestMethod.GET)
	public ResponseEntity<List<NetworkHistory>> listNetworksHistory() {
		
		List<NationalNetwork> allNetworks = nationalNetworkRepository.findByPublishedOrderByNameAsc(true);//OrderByName();
		List<NetworkHistory> NHistoryList = new ArrayList<NetworkHistory>();

		for (NationalNetwork network:allNetworks) {	
			NetworkHistory nhistory = new NetworkHistory();
			nhistory.networkID = network.getId();
			nhistory.country = network.getCountryISO();
			nhistory.validSnapshots =  networkSnapshotRepository.findByNetworkAndStatusOrderByEndTimeAsc(network, SnapshotStatus.VALID);
			NHistoryList.add( nhistory );		
		}
	
		ResponseEntity<List<NetworkHistory>> response = new ResponseEntity<List<NetworkHistory>>(NHistoryList, HttpStatus.OK);
		
		return response;
	}
	
	@RequestMapping(value="/public/listProviderStats", method=RequestMethod.GET)
	@ResponseBody
	public PageResource<OAIProviderStat> listProviderStats(@RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) {
		
		if (page == null)
			page = 0;
		if (size == null)
			size = 100;
		
		Page<OAIProviderStat> pageResult = oaiProviderStatRepository.findAll( new PageRequest(page, size, new Sort(Sort.Direction.DESC,"requestCount")));	
		
		return new PageResource<OAIProviderStat>(pageResult,"page","size");
	}
	
	
	@RequestMapping(value="/public/listOriginsBySnapshotID/{id}", method=RequestMethod.GET)
	@ResponseBody
	public List<OAIOrigin> listOriginsBySnapshotID(@PathVariable Long id) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + id);
		
		return (List<OAIOrigin>) snapshot.getNetwork().getOrigins();
		
	}
	
	
	
	
	@RequestMapping(value="/public/listInvalidRecordsInfoBySnapshotID/{id}", method=RequestMethod.GET)
	@ResponseBody
	public PageResource<OAIRecord> listInvalidRecordsInfoBySnapshotID(@PathVariable Long id, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + id);
			
		if (page == null)
			page = 0;
		if (size == null)
			size = 100;
		
		Page<OAIRecord> pageResult = recordRepository.findBySnapshotAndStatus(snapshot, RecordStatus.INVALID, new PageRequest(page, size));	
		
		return new PageResource<OAIRecord>(pageResult,"page","size");
	}
	
	@RequestMapping(value="/public/listInvalidRecordsInfoByFieldAndSnapshotID/{field}/{id}", method=RequestMethod.GET)
	@ResponseBody
	public PageResource<OAIRecord> listInvalidRecordsInfoByFieldAndSnapshotID(@PathVariable String field, @PathVariable Long id, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + id);
			
		if (page == null)
			page = 0;
		if (size == null)
			size = 100;
		
		Page<OAIRecord> pageResult = recordRepository.findBySnapshotIdAndInvalidField(id, field, new PageRequest(page, size));	
		
		return new PageResource<OAIRecord>(pageResult,"page","size");
	}
	
	
	@RequestMapping(value="/public/listTransformedRecordsInfoBySnapshotID/{id}", method=RequestMethod.GET)
	@ResponseBody
	public PageResource<OAIRecord> listTransformedRecordsInfoBySnapshotID(@PathVariable Long id, @RequestParam(required=false) Integer page, @RequestParam(required=false) Integer size) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot con id: " + id);
			
		if (page == null)
			page = 0;
		if (size == null)
			size = 100;
		
		Page<OAIRecord> pageResult = recordRepository.findBySnapshotAndWasTransformed(snapshot, true, new PageRequest(page, size));	
		
		return new PageResource<OAIRecord>(pageResult,"page","size");
	}
	
	
	@ResponseBody
	@RequestMapping(value="/public/metadataOccurrenceCountBySnapshotId/{id}", method=RequestMethod.GET)
	public List<NetworkSnapshotStat> metadataOccurrenceCountBySnapshotId(@PathVariable Long id) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		if (snapshot == null) 
			throw new Exception("No se encontró snapshot: " + id);
		
		List<NetworkSnapshotStat> stats = statsRepository.findBySnapshotAndStatId(snapshot, MetadataOccurrenceCountSnapshotStatProcessor.ID);
		
		return stats;
	}
	
	@ResponseBody
	@RequestMapping(value="/public/rejectedFieldCountBySnapshotId/{id}", method=RequestMethod.GET)
	public List<NetworkSnapshotStat> rejectedFieldCountBySnapshotId(@PathVariable Long id) throws Exception {
		
		NetworkSnapshot snapshot = networkSnapshotRepository.findOne(id);
		if (snapshot == null) // TODO: Implementar Exc
			throw new Exception("No se encontró snapshot: " + id);
		
		List<NetworkSnapshotStat> stats = statsRepository.findBySnapshotAndStatId(snapshot, RejectedByFieldSnapshotStatProcessor.ID);
			
		return stats;
	}
	
	/**************  Clases de retorno de resultados *******************/
	
	@Getter
	@Setter
	class NetworkInfo {	
		private Long   networkID;
		private String country;
		private String name;
		
		private Long snapshotID;
		
		@JsonSerialize(using=JsonDateSerializer.class)
		private Date datestamp;
		private int size;
		private int validSize;
	}
	
	@Getter
	@Setter
	class NetworkHistory {	
		private Long   networkID;
		private String country;
		private List<NetworkSnapshot> validSnapshots;
	}
	
	@Getter
	@Setter
	class OAIRecordValidationInfo {	
		private Long   id;
		private String originalHeaderId;
		private boolean isValid;
		private boolean isDriverType;
		private String  dcTypeFieldContents;
	}
	
	@Getter
	@Setter
	class OAIRecordTransformationInfo {	
		private Long   id;
		private String originalHeaderId;
		private String originalMetadata;
		private String transformedMetadata;
		
		private ValidationResult preValidationResult;
		private ValidationResult posValidationResult;
		
		private boolean isOriginalValid;
		private boolean isTransformedValid;
	}
	
	
}
