package ortus.boxlang.lsp;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryNotificationInfo;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.util.List;

import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;

import ortus.boxlang.compiler.parser.Parser;

public class MemoryThresholdMonitor {

	public static final double	THRESHOLD	= 0.85d;
	private static boolean		started		= false;

	public static void startMemoryManagement() {

		if ( started ) {
			App.logger.info( "Memory management task already started" );
			return;
		}

		App.logger.info( "Starting memory management utilities" );
		startPeriodicTask();
		registerListener();

		started = true;
	}

	private static void startPeriodicTask() {
		java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate( () -> {
			App.logger.info( "Running periodic task: clearing the parser cache" );
			int size = Parser.getCacheSize();
			Parser.clearParseCache();
			App.logger.info( "Freed {} parser states", size );
		}, 0, 5, java.util.concurrent.TimeUnit.MINUTES );
	}

	private static void registerListener() {
		// Set thresholds on heap memory pools
		List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
		for ( MemoryPoolMXBean pool : memoryPools ) {
			if ( pool.getType() == MemoryType.HEAP && pool.isUsageThresholdSupported() ) {
				long max = pool.getUsage().getMax();

				if ( max > 0 ) {
					long threshold = ( long ) ( max * 0.85 );
					pool.setUsageThreshold( threshold );
				}
			}
		}

		// Add notification listener
		MemoryMXBean		memoryBean	= ManagementFactory.getMemoryMXBean();
		NotificationEmitter	emitter		= ( NotificationEmitter ) memoryBean;
		emitter.addNotificationListener( new NotificationListener() {

			@Override
			public void handleNotification( Notification notification, Object handback ) {
				if ( notification.getType().equals( MemoryNotificationInfo.MEMORY_THRESHOLD_EXCEEDED ) ) {
					App.logger.info( "Memory threshold exceeded: clearing the parser cache" );
					int size = Parser.getCacheSize();
					Parser.clearParseCache();
					App.logger.info( "Freed {} parser states", size );
				}
			}
		}, null, null );
	}
}