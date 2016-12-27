package at.ac.tuwien.infosys.viepep.util;

import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope("singleton")
public class TimeUtil extends Thread {

	public static final AtomicLong TIME = new AtomicLong(new Date().getTime());

	public static final AtomicBoolean STARTED = new AtomicBoolean();
	public static final AtomicBoolean RUNNING = new AtomicBoolean();

	public static final long TIME_INCREMENTS_MS = 1000;
	public static long SEC_SLEEP_TIME_MS = 50;

	private static TimeUtil INSTANCE;

    @Value("${simulate}")
    private boolean simulate;

    public TimeUtil() {
    	if(INSTANCE == null)
    		INSTANCE = this;
    	/* auto-start thread on creation */
		start();
	}

    @Override
    public void run() {
    	synchronized (STARTED) {
			if(STARTED.get()) {
				return;
			}
			STARTED.set(true);
		}
    	while(true) {
    		if(RUNNING.get()) {
    			int tickOutputBatch = 10;
    			if((TIME.get() / 1000) % tickOutputBatch == 0) {
            		System.out.println(tickOutputBatch + " ticks passed");
    			}
        		synchronized (TIME) {
    				TIME.set(TIME.get() + TIME_INCREMENTS_MS);
    				TIME.notifyAll();
    			}
    		}
    		doSleepSafe(SEC_SLEEP_TIME_MS);
    	}
    }

    public void sleepMillis(long millis, boolean debug) {
    	if(debug) {
    		System.out.println("Sleep for " + millis + " - debug " + debug + " - simulate " + simulate);
    	}
    	if(!simulate) {
    		doSleepSafe(millis);
    		return;
    	}
    	long targetTime = now() + millis;
		try {
			long timeNow = now();
			while(targetTime > timeNow) {
				if(debug) {
					System.out.println("Waiting " + timeNow + " - " + targetTime);
				}
				synchronized (TIME) {
					TIME.wait();
				}
				timeNow = now();
			}
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
    }

    public static void sleep(long millis) {
    	sleep(millis, false);
    }

    public static void sleep(long millis, boolean debug) {
    	ensureInstance();
    	INSTANCE.sleepMillis(millis, debug);
    }

    private static void ensureInstance() {
    	if(INSTANCE == null) {
    		INSTANCE = new TimeUtil();
    	}
    }

    private void doSleepSafe(long millis) {
    	try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
    }

    public static void startTicking() {
    	RUNNING.set(true);
    }

    public static void pauseTicking() {
    	RUNNING.set(false);
    }

    public static long now() {
    	ensureInstance();
    	if(!INSTANCE.simulate) {
    		return new Date().getTime();
    	}
		return TIME.get();
	}

    public static Date nowDate() {
    	ensureInstance();
    	if(!INSTANCE.simulate) {
    		return new Date();
    	}
    	return new Date(TIME.get());
    }

}
