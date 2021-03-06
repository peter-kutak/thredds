package thredds.inventory;

import net.jcip.annotations.ThreadSafe;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import thredds.featurecollection.FeatureCollectionConfig;

import java.util.Date;

/**
 * Handle background tasks for updating collections.
 * Singleton, thread safe.
 * Cover for quartz library.
 * Only used in tds/tdm.
 *
 * @author caron
 * @since Nov 21, 2010
 */
@ThreadSafe
public enum CollectionUpdater {
  INSTANCE;   // Singleton cf Bloch p 18

  static private final org.slf4j.Logger startupLogger = org.slf4j.LoggerFactory.getLogger(CollectionUpdater.class);
  static private final String DCM_NAME = "dcm";
  static private final String LOGGER = "logger";
  static private final String UpdateType = "updateType";
  static private final long startupWait = 3 * 1000; // 3 secs
  static private boolean disabled = false;

  // could use Spring DI
  private org.quartz.Scheduler scheduler = null;
  private boolean failed = false;
  private boolean isTdm = false;

  public void setTdm(boolean tdm) {
    isTdm = tdm;
  }

  public boolean isTdm() {
    return isTdm;
  }

  private CollectionUpdater() {
    try {
      scheduler = StdSchedulerFactory.getDefaultScheduler();
      scheduler.start();
      // scheduler.getListenerManager().addSchedulerListener(new MySchedListener());
    } catch (Throwable e) {
      failed = true;
      throw new RuntimeException("quartz scheduler failed to initialize", e);
    }
  }

  public org.quartz.Scheduler getScheduler() {
    return scheduler;
  }

  /**
   *
   * @param config
   * @param manager CollectionUpdateListener
   * @param logger
   */
  public void scheduleTasks(FeatureCollectionConfig config, CollectionUpdateListener manager, Logger logger) {
    if (disabled || failed) return;

    FeatureCollectionConfig.UpdateConfig updateConfig = (isTdm) ? config.tdmConfig : config.updateConfig;
    if (updateConfig == null) return;

    //String jobName = config.name + "-" + Integer.toHexString(config.hashCode());
    String jobName = manager.getCollectionName();

    // Job to update the collection
    org.quartz.JobDataMap map = new org.quartz.JobDataMap();
    map.put(DCM_NAME, manager);
    //map.put(UpdateType, updateConfig.updateType);
    if (logger != null) map.put(LOGGER, logger);
    JobDetail updateJob = JobBuilder.newJob(UpdateCollectionJob.class)
            .withIdentity(jobName, "UpdateCollection")
            .storeDurably()
            .usingJobData(map)
            .build();

    try {
      if(!scheduler.checkExists(updateJob.getKey())) {
      	scheduler.addJob(updateJob, false);
      } else {
        if (logger != null) logger.warn("cronExecutor failed to add updateJob for " + updateJob.getKey() +". Another Job exists with that identification." );
      }
    } catch (Throwable e) {
      if (logger != null)logger.error("cronExecutor failed to add updateJob for " + config, e);
      return;
    }

    // startup always runs
    map = new org.quartz.JobDataMap();
    map.put(UpdateType, updateConfig.startupType);
    Date runTime = new Date(new Date().getTime() + startupWait); // wait startupWait before trigger
    SimpleTrigger startupTrigger = (SimpleTrigger) TriggerBuilder.newTrigger()
            .withIdentity(jobName, "startup")
            .startAt(runTime)
            .forJob(updateJob)
            .usingJobData(map)
            .build();

    try {
      scheduler.scheduleJob(startupTrigger);
      if (logger != null)logger.info("Schedule startup scan force={} for '{}' at {}", updateConfig.startupType.toString(), config.name, runTime);
    } catch (Throwable e) {
      if (logger != null)logger.error("cronExecutor failed to schedule startup Job for " + config, e);
      return;
    }

    if (updateConfig.rescan != null) {
        map = new org.quartz.JobDataMap();
        map.put(UpdateType, updateConfig.updateType);
        CronTrigger rescanTrigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName, "rescan")
                .withSchedule(CronScheduleBuilder.cronSchedule(updateConfig.rescan))
                .forJob(updateJob)
                .usingJobData(map)
                .build();

      try {
    		scheduler.scheduleJob(rescanTrigger);
        if (logger != null)logger.info("Schedule recurring scan for '{}' cronExpr={}", config.name, updateConfig.rescan);
      } catch (Throwable e) {
        if (logger != null)logger.error("cronExecutor failed to schedule cron Job", e);
        // e.printStackTrace();
      }
    }

    // updating the proto dataset
    FeatureCollectionConfig.ProtoConfig pconfig = config.protoConfig;
    if (pconfig.change != null) {
      org.quartz.JobDataMap pmap = new org.quartz.JobDataMap();
      pmap.put(DCM_NAME, manager);
      map.put(LOGGER, logger);
      JobDetail protoJob = JobBuilder.newJob(ChangeProtoJob.class)
              .withIdentity(jobName, "UpdateProto")
              .usingJobData(pmap)
              .storeDurably()
              .build();

      try {
        CronTrigger protoTrigger = TriggerBuilder.newTrigger()
                .withIdentity(jobName, "rereadProto")
                .withSchedule(CronScheduleBuilder.cronSchedule(pconfig.change))
                .build();
        scheduler.scheduleJob(protoJob, protoTrigger);
        if (logger != null)logger.info("Schedule proto update for '{}' cronExpr={}", config.name, pconfig.change);

      } catch (Throwable e) {
        if (logger != null)logger.error("cronExecutor failed to schedule RereadProtoJob", e);
        // e.printStackTrace();
      }
    }

  }

  public void shutdown() {
    if (scheduler == null) return;
    try {
      scheduler.shutdown(true);
      org.slf4j.Logger logServerStartup = org.slf4j.LoggerFactory.getLogger("serverStartup");
      logServerStartup.info("Scheduler shutdown");
    } catch (Throwable e) {
      startupLogger.error("Scheduler failed to shutdown", e);
      scheduler = null;
      //e.printStackTrace();
    }
  }

  // Called by TDS collectionController when trigger is received externally
  public void triggerUpdate(String collectionName, CollectionUpdateType triggerType) {
    JobDataMap map = new org.quartz.JobDataMap();
    map.put(UpdateType, triggerType);
    Trigger trigger = TriggerBuilder.newTrigger()
            .withIdentity(collectionName+"-trigger", triggerType.toString()) // dorky
            .forJob(collectionName, "UpdateCollection") // ??
            .usingJobData(map)
            .startNow()
            .build();

    try {
      // logger.debug("Trigger Update for {} type= {}", collectionName, triggerType);
      scheduler.scheduleJob(trigger);
    } catch (Throwable e) {
      startupLogger.error("triggerUpdate failed", e);
      // e.printStackTrace();
    }
  }

  // do the work in a separate thread
  public static class UpdateCollectionJob implements org.quartz.Job {
    public UpdateCollectionJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
      CollectionUpdateListener manager = (CollectionUpdateListener) context.getJobDetail().getJobDataMap().get(DCM_NAME);
      org.slf4j.Logger loggerfc = (org.slf4j.Logger) context.getJobDetail().getJobDataMap().get(LOGGER);
      CollectionUpdateType type= (CollectionUpdateType) context.getTrigger().getJobDataMap().get(UpdateType);
      String groupName = context.getTrigger().getKey().getGroup();

      try {
        manager.sendEvent(type);
        startupLogger.debug("CollectionUpdate {} on {}", type, manager.getCollectionName());

      } catch (Throwable e) {
        if (loggerfc != null) loggerfc.error("UpdateCollectionJob.execute "+groupName+" failed collection=" + manager.getCollectionName(), e);
      }
    }
  }

  // disabled LOOK
  public static class ChangeProtoJob implements org.quartz.Job {
    public ChangeProtoJob() {
    }

    public void execute(JobExecutionContext context) throws JobExecutionException {
      CollectionUpdateListener manager = (CollectionUpdateListener) context.getJobDetail().getJobDataMap().get(DCM_NAME);
      org.slf4j.Logger loggerfc = (org.slf4j.Logger) context.getJobDetail().getJobDataMap().get(LOGGER);

      try {
        if (loggerfc != null) loggerfc.info("ResetProto for {}", manager.getCollectionName());
        // manager.sendEvent(CollectionUpdateListener.TriggerType.resetProto);
      } catch (Throwable e) {
        if (loggerfc != null) loggerfc.error("ChangeProtoJob.execute failed collection=" + manager.getCollectionName(), e);
      }
    }
  }
}
