package ortus.boxlang.lsp.workspace;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import ortus.boxlang.lsp.App;

/**
 * Provides debouncing functionality for document processing operations.
 * When multiple changes occur in rapid succession, only the final state
 * is processed after a configurable delay.
 */
public class DebouncedDocumentProcessor {

	private static final long									DEFAULT_DEBOUNCE_DELAY_MS	= 300;

	private final ScheduledExecutorService						scheduler;
	private final Map<URI, ScheduledFuture<?>>					pendingTasks;
	private final Map<URI, Runnable>							pendingActions;
	private final long											debounceDelayMs;

	private Consumer<URI>										onProcessDocument;

	public DebouncedDocumentProcessor() {
		this( DEFAULT_DEBOUNCE_DELAY_MS );
	}

	public DebouncedDocumentProcessor( long debounceDelayMs ) {
		this.debounceDelayMs	= debounceDelayMs;
		this.scheduler			= Executors.newSingleThreadScheduledExecutor( r -> {
									Thread t = new Thread( r, "DocumentProcessor-Debounce" );
									t.setDaemon( true );
									return t;
								} );
		this.pendingTasks		= new ConcurrentHashMap<>();
		this.pendingActions		= new ConcurrentHashMap<>();
	}

	/**
	 * Sets the callback to be invoked when a document should be processed.
	 *
	 * @param callback The callback that receives the document URI
	 */
	public void setProcessCallback( Consumer<URI> callback ) {
		this.onProcessDocument = callback;
	}

	/**
	 * Schedules document processing after the debounce delay.
	 * If a previous processing is already scheduled, it is cancelled
	 * and a new one is scheduled.
	 *
	 * @param uri The document URI to process
	 */
	public void scheduleProcessing( URI uri ) {
		scheduleProcessing( uri, null );
	}

	/**
	 * Schedules document processing with a custom action.
	 * If a previous processing is already scheduled, it is cancelled
	 * and a new one is scheduled.
	 *
	 * @param uri The document URI to process
	 * @param customAction Optional custom action to run instead of default callback
	 */
	public void scheduleProcessing( URI uri, Runnable customAction ) {
		// Cancel any existing scheduled task for this document
		ScheduledFuture<?> existingTask = pendingTasks.get( uri );
		if ( existingTask != null ) {
			existingTask.cancel( false );
		}

		// Store the action if provided
		if ( customAction != null ) {
			pendingActions.put( uri, customAction );
		}

		// Schedule the new task
		ScheduledFuture<?> newTask = scheduler.schedule( () -> {
			try {
				pendingTasks.remove( uri );
				Runnable action = pendingActions.remove( uri );

				if ( action != null ) {
					action.run();
				} else if ( onProcessDocument != null ) {
					onProcessDocument.accept( uri );
				}
			} catch ( Exception e ) {
				App.logger.error( "Error processing document: " + uri, e );
			}
		}, debounceDelayMs, TimeUnit.MILLISECONDS );

		pendingTasks.put( uri, newTask );
	}

	/**
	 * Immediately processes a document, cancelling any pending debounced processing.
	 *
	 * @param uri The document URI to process
	 */
	public void processImmediately( URI uri ) {
		// Cancel any pending task
		ScheduledFuture<?> existingTask = pendingTasks.remove( uri );
		if ( existingTask != null ) {
			existingTask.cancel( false );
		}
		pendingActions.remove( uri );

		// Process immediately
		if ( onProcessDocument != null ) {
			try {
				onProcessDocument.accept( uri );
			} catch ( Exception e ) {
				App.logger.error( "Error processing document immediately: " + uri, e );
			}
		}
	}

	/**
	 * Cancels any pending processing for a document.
	 *
	 * @param uri The document URI
	 */
	public void cancelPendingProcessing( URI uri ) {
		ScheduledFuture<?> existingTask = pendingTasks.remove( uri );
		if ( existingTask != null ) {
			existingTask.cancel( false );
		}
		pendingActions.remove( uri );
	}

	/**
	 * Checks if a document has pending processing.
	 *
	 * @param uri The document URI
	 * @return true if processing is pending
	 */
	public boolean hasPendingProcessing( URI uri ) {
		ScheduledFuture<?> task = pendingTasks.get( uri );
		return task != null && !task.isDone() && !task.isCancelled();
	}

	/**
	 * Gets the debounce delay in milliseconds.
	 *
	 * @return The debounce delay
	 */
	public long getDebounceDelayMs() {
		return debounceDelayMs;
	}

	/**
	 * Shuts down the processor.
	 */
	public void shutdown() {
		scheduler.shutdown();
		try {
			if ( !scheduler.awaitTermination( 5, TimeUnit.SECONDS ) ) {
				scheduler.shutdownNow();
			}
		} catch ( InterruptedException e ) {
			scheduler.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}
}
