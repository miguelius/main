package org.lareferencia.backend.tasks;

import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.lareferencia.backend.domain.NationalNetwork;
import org.lareferencia.backend.repositories.NationalNetworkRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

@Component
public class SnapshotManager {
	
	@Autowired
	private NationalNetworkRepository networkRepository;
	
	@Autowired
	private TaskScheduler scheduler;
	
	@Autowired 
	private ApplicationContext applicationContext;

	
	private ConcurrentLinkedQueue<ISnapshotWorker> activeProcessors;
	
	
	public SnapshotManager() {
		activeProcessors = new ConcurrentLinkedQueue<ISnapshotWorker>();
	}
	
	/**
	 * Consulta el repositorio, obtiene las redes, y actualiza el estado de los procesos
	 */	
	public synchronized void refresh() {
		
		/** TODO: Hay que implementar una política de refresh más completa,
		 *  Son varios los casos a analizar e iran siendo contemplados en futuras
		 *  iteraciones.
		 */
		Collection<NationalNetwork> storedNetworks = networkRepository.findAll();
		
		for ( NationalNetwork storedNetwork:storedNetworks ) {
			ISnapshotWorker processor = (ISnapshotWorker) applicationContext.getBean("snapshotWorker");
			processor.setNetwork(storedNetwork);
			activeProcessors.add(processor);
			scheduler.schedule(processor, new SnapshotCronTrigger(storedNetwork) );
		}
		
	}
	
	
	

}
